package com.docsync.docgen.service;

import com.docsync.analysis.entity.CodeEntity;
import com.docsync.analysis.entity.DocumentationPage;
import com.docsync.analysis.repository.CodeEntityRepository;
import com.docsync.analysis.repository.DocumentationCodeReferenceRepository;
import com.docsync.analysis.repository.DocumentationPageRepository;
import com.docsync.common.adapter.llm.LlmClient;
import com.docsync.common.exception.ExternalServiceException;
import com.docsync.common.exception.ValidationException;
import com.docsync.docgen.entity.ChangeStatus;
import com.docsync.docgen.entity.ChangeType;
import com.docsync.docgen.entity.GeneratedDocumentationChange;
import com.docsync.docgen.entity.GenerationRun;
import com.docsync.docgen.repository.GeneratedDocumentationChangeRepository;
import com.docsync.docgen.repository.GenerationRunRepository;
import com.docsync.repository.entity.Repository;
import com.docsync.repository.service.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class DocumentationGenerationServiceImpl implements DocumentationGenerationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentationGenerationServiceImpl.class);
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern SSN = Pattern.compile("\\b\\d{3}-\\d{2}-\\d{4}\\b");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    private final GeneratedDocumentationChangeRepository changeRepository;
    private final GenerationRunRepository runRepository;
    private final DocumentationPageRepository pageRepository;
    private final DocumentationCodeReferenceRepository referenceRepository;
    private final CodeEntityRepository codeEntityRepository;
    private final RepositoryService repositoryService;
    private final com.docsync.analysis.access.RepositoryFileAccessor fileAccessor;
    private final DocumentationPromptBuilder promptBuilder;
    private final ContentDiffGenerator diffGenerator;
    private final Optional<LlmClient> llmClient;
    private final Clock clock;
    private final String model;
    private final int maxTokens;
    private final int maxRetries;

    public DocumentationGenerationServiceImpl(GeneratedDocumentationChangeRepository changeRepository,
                                             GenerationRunRepository runRepository,
                                             DocumentationPageRepository pageRepository,
                                             DocumentationCodeReferenceRepository referenceRepository,
                                             CodeEntityRepository codeEntityRepository,
                                             RepositoryService repositoryService,
                                             com.docsync.analysis.access.RepositoryFileAccessor fileAccessor,
                                             DocumentationPromptBuilder promptBuilder,
                                             ContentDiffGenerator diffGenerator,
                                             Optional<LlmClient> llmClient,
                                             Clock clock,
                                             @Value("${docsync.llm.model:gpt-4}") String model,
                                             @Value("${docsync.llm.max-tokens:4000}") int maxTokens,
                                             @Value("${docsync.llm.max-retries:3}") int maxRetries) {
        this.changeRepository = changeRepository;
        this.runRepository = runRepository;
        this.pageRepository = pageRepository;
        this.referenceRepository = referenceRepository;
        this.codeEntityRepository = codeEntityRepository;
        this.repositoryService = repositoryService;
        this.fileAccessor = fileAccessor;
        this.promptBuilder = promptBuilder;
        this.diffGenerator = diffGenerator;
        this.llmClient = llmClient;
        this.clock = clock;
        this.model = model;
        this.maxTokens = maxTokens;
        this.maxRetries = maxRetries;
    }

    @Override
    public GenerationResult generateUpdates(UUID repositoryId, UUID analysisId) {
        Repository repository = repositoryService.findRepositoryById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));
        LlmClient client = llmClient.orElse(null);
        if (client == null || !client.isAvailable()) {
            throw new ExternalServiceException("LLM", "LLM provider is not available");
        }

        GenerationRun run = new GenerationRun(repositoryId, analysisId);
        runRepository.save(run);

        String commitSha = analysisId == null ? repository.getLastCommitSha() : null;
        if (commitSha == null || commitSha.isBlank()) {
            commitSha = repository.getLastCommitSha() == null ? repository.getDefaultBranch() : repository.getLastCommitSha();
        }

        List<DocumentationPage> pages = pageRepository.findAllByRepositoryId(repositoryId).stream()
                .filter(page -> analysisId == null || analysisId.equals(page.getAnalysisId()))
                .toList();

        List<GeneratedDocumentationChange> changes = new ArrayList<>();
        int totalTokens = 0;
        Instant start = Instant.now(clock);

        for (DocumentationPage page : pages) {
            GeneratedDocumentationChange change = generateForPage(run, repository, page, commitSha, client);
            changes.add(change);
            totalTokens += 30;
        }

        run.setStatus("COMPLETED");
        run.setCompletedAt(Instant.now(clock));
        runRepository.save(run);
        return new GenerationResult(run.getId(), changes, totalTokens,
                Duration.between(start, Instant.now(clock)));
    }

    private GeneratedDocumentationChange generateForPage(GenerationRun run, Repository repository,
                                                         DocumentationPage page, String commitSha, LlmClient client) {
        String original = fileAccessor.readFile(repository, page.getPath(), commitSha);
        if (original == null) {
            original = "";
        }

        List<CodeEntity> referencedEntities = referenceRepository
                .findAllByRepositoryIdAndPageId(repository.getId(), page.getId()).stream()
                .map(ref -> codeEntityRepository.findAllByRepositoryIdAndCommitSha(repository.getId(), commitSha).stream()
                        .filter(entity -> entity.getQualifiedName().equals(ref.getQualifiedName()))
                        .findFirst().orElse(null))
                .filter(entity -> entity != null)
                .toList();

        String codeEntitiesText = referencedEntities.stream()
                .map(entity -> entity.getQualifiedName() + " : " + entity.getSignature())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("No referenced code entities.");

        String codeChangesText = "Update documentation to match the current code entities.";

        LlmClient.LlmRequest request = promptBuilder.buildUpdatePrompt(
                page.getPath(), original, codeChangesText, codeEntitiesText, model, maxTokens);

        LlmClient.LlmResponse response = completeWithRetry(client, request);
        validateGeneratedContent(response.content(), referencedEntities);

        String diff = diffGenerator.generateUnifiedDiff(original, response.content(), page.getPath());
        int version = changeRepository.countByRepositoryIdAndFilePath(repository.getId(), page.getPath()) + 1;

        GeneratedDocumentationChange change = new GeneratedDocumentationChange(
                run.getId(), repository.getId(), page.getAnalysisId(), page.getPath(),
                ChangeType.UPDATE, original, response.content(), diff,
                "Generated from analysis " + page.getAnalysisId(), new BigDecimal("0.80"), version);
        return changeRepository.save(change);
    }

    private LlmClient.LlmResponse completeWithRetry(LlmClient client, LlmClient.LlmRequest request) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                return client.complete(request);
            } catch (RuntimeException ex) {
                last = ex;
                if (attempt < maxRetries - 1) {
                    try {
                        Thread.sleep((long) Math.pow(2, attempt) * 100L);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        throw ex;
                    }
                }
            }
        }
        throw last == null ? new ExternalServiceException("LLM", "LLM request failed") : last;
    }

    private void validateGeneratedContent(String content, List<CodeEntity> entities) {
        if (content == null || content.isBlank()) {
            throw new ValidationException("generatedContent", "Generated content is empty");
        }
        long fenceCount = content.lines().filter(line -> line.trim().startsWith("```")).count();
        if (fenceCount % 2 != 0) {
            throw new ValidationException("generatedContent", "Markdown code fences are unbalanced");
        }
        if (EMAIL.matcher(content).find() || SSN.matcher(content).find()) {
            throw new ValidationException("generatedContent", "Generated content contains PII");
        }

        Set<String> knownNames = entities.stream().map(CodeEntity::getSimpleName).collect(java.util.stream.Collectors.toSet());
        // Only validate identifiers that appear in inline code or fenced code blocks, not prose.
        java.util.regex.Matcher codeSpan = Pattern.compile("`([^`]+)`").matcher(content);
        java.util.Set<String> codeTokens = new java.util.HashSet<>();
        while (codeSpan.find()) {
            for (Matcher matcher = IDENTIFIER.matcher(codeSpan.group(1)); matcher.find(); ) {
                codeTokens.add(matcher.group());
            }
        }
        java.util.regex.Matcher fenced = Pattern.compile("```[^\\n]*\\n(.*?)```", Pattern.DOTALL).matcher(content);
        while (fenced.find()) {
            for (Matcher matcher = IDENTIFIER.matcher(fenced.group(1)); matcher.find(); ) {
                codeTokens.add(matcher.group());
            }
        }
        for (String token : codeTokens) {
            if (Character.isUpperCase(token.charAt(0)) && token.length() > 2 && !knownNames.contains(token)
                    && !Set.of("README", "API", "JSON", "HTTP", "URL").contains(token)) {
                throw new ValidationException("generatedContent",
                        "Generated content references unknown symbol: " + token);
            }
        }
    }

    @Override
    public GeneratedDocumentationChange getChange(UUID changeId) {
        return changeRepository.findById(changeId)
                .orElseThrow(() -> new IllegalArgumentException("Generated change not found: " + changeId));
    }

    @Override
    public List<GeneratedDocumentationChange> getChangesForRepository(UUID repositoryId) {
        return changeRepository.findAllByRepositoryIdOrderByCreatedAtDesc(repositoryId);
    }

    @Override
    public void approveChange(UUID changeId) {
        GeneratedDocumentationChange change = getChange(changeId);
        change.setStatus(ChangeStatus.APPROVED);
        changeRepository.save(change);
    }

    @Override
    public void rejectChange(UUID changeId) {
        GeneratedDocumentationChange change = getChange(changeId);
        change.setStatus(ChangeStatus.REJECTED);
        changeRepository.save(change);
    }

    @Override
    public GeneratedDocumentationChange regenerateChange(UUID changeId) {
        GeneratedDocumentationChange existing = getChange(changeId);
        existing.setStatus(ChangeStatus.REGENERATED);
        changeRepository.save(existing);

        Repository repository = repositoryService.findRepositoryById(existing.getRepositoryId())
                .orElseThrow(() -> new IllegalArgumentException("Repository not found"));
        // Re-run generation for the affected page by filtering to that path.
        GenerationRun run = new GenerationRun(repository.getId(), existing.getAnalysisId());
        runRepository.save(run);

        DocumentationPage page = pageRepository.findAllByRepositoryId(repository.getId()).stream()
                .filter(p -> p.getPath().equals(existing.getFilePath()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Documentation page not found"));

        LlmClient client = llmClient.orElse(null);
        if (client == null || !client.isAvailable()) {
            throw new ExternalServiceException("LLM", "LLM provider is not available");
        }
        String commitSha = repository.getLastCommitSha() == null ? repository.getDefaultBranch() : repository.getLastCommitSha();
        GeneratedDocumentationChange regenerated = generateForPage(run, repository, page, commitSha, client);
        run.setStatus("COMPLETED");
        run.setCompletedAt(Instant.now(clock));
        runRepository.save(run);
        return regenerated;
    }
}

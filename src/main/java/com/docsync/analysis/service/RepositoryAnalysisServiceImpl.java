package com.docsync.analysis.service;

import com.docsync.analysis.access.RepositoryFileAccessor;
import com.docsync.analysis.entity.Analysis;
import com.docsync.analysis.entity.AnalysisScope;
import com.docsync.analysis.entity.AnalysisStatus;
import com.docsync.analysis.entity.CodeEntity;
import com.docsync.analysis.entity.CodeEntityType;
import com.docsync.analysis.entity.DocumentationCodeReference;
import com.docsync.analysis.entity.DocumentationPage;
import com.docsync.analysis.entity.DocumentationType;
import com.docsync.analysis.entity.Finding;
import com.docsync.analysis.entity.FindingCategory;
import com.docsync.analysis.entity.FindingSeverity;
import com.docsync.analysis.entity.FindingStatus;
import com.docsync.analysis.parser.SourceCodeParserRegistry;
import com.docsync.apispec.service.OpenApiAnalysisService;
import com.docsync.analysis.repository.AnalysisRepository;
import com.docsync.analysis.repository.CodeEntityRepository;
import com.docsync.analysis.repository.DocumentationCodeReferenceRepository;
import com.docsync.analysis.repository.DocumentationPageRepository;
import com.docsync.analysis.repository.FindingRepository;
import com.docsync.repository.entity.Repository;
import com.docsync.repository.service.RepositoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class RepositoryAnalysisServiceImpl implements RepositoryAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(RepositoryAnalysisServiceImpl.class);
    private static final Pattern INLINE_CODE = Pattern.compile("`([^`]+)`");
    private static final Pattern FENCED_JAVA = Pattern.compile("```(?:java|javas?)\\s*\\n(.*?)```", Pattern.DOTALL);
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*(?:\\.[A-Za-z_][A-Za-z0-9_]*)*");

    private final AnalysisRepository analysisRepository;
    private final CodeEntityRepository codeEntityRepository;
    private final DocumentationPageRepository documentationPageRepository;
    private final DocumentationCodeReferenceRepository referenceRepository;
    private final FindingRepository findingRepository;
    private final RepositoryService repositoryService;
    private final RepositoryFileAccessor fileAccessor;
    private final SourceCodeParserRegistry parserRegistry;
    private final OpenApiAnalysisService openApiAnalysisService;
    private final Clock clock;
    private final ObjectMapper objectMapper;

    public RepositoryAnalysisServiceImpl(AnalysisRepository analysisRepository,
                                         CodeEntityRepository codeEntityRepository,
                                         DocumentationPageRepository documentationPageRepository,
                                         DocumentationCodeReferenceRepository referenceRepository,
                                         FindingRepository findingRepository,
                                         RepositoryService repositoryService,
                                         RepositoryFileAccessor fileAccessor,
                                         SourceCodeParserRegistry parserRegistry,
                                         OpenApiAnalysisService openApiAnalysisService,
                                         Clock clock,
                                         ObjectMapper objectMapper) {
        this.analysisRepository = analysisRepository;
        this.codeEntityRepository = codeEntityRepository;
        this.documentationPageRepository = documentationPageRepository;
        this.referenceRepository = referenceRepository;
        this.findingRepository = findingRepository;
        this.repositoryService = repositoryService;
        this.fileAccessor = fileAccessor;
        this.parserRegistry = parserRegistry;
        this.openApiAnalysisService = openApiAnalysisService;
        this.clock = clock;
        this.objectMapper = objectMapper;
    }

    @Override
    public Analysis analyzeRepository(UUID repositoryId, String commitSha, AnalysisScope scope, UUID jobId) {
        Repository repository = repositoryService.findRepositoryById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));
        String resolvedCommit = (commitSha == null || commitSha.isBlank())
                ? repository.getLastCommitSha() : commitSha;
        if (resolvedCommit == null || resolvedCommit.isBlank()) {
            resolvedCommit = repository.getDefaultBranch();
        }
        if (resolvedCommit == null || resolvedCommit.isBlank()) {
            throw new IllegalStateException("Repository has no commit to analyze");
        }

        Analysis analysis = new Analysis(repositoryId, jobId, repository.getDefaultBranch(),
                resolvedCommit, scope == null ? AnalysisScope.FULL : scope);
        analysis.setStatus(AnalysisStatus.RUNNING);
        analysis.setStartedAt(Instant.now(clock));
        analysisRepository.save(analysis);

        repository.setStatus(com.docsync.repository.entity.RepositoryStatus.INDEXING);
        repositoryService.save(repository);
        try {
            if (analysis.getScope() == AnalysisScope.FULL) {
                runFullAnalysis(repository, analysis);
            } else {
                runIncrementalAnalysis(repository, analysis, null, resolvedCommit);
            }
            analysis.setStatus(AnalysisStatus.COMPLETED);
            analysis.setCompletedAt(Instant.now(clock));
            analysisRepository.save(analysis);

            repository.setLastAnalysisAt(Instant.now(clock));
            repository.setStatus(com.docsync.repository.entity.RepositoryStatus.READY);
            repositoryService.save(repository);
            return analysis;
        } catch (RuntimeException ex) {
            analysis.setStatus(AnalysisStatus.FAILED);
            analysis.setErrorMessage(truncate(ex.getMessage()));
            analysis.setCompletedAt(Instant.now(clock));
            analysisRepository.save(analysis);
            repository.setStatus(com.docsync.repository.entity.RepositoryStatus.ERROR);
            repository.setErrorMessage(truncate(ex.getMessage()));
            repositoryService.save(repository);
            throw ex;
        }
    }

    @Override
    public Analysis analyzeChangedFiles(UUID repositoryId, String baseCommit, String headCommit, UUID jobId) {
        Repository repository = repositoryService.findRepositoryById(repositoryId)
                .orElseThrow(() -> new IllegalArgumentException("Repository not found: " + repositoryId));
        String resolvedHead = (headCommit == null || headCommit.isBlank())
                ? repository.getLastCommitSha() : headCommit;
        if (resolvedHead == null || resolvedHead.isBlank()) {
            resolvedHead = repository.getDefaultBranch();
        }
        if (resolvedHead == null || resolvedHead.isBlank()) {
            throw new IllegalStateException("Repository has no head commit to analyze");
        }

        Analysis analysis = new Analysis(repositoryId, jobId, repository.getDefaultBranch(),
                resolvedHead, AnalysisScope.INCREMENTAL);
        analysis.setStatus(AnalysisStatus.RUNNING);
        analysis.setStartedAt(Instant.now(clock));
        analysisRepository.save(analysis);

        repository.setStatus(com.docsync.repository.entity.RepositoryStatus.INDEXING);
        repositoryService.save(repository);
        try {
            runIncrementalAnalysis(repository, analysis, baseCommit, resolvedHead);
            analysis.setStatus(AnalysisStatus.COMPLETED);
            analysis.setCompletedAt(Instant.now(clock));
            analysisRepository.save(analysis);

            repository.setLastAnalysisAt(Instant.now(clock));
            repository.setStatus(com.docsync.repository.entity.RepositoryStatus.READY);
            repositoryService.save(repository);
            return analysis;
        } catch (RuntimeException ex) {
            analysis.setStatus(AnalysisStatus.FAILED);
            analysis.setErrorMessage(truncate(ex.getMessage()));
            analysis.setCompletedAt(Instant.now(clock));
            analysisRepository.save(analysis);
            repository.setStatus(com.docsync.repository.entity.RepositoryStatus.ERROR);
            repository.setErrorMessage(truncate(ex.getMessage()));
            repositoryService.save(repository);
            throw ex;
        }
    }

    private void runFullAnalysis(Repository repository, Analysis analysis) {
        List<String> files = fileAccessor.listFiles(repository, analysis.getCommitSha());
        List<String> sourceFiles = files.stream().filter(this::isJavaSource).toList();

        List<CodeEntity> allEntities = new ArrayList<>();
        for (String path : sourceFiles) {
            String content = fileAccessor.readFile(repository, path, analysis.getCommitSha());
            List<CodeEntity> parsed = parserRegistry.parse(path, content, repository.getId(),
                    analysis.getId(), analysis.getCommitSha());
            codeEntityRepository.saveAll(parsed);
            allEntities.addAll(parsed);
        }

        List<DocumentationPage> pages = discoverDocumentation(repository, analysis, files);
        buildReferences(repository, pages, allEntities);
        createFindings(repository, analysis, allEntities, pages);

        ObjectNode stats = objectMapper.createObjectNode();
        stats.put("filesAnalyzed", sourceFiles.size());
        stats.put("entitiesFound", allEntities.size());
        stats.put("documentationPages", pages.size());
        stats.put("findingsCount", findingRepository.findAllByAnalysisId(analysis.getId()).size());
        int openApiEndpoints = 0;
        for (String path : files) {
            String lower = path.toLowerCase();
            if ((lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".json"))
                    && (lower.contains("openapi") || lower.contains("swagger"))) {
                String content = fileAccessor.readFile(repository, path, analysis.getCommitSha());
                openApiEndpoints += openApiAnalysisService.analyze(content).endpointCount();
            }
        }
        stats.put("openApiSpecs", countOpenApiSpecs(files));
        stats.put("openApiEndpoints", openApiEndpoints);
        analysis.setStats(stats);
    }

    private void runIncrementalAnalysis(Repository repository, Analysis analysis, String baseCommit, String headCommit) {
        String effectiveBase = (baseCommit == null || baseCommit.isBlank())
                ? repository.getLastProcessedCommitSha() : baseCommit;
        if (effectiveBase == null || effectiveBase.isBlank()) {
            effectiveBase = repository.getLastCommitSha();
        }

        List<com.docsync.changedetection.model.ChangedFile> changedFiles =
                fileAccessor.getChangedFiles(repository, effectiveBase, headCommit);

        Set<String> changedSourceFiles = new HashSet<>();
        Set<String> removedSourceFiles = new HashSet<>();
        for (com.docsync.changedetection.model.ChangedFile changed : changedFiles) {
            if (!isJavaSource(changed.path())) {
                continue;
            }
            if (changed.status() == com.docsync.changedetection.model.ChangedFile.ChangeStatus.REMOVED) {
                removedSourceFiles.add(changed.path());
            } else {
                changedSourceFiles.add(changed.path());
            }
        }

        List<CodeEntity> previousEntities = effectiveBase == null || effectiveBase.isBlank()
                ? List.of()
                : codeEntityRepository.findAllByRepositoryIdAndCommitSha(repository.getId(), effectiveBase);
        Map<String, CodeEntity> previousByQualified = previousEntities.stream()
                .collect(java.util.stream.Collectors.toMap(CodeEntity::getQualifiedName, e -> e, (a, b) -> a));

        List<CodeEntity> currentChangedEntities = new ArrayList<>();
        for (String path : changedSourceFiles) {
            codeEntityRepository.deleteAllByRepositoryIdAndCommitShaAndFilePath(
                    repository.getId(), headCommit, path);
            String content = fileAccessor.readFile(repository, path, headCommit);
            List<CodeEntity> parsed = parserRegistry.parse(path, content, repository.getId(),
                    analysis.getId(), headCommit);
            codeEntityRepository.saveAll(parsed);
            currentChangedEntities.addAll(parsed);
        }

        List<String> allFiles = fileAccessor.listFiles(repository, headCommit);
        List<DocumentationPage> pages = discoverDocumentation(repository, analysis, allFiles);
        List<CodeEntity> allCurrentEntities = new ArrayList<>(currentChangedEntities);
        allCurrentEntities.addAll(codeEntityRepository.findAllByRepositoryIdAndCommitSha(repository.getId(), headCommit));
        buildReferences(repository, pages, allCurrentEntities);

        List<Finding> impactFindings = createImpactFindings(repository, analysis, previousByQualified,
                currentChangedEntities, pages);
        createMissingDocumentationFindings(repository, analysis, allCurrentEntities, pages);

        ObjectNode stats = objectMapper.createObjectNode();
        stats.put("changedFiles", changedFiles.size());
        stats.put("changedSourceFiles", changedSourceFiles.size());
        stats.put("entitiesFound", allCurrentEntities.size());
        stats.put("documentationPages", pages.size());
        stats.put("findingsCount", findingRepository.findAllByAnalysisId(analysis.getId()).size());
        stats.put("impactFindings", impactFindings.size());
        analysis.setStats(stats);
    }

    private List<DocumentationPage> discoverDocumentation(Repository repository, Analysis analysis, List<String> files) {
        List<DocumentationPage> pages = new ArrayList<>();
        for (String path : files) {
            if (!isDocumentation(path)) {
                continue;
            }
            String content = fileAccessor.readFile(repository, path, analysis.getCommitSha());
            if (content == null) {
                continue;
            }
            DocumentationPage existing = documentationPageRepository
                    .findByRepositoryIdAndPath(repository.getId(), path)
                    .orElse(null);
            String hash = sha256(content);
            DocumentationPage page = new DocumentationPage(repository.getId(), analysis.getId(),
                    titleFromPath(path), typeFromPath(path), path, hash, analysis.getCommitSha());
            if (existing != null) {
                // Keep the latest analysis id and hash while retaining the page row.
            }
            documentationPageRepository.save(page);
            pages.add(page);
        }
        return pages;
    }

    private void buildReferences(Repository repository, List<DocumentationPage> pages, List<CodeEntity> entities) {
        Map<String, CodeEntity> byQualifiedName = entities.stream()
                .collect(java.util.stream.Collectors.toMap(CodeEntity::getQualifiedName, e -> e, (a, b) -> a));
        Map<String, CodeEntity> bySimpleName = entities.stream()
                .collect(java.util.stream.Collectors.toMap(CodeEntity::getSimpleName, e -> e, (a, b) -> a));

        for (DocumentationPage page : pages) {
            referenceRepository.deleteAllByRepositoryIdAndPageId(repository.getId(), page.getId());
            String content = fileAccessor.readFile(repository, page.getPath(), page.getLastAnalyzedCommit());
            if (content == null) {
                continue;
            }
            Set<String> candidates = extractCodeCandidates(content);
            Set<String> matched = new HashSet<>();
            for (String candidate : candidates) {
                CodeEntity entity = byQualifiedName.get(candidate);
                if (entity == null) {
                    String simple = candidate.contains(".")
                            ? candidate.substring(candidate.lastIndexOf('.') + 1) : candidate;
                    // strip method call parentheses if present
                    simple = simple.replace("()", "");
                    entity = bySimpleName.get(simple);
                }
                if (entity != null && matched.add(entity.getQualifiedName())) {
                    referenceRepository.save(new DocumentationCodeReference(
                            repository.getId(), page.getId(), entity.getId(), entity.getQualifiedName()));
                }
            }
        }
    }

    private void createFindings(Repository repository, Analysis analysis, List<CodeEntity> entities,
                                List<DocumentationPage> pages) {
        Set<String> referenced = new HashSet<>();
        for (DocumentationPage page : pages) {
            referenced.addAll(referenceRepository.findAllByRepositoryIdAndPageId(repository.getId(), page.getId())
                    .stream().map(DocumentationCodeReference::getQualifiedName).toList());
        }

        int[] counter = {0};
        for (CodeEntity entity : entities) {
            if (counter[0] >= 100) {
                break;
            }
            if (entity.getEntityType() == CodeEntityType.METHOD || entity.getEntityType() == CodeEntityType.CLASS
                    || entity.getEntityType() == CodeEntityType.INTERFACE) {
                if (entity.getModifiers() != null && entity.getModifiers().contains("public")
                        && !referenced.contains(entity.getQualifiedName())) {
                    findingRepository.save(new Finding(repository.getId(), analysis.getId(),
                            FindingSeverity.INFO, FindingCategory.MISSING_DOCS,
                            "Undocumented public API: " + entity.getSimpleName(),
                            "Public symbol " + entity.getQualifiedName() + " has no documentation reference.",
                            entity.getId(), entity.getFilePath(), entity.getLineNumber()));
                    counter[0]++;
                }
            }
        }

        // References to unknown symbols are inconsistent documentation.
        for (DocumentationPage page : pages) {
            String content = fileAccessor.readFile(repository, page.getPath(), analysis.getCommitSha());
            if (content == null) {
                continue;
            }
            for (String candidate : extractCodeCandidates(content)) {
                if (counter[0] >= 200) {
                    break;
                }
                String simpleName = candidate.contains(".") ? candidate.substring(candidate.lastIndexOf('.') + 1) : candidate;
                simpleName = simpleName.replace("()", "");
                final String resolvedSimpleName = simpleName;
                boolean exists = entities.stream().anyMatch(e -> e.getSimpleName().equals(resolvedSimpleName)
                        || e.getQualifiedName().equals(candidate));
                if (!exists && candidate.length() > 3 && Character.isUpperCase(candidate.charAt(0))) {
                    findingRepository.save(new Finding(repository.getId(), analysis.getId(),
                            FindingSeverity.WARNING, FindingCategory.INCONSISTENT,
                            "Unresolved code reference: " + candidate,
                            "Documentation references " + candidate + " but no matching code entity was found.",
                            null, page.getPath(), null));
                    counter[0]++;
                }
            }
        }
    }

    private List<Finding> createImpactFindings(Repository repository, Analysis analysis,
                                              Map<String, CodeEntity> previousByQualified,
                                              List<CodeEntity> currentChangedEntities,
                                              List<DocumentationPage> pages) {
        List<Finding> findings = new ArrayList<>();
        for (CodeEntity current : currentChangedEntities) {
            CodeEntity previous = previousByQualified.get(current.getQualifiedName());
            if (previous == null) {
                findings.add(saveFinding(repository, analysis, FindingSeverity.INFO, FindingCategory.MISSING_DOCS,
                        "New API symbol: " + current.getSimpleName(),
                        "Added symbol " + current.getQualifiedName() + " may require documentation.",
                        current.getId(), current.getFilePath(), current.getLineNumber()));
                continue;
            }
            if (!java.util.Objects.equals(previous.getSignatureHash(), current.getSignatureHash())) {
                findings.add(saveFinding(repository, analysis, FindingSeverity.WARNING, FindingCategory.OUTDATED,
                        "Changed API signature: " + current.getSimpleName(),
                        "Signature changed for " + current.getQualifiedName() + "; referencing documentation may be outdated.",
                        current.getId(), current.getFilePath(), current.getLineNumber()));
            }
        }

        // Removed symbols are breaking for referencing documentation.
        Set<String> currentQualified = currentChangedEntities.stream()
                .map(CodeEntity::getQualifiedName).collect(java.util.stream.Collectors.toSet());
        for (CodeEntity previous : previousByQualified.values()) {
            if (currentQualified.contains(previous.getQualifiedName())) {
                continue;
            }
            if (previous.getModifiers() != null && previous.getModifiers().contains("public")) {
                findings.add(saveFinding(repository, analysis, FindingSeverity.CRITICAL, FindingCategory.INCONSISTENT,
                        "Removed API symbol: " + previous.getSimpleName(),
                        "Removed symbol " + previous.getQualifiedName() + " may break referencing documentation.",
                        previous.getId(), previous.getFilePath(), previous.getLineNumber()));
            }
        }
        return findings;
    }

    private void createMissingDocumentationFindings(Repository repository, Analysis analysis,
                                                   List<CodeEntity> entities, List<DocumentationPage> pages) {
        createFindings(repository, analysis, entities, pages);
    }

    private Finding saveFinding(Repository repository, Analysis analysis, FindingSeverity severity,
                               FindingCategory category, String title, String description,
                               UUID entityId, String filePath, Integer lineNumber) {
        Finding finding = new Finding(repository.getId(), analysis.getId(), severity, category, title,
                description, entityId, filePath, lineNumber);
        return findingRepository.save(finding);
    }

    private Set<String> extractCodeCandidates(String content) {
        Set<String> candidates = new HashSet<>();
        Matcher inline = INLINE_CODE.matcher(content);
        while (inline.find()) {
            for (Matcher m = IDENTIFIER.matcher(inline.group(1)); m.find(); ) {
                candidates.add(m.group());
            }
        }
        Matcher fenced = FENCED_JAVA.matcher(content);
        while (fenced.find()) {
            for (Matcher m = IDENTIFIER.matcher(fenced.group(1)); m.find(); ) {
                candidates.add(m.group());
            }
        }
        return candidates;
    }

    private boolean isJavaSource(String path) {
        return path != null && path.endsWith(".java");
    }

    private boolean isDocumentation(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase();
        return lower.endsWith(".md") || lower.endsWith(".adoc") || lower.endsWith(".rst");
    }

    private String titleFromPath(String path) {
        String name = path.substring(path.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private DocumentationType typeFromPath(String path) {
        String lower = path.toLowerCase();
        if (lower.contains("readme")) {
            return DocumentationType.README;
        }
        if (lower.contains("openapi") || lower.contains("swagger")) {
            return DocumentationType.API_REFERENCE;
        }
        if (lower.contains("architecture")) {
            return DocumentationType.ARCHITECTURE;
        }
        return DocumentationType.GUIDE;
    }

    private int countOpenApiSpecs(List<String> files) {
        int count = 0;
        for (String path : files) {
            String lower = path.toLowerCase();
            if (lower.endsWith(".yaml") || lower.endsWith(".yml") || lower.endsWith(".json")) {
                if (lower.contains("openapi") || lower.contains("swagger")) {
                    count++;
                }
            }
        }
        return count;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private String truncate(String message) {
        if (message == null || message.isBlank()) {
            return "Analysis failed";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    @Override
    public List<Analysis> listAnalyses(UUID repositoryId) {
        return analysisRepository.findAllByRepositoryIdOrderByCreatedAtDesc(repositoryId);
    }

    @Override
    public Analysis getAnalysis(UUID analysisId) {
        return analysisRepository.findById(analysisId)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found: " + analysisId));
    }

    @Override
    public List<Finding> getFindings(UUID repositoryId) {
        return findingRepository.findAllByRepositoryIdOrderByCreatedAtDesc(repositoryId);
    }
}

package com.docsync.analysis.api;

import com.docsync.analysis.access.RepositoryFileAccessor;
import com.docsync.analysis.dto.AnalysisDto;
import com.docsync.analysis.dto.DocumentationPageDetailDto;
import com.docsync.analysis.dto.DocumentationPageDto;
import com.docsync.analysis.dto.FindingDto;
import com.docsync.analysis.entity.AnalysisScope;
import com.docsync.analysis.repository.DocumentationPageRepository;
import com.docsync.analysis.service.RepositoryAnalysisService;
import com.docsync.identity.security.CurrentUser;
import com.docsync.organization.service.OrganizationAuthorizationService;
import com.docsync.repository.entity.Repository;
import com.docsync.repository.service.RepositoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/organizations/{organizationId}/repositories/{repositoryId}")
public class AnalysisController {

    private final RepositoryAnalysisService analysisService;
    private final DocumentationPageRepository documentationPageRepository;
    private final RepositoryFileAccessor fileAccessor;
    private final RepositoryService repositoryService;
    private final OrganizationAuthorizationService authorizationService;
    private final CurrentUser currentUser;

    public AnalysisController(RepositoryAnalysisService analysisService,
                              DocumentationPageRepository documentationPageRepository,
                              RepositoryFileAccessor fileAccessor,
                              RepositoryService repositoryService,
                              OrganizationAuthorizationService authorizationService,
                              CurrentUser currentUser) {
        this.analysisService = analysisService;
        this.documentationPageRepository = documentationPageRepository;
        this.fileAccessor = fileAccessor;
        this.repositoryService = repositoryService;
        this.authorizationService = authorizationService;
        this.currentUser = currentUser;
    }

    @PostMapping("/analysis")
    public ResponseEntity<AnalysisDto> triggerAnalysis(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable UUID organizationId,
                                                      @PathVariable UUID repositoryId,
                                                      @RequestParam(required = false) AnalysisScope scope) {
        authorizationService.requireRole(currentUser.id(jwt), organizationId,
                com.docsync.organization.entity.MemberRole.MEMBER);
        Repository repository = repositoryService.findRepositoryById(repositoryId)
                .orElseThrow(() -> new com.docsync.common.exception.ResourceNotFoundException("Repository", repositoryId.toString()));
        if (!repository.getOrganizationId().equals(organizationId)) {
            throw new com.docsync.common.exception.ResourceNotFoundException("Repository", repositoryId.toString());
        }
        AnalysisDto result = AnalysisDto.from(analysisService.analyzeRepository(
                repositoryId, null, scope, null));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/analysis")
    public List<AnalysisDto> listAnalyses(@AuthenticationPrincipal Jwt jwt,
                                         @PathVariable UUID organizationId,
                                         @PathVariable UUID repositoryId) {
        authorizationService.requireMembership(currentUser.id(jwt), organizationId);
        return analysisService.listAnalyses(repositoryId).stream().map(AnalysisDto::from).toList();
    }

    @GetMapping("/analysis/{analysisId}")
    public AnalysisDto getAnalysis(@AuthenticationPrincipal Jwt jwt,
                                  @PathVariable UUID organizationId,
                                  @PathVariable UUID repositoryId,
                                  @PathVariable UUID analysisId) {
        authorizationService.requireMembership(currentUser.id(jwt), organizationId);
        return AnalysisDto.from(analysisService.getAnalysis(analysisId));
    }

    @GetMapping("/findings")
    public List<FindingDto> getFindings(@AuthenticationPrincipal Jwt jwt,
                                        @PathVariable UUID organizationId,
                                        @PathVariable UUID repositoryId) {
        authorizationService.requireMembership(currentUser.id(jwt), organizationId);
        return analysisService.getFindings(repositoryId).stream().map(FindingDto::from).toList();
    }

    @GetMapping("/documentation")
    public List<DocumentationPageDto> getDocumentation(@AuthenticationPrincipal Jwt jwt,
                                                      @PathVariable UUID organizationId,
                                                      @PathVariable UUID repositoryId) {
        authorizationService.requireMembership(currentUser.id(jwt), organizationId);
        return documentationPageRepository.findAllByRepositoryId(repositoryId).stream()
                .map(DocumentationPageDto::from).toList();
    }

    @GetMapping("/documentation/{pageId}")
    public DocumentationPageDetailDto getDocumentationPage(@AuthenticationPrincipal Jwt jwt,
                                                          @PathVariable UUID organizationId,
                                                          @PathVariable UUID repositoryId,
                                                          @PathVariable UUID pageId) {
        authorizationService.requireMembership(currentUser.id(jwt), organizationId);
        com.docsync.analysis.entity.DocumentationPage page = documentationPageRepository.findById(pageId)
                .orElseThrow(() -> new com.docsync.common.exception.ResourceNotFoundException("DocumentationPage", pageId.toString()));
        if (!page.getRepositoryId().equals(repositoryId)) {
            throw new com.docsync.common.exception.ResourceNotFoundException("DocumentationPage", pageId.toString());
        }
        Repository repository = repositoryService.findRepositoryById(repositoryId)
                .orElseThrow(() -> new com.docsync.common.exception.ResourceNotFoundException("Repository", repositoryId.toString()));
        if (!repository.getOrganizationId().equals(organizationId)) {
            throw new com.docsync.common.exception.ResourceNotFoundException("Repository", repositoryId.toString());
        }
        String content = null;
        try {
            content = fileAccessor.readFile(repository, page.getPath(), page.getLastAnalyzedCommit());
        } catch (RuntimeException ignored) {
            content = null;
        }
        return DocumentationPageDetailDto.from(page, content);
    }
}

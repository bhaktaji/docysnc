package com.docsync.pullrequest.entity;

import com.docsync.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "pull_requests")
public class PullRequest extends BaseEntity {

    @Column(name = "repository_id", nullable = false)
    private UUID repositoryId;

    @Column(name = "pr_number", nullable = false)
    private int prNumber;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "author", nullable = false, length = 255)
    private String author;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PullRequestStatus status = PullRequestStatus.OPEN;

    @Column(name = "head_branch", nullable = false, length = 255)
    private String headBranch;

    @Column(name = "base_branch", nullable = false, length = 255)
    private String baseBranch;

    @Column(name = "head_sha", nullable = false, length = 40)
    private String headSha;

    @Column(name = "base_sha", length = 40)
    private String baseSha;

    @Column(name = "analysis_job_id")
    private UUID analysisJobId;

    protected PullRequest() {
    }

    public PullRequest(UUID repositoryId, int prNumber, String title, String author,
                       PullRequestStatus status, String headBranch, String baseBranch,
                       String headSha, String baseSha) {
        this.repositoryId = repositoryId;
        this.prNumber = prNumber;
        this.title = title;
        this.author = author;
        this.status = status;
        this.headBranch = headBranch;
        this.baseBranch = baseBranch;
        this.headSha = headSha;
        this.baseSha = baseSha;
    }

    public UUID getRepositoryId() {
        return repositoryId;
    }

    public int getPrNumber() {
        return prNumber;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public PullRequestStatus getStatus() {
        return status;
    }

    public void setStatus(PullRequestStatus status) {
        this.status = status;
    }

    public String getHeadBranch() {
        return headBranch;
    }

    public String getBaseBranch() {
        return baseBranch;
    }

    public String getHeadSha() {
        return headSha;
    }

    public void setHeadSha(String headSha) {
        this.headSha = headSha;
    }

    public String getBaseSha() {
        return baseSha;
    }

    public void setBaseSha(String baseSha) {
        this.baseSha = baseSha;
    }

    public UUID getAnalysisJobId() {
        return analysisJobId;
    }

    public void setAnalysisJobId(UUID analysisJobId) {
        this.analysisJobId = analysisJobId;
    }
}

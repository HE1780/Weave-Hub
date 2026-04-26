package com.iflytek.skillhub.domain.agent.review;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "agent_review_task")
public class AgentReviewTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_version_id", nullable = false)
    private Long agentVersionId;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentReviewTaskStatus status = AgentReviewTaskStatus.PENDING;

    @Version
    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "submitted_by", nullable = false, length = 64)
    private String submittedBy;

    @Column(name = "reviewed_by", length = 64)
    private String reviewedBy;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected AgentReviewTask() {}

    public AgentReviewTask(Long agentVersionId, Long namespaceId, String submittedBy) {
        this.agentVersionId = agentVersionId;
        this.namespaceId = namespaceId;
        this.submittedBy = submittedBy;
    }

    public Long getId() { return id; }
    public Long getAgentVersionId() { return agentVersionId; }
    public Long getNamespaceId() { return namespaceId; }
    public AgentReviewTaskStatus getStatus() { return status; }
    public Integer getOptimisticVersion() { return version; }
    public String getSubmittedBy() { return submittedBy; }
    public String getReviewedBy() { return reviewedBy; }
    public String getReviewComment() { return reviewComment; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }

    public void approve(String reviewerUserId, String comment) {
        require(status == AgentReviewTaskStatus.PENDING,
                "Only PENDING reviews can be approved (was " + status + ")");
        this.status = AgentReviewTaskStatus.APPROVED;
        this.reviewedBy = reviewerUserId;
        this.reviewComment = comment;
        this.reviewedAt = Instant.now();
    }

    public void reject(String reviewerUserId, String comment) {
        require(status == AgentReviewTaskStatus.PENDING,
                "Only PENDING reviews can be rejected (was " + status + ")");
        this.status = AgentReviewTaskStatus.REJECTED;
        this.reviewedBy = reviewerUserId;
        this.reviewComment = comment;
        this.reviewedAt = Instant.now();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new DomainBadRequestException(message);
        }
    }
}

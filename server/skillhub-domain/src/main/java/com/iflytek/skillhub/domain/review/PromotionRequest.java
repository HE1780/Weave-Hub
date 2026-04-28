package com.iflytek.skillhub.domain.review;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "promotion_request")
public class PromotionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private SourceType sourceType = SourceType.SKILL;

    @Column(name = "source_skill_id")
    private Long sourceSkillId;

    @Column(name = "source_version_id")
    private Long sourceVersionId;

    @Column(name = "source_agent_id")
    private Long sourceAgentId;

    @Column(name = "source_agent_version_id")
    private Long sourceAgentVersionId;

    @Column(name = "target_namespace_id", nullable = false)
    private Long targetNamespaceId;

    @Column(name = "target_skill_id")
    private Long targetSkillId;

    @Column(name = "target_agent_id")
    private Long targetAgentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewTaskStatus status = ReviewTaskStatus.PENDING;

    @Version
    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "submitted_by", nullable = false)
    private String submittedBy;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected PromotionRequest() {}

    /**
     * Legacy skill-only constructor — kept for backward compatibility.
     * Defaults sourceType to SKILL.
     */
    public PromotionRequest(Long sourceSkillId, Long sourceVersionId,
                            Long targetNamespaceId, String submittedBy) {
        this.sourceType = SourceType.SKILL;
        this.sourceSkillId = sourceSkillId;
        this.sourceVersionId = sourceVersionId;
        this.targetNamespaceId = targetNamespaceId;
        this.submittedBy = submittedBy;
    }

    public static PromotionRequest forSkill(Long sourceSkillId, Long sourceVersionId,
                                            Long targetNamespaceId, String submittedBy) {
        PromotionRequest r = new PromotionRequest();
        r.sourceType = SourceType.SKILL;
        r.sourceSkillId = sourceSkillId;
        r.sourceVersionId = sourceVersionId;
        r.targetNamespaceId = targetNamespaceId;
        r.submittedBy = submittedBy;
        return r;
    }

    public static PromotionRequest forAgent(Long sourceAgentId, Long sourceAgentVersionId,
                                            Long targetNamespaceId, String submittedBy) {
        PromotionRequest r = new PromotionRequest();
        r.sourceType = SourceType.AGENT;
        r.sourceAgentId = sourceAgentId;
        r.sourceAgentVersionId = sourceAgentVersionId;
        r.targetNamespaceId = targetNamespaceId;
        r.submittedBy = submittedBy;
        return r;
    }

    public Long getId() { return id; }
    public SourceType getSourceType() { return sourceType; }
    public Long getSourceSkillId() { return sourceSkillId; }
    public Long getSourceVersionId() { return sourceVersionId; }
    public Long getSourceAgentId() { return sourceAgentId; }
    public Long getSourceAgentVersionId() { return sourceAgentVersionId; }
    public Long getTargetNamespaceId() { return targetNamespaceId; }
    public Long getTargetSkillId() { return targetSkillId; }
    public Long getTargetAgentId() { return targetAgentId; }

    public void setTargetSkillId(Long targetSkillId) { this.targetSkillId = targetSkillId; }
    public void setTargetAgentId(Long targetAgentId) { this.targetAgentId = targetAgentId; }

    /**
     * Writes the materialized target entity id into the slot matching the
     * promotion's source type.
     */
    public void setTargetEntityId(Long id, SourceType type) {
        if (type == SourceType.SKILL) {
            this.targetSkillId = id;
        } else {
            this.targetAgentId = id;
        }
    }

    public ReviewTaskStatus getStatus() { return status; }
    public void setStatus(ReviewTaskStatus status) { this.status = status; }

    public Integer getVersion() { return version; }

    public String getSubmittedBy() { return submittedBy; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }

    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}

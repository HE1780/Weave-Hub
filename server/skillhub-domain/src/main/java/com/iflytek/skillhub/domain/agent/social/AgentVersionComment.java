package com.iflytek.skillhub.domain.agent.social;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

/**
 * Comment posted by a user against an agent version. Direct sibling of
 * {@code com.iflytek.skillhub.domain.social.SkillVersionComment}; the two
 * tables are kept independent so the visibility/permission models can
 * evolve separately. See docs/plans/2026-04-27-fork-backlog.md (A1).
 */
@Entity
@Table(name = "agent_version_comment")
public class AgentVersionComment {

    public static final int MAX_BODY_LENGTH = 8192;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_version_id", nullable = false)
    private Long agentVersionId;

    @Column(name = "author_id", nullable = false, length = 128)
    private String authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_edited_at")
    private Instant lastEditedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 128)
    private String deletedBy;

    protected AgentVersionComment() {}

    public AgentVersionComment(Long agentVersionId, String authorId, String body) {
        validateBody(body);
        this.agentVersionId = agentVersionId;
        this.authorId = authorId;
        this.body = body;
        this.pinned = false;
    }

    public void edit(String newBody) {
        validateBody(newBody);
        this.body = newBody;
        this.lastEditedAt = Instant.now(Clock.systemUTC());
    }

    public void softDelete(String deletedByUserId) {
        Instant now = Instant.now(Clock.systemUTC());
        this.deletedAt = now;
        this.deletedBy = deletedByUserId;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now(Clock.systemUTC());
        }
    }

    private static void validateBody(String body) {
        if (body == null || body.isBlank()) {
            throw new DomainBadRequestException("error.comment.body.empty");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new DomainBadRequestException("error.comment.body.tooLong");
        }
    }

    public Long getId() { return id; }
    public Long getAgentVersionId() { return agentVersionId; }
    public String getAuthorId() { return authorId; }
    public String getBody() { return body; }
    public boolean isPinned() { return pinned; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastEditedAt() { return lastEditedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedBy() { return deletedBy; }
}

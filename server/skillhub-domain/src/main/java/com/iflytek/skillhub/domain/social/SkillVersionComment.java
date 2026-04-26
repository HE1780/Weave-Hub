package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "skill_version_comment")
public class SkillVersionComment {

    public static final int MAX_BODY_LENGTH = 8192;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_version_id", nullable = false)
    private Long skillVersionId;

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

    protected SkillVersionComment() {}

    public SkillVersionComment(Long skillVersionId, String authorId, String body) {
        validateBody(body);
        this.skillVersionId = skillVersionId;
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
    public Long getSkillVersionId() { return skillVersionId; }
    public String getAuthorId() { return authorId; }
    public String getBody() { return body; }
    public boolean isPinned() { return pinned; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastEditedAt() { return lastEditedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedBy() { return deletedBy; }
}

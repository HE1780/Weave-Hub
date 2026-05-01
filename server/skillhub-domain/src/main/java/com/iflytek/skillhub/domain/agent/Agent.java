package com.iflytek.skillhub.domain.agent;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "agent")
public class Agent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(nullable = false, length = 64)
    private String slug;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentVisibility visibility = AgentVisibility.PRIVATE;

    @Column(name = "owner_id", nullable = false, length = 64)
    private String ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AgentStatus status = AgentStatus.ACTIVE;

    @Column(name = "star_count", nullable = false)
    private Integer starCount = 0;

    @Column(name = "rating_avg", precision = 3, scale = 2, nullable = false)
    private BigDecimal ratingAvg = BigDecimal.ZERO;

    @Column(name = "rating_count", nullable = false)
    private Integer ratingCount = 0;

    @Column(name = "download_count", nullable = false)
    private Integer downloadCount = 0;

    @Column(name = "latest_version_id")
    private Long latestVersionId;

    @Column(nullable = false)
    private boolean hidden = false;

    @Column(name = "hidden_at")
    private Instant hiddenAt;

    @Column(name = "hidden_by", length = 128)
    private String hiddenBy;

    @Column(name = "hide_reason", columnDefinition = "TEXT")
    private String hideReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Agent() {}

    public Agent(Long namespaceId, String slug, String displayName, String ownerId,
                 AgentVisibility visibility) {
        this.namespaceId = namespaceId;
        this.slug = slug;
        this.displayName = displayName;
        this.ownerId = ownerId;
        this.visibility = visibility;
    }

    public Long getId() { return id; }
    public Long getNamespaceId() { return namespaceId; }
    public String getSlug() { return slug; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public AgentVisibility getVisibility() { return visibility; }
    public String getOwnerId() { return ownerId; }
    public AgentStatus getStatus() { return status; }
    public Integer getStarCount() { return starCount; }
    public BigDecimal getRatingAvg() { return ratingAvg; }
    public Integer getRatingCount() { return ratingCount; }
    public Integer getDownloadCount() { return downloadCount; }
    public Long getLatestVersionId() { return latestVersionId; }
    public boolean isHidden() { return hidden; }
    public Instant getHiddenAt() { return hiddenAt; }
    public String getHiddenBy() { return hiddenBy; }
    public String getHideReason() { return hideReason; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        touch();
    }

    public void setDescription(String description) {
        this.description = description;
        touch();
    }

    public void setLatestVersionId(Long latestVersionId) {
        this.latestVersionId = latestVersionId;
        touch();
    }

    public void hide(String by, String reason) {
        this.hidden = true;
        this.hiddenAt = Instant.now();
        this.hiddenBy = by;
        this.hideReason = reason;
        touch();
    }

    public void unhide() {
        this.hidden = false;
        this.hiddenAt = null;
        this.hiddenBy = null;
        this.hideReason = null;
        touch();
    }

    public void archive() {
        this.status = AgentStatus.ARCHIVED;
        touch();
    }

    public void unarchive() {
        this.status = AgentStatus.ACTIVE;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}

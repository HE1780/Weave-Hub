package com.iflytek.skillhub.domain.agent;

import jakarta.persistence.*;
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

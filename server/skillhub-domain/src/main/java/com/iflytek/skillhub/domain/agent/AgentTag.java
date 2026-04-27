package com.iflytek.skillhub.domain.agent;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

/**
 * User-applied named tag pointing at a specific agent version. Mirrors
 * {@link com.iflytek.skillhub.domain.skill.SkillTag}: same fields, same
 * permission rules, same "latest" reserved-name guard at the service layer.
 */
@Entity
@Table(name = "agent_tag")
public class AgentTag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "tag_name", nullable = false, length = 64)
    private String tagName;

    @Column(name = "version_id", nullable = false)
    private Long versionId;

    @Column(name = "created_by", nullable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected AgentTag() {
    }

    public AgentTag(Long agentId, String tagName, Long versionId, String createdBy) {
        this.agentId = agentId;
        this.tagName = tagName;
        this.versionId = versionId;
        this.createdBy = createdBy;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
        updatedAt = createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() { return id; }
    public Long getAgentId() { return agentId; }
    public String getTagName() { return tagName; }
    public Long getVersionId() { return versionId; }
    public String getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setVersionId(Long versionId) { this.versionId = versionId; }
}

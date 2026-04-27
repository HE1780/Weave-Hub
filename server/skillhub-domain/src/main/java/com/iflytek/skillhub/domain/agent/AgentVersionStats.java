package com.iflytek.skillhub.domain.agent;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "agent_version_stats")
public class AgentVersionStats {

    @Id
    @Column(name = "agent_version_id", nullable = false)
    private Long agentVersionId;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "download_count", nullable = false)
    private Long downloadCount = 0L;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentVersionStats() {
    }

    public AgentVersionStats(Long agentVersionId, Long agentId) {
        this.agentVersionId = agentVersionId;
        this.agentId = agentId;
    }

    @PrePersist
    protected void onCreate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now(Clock.systemUTC());
    }

    public Long getAgentVersionId() {
        return agentVersionId;
    }

    public Long getAgentId() {
        return agentId;
    }

    public Long getDownloadCount() {
        return downloadCount;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

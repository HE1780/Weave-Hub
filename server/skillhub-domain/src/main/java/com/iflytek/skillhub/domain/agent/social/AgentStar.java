package com.iflytek.skillhub.domain.agent.social;

import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "agent_star",
    uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "user_id"}))
public class AgentStar {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentStar() {}

    public AgentStar(Long agentId, String userId) {
        this.agentId = agentId;
        this.userId = userId;
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() { return id; }
    public Long getAgentId() { return agentId; }
    public String getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }
}

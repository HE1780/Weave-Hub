package com.iflytek.skillhub.domain.agent.social;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "agent_rating",
    uniqueConstraints = @UniqueConstraint(columnNames = {"agent_id", "user_id"}))
public class AgentRating {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private Short score;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AgentRating() {}

    public AgentRating(Long agentId, String userId, short score) {
        if (score < 1 || score > 5) throw new DomainBadRequestException("error.rating.score.invalid");
        this.agentId = agentId;
        this.userId = userId;
        this.score = score;
    }

    public void updateScore(short newScore) {
        if (newScore < 1 || newScore > 5) throw new DomainBadRequestException("error.rating.score.invalid");
        this.score = newScore;
        this.updatedAt = Instant.now(Clock.systemUTC());
    }

    @PrePersist
    void prePersist() {
        this.createdAt = Instant.now(Clock.systemUTC());
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() { return id; }
    public Long getAgentId() { return agentId; }
    public String getUserId() { return userId; }
    public Short getScore() { return score; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

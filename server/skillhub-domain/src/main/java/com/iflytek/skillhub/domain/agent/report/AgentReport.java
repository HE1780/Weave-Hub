package com.iflytek.skillhub.domain.agent.report;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Clock;
import java.time.Instant;

/**
 * Agent abuse report. Direct sibling of
 * {@link com.iflytek.skillhub.domain.report.SkillReport}: the schema is the
 * same except {@code agent_id} replaces {@code skill_id}, and the table is
 * separate so the two governance flows can evolve independently.
 */
@Entity
@Table(name = "agent_report")
public class AgentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "agent_id", nullable = false)
    private Long agentId;

    @Column(name = "namespace_id", nullable = false)
    private Long namespaceId;

    @Column(name = "reporter_id", nullable = false, length = 128)
    private String reporterId;

    @Column(nullable = false, length = 200)
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentReportStatus status = AgentReportStatus.PENDING;

    @Column(name = "handled_by", length = 128)
    private String handledBy;

    @Column(name = "handle_comment", columnDefinition = "TEXT")
    private String handleComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "handled_at")
    private Instant handledAt;

    protected AgentReport() {
    }

    public AgentReport(Long agentId, Long namespaceId, String reporterId, String reason, String details) {
        this.agentId = agentId;
        this.namespaceId = namespaceId;
        this.reporterId = reporterId;
        this.reason = reason;
        this.details = details;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now(Clock.systemUTC());
    }

    public Long getId() { return id; }
    public Long getAgentId() { return agentId; }
    public Long getNamespaceId() { return namespaceId; }
    public String getReporterId() { return reporterId; }
    public String getReason() { return reason; }
    public String getDetails() { return details; }
    public AgentReportStatus getStatus() { return status; }
    public void setStatus(AgentReportStatus status) { this.status = status; }
    public String getHandledBy() { return handledBy; }
    public void setHandledBy(String handledBy) { this.handledBy = handledBy; }
    public String getHandleComment() { return handleComment; }
    public void setHandleComment(String handleComment) { this.handleComment = handleComment; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getHandledAt() { return handledAt; }
    public void setHandledAt(Instant handledAt) { this.handledAt = handledAt; }
}

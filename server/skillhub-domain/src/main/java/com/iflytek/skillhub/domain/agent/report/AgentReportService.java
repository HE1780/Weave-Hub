package com.iflytek.skillhub.domain.agent.report;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentStatus;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Submit-side service for agent abuse reports. Mirrors the submit path of
 * {@link com.iflytek.skillhub.domain.report.SkillReportService}; the
 * resolve/dismiss admin flow is intentionally deferred until the Agent
 * moderation surface lands (fork-backlog A6 v1 scope).
 *
 * <p>Validation is identical to the Skill side:
 * <ul>
 *   <li>Reason is required and trimmed.</li>
 *   <li>Target agent must exist and be {@code ACTIVE} (archived agents
 *       cannot be reported — they are already withdrawn from view).</li>
 *   <li>Self-reports are rejected so an owner can't pollute the queue
 *       with their own agent.</li>
 *   <li>One pending report per (agent, reporter) pair to dedupe.</li>
 * </ul>
 *
 * <p>Audit log entry is recorded under {@code REPORT_AGENT} for the
 * {@code AGENT} target type, matching the Skill side's
 * {@code REPORT_SKILL} / {@code SKILL} pair.
 */
@Service
public class AgentReportService {

    private final AgentRepository agentRepository;
    private final AgentReportRepository reportRepository;
    private final AuditLogService auditLogService;

    public AgentReportService(AgentRepository agentRepository,
                              AgentReportRepository reportRepository,
                              AuditLogService auditLogService) {
        this.agentRepository = agentRepository;
        this.reportRepository = reportRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AgentReport submitReport(Long agentId,
                                    String reporterId,
                                    String reason,
                                    String details,
                                    String clientIp,
                                    String userAgent) {
        if (reason == null || reason.isBlank()) {
            throw new DomainBadRequestException("error.agent.report.reason.required");
        }

        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.notFound", agentId));
        if (agent.getStatus() != AgentStatus.ACTIVE) {
            throw new DomainBadRequestException("error.agent.report.unavailable", agent.getSlug());
        }
        if (agent.getOwnerId().equals(reporterId)) {
            throw new DomainBadRequestException("error.agent.report.self");
        }
        if (reportRepository.existsByAgentIdAndReporterIdAndStatus(agentId, reporterId, AgentReportStatus.PENDING)) {
            throw new DomainBadRequestException("error.agent.report.duplicate");
        }

        AgentReport saved = reportRepository.save(new AgentReport(
                agentId,
                agent.getNamespaceId(),
                reporterId,
                reason.trim(),
                normalize(details)
        ));
        auditLogService.record(reporterId, "REPORT_AGENT", "AGENT", agentId, null, clientIp, userAgent,
                "{\"reportId\":" + saved.getId() + "}");
        return saved;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

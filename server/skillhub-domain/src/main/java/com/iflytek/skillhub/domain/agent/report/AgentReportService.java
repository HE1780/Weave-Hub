package com.iflytek.skillhub.domain.agent.report;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentStatus;
import com.iflytek.skillhub.domain.agent.report.event.AgentReportDismissedEvent;
import com.iflytek.skillhub.domain.agent.report.event.AgentReportResolvedEvent;
import com.iflytek.skillhub.domain.agent.service.AgentLifecycleService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.governance.GovernanceNotificationService;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Submit-side and admin-moderation service for agent abuse reports. Mirrors
 * {@link com.iflytek.skillhub.domain.report.SkillReportService} — submit
 * validation matches the skill side, and the admin
 * resolve / dismiss path emits audit log entries plus
 * {@link AgentReportResolvedEvent} / {@link AgentReportDismissedEvent} for
 * downstream listeners (governance counters, notifications).
 *
 * <p>Submit validation:
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
 * {@code REPORT_SKILL} / {@code SKILL} pair. Admin actions record under
 * {@code RESOLVE_AGENT_REPORT} / {@code DISMISS_AGENT_REPORT} for the
 * {@code AGENT_REPORT} target type.
 */
@Service
public class AgentReportService {

    private final AgentRepository agentRepository;
    private final AgentReportRepository reportRepository;
    private final AuditLogService auditLogService;
    private final AgentLifecycleService agentLifecycleService;
    private final GovernanceNotificationService governanceNotificationService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AgentReportService(AgentRepository agentRepository,
                              AgentReportRepository reportRepository,
                              AuditLogService auditLogService,
                              AgentLifecycleService agentLifecycleService,
                              GovernanceNotificationService governanceNotificationService,
                              ApplicationEventPublisher eventPublisher,
                              Clock clock) {
        this.agentRepository = agentRepository;
        this.reportRepository = reportRepository;
        this.auditLogService = auditLogService;
        this.agentLifecycleService = agentLifecycleService;
        this.governanceNotificationService = governanceNotificationService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
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

    /**
     * Returns a page of reports filtered by status, used by the admin queue.
     */
    public Page<AgentReport> listByStatus(AgentReportStatus status, Pageable pageable) {
        return reportRepository.findByStatus(status, pageable);
    }

    @Transactional
    public AgentReport resolveReport(Long reportId,
                                     String actorUserId,
                                     String comment,
                                     String clientIp,
                                     String userAgent) {
        return resolveReport(reportId, actorUserId, AgentReportDisposition.RESOLVE_ONLY, comment, clientIp, userAgent);
    }

    @Transactional
    public AgentReport resolveReport(Long reportId,
                                     String actorUserId,
                                     AgentReportDisposition disposition,
                                     String comment,
                                     String clientIp,
                                     String userAgent) {
        AgentReport report = requirePendingReport(reportId);
        if (disposition == AgentReportDisposition.RESOLVE_AND_ARCHIVE) {
            agentLifecycleService.archiveAsAdmin(report.getAgentId(), actorUserId, clientIp, userAgent, comment);
        }
        report.setStatus(AgentReportStatus.RESOLVED);
        report.setHandledBy(actorUserId);
        report.setHandleComment(normalize(comment));
        report.setHandledAt(currentTime());
        AgentReport saved = reportRepository.save(report);
        auditLogService.record(actorUserId, "RESOLVE_AGENT_REPORT", "AGENT_REPORT", reportId, null, clientIp, userAgent, null);
        eventPublisher.publishEvent(new AgentReportResolvedEvent(
                saved.getId(), saved.getAgentId(), actorUserId, saved.getReporterId(),
                disposition.name().toLowerCase()));
        governanceNotificationService.notifyUser(
                report.getReporterId(),
                "REPORT",
                "AGENT_REPORT",
                reportId,
                "Report handled",
                "{\"status\":\"RESOLVED\"}"
        );
        return saved;
    }

    @Transactional
    public AgentReport dismissReport(Long reportId,
                                     String actorUserId,
                                     String comment,
                                     String clientIp,
                                     String userAgent) {
        AgentReport report = requirePendingReport(reportId);
        report.setStatus(AgentReportStatus.DISMISSED);
        report.setHandledBy(actorUserId);
        report.setHandleComment(normalize(comment));
        report.setHandledAt(currentTime());
        AgentReport saved = reportRepository.save(report);
        auditLogService.record(actorUserId, "DISMISS_AGENT_REPORT", "AGENT_REPORT", reportId, null, clientIp, userAgent, null);
        eventPublisher.publishEvent(new AgentReportDismissedEvent(
                saved.getId(), saved.getAgentId(), actorUserId, saved.getReporterId()));
        governanceNotificationService.notifyUser(
                report.getReporterId(),
                "REPORT",
                "AGENT_REPORT",
                reportId,
                "Report dismissed",
                "{\"status\":\"DISMISSED\"}"
        );
        return saved;
    }

    private AgentReport requirePendingReport(Long reportId) {
        AgentReport report = reportRepository.findById(reportId)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.report.notFound", reportId));
        if (report.getStatus() != AgentReportStatus.PENDING) {
            throw new DomainBadRequestException("error.agent.report.notPending");
        }
        return report;
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Instant currentTime() {
        return Instant.now(clock);
    }
}

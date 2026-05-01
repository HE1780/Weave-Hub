package com.iflytek.skillhub.domain.agent.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin-only governance writes for the Agent aggregate. Mirrors
 * {@code SkillGovernanceService} hide / unhide / yank surface; archive lives
 * on {@link AgentLifecycleService} (was there before parity work).
 *
 * <p>Yank delegates to {@code AgentLifecycleService.yankVersion} so the
 * {@link Agent#getLatestVersionId} repointing logic stays in one place.
 */
@Service
public class AgentGovernanceService {

    private final AgentRepository agentRepository;
    private final AgentLifecycleService agentLifecycleService;
    private final AuditLogService auditLogService;

    public AgentGovernanceService(AgentRepository agentRepository,
                                  AgentLifecycleService agentLifecycleService,
                                  AuditLogService auditLogService) {
        this.agentRepository = agentRepository;
        this.agentLifecycleService = agentLifecycleService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Agent hideAgent(Long agentId,
                           String actorUserId,
                           String clientIp,
                           String userAgent,
                           String reason) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.notFound", agentId));
        agent.hide(actorUserId, reason);
        Agent saved = agentRepository.save(agent);
        auditLogService.record(actorUserId, "HIDE_AGENT", "AGENT", agentId, null,
                clientIp, userAgent, jsonReason(reason));
        return saved;
    }

    @Transactional
    public Agent unhideAgent(Long agentId,
                             String actorUserId,
                             String clientIp,
                             String userAgent) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.notFound", agentId));
        agent.unhide();
        Agent saved = agentRepository.save(agent);
        auditLogService.record(actorUserId, "UNHIDE_AGENT", "AGENT", agentId, null,
                clientIp, userAgent, null);
        return saved;
    }

    @Transactional
    public AgentVersion yankVersion(Long versionId,
                                    String actorUserId,
                                    String clientIp,
                                    String userAgent,
                                    String reason) {
        return agentLifecycleService.yankVersion(versionId, actorUserId, clientIp, userAgent, reason);
    }

    private String jsonReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return "{\"reason\":\"" + reason.replace("\"", "\\\"") + "\"}";
    }
}

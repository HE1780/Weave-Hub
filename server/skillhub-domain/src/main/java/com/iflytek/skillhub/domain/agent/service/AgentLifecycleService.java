package com.iflytek.skillhub.domain.agent.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Governance writes for agents — archive / unarchive at the Agent level.
 * Mirrors {@code SkillGovernanceService.archiveSkill / unarchiveSkill}, scoped to the
 * trimmed {@code AgentStatus} enum (no HIDDEN moderation state in v1).
 *
 * <p>Permission: agent owner OR namespace ADMIN/OWNER.
 */
@Service
public class AgentLifecycleService {

    private final AgentRepository agentRepository;

    public AgentLifecycleService(AgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    @Transactional
    public Agent archive(Long agentId,
                         String actorUserId,
                         Map<Long, NamespaceRole> userNamespaceRoles) {
        Agent agent = loadAndAuthorize(agentId, actorUserId, userNamespaceRoles);
        agent.archive();
        return agentRepository.save(agent);
    }

    @Transactional
    public Agent unarchive(Long agentId,
                           String actorUserId,
                           Map<Long, NamespaceRole> userNamespaceRoles) {
        Agent agent = loadAndAuthorize(agentId, actorUserId, userNamespaceRoles);
        agent.unarchive();
        return agentRepository.save(agent);
    }

    private Agent loadAndAuthorize(Long agentId,
                                   String actorUserId,
                                   Map<Long, NamespaceRole> userNamespaceRoles) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.notFound", agentId));
        Map<Long, NamespaceRole> roles = userNamespaceRoles == null ? Map.of() : userNamespaceRoles;
        NamespaceRole role = roles.get(agent.getNamespaceId());
        boolean canManage = agent.getOwnerId().equals(actorUserId)
                || role == NamespaceRole.ADMIN
                || role == NamespaceRole.OWNER;
        if (!canManage) {
            throw new DomainForbiddenException("error.agent.lifecycle.noPermission");
        }
        return agent;
    }
}

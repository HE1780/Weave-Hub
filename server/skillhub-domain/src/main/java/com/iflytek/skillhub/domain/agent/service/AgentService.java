package com.iflytek.skillhub.domain.agent.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentStatus;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.AgentVisibilityChecker;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Read-side application service for agents. Phase B will introduce
 * AgentPublishService for the write paths; this class focuses on visibility-gated lookups.
 */
@Service
@Transactional(readOnly = true)
public class AgentService {

    private final AgentRepository agentRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final AgentVisibilityChecker visibilityChecker;

    public AgentService(AgentRepository agentRepository,
                        AgentVersionRepository agentVersionRepository,
                        AgentVisibilityChecker visibilityChecker) {
        this.agentRepository = agentRepository;
        this.agentVersionRepository = agentVersionRepository;
        this.visibilityChecker = visibilityChecker;
    }

    /**
     * Public list — only PUBLIC + ACTIVE agents. Used by the unauthenticated /api/web/agents endpoint.
     */
    public Page<Agent> listPublic(Pageable pageable) {
        return agentRepository.findByVisibilityAndStatus(AgentVisibility.PUBLIC, AgentStatus.ACTIVE, pageable);
    }

    /**
     * Detail by namespace+slug, with visibility check. Throws DomainNotFoundException
     * if the agent doesn't exist OR if the caller lacks read permission (we don't leak
     * existence to unauthorized viewers).
     */
    public Agent getByNamespaceAndSlug(Long namespaceId,
                                       String slug,
                                       String currentUserId,
                                       Map<Long, NamespaceRole> userNamespaceRoles,
                                       Set<String> platformRoles) {
        Agent agent = agentRepository.findByNamespaceIdAndSlug(namespaceId, slug)
                .orElseThrow(() -> new DomainNotFoundException("Agent not found"));
        if (!visibilityChecker.canAccess(agent, currentUserId, userNamespaceRoles, platformRoles)) {
            throw new DomainNotFoundException("Agent not found");
        }
        return agent;
    }

    /**
     * Latest PUBLISHED version for the public detail view. Empty for agents that never published.
     */
    public Optional<AgentVersion> findLatestPublished(Long agentId) {
        return agentVersionRepository.findFirstByAgentIdAndStatusOrderByPublishedAtDesc(
                agentId, AgentVersionStatus.PUBLISHED);
    }

    /**
     * Version list for an agent. Public callers see PUBLISHED only; the owner and
     * namespace admins see all versions including DRAFT/PENDING_REVIEW/REJECTED.
     */
    public List<AgentVersion> listVersions(Agent agent,
                                           String currentUserId,
                                           Map<Long, NamespaceRole> userNamespaceRoles,
                                           Set<String> platformRoles) {
        boolean privileged = isOwner(agent, currentUserId)
                || isAdminOrAbove(userNamespaceRoles.get(agent.getNamespaceId()))
                || isSuperAdmin(platformRoles);
        List<AgentVersion> all = agentVersionRepository.findByAgentIdOrderBySubmittedAtDesc(agent.getId());
        if (privileged) {
            return all;
        }
        return all.stream()
                .filter(v -> v.getStatus() == AgentVersionStatus.PUBLISHED)
                .toList();
    }

    private boolean isOwner(Agent agent, String currentUserId) {
        return currentUserId != null && agent.getOwnerId().equals(currentUserId);
    }

    private boolean isAdminOrAbove(NamespaceRole role) {
        return role == NamespaceRole.ADMIN || role == NamespaceRole.OWNER;
    }

    private boolean isSuperAdmin(Set<String> platformRoles) {
        return platformRoles != null && platformRoles.contains("SUPER_ADMIN");
    }
}

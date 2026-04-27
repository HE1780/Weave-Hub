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
import org.springframework.data.domain.PageImpl;
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
     *
     * @deprecated New callers should use {@link #searchPublic} which supports the
     *     "everything I can see" semantics (Q4=B). This method is kept only because
     *     existing tests reference it; remove once those tests migrate.
     */
    @Deprecated
    public Page<Agent> listPublic(Pageable pageable) {
        return agentRepository.findByVisibilityAndStatus(AgentVisibility.PUBLIC, AgentStatus.ACTIVE, pageable);
    }

    /**
     * Search-and-list ACTIVE agents with caller-aware visibility filtering.
     *
     * <p>Repository pre-filters by keyword (display_name + description, case-insensitive)
     * and namespace; this method then strips entries the caller cannot read via
     * {@link AgentVisibilityChecker}, and optionally narrows by an explicit
     * {@code visibilityFilter}.
     *
     * <p><b>Visibility semantics (Q4=B "everything I can see"):</b>
     * <ul>
     *   <li>Anonymous caller → only PUBLIC items survive.</li>
     *   <li>Authenticated caller, {@code visibilityFilter == null} → PUBLIC plus
     *       caller-accessible PRIVATE/NAMESPACE_ONLY items.</li>
     *   <li>{@code visibilityFilter == PUBLIC} → only PUBLIC items.</li>
     *   <li>{@code visibilityFilter == PRIVATE} → only PRIVATE items the caller can read
     *       (own + namespace ADMIN+).</li>
     *   <li>{@code visibilityFilter == NAMESPACE_ONLY} → only NAMESPACE_ONLY items in
     *       namespaces where the caller has any role.</li>
     * </ul>
     *
     * <p><b>Pagination caveat:</b> the returned {@code Page.totalElements} reflects the
     * raw repository total before visibility filtering. For P0 ILIKE scale this is
     * acceptable; revisit when {@code agent_search_document} lands (P3-3).
     */
    public Page<Agent> searchPublic(String keyword,
                                    Long namespaceId,
                                    AgentVisibility visibilityFilter,
                                    String currentUserId,
                                    Map<Long, NamespaceRole> userNamespaceRoles,
                                    Set<String> platformRoles,
                                    Pageable pageable) {
        Page<Agent> raw = agentRepository.searchPublic(keyword, namespaceId, pageable);
        Map<Long, NamespaceRole> roles = userNamespaceRoles == null ? Map.of() : userNamespaceRoles;
        Set<String> platforms = platformRoles == null ? Set.of() : platformRoles;

        List<Agent> filtered = raw.getContent().stream()
                .filter(a -> visibilityChecker.canAccess(a, currentUserId, roles, platforms))
                .filter(a -> visibilityFilter == null || a.getVisibility() == visibilityFilter)
                .toList();
        return new PageImpl<>(filtered, pageable, raw.getTotalElements());
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

    /**
     * Whether {@code currentUserId} can perform agent-level governance actions
     * (archive / unarchive). Mirrors {@link AgentLifecycleService}'s authorization rule:
     * agent owner OR namespace ADMIN / OWNER. Anonymous callers always get {@code false}.
     *
     * <p>This is the authoritative read used by {@code AgentResponse.canManageLifecycle}
     * so the frontend can show governance UI to the same users the backend would accept.
     */
    public boolean canManageLifecycle(Agent agent,
                                      String currentUserId,
                                      Map<Long, NamespaceRole> userNamespaceRoles) {
        if (currentUserId == null) {
            return false;
        }
        if (isOwner(agent, currentUserId)) {
            return true;
        }
        Map<Long, NamespaceRole> roles = userNamespaceRoles == null ? Map.of() : userNamespaceRoles;
        return isAdminOrAbove(roles.get(agent.getNamespaceId()));
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

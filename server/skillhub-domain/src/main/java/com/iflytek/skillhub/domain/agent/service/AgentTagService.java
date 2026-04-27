package com.iflytek.skillhub.domain.agent.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentTag;
import com.iflytek.skillhub.domain.agent.AgentTagRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibilityChecker;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages named tags resolving to agent versions. Mirrors
 * {@link com.iflytek.skillhub.domain.skill.service.SkillTagService}: same
 * "latest" reserved tag, same namespace ADMIN/OWNER write requirement,
 * same visibility-gated read.
 *
 * <p>The synthetic "latest" tag is appended on read (so callers always
 * see it even though it is never persisted). Writes are rejected when
 * targeting it.
 */
@Service
public class AgentTagService {

    private static final String RESERVED_TAG_LATEST = "latest";

    private final NamespaceRepository namespaceRepository;
    private final NamespaceMemberRepository namespaceMemberRepository;
    private final AgentRepository agentRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final AgentTagRepository agentTagRepository;
    private final AgentVisibilityChecker visibilityChecker;

    public AgentTagService(NamespaceRepository namespaceRepository,
                           NamespaceMemberRepository namespaceMemberRepository,
                           AgentRepository agentRepository,
                           AgentVersionRepository agentVersionRepository,
                           AgentTagRepository agentTagRepository,
                           AgentVisibilityChecker visibilityChecker) {
        this.namespaceRepository = namespaceRepository;
        this.namespaceMemberRepository = namespaceMemberRepository;
        this.agentRepository = agentRepository;
        this.agentVersionRepository = agentVersionRepository;
        this.agentTagRepository = agentTagRepository;
        this.visibilityChecker = visibilityChecker;
    }

    public List<AgentTag> listTags(String namespaceSlug,
                                   String agentSlug,
                                   String currentUserId,
                                   Map<Long, NamespaceRole> userNamespaceRoles) {
        Namespace namespace = findNamespace(namespaceSlug);
        Agent agent = resolveVisibleAgent(namespace.getId(), agentSlug, currentUserId, userNamespaceRoles);
        if (!visibilityChecker.canAccess(agent, currentUserId, userNamespaceRoles, Set.of())) {
            throw new DomainForbiddenException("error.agent.access.denied", agentSlug);
        }
        List<AgentTag> tags = new ArrayList<>(agentTagRepository.findByAgentId(agent.getId()));
        AgentVersion latestPublished = agentVersionRepository
                .findFirstByAgentIdAndStatusOrderByPublishedAtDesc(agent.getId(), AgentVersionStatus.PUBLISHED)
                .orElse(null);
        if (latestPublished != null) {
            tags.add(new AgentTag(agent.getId(), RESERVED_TAG_LATEST, latestPublished.getId(), agent.getOwnerId()));
        }
        return tags;
    }

    public List<AgentTag> listTags(String namespaceSlug, String agentSlug) {
        return listTags(namespaceSlug, agentSlug, null, Map.of());
    }

    @Transactional
    public AgentTag createOrMoveTag(String namespaceSlug,
                                    String agentSlug,
                                    String tagName,
                                    String targetVersion,
                                    String operatorId) {
        if (RESERVED_TAG_LATEST.equalsIgnoreCase(tagName)) {
            throw new DomainBadRequestException("error.agent.tag.latest.reserved");
        }

        Namespace namespace = findNamespace(namespaceSlug);
        assertAdminOrOwner(namespace.getId(), operatorId);
        Agent agent = resolveVisibleAgent(namespace.getId(), agentSlug, operatorId, Map.of());

        AgentVersion version = agentVersionRepository.findByAgentIdAndVersion(agent.getId(), targetVersion)
                .orElseThrow(() -> new DomainBadRequestException("error.agent.version.notFound", targetVersion));

        if (version.getStatus() != AgentVersionStatus.PUBLISHED) {
            throw new DomainBadRequestException("error.agent.tag.targetVersion.notPublished");
        }

        AgentTag existing = agentTagRepository.findByAgentIdAndTagName(agent.getId(), tagName).orElse(null);
        if (existing != null) {
            existing.setVersionId(version.getId());
            return agentTagRepository.save(existing);
        }
        return agentTagRepository.save(new AgentTag(agent.getId(), tagName, version.getId(), operatorId));
    }

    @Transactional
    public void deleteTag(String namespaceSlug, String agentSlug, String tagName, String operatorId) {
        if (RESERVED_TAG_LATEST.equalsIgnoreCase(tagName)) {
            throw new DomainBadRequestException("error.agent.tag.latest.delete");
        }

        Namespace namespace = findNamespace(namespaceSlug);
        assertAdminOrOwner(namespace.getId(), operatorId);
        Agent agent = resolveVisibleAgent(namespace.getId(), agentSlug, operatorId, Map.of());

        AgentTag tag = agentTagRepository.findByAgentIdAndTagName(agent.getId(), tagName)
                .orElseThrow(() -> new DomainBadRequestException("error.agent.tag.notFound", tagName));
        agentTagRepository.delete(tag);
    }

    private Namespace findNamespace(String slug) {
        return namespaceRepository.findBySlug(slug)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", slug));
    }

    private Agent resolveVisibleAgent(Long namespaceId,
                                      String agentSlug,
                                      String currentUserId,
                                      Map<Long, NamespaceRole> userNamespaceRoles) {
        Agent agent = agentRepository.findByNamespaceIdAndSlug(namespaceId, agentSlug)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.notFound", agentSlug));
        // Probe used by AgentService for permission queries; here we simply ensure
        // visibility for non-write paths so callers never leak the existence of
        // hidden agents.
        if (!visibilityChecker.canAccess(agent, currentUserId, userNamespaceRoles, Set.of())) {
            throw new DomainNotFoundException("error.agent.notFound", agentSlug);
        }
        return agent;
    }

    private void assertAdminOrOwner(Long namespaceId, String operatorId) {
        NamespaceRole role = namespaceMemberRepository.findByNamespaceIdAndUserId(namespaceId, operatorId)
                .map(member -> member.getRole())
                .orElseThrow(() -> new DomainForbiddenException("error.namespace.membership.required"));
        if (role != NamespaceRole.OWNER && role != NamespaceRole.ADMIN) {
            throw new DomainForbiddenException("error.namespace.admin.required");
        }
    }
}

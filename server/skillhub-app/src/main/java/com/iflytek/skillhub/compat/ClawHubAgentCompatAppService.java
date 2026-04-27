package com.iflytek.skillhub.compat;

import com.iflytek.skillhub.compat.dto.ClawHubResolveResponse;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibilityChecker;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Compatibility service that exposes Agent records via the ClawHub-style
 * canonical slug protocol. Mirrors {@link ClawHubCompatAppService} for the
 * resolve/download surface; A8 v1 covers only the read-and-pull path
 * (CLI publish for agents is out of scope).
 */
@Service
public class ClawHubAgentCompatAppService {

    private final CanonicalSlugMapper mapper;
    private final NamespaceRepository namespaceRepository;
    private final AgentRepository agentRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final AgentVisibilityChecker visibilityChecker;

    public ClawHubAgentCompatAppService(CanonicalSlugMapper mapper,
                                        NamespaceRepository namespaceRepository,
                                        AgentRepository agentRepository,
                                        AgentVersionRepository agentVersionRepository,
                                        AgentVisibilityChecker visibilityChecker) {
        this.mapper = mapper;
        this.namespaceRepository = namespaceRepository;
        this.agentRepository = agentRepository;
        this.agentVersionRepository = agentVersionRepository;
        this.visibilityChecker = visibilityChecker;
    }

    public ClawHubResolveResponse resolve(String canonicalSlug,
                                          String version,
                                          String userId,
                                          Map<Long, NamespaceRole> userNsRoles) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        AgentResolution resolution = resolveAgentVersion(coord, version, userId, userNsRoles);
        ClawHubResolveResponse.VersionInfo matched = new ClawHubResolveResponse.VersionInfo(resolution.matched().getVersion());
        ClawHubResolveResponse.VersionInfo latest = new ClawHubResolveResponse.VersionInfo(resolution.latest().getVersion());
        return new ClawHubResolveResponse(matched, latest);
    }

    public String downloadLocationByPath(String canonicalSlug, String version) {
        SkillCoordinate coord = mapper.fromCanonical(canonicalSlug);
        return "latest".equals(version)
                ? "/api/v1/agents/" + coord.namespace() + "/" + coord.slug() + "/download"
                : "/api/v1/agents/" + coord.namespace() + "/" + coord.slug() + "/versions/" + version + "/download";
    }

    public String downloadLocationByQuery(String slug, String version) {
        SkillCoordinate coord = mapper.fromCanonical(slug);
        return downloadLocationByPath(mapper.toCanonical(coord.namespace(), coord.slug()), version);
    }

    private AgentResolution resolveAgentVersion(SkillCoordinate coord,
                                                String version,
                                                String userId,
                                                Map<Long, NamespaceRole> userNsRoles) {
        Namespace namespace = namespaceRepository.findBySlug(coord.namespace())
                .orElseThrow(() -> new DomainNotFoundException("error.namespace.notFound", coord.namespace()));
        Agent agent = agentRepository.findByNamespaceIdAndSlug(namespace.getId(), coord.slug())
                .orElseThrow(() -> new DomainNotFoundException("error.agent.notFound", coord.slug()));

        Map<Long, NamespaceRole> roles = userNsRoles == null ? Map.of() : userNsRoles;
        if (!visibilityChecker.canAccess(agent, userId, roles, Set.of())) {
            throw new DomainNotFoundException("error.agent.notFound", coord.slug());
        }

        AgentVersion latestPublished = agentVersionRepository
                .findFirstByAgentIdAndStatusOrderByPublishedAtDesc(agent.getId(), AgentVersionStatus.PUBLISHED)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.version.latest.unavailable", coord.slug()));

        AgentVersion matched;
        if (version == null || version.isBlank() || "latest".equals(version)) {
            matched = latestPublished;
        } else {
            matched = agentVersionRepository.findByAgentIdAndVersion(agent.getId(), version)
                    .filter(v -> v.getStatus() == AgentVersionStatus.PUBLISHED)
                    .orElseThrow(() -> new DomainNotFoundException("error.agent.version.notFound", version));
        }
        return new AgentResolution(matched, latestPublished);
    }

    private record AgentResolution(AgentVersion matched, AgentVersion latest) {}
}

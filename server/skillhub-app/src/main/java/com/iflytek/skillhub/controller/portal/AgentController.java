package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.service.AgentService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.AgentResponse;
import com.iflytek.skillhub.dto.AgentVersionResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Public read endpoints for agents. Anonymous callers see PUBLIC + ACTIVE only;
 * authenticated callers additionally see namespace-only and private agents they own
 * or have admin access to.
 */
@RestController
@RequestMapping({"/api/v1/agents", "/api/web/agents"})
public class AgentController extends BaseApiController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AgentService agentService;
    private final NamespaceRepository namespaceRepository;

    public AgentController(AgentService agentService,
                           NamespaceRepository namespaceRepository,
                           ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.agentService = agentService;
        this.namespaceRepository = namespaceRepository;
    }

    @GetMapping
    public ApiResponse<PageResponse<AgentResponse>> listPublic(
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "namespace", required = false) String namespace,
            @RequestParam(value = "visibility", required = false) String visibility,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Long namespaceId = null;
        if (namespace != null && !namespace.isBlank()) {
            namespaceId = namespaceRepository.findBySlug(namespace)
                    .orElseThrow(() -> new DomainNotFoundException("Namespace not found: " + namespace))
                    .getId();
        }

        AgentVisibility visibilityFilter = parseVisibility(visibility);

        Page<Agent> result = agentService.searchPublic(
                q,
                namespaceId,
                visibilityFilter,
                principal == null ? null : principal.userId(),
                rolesOrEmpty(userNsRoles),
                principal == null ? Set.of() : principal.platformRoles(),
                PageRequest.of(Math.max(page, 0), safeSize));

        Map<Long, String> namespaceSlugs = resolveNamespaceSlugs(result.getContent());
        String currentUserId = principal == null ? null : principal.userId();
        Map<Long, NamespaceRole> rolesForCheck = rolesOrEmpty(userNsRoles);
        List<AgentResponse> items = result.getContent().stream()
                .map(a -> AgentResponse.from(
                        a,
                        namespaceSlugs.getOrDefault(a.getNamespaceId(), ""),
                        agentService.canManageLifecycle(a, currentUserId, rolesForCheck)))
                .toList();

        return ok("response.success.read", new PageResponse<>(
                items, result.getTotalElements(), result.getNumber(), result.getSize()));
    }

    private AgentVisibility parseVisibility(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return AgentVisibility.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DomainBadRequestException("Invalid visibility: " + raw);
        }
    }

    @GetMapping("/{namespace}/{slug}")
    public ApiResponse<AgentResponse> getOne(
            @PathVariable String namespace,
            @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        Namespace ns = resolveNamespaceOr404(namespace);
        String currentUserId = principal == null ? null : principal.userId();
        Map<Long, NamespaceRole> roles = rolesOrEmpty(userNsRoles);
        Agent agent = agentService.getByNamespaceAndSlug(
                ns.getId(), slug,
                currentUserId,
                roles,
                principal == null ? Set.of() : principal.platformRoles());

        return ok("response.success.read",
                AgentResponse.from(agent, namespace,
                        agentService.canManageLifecycle(agent, currentUserId, roles)));
    }

    @GetMapping("/{namespace}/{slug}/versions")
    public ApiResponse<List<AgentVersionResponse>> listVersions(
            @PathVariable String namespace,
            @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        Namespace ns = resolveNamespaceOr404(namespace);
        Agent agent = agentService.getByNamespaceAndSlug(
                ns.getId(), slug,
                principal == null ? null : principal.userId(),
                rolesOrEmpty(userNsRoles),
                principal == null ? Set.of() : principal.platformRoles());

        List<AgentVersion> versions = agentService.listVersions(
                agent,
                principal == null ? null : principal.userId(),
                rolesOrEmpty(userNsRoles),
                principal == null ? Set.of() : principal.platformRoles());

        return ok("response.success.read",
                versions.stream().map(AgentVersionResponse::summaryFrom).toList());
    }

    @GetMapping("/{namespace}/{slug}/versions/{version}")
    public ApiResponse<AgentVersionResponse> getVersion(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        Namespace ns = resolveNamespaceOr404(namespace);
        Agent agent = agentService.getByNamespaceAndSlug(
                ns.getId(), slug,
                principal == null ? null : principal.userId(),
                rolesOrEmpty(userNsRoles),
                principal == null ? Set.of() : principal.platformRoles());

        AgentVersion v = agentService.listVersions(
                        agent,
                        principal == null ? null : principal.userId(),
                        rolesOrEmpty(userNsRoles),
                        principal == null ? Set.of() : principal.platformRoles())
                .stream()
                .filter(av -> av.getVersion().equals(version))
                .findFirst()
                .orElseThrow(() -> new DomainNotFoundException("Agent version not found"));

        return ok("response.success.read", AgentVersionResponse.from(v));
    }

    private Namespace resolveNamespaceOr404(String slug) {
        return namespaceRepository.findBySlug(slug)
                .orElseThrow(() -> new DomainNotFoundException("Namespace not found: " + slug));
    }

    private Map<Long, String> resolveNamespaceSlugs(List<Agent> agents) {
        Map<Long, String> result = new HashMap<>();
        for (Agent a : agents) {
            if (!result.containsKey(a.getNamespaceId())) {
                namespaceRepository.findById(a.getNamespaceId())
                        .ifPresent(ns -> result.put(a.getNamespaceId(), ns.getSlug()));
            }
        }
        return result;
    }

    private Map<Long, NamespaceRole> rolesOrEmpty(Map<Long, NamespaceRole> roles) {
        return roles == null ? Map.of() : roles;
    }
}

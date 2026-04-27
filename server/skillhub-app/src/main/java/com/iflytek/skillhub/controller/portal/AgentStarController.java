package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.social.AgentStarService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints for starring, unstarring, and checking star state on an agent.
 * Mirrors {@link SkillStarController} but uses {namespace}/{slug} (3-segment)
 * paths to avoid colliding with the GET /agents/{ns}/{slug} detail route.
 */
@RestController
@RequestMapping({"/api/v1/agents", "/api/web/agents"})
public class AgentStarController extends BaseApiController {

    private final AgentStarService agentStarService;
    private final AgentRepository agentRepository;
    private final NamespaceRepository namespaceRepository;

    public AgentStarController(ApiResponseFactory responseFactory,
                               AgentStarService agentStarService,
                               AgentRepository agentRepository,
                               NamespaceRepository namespaceRepository) {
        super(responseFactory);
        this.agentStarService = agentStarService;
        this.agentRepository = agentRepository;
        this.namespaceRepository = namespaceRepository;
    }

    @PutMapping("/{namespace}/{slug}/star")
    public ApiResponse<Void> starAgent(
            @PathVariable String namespace,
            @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        Agent agent = resolve(namespace, slug);
        agentStarService.star(agent.getId(), principal.userId());
        return ok("response.success.updated", null);
    }

    @DeleteMapping("/{namespace}/{slug}/star")
    public ApiResponse<Void> unstarAgent(
            @PathVariable String namespace,
            @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        Agent agent = resolve(namespace, slug);
        agentStarService.unstar(agent.getId(), principal.userId());
        return ok("response.success.updated", null);
    }

    @GetMapping("/{namespace}/{slug}/star")
    public ApiResponse<Boolean> checkStarred(
            @PathVariable String namespace,
            @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        Agent agent = resolve(namespace, slug);
        if (principal == null) {
            return ok("response.success.read", false);
        }
        boolean starred = agentStarService.isStarred(agent.getId(), principal.userId());
        return ok("response.success.read", starred);
    }

    private Agent resolve(String namespaceSlug, String slug) {
        String cleanNamespace = namespaceSlug.startsWith("@") ? namespaceSlug.substring(1) : namespaceSlug;
        Namespace ns = namespaceRepository.findBySlug(cleanNamespace)
                .orElseThrow(() -> new DomainNotFoundException("Namespace not found: " + cleanNamespace));
        return agentRepository.findByNamespaceIdAndSlug(ns.getId(), slug)
                .orElseThrow(() -> new DomainNotFoundException("Agent not found: " + slug));
    }
}

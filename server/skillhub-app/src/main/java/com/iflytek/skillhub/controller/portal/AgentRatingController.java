package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.social.AgentRatingService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.AgentRatingRequest;
import com.iflytek.skillhub.dto.AgentRatingStatusResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

/**
 * Endpoints for reading and mutating the current user's rating on an agent.
 * Mirrors {@link SkillRatingController}; uses {namespace}/{slug} path for
 * the same routing-collision reason as {@link AgentStarController}.
 */
@RestController
@RequestMapping({"/api/v1/agents", "/api/web/agents"})
public class AgentRatingController extends BaseApiController {

    private final AgentRatingService agentRatingService;
    private final AgentRepository agentRepository;
    private final NamespaceRepository namespaceRepository;

    public AgentRatingController(ApiResponseFactory responseFactory,
                                 AgentRatingService agentRatingService,
                                 AgentRepository agentRepository,
                                 NamespaceRepository namespaceRepository) {
        super(responseFactory);
        this.agentRatingService = agentRatingService;
        this.agentRepository = agentRepository;
        this.namespaceRepository = namespaceRepository;
    }

    @PutMapping("/{namespace}/{slug}/rating")
    public ApiResponse<Void> rateAgent(
            @PathVariable String namespace,
            @PathVariable String slug,
            @Valid @RequestBody AgentRatingRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        Agent agent = resolve(namespace, slug);
        agentRatingService.rate(agent.getId(), principal.userId(), request.score());
        return ok("response.success.updated", null);
    }

    @GetMapping("/{namespace}/{slug}/rating")
    public ApiResponse<AgentRatingStatusResponse> getUserRating(
            @PathVariable String namespace,
            @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        Agent agent = resolve(namespace, slug);
        if (principal == null) {
            return ok("response.success.read", new AgentRatingStatusResponse((short) 0, false));
        }
        Optional<Short> rating = agentRatingService.getUserRating(agent.getId(), principal.userId());
        return ok(
                "response.success.read",
                new AgentRatingStatusResponse(
                        rating.orElse((short) 0),
                        rating.isPresent()
                )
        );
    }

    private Agent resolve(String namespaceSlug, String slug) {
        String cleanNamespace = namespaceSlug.startsWith("@") ? namespaceSlug.substring(1) : namespaceSlug;
        Namespace ns = namespaceRepository.findBySlug(cleanNamespace)
                .orElseThrow(() -> new DomainNotFoundException("Namespace not found: " + cleanNamespace));
        return agentRepository.findByNamespaceIdAndSlug(ns.getId(), slug)
                .orElseThrow(() -> new DomainNotFoundException("Agent not found: " + slug));
    }
}

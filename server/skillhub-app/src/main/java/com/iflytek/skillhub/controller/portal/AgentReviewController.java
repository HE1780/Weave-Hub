package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.review.AgentReviewService;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTask;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTaskStatus;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.AgentReviewActionRequest;
import com.iflytek.skillhub.dto.AgentReviewTaskResponse;
import com.iflytek.skillhub.dto.AgentReviewVersionDetailResponse;
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
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping({"/api/v1/agents/reviews", "/api/web/agents/reviews"})
public class AgentReviewController extends BaseApiController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AgentReviewService reviewService;
    private final AgentRepository agentRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final NamespaceRepository namespaceRepository;

    public AgentReviewController(AgentReviewService reviewService,
                                 AgentRepository agentRepository,
                                 AgentVersionRepository agentVersionRepository,
                                 NamespaceRepository namespaceRepository,
                                 ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.reviewService = reviewService;
        this.agentRepository = agentRepository;
        this.agentVersionRepository = agentVersionRepository;
        this.namespaceRepository = namespaceRepository;
    }

    @GetMapping
    public ApiResponse<PageResponse<AgentReviewTaskResponse>> list(
            @RequestParam("namespaceId") Long namespaceId,
            @RequestParam(value = "status", defaultValue = "PENDING") String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        requireAuth(principal);
        AgentReviewTaskStatus statusEnum = parseStatus(status);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<AgentReviewTask> result = reviewService.listForReviewer(
                namespaceId,
                statusEnum,
                rolesOrEmpty(userNsRoles),
                principal.platformRoles(),
                PageRequest.of(Math.max(page, 0), safeSize));

        return ok("response.success.read", new PageResponse<>(
                result.getContent().stream().map(AgentReviewTaskResponse::from).toList(),
                result.getTotalElements(),
                result.getNumber(),
                result.getSize()));
    }

    @GetMapping("/{taskId}")
    public ApiResponse<AgentReviewTaskResponse> getOne(
            @PathVariable Long taskId,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        requireAuth(principal);
        AgentReviewTask task = reviewService.getById(taskId, rolesOrEmpty(userNsRoles), principal.platformRoles());
        return ok("response.success.read", AgentReviewTaskResponse.from(task));
    }

    /**
     * Returns the full review payload: task + agent metadata + version (with
     * inline soul.md and workflow.yaml). Reviewers need this on the detail
     * screen; the lighter {@link #getOne} stays for callers that only need the
     * task row.
     */
    @GetMapping("/{taskId}/detail")
    public ApiResponse<AgentReviewVersionDetailResponse> getDetail(
            @PathVariable Long taskId,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        requireAuth(principal);
        AgentReviewTask task = reviewService.getById(taskId, rolesOrEmpty(userNsRoles), principal.platformRoles());
        AgentVersion version = agentVersionRepository.findById(task.getAgentVersionId())
                .orElseThrow(() -> new DomainNotFoundException(
                        "Agent version not found: " + task.getAgentVersionId()));
        Agent agent = agentRepository.findById(version.getAgentId())
                .orElseThrow(() -> new DomainNotFoundException(
                        "Agent not found: " + version.getAgentId()));
        Namespace namespace = namespaceRepository.findById(agent.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException(
                        "Namespace not found: " + agent.getNamespaceId()));

        AgentReviewVersionDetailResponse body = new AgentReviewVersionDetailResponse(
                AgentReviewTaskResponse.from(task),
                AgentResponse.from(agent, namespace.getSlug()),
                AgentVersionResponse.from(version));
        return ok("response.success.read", body);
    }

    @PostMapping("/{taskId}/approve")
    public ApiResponse<AgentReviewTaskResponse> approve(
            @PathVariable Long taskId,
            @RequestBody(required = false) AgentReviewActionRequest body,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        requireAuth(principal);
        AgentReviewTask task = reviewService.approve(
                taskId,
                principal.userId(),
                body == null ? null : body.comment(),
                rolesOrEmpty(userNsRoles),
                principal.platformRoles());
        return ok("response.success.updated", AgentReviewTaskResponse.from(task));
    }

    @PostMapping("/{taskId}/reject")
    public ApiResponse<AgentReviewTaskResponse> reject(
            @PathVariable Long taskId,
            @RequestBody(required = false) AgentReviewActionRequest body,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        requireAuth(principal);
        AgentReviewTask task = reviewService.reject(
                taskId,
                principal.userId(),
                body == null ? null : body.comment(),
                rolesOrEmpty(userNsRoles),
                principal.platformRoles());
        return ok("response.success.updated", AgentReviewTaskResponse.from(task));
    }

    private void requireAuth(PlatformPrincipal principal) {
        if (principal == null) {
            throw new DomainForbiddenException("Authenticated principal required");
        }
    }

    private Map<Long, NamespaceRole> rolesOrEmpty(Map<Long, NamespaceRole> roles) {
        return roles == null ? new HashMap<>() : roles;
    }

    private AgentReviewTaskStatus parseStatus(String raw) {
        try {
            return AgentReviewTaskStatus.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new DomainBadRequestException("Invalid status: " + raw);
        }
    }

    @SuppressWarnings("unused")
    private Set<String> noPlatformRoles() {
        return Set.of();
    }
}

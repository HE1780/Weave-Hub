package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.agent.AgentTag;
import com.iflytek.skillhub.domain.agent.service.AgentTagService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.AgentTagResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.dto.TagRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Endpoints for reading and mutating named tags that point to agent versions.
 * Mirrors {@link SkillTagController}; uses {@code @AuthenticationPrincipal
 * PlatformPrincipal} to match the rest of the Agent controllers rather than
 * the legacy {@code @RequestAttribute("userId")} pattern Skill side uses.
 */
@RestController
@RequestMapping({
        "/api/v1/agents/{namespace}/{slug}/tags",
        "/api/web/agents/{namespace}/{slug}/tags"
})
public class AgentTagController extends BaseApiController {

    private final AgentTagService agentTagService;

    public AgentTagController(AgentTagService agentTagService,
                              ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.agentTagService = agentTagService;
    }

    @GetMapping
    public ApiResponse<List<AgentTagResponse>> listTags(
            @PathVariable String namespace,
            @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

        List<AgentTag> tags = agentTagService.listTags(
                namespace,
                slug,
                principal == null ? null : principal.userId(),
                userNsRoles != null ? userNsRoles : Map.of()
        );

        List<AgentTagResponse> response = tags.stream()
                .map(AgentTagResponse::from)
                .collect(Collectors.toList());

        return ok("response.success.read", response);
    }

    @PutMapping("/{tagName}")
    public ApiResponse<AgentTagResponse> createOrMoveTag(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String tagName,
            @Valid @RequestBody TagRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {

        AgentTag tag = agentTagService.createOrMoveTag(
                namespace,
                slug,
                tagName,
                request.targetVersion(),
                principal.userId()
        );

        return ok("response.success.updated", AgentTagResponse.from(tag));
    }

    @DeleteMapping("/{tagName}")
    public ApiResponse<MessageResponse> deleteTag(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String tagName,
            @AuthenticationPrincipal PlatformPrincipal principal) {

        agentTagService.deleteTag(namespace, slug, tagName, principal.userId());
        return ok("response.success.deleted", new MessageResponse("Tag deleted"));
    }
}

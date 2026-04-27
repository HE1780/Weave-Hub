package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.dto.AgentLabelDto;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.service.AgentLabelAppService;
import com.iflytek.skillhub.service.AuditRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints for attaching/detaching shared {@code label_definition} entries to
 * agents. Mirrors {@link SkillLabelController} but follows the Agent-side
 * principal style ({@code @AuthenticationPrincipal PlatformPrincipal}).
 */
@RestController
@RequestMapping({
        "/api/v1/agents/{namespace}/{slug}/labels",
        "/api/web/agents/{namespace}/{slug}/labels"
})
public class AgentLabelController extends BaseApiController {

    private final AgentLabelAppService agentLabelAppService;

    public AgentLabelController(AgentLabelAppService agentLabelAppService,
                                ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.agentLabelAppService = agentLabelAppService;
    }

    @GetMapping
    public ApiResponse<List<AgentLabelDto>> listLabels(
            @PathVariable String namespace,
            @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {
        return ok("response.success.read",
                agentLabelAppService.listAgentLabels(
                        namespace,
                        slug,
                        principal == null ? null : principal.userId(),
                        userNsRoles != null ? userNsRoles : Map.of()));
    }

    @PutMapping("/{labelSlug}")
    public ApiResponse<AgentLabelDto> attachLabel(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String labelSlug,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            HttpServletRequest httpRequest) {
        return ok("response.success.updated",
                agentLabelAppService.attachLabel(
                        namespace,
                        slug,
                        labelSlug,
                        principal.userId(),
                        userNsRoles != null ? userNsRoles : Map.of(),
                        AuditRequestContext.from(httpRequest)));
    }

    @DeleteMapping("/{labelSlug}")
    public ApiResponse<MessageResponse> detachLabel(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String labelSlug,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            HttpServletRequest httpRequest) {
        return ok("response.success.deleted",
                agentLabelAppService.detachLabel(
                        namespace,
                        slug,
                        labelSlug,
                        principal.userId(),
                        userNsRoles != null ? userNsRoles : Map.of(),
                        AuditRequestContext.from(httpRequest)));
    }
}

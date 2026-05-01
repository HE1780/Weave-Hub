package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.service.AgentGovernanceService;
import com.iflytek.skillhub.dto.AdminAgentActionRequest;
import com.iflytek.skillhub.dto.AdminAgentMutationResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative agent-governance endpoints reserved for platform-level
 * moderation actions: hide / unhide an Agent and yank a published version.
 * Mirrors {@link AdminSkillController}.
 */
@RestController
@RequestMapping("/api/v1/admin/agents")
public class AdminAgentController extends BaseApiController {

    private final AgentGovernanceService agentGovernanceService;

    public AdminAgentController(ApiResponseFactory responseFactory,
                                AgentGovernanceService agentGovernanceService) {
        super(responseFactory);
        this.agentGovernanceService = agentGovernanceService;
    }

    @PostMapping("/{agentId}/hide")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<AdminAgentMutationResponse> hideAgent(@PathVariable Long agentId,
                                                             @RequestBody(required = false) AdminAgentActionRequest request,
                                                             @AuthenticationPrincipal PlatformPrincipal principal,
                                                             HttpServletRequest httpRequest) {
        Agent agent = agentGovernanceService.hideAgent(
                agentId,
                principal.userId(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"),
                request != null ? request.reason() : null);
        return ok("response.success.updated",
                new AdminAgentMutationResponse(agent.getId(), null, "HIDE", agent.getStatus().name()));
    }

    @PostMapping("/{agentId}/unhide")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ApiResponse<AdminAgentMutationResponse> unhideAgent(@PathVariable Long agentId,
                                                               @AuthenticationPrincipal PlatformPrincipal principal,
                                                               HttpServletRequest httpRequest) {
        Agent agent = agentGovernanceService.unhideAgent(
                agentId,
                principal.userId(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"));
        return ok("response.success.updated",
                new AdminAgentMutationResponse(agent.getId(), null, "UNHIDE", agent.getStatus().name()));
    }

    @PostMapping("/versions/{versionId}/yank")
    @PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AdminAgentMutationResponse> yankVersion(@PathVariable Long versionId,
                                                               @RequestBody(required = false) AdminAgentActionRequest request,
                                                               @AuthenticationPrincipal PlatformPrincipal principal,
                                                               HttpServletRequest httpRequest) {
        AgentVersion version = agentGovernanceService.yankVersion(
                versionId,
                principal.userId(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent"),
                request != null ? request.reason() : null);
        return ok("response.success.updated",
                new AdminAgentMutationResponse(version.getAgentId(), versionId, "YANK", version.getStatus().name()));
    }
}

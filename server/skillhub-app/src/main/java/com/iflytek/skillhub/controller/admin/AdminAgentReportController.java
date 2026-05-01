package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.agent.report.AgentReportDisposition;
import com.iflytek.skillhub.domain.agent.report.AgentReportService;
import com.iflytek.skillhub.dto.AdminAgentReportActionRequest;
import com.iflytek.skillhub.dto.AdminAgentReportSummaryResponse;
import com.iflytek.skillhub.dto.AgentReportMutationResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.AdminAgentReportAppService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative endpoints for reviewing and resolving user-submitted agent
 * reports. Mirrors {@link AdminSkillReportController}.
 *
 * <p>Both {@code SKILL_ADMIN} and {@code SUPER_ADMIN} can view, resolve, and
 * dismiss reports. The {@code RESOLVE_AND_HIDE} disposition routes through
 * {@code AgentGovernanceService.hideAgent}; {@code RESOLVE_AND_ARCHIVE} stays
 * on {@code AgentLifecycleService.archiveAsAdmin}.
 */
@RestController
@RequestMapping("/api/v1/admin/agent-reports")
public class AdminAgentReportController extends BaseApiController {

    private final AdminAgentReportAppService adminAgentReportAppService;
    private final AgentReportService agentReportService;

    public AdminAgentReportController(AdminAgentReportAppService adminAgentReportAppService,
                                      AgentReportService agentReportService,
                                      ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.adminAgentReportAppService = adminAgentReportAppService;
        this.agentReportService = agentReportService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<PageResponse<AdminAgentReportSummaryResponse>> listReports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok("response.success", adminAgentReportAppService.listReports(status, page, size));
    }

    @PostMapping("/{reportId}/resolve")
    @PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AgentReportMutationResponse> resolveReport(@PathVariable Long reportId,
                                                                  @RequestBody(required = false) AdminAgentReportActionRequest request,
                                                                  @AuthenticationPrincipal PlatformPrincipal principal,
                                                                  HttpServletRequest httpRequest) {
        AgentReportDisposition disposition = request != null && request.disposition() != null
                ? AgentReportDisposition.valueOf(request.disposition().trim().toUpperCase())
                : AgentReportDisposition.RESOLVE_ONLY;
        var report = agentReportService.resolveReport(
                reportId,
                principal.userId(),
                disposition,
                request != null ? request.comment() : null,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ok("response.success.updated", new AgentReportMutationResponse(report.getId(), report.getStatus().name()));
    }

    @PostMapping("/{reportId}/dismiss")
    @PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AgentReportMutationResponse> dismissReport(@PathVariable Long reportId,
                                                                  @RequestBody(required = false) AdminAgentReportActionRequest request,
                                                                  @AuthenticationPrincipal PlatformPrincipal principal,
                                                                  HttpServletRequest httpRequest) {
        var report = agentReportService.dismissReport(
                reportId,
                principal.userId(),
                request != null ? request.comment() : null,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ok("response.success.updated", new AgentReportMutationResponse(report.getId(), report.getStatus().name()));
    }
}

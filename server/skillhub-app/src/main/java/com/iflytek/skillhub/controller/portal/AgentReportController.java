package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.report.AgentReport;
import com.iflytek.skillhub.domain.agent.report.AgentReportService;
import com.iflytek.skillhub.domain.agent.service.AgentService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.dto.AgentReportMutationResponse;
import com.iflytek.skillhub.dto.AgentReportSubmitRequest;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

/**
 * Agent abuse-report submit endpoint. Mirrors
 * {@link SkillReportController} on the {namespace}/{slug} resolution
 * pattern. The admin-side moderation queue is intentionally deferred to a
 * later A6 follow-up — only the user-facing submit lives here in v1.
 */
@RestController
@RequestMapping({"/api/v1/agents", "/api/web/agents"})
public class AgentReportController extends BaseApiController {

    private final NamespaceRepository namespaceRepository;
    private final AgentService agentService;
    private final AgentReportService agentReportService;

    public AgentReportController(NamespaceRepository namespaceRepository,
                                 AgentService agentService,
                                 AgentReportService agentReportService,
                                 ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.namespaceRepository = namespaceRepository;
        this.agentService = agentService;
        this.agentReportService = agentReportService;
    }

    @PostMapping("/{namespace}/{slug}/reports")
    public ApiResponse<AgentReportMutationResponse> submitReport(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestBody AgentReportSubmitRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            HttpServletRequest httpRequest) {

        Agent agent = resolveAgent(namespace, slug, principal, userNsRoles);
        AgentReport report = agentReportService.submitReport(
                agent.getId(),
                principal.userId(),
                request.reason(),
                request.details(),
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ok("response.success.created", new AgentReportMutationResponse(
                report.getId(), report.getStatus().name()));
    }

    private Agent resolveAgent(String namespaceSlug,
                               String slug,
                               PlatformPrincipal principal,
                               Map<Long, NamespaceRole> userNsRoles) {
        String cleanNamespace = namespaceSlug.startsWith("@") ? namespaceSlug.substring(1) : namespaceSlug;
        Namespace ns = namespaceRepository.findBySlug(cleanNamespace)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", cleanNamespace));
        return agentService.getByNamespaceAndSlug(
                ns.getId(),
                slug,
                principal.userId(),
                userNsRoles == null ? Map.of() : userNsRoles,
                principal.platformRoles() == null ? Set.of() : principal.platformRoles());
    }
}

package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.service.AgentHardDeleteService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.AgentDeleteResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.service.AuditRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hard-delete endpoint for an Agent. Mirrors {@code SkillDeleteController} on the
 * web surface: owner OR SUPER_ADMIN can call. Cascades agent_version, agent_star,
 * agent_rating + zip(s) in object storage. Idempotent — missing slug returns
 * {@code deleted=false}.
 */
@RestController
@RequestMapping({"/api/v1/agents", "/api/web/agents"})
public class AgentDeleteController extends BaseApiController {

    private final AgentHardDeleteService agentHardDeleteService;
    private final AgentRepository agentRepository;
    private final NamespaceRepository namespaceRepository;

    public AgentDeleteController(ApiResponseFactory responseFactory,
                                 AgentHardDeleteService agentHardDeleteService,
                                 AgentRepository agentRepository,
                                 NamespaceRepository namespaceRepository) {
        super(responseFactory);
        this.agentHardDeleteService = agentHardDeleteService;
        this.agentRepository = agentRepository;
        this.namespaceRepository = namespaceRepository;
    }

    @DeleteMapping("/{namespace}/{slug}")
    public ApiResponse<AgentDeleteResponse> deleteAgent(
            @PathVariable String namespace,
            @PathVariable String slug,
            @AuthenticationPrincipal PlatformPrincipal principal,
            HttpServletRequest httpRequest) {

        String cleanNamespace = namespace.startsWith("@") ? namespace.substring(1) : namespace;
        Namespace ns = namespaceRepository.findBySlug(cleanNamespace).orElse(null);
        if (ns == null) {
            return ok("response.success.deleted",
                    new AgentDeleteResponse(null, namespace, slug, false));
        }
        Agent agent = agentRepository.findByNamespaceIdAndSlug(ns.getId(), slug).orElse(null);
        if (agent == null) {
            return ok("response.success.deleted",
                    new AgentDeleteResponse(null, namespace, slug, false));
        }

        boolean isSuperAdmin = principal.platformRoles() != null
                && principal.platformRoles().contains("SUPER_ADMIN");
        if (!isSuperAdmin && !principal.userId().equals(agent.getOwnerId())) {
            throw new DomainForbiddenException("error.agent.delete.noPermission");
        }

        AuditRequestContext context = AuditRequestContext.from(httpRequest);
        agentHardDeleteService.hardDeleteAgent(
                agent,
                principal.userId(),
                context.clientIp(),
                context.userAgent());

        return ok("response.success.deleted",
                new AgentDeleteResponse(agent.getId(), namespace, slug, true));
    }
}

package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.service.AgentLifecycleService;
import com.iflytek.skillhub.domain.agent.service.AgentReviewSubmitService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.AdminSkillActionRequest;
import com.iflytek.skillhub.dto.AgentConfirmPublishRequest;
import com.iflytek.skillhub.dto.AgentLifecycleMutationResponse;
import com.iflytek.skillhub.dto.AgentSubmitReviewRequest;
import com.iflytek.skillhub.dto.AgentVersionMutationResponse;
import com.iflytek.skillhub.dto.AgentVersionRereleaseRequest;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.service.AuditRequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Governance mutations for agents (archive / unarchive). Mirrors
 * {@link SkillLifecycleController} but trimmed to the v1 {@code AgentStatus} surface.
 */
@RestController
@RequestMapping({"/api/v1/agents", "/api/web/agents"})
public class AgentLifecycleController extends BaseApiController {

    private final AgentLifecycleService agentLifecycleService;
    private final AgentReviewSubmitService agentReviewSubmitService;
    private final NamespaceRepository namespaceRepository;
    private final com.iflytek.skillhub.domain.agent.AgentRepository agentRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final AuditLogService auditLogService;

    public AgentLifecycleController(AgentLifecycleService agentLifecycleService,
                                    AgentReviewSubmitService agentReviewSubmitService,
                                    NamespaceRepository namespaceRepository,
                                    com.iflytek.skillhub.domain.agent.AgentRepository agentRepository,
                                    AgentVersionRepository agentVersionRepository,
                                    AuditLogService auditLogService,
                                    ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.agentLifecycleService = agentLifecycleService;
        this.agentReviewSubmitService = agentReviewSubmitService;
        this.namespaceRepository = namespaceRepository;
        this.agentRepository = agentRepository;
        this.agentVersionRepository = agentVersionRepository;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/{namespace}/{slug}/archive")
    public ApiResponse<AgentLifecycleMutationResponse> archiveAgent(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestBody(required = false) AdminSkillActionRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            HttpServletRequest httpRequest) {

        Agent agent = resolve(namespace, slug);
        Agent archived = agentLifecycleService.archive(agent.getId(), userId, rolesOrEmpty(userNsRoles));
        auditLogService.record(
                userId,
                "ARCHIVE_AGENT",
                "AGENT",
                archived.getId(),
                null,
                AuditRequestContext.from(httpRequest).clientIp(),
                AuditRequestContext.from(httpRequest).userAgent(),
                jsonReason(request != null ? request.reason() : null));
        return ok("response.success.updated",
                new AgentLifecycleMutationResponse(archived.getId(), "ARCHIVE", archived.getStatus().name()));
    }

    @PostMapping("/{namespace}/{slug}/unarchive")
    public ApiResponse<AgentLifecycleMutationResponse> unarchiveAgent(
            @PathVariable String namespace,
            @PathVariable String slug,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            HttpServletRequest httpRequest) {

        Agent agent = resolve(namespace, slug);
        Agent restored = agentLifecycleService.unarchive(agent.getId(), userId, rolesOrEmpty(userNsRoles));
        auditLogService.record(
                userId,
                "UNARCHIVE_AGENT",
                "AGENT",
                restored.getId(),
                null,
                AuditRequestContext.from(httpRequest).clientIp(),
                AuditRequestContext.from(httpRequest).userAgent(),
                null);
        return ok("response.success.updated",
                new AgentLifecycleMutationResponse(restored.getId(), "UNARCHIVE", restored.getStatus().name()));
    }

    @PostMapping("/{namespace}/{slug}/versions/{version}/withdraw-review")
    public ApiResponse<AgentVersionMutationResponse> withdrawReview(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            HttpServletRequest httpRequest) {

        Agent agent = resolve(namespace, slug);
        AgentVersion withdrawn = agentLifecycleService.withdrawReview(
                agent.getId(), version, userId, rolesOrEmpty(userNsRoles));
        auditLogService.record(
                userId,
                "WITHDRAW_AGENT_REVIEW",
                "AGENT_VERSION",
                withdrawn.getId(),
                null,
                AuditRequestContext.from(httpRequest).clientIp(),
                AuditRequestContext.from(httpRequest).userAgent(),
                null);
        return ok("response.success.updated",
                new AgentVersionMutationResponse(
                        agent.getId(),
                        withdrawn.getId(),
                        withdrawn.getVersion(),
                        "WITHDRAW_REVIEW",
                        withdrawn.getStatus().name()));
    }

    @PostMapping("/{namespace}/{slug}/versions/{version}/rerelease")
    public ApiResponse<AgentVersionMutationResponse> rereleaseVersion(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            @Valid @RequestBody AgentVersionRereleaseRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            HttpServletRequest httpRequest) {

        Agent agent = resolve(namespace, slug);
        AgentVersion fresh = agentLifecycleService.rereleaseVersion(
                agent.getId(), version, request.targetVersion(),
                userId, rolesOrEmpty(userNsRoles));
        auditLogService.record(
                userId,
                "RERELEASE_AGENT_VERSION",
                "AGENT_VERSION",
                fresh.getId(),
                null,
                AuditRequestContext.from(httpRequest).clientIp(),
                AuditRequestContext.from(httpRequest).userAgent(),
                null);
        return ok("response.success.updated",
                new AgentVersionMutationResponse(
                        agent.getId(),
                        fresh.getId(),
                        fresh.getVersion(),
                        "RERELEASE",
                        fresh.getStatus().name()));
    }

    @PostMapping("/{namespace}/{slug}/submit-review")
    public ApiResponse<AgentVersionMutationResponse> submitForReview(
            @PathVariable String namespace,
            @PathVariable String slug,
            @Valid @RequestBody AgentSubmitReviewRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            HttpServletRequest httpRequest) {

        Agent agent = resolve(namespace, slug);
        AgentVersion version = findVersion(agent.getId(), request.version());
        AgentVisibility targetVisibility;
        try {
            targetVisibility = AgentVisibility.valueOf(request.targetVisibility());
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("Invalid target visibility: " + request.targetVisibility());
        }
        AgentVersion submitted = agentReviewSubmitService.submitForReview(
                agent.getId(), version.getId(), targetVisibility, userId, rolesOrEmpty(userNsRoles));
        auditLogService.record(
                userId,
                "SUBMIT_AGENT_REVIEW",
                "AGENT_VERSION",
                submitted.getId(),
                null,
                AuditRequestContext.from(httpRequest).clientIp(),
                AuditRequestContext.from(httpRequest).userAgent(),
                "{\"version\":\"" + request.version().replace("\"", "\\\"")
                        + "\",\"targetVisibility\":\"" + request.targetVisibility() + "\"}");
        return ok("response.success.updated",
                new AgentVersionMutationResponse(
                        agent.getId(),
                        submitted.getId(),
                        submitted.getVersion(),
                        "SUBMIT_REVIEW",
                        submitted.getStatus().name()));
    }

    @PostMapping("/{namespace}/{slug}/confirm-publish")
    public ApiResponse<AgentVersionMutationResponse> confirmPublish(
            @PathVariable String namespace,
            @PathVariable String slug,
            @Valid @RequestBody AgentConfirmPublishRequest request,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            HttpServletRequest httpRequest) {

        Agent agent = resolve(namespace, slug);
        AgentVersion version = findVersion(agent.getId(), request.version());
        AgentVersion published = agentReviewSubmitService.confirmPublish(
                agent.getId(), version.getId(), userId, rolesOrEmpty(userNsRoles));
        auditLogService.record(
                userId,
                "CONFIRM_AGENT_PUBLISH",
                "AGENT_VERSION",
                published.getId(),
                null,
                AuditRequestContext.from(httpRequest).clientIp(),
                AuditRequestContext.from(httpRequest).userAgent(),
                "{\"version\":\"" + request.version().replace("\"", "\\\"") + "\"}");
        return ok("response.success.updated",
                new AgentVersionMutationResponse(
                        agent.getId(),
                        published.getId(),
                        published.getVersion(),
                        "CONFIRM_PUBLISH",
                        published.getStatus().name()));
    }

    @DeleteMapping("/{namespace}/{slug}/versions/{version}")
    public ApiResponse<AgentVersionMutationResponse> deleteVersion(
            @PathVariable String namespace,
            @PathVariable String slug,
            @PathVariable String version,
            @RequestAttribute("userId") String userId,
            @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles,
            HttpServletRequest httpRequest) {

        Agent agent = resolve(namespace, slug);
        AgentVersion deleted = agentLifecycleService.deleteVersion(
                agent.getId(), version, userId, rolesOrEmpty(userNsRoles));
        auditLogService.record(
                userId,
                "DELETE_AGENT_VERSION",
                "AGENT_VERSION",
                deleted.getId(),
                null,
                AuditRequestContext.from(httpRequest).clientIp(),
                AuditRequestContext.from(httpRequest).userAgent(),
                null);
        return ok("response.success.deleted",
                new AgentVersionMutationResponse(
                        agent.getId(),
                        deleted.getId(),
                        deleted.getVersion(),
                        "DELETE_VERSION",
                        deleted.getStatus().name()));
    }

    private AgentVersion findVersion(Long agentId, String version) {
        return agentVersionRepository.findByAgentIdAndVersion(agentId, version)
                .orElseThrow(() -> new DomainNotFoundException("Agent version not found: " + version));
    }

    private Agent resolve(String namespaceSlug, String slug) {
        String cleanNamespace = namespaceSlug.startsWith("@") ? namespaceSlug.substring(1) : namespaceSlug;
        Namespace ns = namespaceRepository.findBySlug(cleanNamespace)
                .orElseThrow(() -> new DomainNotFoundException("Namespace not found: " + cleanNamespace));
        return agentRepository.findByNamespaceIdAndSlug(ns.getId(), slug)
                .orElseThrow(() -> new DomainNotFoundException("Agent not found: " + slug));
    }

    private Map<Long, NamespaceRole> rolesOrEmpty(Map<Long, NamespaceRole> roles) {
        return roles == null ? Map.of() : roles;
    }

    private String jsonReason(String reason) {
        if (reason == null || reason.isBlank()) {
            return null;
        }
        return "{\"reason\":\"" + reason.replace("\"", "\\\"") + "\"}";
    }
}

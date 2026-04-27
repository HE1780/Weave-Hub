package com.iflytek.skillhub.service;

import com.iflytek.skillhub.auth.rbac.RbacService;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVisibilityChecker;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.label.AgentLabel;
import com.iflytek.skillhub.domain.label.AgentLabelService;
import com.iflytek.skillhub.domain.label.LabelDefinition;
import com.iflytek.skillhub.domain.label.LabelDefinitionService;
import com.iflytek.skillhub.domain.label.LabelTranslation;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.AgentLabelDto;
import com.iflytek.skillhub.dto.MessageResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that wires HTTP-layer concerns (slug resolution, audit
 * context, locale resolution) to {@link AgentLabelService}. Mirrors
 * {@link SkillLabelAppService}; the LabelDefinition vocabulary is shared so
 * the localization path is identical.
 */
@Service
public class AgentLabelAppService {

    private final NamespaceRepository namespaceRepository;
    private final AgentRepository agentRepository;
    private final AgentVisibilityChecker visibilityChecker;
    private final LabelDefinitionService labelDefinitionService;
    private final AgentLabelService agentLabelService;
    private final LabelLocalizationService labelLocalizationService;
    private final RbacService rbacService;
    private final AuditLogService auditLogService;

    public AgentLabelAppService(NamespaceRepository namespaceRepository,
                                AgentRepository agentRepository,
                                AgentVisibilityChecker visibilityChecker,
                                LabelDefinitionService labelDefinitionService,
                                AgentLabelService agentLabelService,
                                LabelLocalizationService labelLocalizationService,
                                RbacService rbacService,
                                AuditLogService auditLogService) {
        this.namespaceRepository = namespaceRepository;
        this.agentRepository = agentRepository;
        this.visibilityChecker = visibilityChecker;
        this.labelDefinitionService = labelDefinitionService;
        this.agentLabelService = agentLabelService;
        this.labelLocalizationService = labelLocalizationService;
        this.rbacService = rbacService;
        this.auditLogService = auditLogService;
    }

    public List<AgentLabelDto> listAgentLabels(String namespaceSlug,
                                               String agentSlug,
                                               String userId,
                                               Map<Long, NamespaceRole> userNsRoles) {
        Agent agent = resolveAgentForRead(namespaceSlug, agentSlug, userId, userNsRoles);
        return toDtos(agentLabelService.listAgentLabels(agent.getId()));
    }

    public List<AgentLabelDto> listAgentLabelsByAgentId(Long agentId) {
        return toDtos(agentLabelService.listAgentLabels(agentId));
    }

    @Transactional
    public AgentLabelDto attachLabel(String namespaceSlug,
                                     String agentSlug,
                                     String labelSlug,
                                     String userId,
                                     Map<Long, NamespaceRole> userNsRoles,
                                     AuditRequestContext auditContext) {
        Agent agent = resolveAgent(namespaceSlug, agentSlug);
        AgentLabel attached = agentLabelService.attachLabel(
                agent.getId(),
                labelSlug,
                userId,
                normalizeRoles(userNsRoles),
                platformRoles(userId)
        );
        recordAudit("AGENT_LABEL_ATTACH", userId, agent.getId(), auditContext, "{\"labelSlug\":\"" + labelSlug + "\"}");
        return toDtos(List.of(attached)).getFirst();
    }

    @Transactional
    public MessageResponse detachLabel(String namespaceSlug,
                                       String agentSlug,
                                       String labelSlug,
                                       String userId,
                                       Map<Long, NamespaceRole> userNsRoles,
                                       AuditRequestContext auditContext) {
        Agent agent = resolveAgent(namespaceSlug, agentSlug);
        agentLabelService.detachLabel(
                agent.getId(),
                labelSlug,
                userId,
                normalizeRoles(userNsRoles),
                platformRoles(userId)
        );
        recordAudit("AGENT_LABEL_DETACH", userId, agent.getId(), auditContext, "{\"labelSlug\":\"" + labelSlug + "\"}");
        return new MessageResponse("Label detached");
    }

    private List<AgentLabelDto> toDtos(List<AgentLabel> agentLabels) {
        if (agentLabels.isEmpty()) {
            return List.of();
        }
        List<Long> labelIds = agentLabels.stream()
                .map(AgentLabel::getLabelId)
                .distinct()
                .toList();
        Map<Long, LabelDefinition> definitionsById = labelDefinitionService.listByIds(labelIds).stream()
                .collect(Collectors.toMap(LabelDefinition::getId, Function.identity()));
        Map<Long, List<LabelTranslation>> translationsByLabelId = labelDefinitionService.listTranslationsByLabelIds(labelIds);
        return agentLabels.stream()
                .filter(agentLabel -> definitionsById.containsKey(agentLabel.getLabelId()))
                .map(agentLabel -> {
                    LabelDefinition definition = definitionsById.get(agentLabel.getLabelId());
                    return new AgentLabelDto(
                            definition.getSlug(),
                            definition.getType().name(),
                            labelLocalizationService.resolveDisplayName(
                                    definition.getSlug(),
                                    translationsByLabelId.getOrDefault(definition.getId(), List.of()))
                    );
                })
                .sorted(java.util.Comparator.comparing(AgentLabelDto::type).thenComparing(AgentLabelDto::slug))
                .toList();
    }

    private Agent resolveAgent(String namespaceSlug, String agentSlug) {
        Namespace namespace = namespaceRepository.findBySlug(namespaceSlug)
                .orElseThrow(() -> new DomainBadRequestException("error.namespace.slug.notFound", namespaceSlug));
        return agentRepository.findByNamespaceIdAndSlug(namespace.getId(), agentSlug)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.notFound", agentSlug));
    }

    private Agent resolveAgentForRead(String namespaceSlug,
                                      String agentSlug,
                                      String userId,
                                      Map<Long, NamespaceRole> userNsRoles) {
        Agent agent = resolveAgent(namespaceSlug, agentSlug);
        Set<String> platforms = platformRoles(userId);
        if (platforms.contains("SUPER_ADMIN")) {
            return agent;
        }
        if (!visibilityChecker.canAccess(agent, userId, normalizeRoles(userNsRoles), platforms)) {
            throw new DomainNotFoundException("error.agent.notFound", agentSlug);
        }
        return agent;
    }

    private Map<Long, NamespaceRole> normalizeRoles(Map<Long, NamespaceRole> userNsRoles) {
        return userNsRoles != null ? userNsRoles : Map.of();
    }

    private Set<String> platformRoles(String userId) {
        return userId == null ? Set.of() : rbacService.getUserRoleCodes(userId);
    }

    private void recordAudit(String action,
                             String userId,
                             Long targetId,
                             AuditRequestContext auditContext,
                             String detailJson) {
        auditLogService.record(
                userId,
                action,
                "AGENT",
                targetId,
                MDC.get("requestId"),
                auditContext != null ? auditContext.clientIp() : null,
                auditContext != null ? auditContext.userAgent() : null,
                detailJson
        );
    }
}

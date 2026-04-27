package com.iflytek.skillhub.domain.label;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Manages label attachments on agents. Mirrors {@link SkillLabelService}; the
 * underlying {@link LabelDefinition} vocabulary is shared between Skills and
 * Agents (per ADR 0003 fork-backlog A4 decision).
 */
@Service
public class AgentLabelService {

    private final int maxLabelsPerAgent;

    private final AgentRepository agentRepository;
    private final LabelDefinitionRepository labelDefinitionRepository;
    private final AgentLabelRepository agentLabelRepository;
    private final LabelPermissionChecker labelPermissionChecker;

    public AgentLabelService(AgentRepository agentRepository,
                             LabelDefinitionRepository labelDefinitionRepository,
                             AgentLabelRepository agentLabelRepository,
                             LabelPermissionChecker labelPermissionChecker,
                             @Value("${skillhub.label.max-per-agent:10}") int maxLabelsPerAgent) {
        this.agentRepository = agentRepository;
        this.labelDefinitionRepository = labelDefinitionRepository;
        this.agentLabelRepository = agentLabelRepository;
        this.labelPermissionChecker = labelPermissionChecker;
        this.maxLabelsPerAgent = requirePositive(maxLabelsPerAgent, "skillhub.label.max-per-agent");
    }

    public List<AgentLabel> listAgentLabels(Long agentId) {
        return agentLabelRepository.findByAgentId(agentId);
    }

    public List<AgentLabel> listByLabelId(Long labelId) {
        return agentLabelRepository.findByLabelId(labelId);
    }

    @Transactional
    public AgentLabel attachLabel(Long agentId,
                                  String labelSlug,
                                  String operatorId,
                                  Map<Long, NamespaceRole> userNamespaceRoles,
                                  Set<String> platformRoles) {
        Agent agent = findAgent(agentId);
        LabelDefinition labelDefinition = findLabel(labelSlug);
        requireAgentLabelPermission(agent, labelDefinition, operatorId, userNamespaceRoles, platformRoles);

        List<AgentLabel> existingLabels = agentLabelRepository.findByAgentId(agentId);
        if (existingLabels.size() >= maxLabelsPerAgent) {
            throw new DomainBadRequestException("label.agent.too_many", agentId, maxLabelsPerAgent);
        }
        return agentLabelRepository.findByAgentIdAndLabelId(agentId, labelDefinition.getId())
                .orElseGet(() -> agentLabelRepository.save(new AgentLabel(agentId, labelDefinition.getId(), operatorId)));
    }

    @Transactional
    public void detachLabel(Long agentId,
                            String labelSlug,
                            String operatorId,
                            Map<Long, NamespaceRole> userNamespaceRoles,
                            Set<String> platformRoles) {
        Agent agent = findAgent(agentId);
        LabelDefinition labelDefinition = findLabel(labelSlug);
        requireAgentLabelPermission(agent, labelDefinition, operatorId, userNamespaceRoles, platformRoles);

        AgentLabel agentLabel = agentLabelRepository.findByAgentIdAndLabelId(agentId, labelDefinition.getId())
                .orElseThrow(() -> new DomainBadRequestException("label.agent.not_found", agentId, labelSlug));
        agentLabelRepository.delete(agentLabel);
    }

    private Agent findAgent(Long agentId) {
        return agentRepository.findById(agentId)
                .orElseThrow(() -> new DomainBadRequestException("error.agent.notFound", agentId));
    }

    private LabelDefinition findLabel(String labelSlug) {
        String normalizedSlug = LabelSlugValidator.normalize(labelSlug);
        return labelDefinitionRepository.findBySlugIgnoreCase(normalizedSlug)
                .orElseThrow(() -> new DomainBadRequestException("label.not_found", normalizedSlug));
    }

    private void requireAgentLabelPermission(Agent agent,
                                             LabelDefinition labelDefinition,
                                             String operatorId,
                                             Map<Long, NamespaceRole> userNamespaceRoles,
                                             Set<String> platformRoles) {
        if (!labelPermissionChecker.canManageAgentLabel(agent, labelDefinition, operatorId, userNamespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("label.agent.no_permission");
        }
    }

    private int requirePositive(int value, String propertyName) {
        if (value <= 0) {
            throw new IllegalArgumentException(propertyName + " must be greater than 0");
        }
        return value;
    }
}

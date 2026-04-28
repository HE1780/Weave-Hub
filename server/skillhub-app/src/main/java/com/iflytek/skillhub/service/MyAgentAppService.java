package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.social.AgentStar;
import com.iflytek.skillhub.domain.agent.social.AgentStarRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.dto.AgentSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mirrors {@link MySkillAppService}'s listMyStars for agents — returns the
 * current user's starred agents as a paginated {@link AgentSummaryResponse}
 * page, with namespace slug resolution and missing/deleted agents filtered out.
 */
@Service
public class MyAgentAppService {

    private final AgentStarRepository agentStarRepository;
    private final AgentRepository agentRepository;
    private final NamespaceRepository namespaceRepository;

    public MyAgentAppService(AgentStarRepository agentStarRepository,
                             AgentRepository agentRepository,
                             NamespaceRepository namespaceRepository) {
        this.agentStarRepository = agentStarRepository;
        this.agentRepository = agentRepository;
        this.namespaceRepository = namespaceRepository;
    }

    public PageResponse<AgentSummaryResponse> listMyAgentStars(String userId, int page, int size) {
        Page<AgentStar> starPage = agentStarRepository.findByUserId(userId, PageRequest.of(page, size));
        List<AgentStar> stars = starPage.getContent();

        List<Long> agentIds = stars.stream().map(AgentStar::getAgentId).distinct().toList();
        Map<Long, Agent> agentsById = agentIds.isEmpty()
                ? Map.of()
                : agentRepository.findByIdIn(agentIds).stream()
                        .collect(Collectors.toMap(Agent::getId, Function.identity()));

        List<Long> namespaceIds = agentsById.values().stream()
                .map(Agent::getNamespaceId).distinct().toList();
        Map<Long, String> namespaceSlugs = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                        .collect(Collectors.toMap(Namespace::getId, Namespace::getSlug));

        List<AgentSummaryResponse> items = stars.stream()
                .map(star -> agentsById.get(star.getAgentId()))
                .filter(Objects::nonNull)
                .map(agent -> AgentSummaryResponse.from(agent, namespaceSlugs.get(agent.getNamespaceId())))
                .toList();

        return new PageResponse<>(items, starPage.getTotalElements(), starPage.getNumber(), starPage.getSize());
    }
}

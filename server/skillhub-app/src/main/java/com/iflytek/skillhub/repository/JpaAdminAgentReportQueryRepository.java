package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.report.AgentReport;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.dto.AdminAgentReportSummaryResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAdminAgentReportQueryRepository implements AdminAgentReportQueryRepository {

    private final AgentRepository agentRepository;
    private final NamespaceRepository namespaceRepository;

    public JpaAdminAgentReportQueryRepository(AgentRepository agentRepository,
                                              NamespaceRepository namespaceRepository) {
        this.agentRepository = agentRepository;
        this.namespaceRepository = namespaceRepository;
    }

    @Override
    public List<AdminAgentReportSummaryResponse> getAgentReportSummaries(List<AgentReport> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }
        List<Long> agentIds = reports.stream().map(AgentReport::getAgentId).distinct().toList();
        Map<Long, Agent> agentsById = agentIds.isEmpty()
                ? Map.of()
                : agentRepository.findByIdIn(agentIds).stream()
                        .collect(Collectors.toMap(Agent::getId, Function.identity()));

        List<Long> namespaceIds = agentsById.values().stream().map(Agent::getNamespaceId).distinct().toList();
        Map<Long, String> namespaceSlugs = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                        .collect(Collectors.toMap(Namespace::getId, Namespace::getSlug));

        return reports.stream()
                .map(report -> toResponse(report, agentsById.get(report.getAgentId()), namespaceSlugs))
                .toList();
    }

    private AdminAgentReportSummaryResponse toResponse(AgentReport report,
                                                       Agent agent,
                                                       Map<Long, String> namespaceSlugs) {
        return new AdminAgentReportSummaryResponse(
                report.getId(),
                report.getAgentId(),
                agent != null ? namespaceSlugs.get(agent.getNamespaceId()) : null,
                agent != null ? agent.getSlug() : null,
                agent != null ? agent.getDisplayName() : null,
                report.getReporterId(),
                report.getReason(),
                report.getDetails(),
                report.getStatus().name(),
                report.getHandledBy(),
                report.getHandleComment(),
                report.getCreatedAt(),
                report.getHandledAt()
        );
    }
}

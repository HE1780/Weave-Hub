package com.iflytek.skillhub.domain.agent.report;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for agent abuse reports. Mirrors
 * {@link com.iflytek.skillhub.domain.report.SkillReportRepository}.
 */
public interface AgentReportRepository {
    AgentReport save(AgentReport report);
    Optional<AgentReport> findById(Long id);
    boolean existsByAgentIdAndReporterIdAndStatus(Long agentId, String reporterId, AgentReportStatus status);
    Page<AgentReport> findByStatus(AgentReportStatus status, Pageable pageable);
    List<AgentReport> findByAgentIdIn(Collection<Long> agentIds);
    void deleteByAgentId(Long agentId);
}

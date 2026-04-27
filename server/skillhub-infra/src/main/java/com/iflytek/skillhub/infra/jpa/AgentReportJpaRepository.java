package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.agent.report.AgentReport;
import com.iflytek.skillhub.domain.agent.report.AgentReportRepository;
import com.iflytek.skillhub.domain.agent.report.AgentReportStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * JPA-backed repository for agent abuse reports. Mirrors
 * {@link SkillReportJpaRepository}.
 */
public interface AgentReportJpaRepository extends JpaRepository<AgentReport, Long>, AgentReportRepository {
    boolean existsByAgentIdAndReporterIdAndStatus(Long agentId, String reporterId, AgentReportStatus status);
    Page<AgentReport> findByStatusOrderByCreatedAtDesc(AgentReportStatus status, Pageable pageable);
    List<AgentReport> findByAgentIdIn(Collection<Long> agentIds);
    void deleteByAgentId(Long agentId);

    @Override
    default Page<AgentReport> findByStatus(AgentReportStatus status, Pageable pageable) {
        return findByStatusOrderByCreatedAtDesc(status, pageable);
    }
}

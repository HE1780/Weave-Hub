package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.agent.report.AgentReport;
import com.iflytek.skillhub.dto.AdminAgentReportSummaryResponse;
import java.util.List;

/**
 * Query-side repository for admin-facing agent report summary rows.
 * Mirrors {@link AdminSkillReportQueryRepository}.
 */
public interface AdminAgentReportQueryRepository {
    List<AdminAgentReportSummaryResponse> getAgentReportSummaries(List<AgentReport> reports);
}

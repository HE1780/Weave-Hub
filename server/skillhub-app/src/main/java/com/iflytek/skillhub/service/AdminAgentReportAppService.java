package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.agent.report.AgentReportService;
import com.iflytek.skillhub.domain.agent.report.AgentReportStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.dto.AdminAgentReportSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.repository.AdminAgentReportQueryRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Application service that enriches raw agent report records with agent and
 * namespace context required by admin UIs. Mirrors
 * {@link AdminSkillReportAppService}.
 */
@Service
public class AdminAgentReportAppService {

    private final AgentReportService agentReportService;
    private final AdminAgentReportQueryRepository adminAgentReportQueryRepository;

    public AdminAgentReportAppService(AgentReportService agentReportService,
                                      AdminAgentReportQueryRepository adminAgentReportQueryRepository) {
        this.agentReportService = agentReportService;
        this.adminAgentReportQueryRepository = adminAgentReportQueryRepository;
    }

    public PageResponse<AdminAgentReportSummaryResponse> listReports(String status, int page, int size) {
        AgentReportStatus resolvedStatus = parseStatus(status);
        var reportPage = agentReportService.listByStatus(resolvedStatus, PageRequest.of(page, size));
        List<AdminAgentReportSummaryResponse> items = adminAgentReportQueryRepository
                .getAgentReportSummaries(reportPage.getContent());

        return new PageResponse<>(items, reportPage.getTotalElements(), reportPage.getNumber(), reportPage.getSize());
    }

    private AgentReportStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return AgentReportStatus.PENDING;
        }
        try {
            return AgentReportStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("error.agent.report.status.invalid", status);
        }
    }
}

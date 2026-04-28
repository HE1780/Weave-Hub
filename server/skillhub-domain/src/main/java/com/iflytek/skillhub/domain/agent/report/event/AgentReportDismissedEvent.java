package com.iflytek.skillhub.domain.agent.report.event;

/**
 * Published after an admin dismisses an agent abuse report.
 */
public record AgentReportDismissedEvent(
        Long reportId,
        Long agentId,
        String adminUserId,
        String reporterId
) {}

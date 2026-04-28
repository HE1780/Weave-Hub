package com.iflytek.skillhub.dto;

import java.time.Instant;

/**
 * Admin-facing summary row for an agent abuse report. Mirrors
 * {@link AdminSkillReportSummaryResponse}.
 */
public record AdminAgentReportSummaryResponse(
        Long id,
        Long agentId,
        String namespace,
        String agentSlug,
        String agentDisplayName,
        String reporterId,
        String reason,
        String details,
        String status,
        String handledBy,
        String handleComment,
        Instant createdAt,
        Instant handledAt
) {}

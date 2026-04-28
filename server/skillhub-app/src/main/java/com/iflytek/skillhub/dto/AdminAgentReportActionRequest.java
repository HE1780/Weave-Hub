package com.iflytek.skillhub.dto;

/**
 * Request body for admin resolve / dismiss actions on agent reports.
 * Mirrors {@link AdminSkillReportActionRequest}.
 */
public record AdminAgentReportActionRequest(
        String comment,
        String disposition
) {}

package com.iflytek.skillhub.domain.agent.report;

/**
 * Moderation outcomes a moderator can apply when resolving an agent report.
 * Mirrors {@link com.iflytek.skillhub.domain.report.SkillReportDisposition}
 * including the soft-hide path (RESOLVE_AND_HIDE) once the {@code Agent.hidden}
 * flag is in place.
 */
public enum AgentReportDisposition {
    RESOLVE_ONLY,
    RESOLVE_AND_ARCHIVE,
    RESOLVE_AND_HIDE
}

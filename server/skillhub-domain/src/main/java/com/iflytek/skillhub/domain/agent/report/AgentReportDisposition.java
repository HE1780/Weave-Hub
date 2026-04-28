package com.iflytek.skillhub.domain.agent.report;

/**
 * Moderation outcomes a moderator can apply when resolving an agent report.
 *
 * <p>Mirrors {@link com.iflytek.skillhub.domain.report.SkillReportDisposition}
 * but intentionally omits the {@code RESOLVE_AND_HIDE} option — the agent
 * surface has no soft-hide path yet (no {@code hidden} flag on the agent
 * aggregate). Moderators can still archive the agent via
 * {@code RESOLVE_AND_ARCHIVE}, which mirrors the skill archive path.
 */
public enum AgentReportDisposition {
    RESOLVE_ONLY,
    RESOLVE_AND_ARCHIVE
}

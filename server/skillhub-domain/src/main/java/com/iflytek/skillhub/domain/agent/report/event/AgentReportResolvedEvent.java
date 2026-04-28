package com.iflytek.skillhub.domain.agent.report.event;

/**
 * Published after an admin resolves an agent abuse report. Mirrors
 * {@link com.iflytek.skillhub.domain.event.ReportResolvedEvent} on the skill
 * side; consumers (notifications, governance counters) listen for either
 * event family to refresh their derived state.
 *
 * <p>{@code dispositionLabel} is the lower-case form of the chosen
 * {@link com.iflytek.skillhub.domain.agent.report.AgentReportDisposition}
 * (e.g. {@code "resolved"}, {@code "resolve_and_archive"}). Skill side uses
 * {@code "resolved"} / {@code "dismissed"}; agent side widens it so listeners
 * can distinguish the resolution mode without inspecting the report.
 */
public record AgentReportResolvedEvent(
        Long reportId,
        Long agentId,
        String adminUserId,
        String reporterId,
        String dispositionLabel
) {}

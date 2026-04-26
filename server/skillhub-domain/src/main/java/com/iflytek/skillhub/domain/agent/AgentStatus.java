package com.iflytek.skillhub.domain.agent;

/**
 * Lifecycle of an Agent record. Trimmed compared to {@code SkillStatus} —
 * agents do not have a HIDDEN moderation state in v1.
 */
public enum AgentStatus {
    ACTIVE,
    ARCHIVED
}

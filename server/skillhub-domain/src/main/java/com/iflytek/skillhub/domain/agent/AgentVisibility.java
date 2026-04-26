package com.iflytek.skillhub.domain.agent;

/**
 * Visibility of an agent. Mirrors {@code SkillVisibility} so the two
 * channels carry identical access semantics.
 */
public enum AgentVisibility {
    PUBLIC,
    NAMESPACE_ONLY,
    PRIVATE
}

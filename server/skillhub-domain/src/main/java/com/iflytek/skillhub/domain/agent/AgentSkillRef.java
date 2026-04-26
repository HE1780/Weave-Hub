package com.iflytek.skillhub.domain.agent;

/**
 * A reference to a skill dependency declared in AGENT.md frontmatter.
 *
 * <p>Per ADR §File Contracts, {@code version} is opaque (not a constraint
 * expression) and may be {@code null} when only a name is given.
 */
public record AgentSkillRef(String name, String version) {
}

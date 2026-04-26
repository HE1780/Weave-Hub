package com.iflytek.skillhub.domain.agent;

import java.util.List;
import java.util.Map;

/**
 * Parsed AGENT.md manifest. Mirrors SkillMetadata but adds agent-specific fields.
 *
 * @param name           required; agent slug
 * @param description    required; one-line summary
 * @param version        optional; opaque string per ADR
 * @param soulFile       optional; relative path, default "soul.md"
 * @param workflowFile   optional; relative path, default "workflow.yaml"
 * @param skills         optional list of skill refs (may be empty)
 * @param body           markdown body after frontmatter, may be empty
 * @param frontmatter    raw map of all frontmatter keys (preserved verbatim)
 */
public record AgentMetadata(
        String name,
        String description,
        String version,
        String soulFile,
        String workflowFile,
        List<AgentSkillRef> skills,
        String body,
        Map<String, Object> frontmatter
) {
    public static final String DEFAULT_SOUL_FILE = "soul.md";
    public static final String DEFAULT_WORKFLOW_FILE = "workflow.yaml";
}

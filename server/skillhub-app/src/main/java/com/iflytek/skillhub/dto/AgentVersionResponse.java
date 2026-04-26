package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.agent.AgentVersion;

import java.time.Instant;

/**
 * AgentVersion projection. The {@code soulMd} and {@code workflowYaml} fields are
 * included because clients need them to render the detail and review screens; they
 * are bounded by the workflow.yaml size cap enforced at upload time, so this is safe.
 */
public record AgentVersionResponse(
        Long id,
        Long agentId,
        String version,
        String status,
        String submittedBy,
        Instant submittedAt,
        Instant publishedAt,
        long packageSizeBytes,
        String manifestYaml,
        String soulMd,
        String workflowYaml
) {
    public static AgentVersionResponse from(AgentVersion v) {
        return new AgentVersionResponse(
                v.getId(),
                v.getAgentId(),
                v.getVersion(),
                v.getStatus().name(),
                v.getSubmittedBy(),
                v.getSubmittedAt(),
                v.getPublishedAt(),
                v.getPackageSizeBytes() == null ? 0L : v.getPackageSizeBytes(),
                v.getManifestYaml(),
                v.getSoulMd(),
                v.getWorkflowYaml()
        );
    }

    /** Light projection for list views: omits the heavy inline content. */
    public static AgentVersionResponse summaryFrom(AgentVersion v) {
        return new AgentVersionResponse(
                v.getId(),
                v.getAgentId(),
                v.getVersion(),
                v.getStatus().name(),
                v.getSubmittedBy(),
                v.getSubmittedAt(),
                v.getPublishedAt(),
                v.getPackageSizeBytes() == null ? 0L : v.getPackageSizeBytes(),
                null,
                null,
                null
        );
    }
}

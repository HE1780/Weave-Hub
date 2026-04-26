package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTask;

import java.time.Instant;

/**
 * Review task projection. The {@code agentSlug}/{@code agentNamespace}/{@code agentVersion}
 * fields are populated by the list endpoint via {@link #enriched} so reviewers can
 * see what they're approving without an extra round-trip; single-task lookups
 * (approve/reject/getOne) leave these null and use {@link #from}.
 */
public record AgentReviewTaskResponse(
        Long id,
        Long agentVersionId,
        Long namespaceId,
        String status,
        String submittedBy,
        String reviewedBy,
        String reviewComment,
        Instant submittedAt,
        Instant reviewedAt,
        String agentSlug,
        String agentDisplayName,
        String agentNamespace,
        String agentVersion
) {
    public static AgentReviewTaskResponse from(AgentReviewTask task) {
        return new AgentReviewTaskResponse(
                task.getId(),
                task.getAgentVersionId(),
                task.getNamespaceId(),
                task.getStatus().name(),
                task.getSubmittedBy(),
                task.getReviewedBy(),
                task.getReviewComment(),
                task.getSubmittedAt(),
                task.getReviewedAt(),
                null,
                null,
                null,
                null
        );
    }

    public static AgentReviewTaskResponse enriched(AgentReviewTask task,
                                                   Agent agent,
                                                   AgentVersion version,
                                                   String namespaceSlug) {
        return new AgentReviewTaskResponse(
                task.getId(),
                task.getAgentVersionId(),
                task.getNamespaceId(),
                task.getStatus().name(),
                task.getSubmittedBy(),
                task.getReviewedBy(),
                task.getReviewComment(),
                task.getSubmittedAt(),
                task.getReviewedAt(),
                agent == null ? null : agent.getSlug(),
                agent == null ? null : agent.getDisplayName(),
                namespaceSlug,
                version == null ? null : version.getVersion()
        );
    }
}

package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.agent.Agent;

import java.math.BigDecimal;
import java.time.Instant;

public record AgentResponse(
        Long id,
        String namespace,
        String slug,
        String displayName,
        String description,
        String visibility,
        String ownerId,
        String status,
        Integer starCount,
        BigDecimal ratingAvg,
        Integer ratingCount,
        Integer downloadCount,
        boolean canManageLifecycle,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * Builds a response with {@code canManageLifecycle = false}. Use only when the
     * caller's permission is irrelevant or unknown (publish / lifecycle mutation
     * responses, internal callers). Public read paths should call
     * {@link #from(Agent, String, boolean)} so the frontend can gate governance UI.
     */
    public static AgentResponse from(Agent agent, String namespace) {
        return from(agent, namespace, false);
    }

    public static AgentResponse from(Agent agent, String namespace, boolean canManageLifecycle) {
        return new AgentResponse(
                agent.getId(),
                namespace,
                agent.getSlug(),
                agent.getDisplayName(),
                agent.getDescription(),
                agent.getVisibility().name(),
                agent.getOwnerId(),
                agent.getStatus().name(),
                agent.getStarCount(),
                agent.getRatingAvg(),
                agent.getRatingCount(),
                agent.getDownloadCount(),
                canManageLifecycle,
                agent.getCreatedAt(),
                agent.getUpdatedAt()
        );
    }
}

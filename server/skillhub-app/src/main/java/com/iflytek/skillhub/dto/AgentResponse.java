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
        Instant createdAt,
        Instant updatedAt
) {
    public static AgentResponse from(Agent agent, String namespace) {
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
                agent.getCreatedAt(),
                agent.getUpdatedAt()
        );
    }
}

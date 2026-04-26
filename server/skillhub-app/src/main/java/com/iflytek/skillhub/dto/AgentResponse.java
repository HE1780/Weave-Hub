package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.agent.Agent;

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
                agent.getCreatedAt(),
                agent.getUpdatedAt()
        );
    }
}

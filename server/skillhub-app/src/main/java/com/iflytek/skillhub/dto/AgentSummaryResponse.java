package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.agent.Agent;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Slimmed-down agent payload for list views (My Stars, future search). Mirrors
 * {@link SkillSummaryResponse} in spirit — drops detail-only fields like
 * headlineVersion / publishedVersion. Use {@link AgentResponse} for detail
 * paths where lifecycle context is needed.
 */
public record AgentSummaryResponse(
        Long id,
        String slug,
        String displayName,
        String description,
        String visibility,
        String status,
        Integer downloadCount,
        Integer starCount,
        BigDecimal ratingAvg,
        Integer ratingCount,
        String namespace,
        Instant updatedAt
) {
    public static AgentSummaryResponse from(Agent agent, String namespace) {
        return new AgentSummaryResponse(
                agent.getId(),
                agent.getSlug(),
                agent.getDisplayName(),
                agent.getDescription(),
                agent.getVisibility().name(),
                agent.getStatus().name(),
                agent.getDownloadCount(),
                agent.getStarCount(),
                agent.getRatingAvg(),
                agent.getRatingCount(),
                namespace,
                agent.getUpdatedAt()
        );
    }
}

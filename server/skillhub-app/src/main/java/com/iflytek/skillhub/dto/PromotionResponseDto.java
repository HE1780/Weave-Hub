package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.review.SourceType;

import java.time.Instant;

/**
 * Promotion response DTO supporting both skill and agent source types.
 * For SKILL-source rows: sourceSkillId/sourceSkillSlug/sourceVersion + targetSkillId populated; agent fields null.
 * For AGENT-source rows: sourceAgentId/sourceAgentSlug/sourceAgentVersion + targetAgentId populated; skill fields null.
 * sourceNamespace + targetNamespace are populated for both.
 */
public record PromotionResponseDto(
        Long id,
        SourceType sourceType,
        Long sourceSkillId,
        String sourceNamespace,
        String sourceSkillSlug,
        String sourceVersion,
        Long sourceAgentId,
        String sourceAgentSlug,
        String sourceAgentVersion,
        String targetNamespace,
        Long targetSkillId,
        Long targetAgentId,
        String status,
        String submittedBy,
        String submittedByName,
        String reviewedBy,
        String reviewedByName,
        String reviewComment,
        Instant submittedAt,
        Instant reviewedAt
) {}

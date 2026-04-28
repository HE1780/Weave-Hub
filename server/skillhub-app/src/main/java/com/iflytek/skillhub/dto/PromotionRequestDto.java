package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.review.SourceType;

/**
 * Promotion request payload. Backwards compatible with skill-only callers:
 * if sourceType is null, defaults to SKILL via the compact constructor.
 * For SKILL submissions, sourceSkillId + sourceVersionId are required.
 * For AGENT submissions, sourceAgentId + sourceAgentVersionId are required.
 */
public record PromotionRequestDto(
        SourceType sourceType,
        Long sourceSkillId,
        Long sourceVersionId,
        Long sourceAgentId,
        Long sourceAgentVersionId,
        Long targetNamespaceId
) {
    public PromotionRequestDto {
        if (sourceType == null) {
            sourceType = SourceType.SKILL;
        }
    }
}

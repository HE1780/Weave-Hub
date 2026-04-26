package com.iflytek.skillhub.dto;

import java.util.List;

public record SkillVersionCommentPageResponse(
        int page,
        int size,
        long totalElements,
        boolean hasNext,
        List<SkillVersionCommentResponse> content
) {}

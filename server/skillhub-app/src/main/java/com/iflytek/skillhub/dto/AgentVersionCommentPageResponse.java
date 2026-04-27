package com.iflytek.skillhub.dto;

import java.util.List;

public record AgentVersionCommentPageResponse(
        int page,
        int size,
        long totalElements,
        boolean hasNext,
        List<AgentVersionCommentResponse> content
) {}

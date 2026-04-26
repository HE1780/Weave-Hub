package com.iflytek.skillhub.dto;

public record AgentPublishResponse(
        Long agentId,
        Long agentVersionId,
        String namespace,
        String slug,
        String version,
        String status,
        long packageSizeBytes
) {}

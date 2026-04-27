package com.iflytek.skillhub.dto;

public record AgentDeleteResponse(
        Long agentId,
        String namespace,
        String slug,
        boolean deleted
) {
}

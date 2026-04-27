package com.iflytek.skillhub.dto;

public record AgentVersionMutationResponse(
        Long agentId,
        Long versionId,
        String version,
        String action,
        String status
) {
}

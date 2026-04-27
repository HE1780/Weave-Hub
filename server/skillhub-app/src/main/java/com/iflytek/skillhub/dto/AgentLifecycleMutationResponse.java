package com.iflytek.skillhub.dto;

public record AgentLifecycleMutationResponse(
        Long agentId,
        String action,
        String status
) {}

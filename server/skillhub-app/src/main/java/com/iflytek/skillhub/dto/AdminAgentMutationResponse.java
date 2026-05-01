package com.iflytek.skillhub.dto;

public record AdminAgentMutationResponse(
    Long agentId,
    Long versionId,
    String action,
    String status
) {}

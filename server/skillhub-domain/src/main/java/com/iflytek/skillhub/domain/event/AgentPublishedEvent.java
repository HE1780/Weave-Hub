package com.iflytek.skillhub.domain.event;

public record AgentPublishedEvent(Long agentId, Long versionId, String publisherId) {}

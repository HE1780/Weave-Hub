package com.iflytek.skillhub.domain.agent.social.event;

public record AgentRatedEvent(Long agentId, String userId, short score) {}

package com.iflytek.skillhub.domain.event;

import java.time.Instant;

/**
 * Domain event published when an AgentVersion enters the PUBLISHED state — both
 * via PRIVATE auto-publish and via reviewer approval. Notification listeners
 * subscribe to this to fan out AGENT_PUBLISHED notifications.
 */
public record AgentPublishedEvent(
        Long agentId,
        Long agentVersionId,
        Long namespaceId,
        String publisherId,
        Instant publishedAt
) {}

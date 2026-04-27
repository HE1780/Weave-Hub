package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.agent.social.event.AgentRatedEvent;
import com.iflytek.skillhub.projection.AgentEngagementProjectionService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Updates denormalized agent rating counters when rating events are emitted by
 * the agent social domain. Mirrors {@link SkillRatingEventListener}.
 */
@Component
public class AgentRatingEventListener {
    private final AgentEngagementProjectionService agentEngagementProjectionService;

    public AgentRatingEventListener(AgentEngagementProjectionService agentEngagementProjectionService) {
        this.agentEngagementProjectionService = agentEngagementProjectionService;
    }

    @Async
    @TransactionalEventListener
    public void onRated(AgentRatedEvent event) {
        agentEngagementProjectionService.refreshRatingStats(event.agentId());
    }
}

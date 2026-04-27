package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.agent.social.event.AgentStarredEvent;
import com.iflytek.skillhub.domain.agent.social.event.AgentUnstarredEvent;
import com.iflytek.skillhub.projection.AgentEngagementProjectionService;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps the stored star count in sync with the agent star/unstar event stream.
 * Mirrors {@link SkillStarEventListener}.
 */
@Component
public class AgentStarEventListener {
    private final AgentEngagementProjectionService agentEngagementProjectionService;

    public AgentStarEventListener(AgentEngagementProjectionService agentEngagementProjectionService) {
        this.agentEngagementProjectionService = agentEngagementProjectionService;
    }

    @Async
    @TransactionalEventListener
    public void onStarred(AgentStarredEvent event) {
        agentEngagementProjectionService.refreshStarCount(event.agentId());
    }

    @Async
    @TransactionalEventListener
    public void onUnstarred(AgentUnstarredEvent event) {
        agentEngagementProjectionService.refreshStarCount(event.agentId());
    }
}

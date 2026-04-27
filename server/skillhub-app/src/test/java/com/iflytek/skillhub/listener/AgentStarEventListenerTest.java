package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.agent.social.event.AgentStarredEvent;
import com.iflytek.skillhub.domain.agent.social.event.AgentUnstarredEvent;
import com.iflytek.skillhub.projection.AgentEngagementProjectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentStarEventListenerTest {
    @Mock AgentEngagementProjectionService agentEngagementProjectionService;
    @InjectMocks AgentStarEventListener listener;

    @Test
    void onStarred_updates_star_count() {
        listener.onStarred(new AgentStarredEvent(1L, "10"));
        verify(agentEngagementProjectionService).refreshStarCount(1L);
    }

    @Test
    void onUnstarred_updates_star_count() {
        listener.onUnstarred(new AgentUnstarredEvent(1L, "10"));
        verify(agentEngagementProjectionService).refreshStarCount(1L);
    }
}

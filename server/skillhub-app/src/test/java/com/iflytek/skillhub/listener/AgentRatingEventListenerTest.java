package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.agent.social.event.AgentRatedEvent;
import com.iflytek.skillhub.projection.AgentEngagementProjectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentRatingEventListenerTest {
    @Mock AgentEngagementProjectionService agentEngagementProjectionService;
    @InjectMocks AgentRatingEventListener listener;

    @Test
    void onRated_updates_rating_avg_and_count() {
        listener.onRated(new AgentRatedEvent(1L, "10", (short) 5));
        verify(agentEngagementProjectionService).refreshRatingStats(1L);
    }
}

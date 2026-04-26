package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.event.AgentPublishedEvent;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.service.NotificationDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentNotificationListenerTest {

    private NotificationDispatcher dispatcher;
    private AgentRepository agentRepo;
    private AgentNotificationListener listener;

    @BeforeEach
    void setUp() {
        dispatcher = mock(NotificationDispatcher.class);
        agentRepo = mock(AgentRepository.class);
        listener = new AgentNotificationListener(dispatcher, agentRepo);
    }

    @Test
    void notifies_agent_owner_when_a_different_user_publishes() {
        Agent agent = mock(Agent.class);
        when(agent.getOwnerId()).thenReturn("owner-1");
        when(agentRepo.findById(7L)).thenReturn(Optional.of(agent));

        listener.onAgentPublished(new AgentPublishedEvent(
                7L, 70L, 1L, "reviewer-2", Instant.now()));

        verify(dispatcher).dispatch(
                eq("owner-1"),
                eq(NotificationCategory.AGENT),
                eq("AGENT_PUBLISHED"),
                anyString(),
                anyString(),
                eq("AGENT_VERSION"),
                eq(70L));
    }

    @Test
    void skips_self_notify_on_private_auto_publish() {
        Agent agent = mock(Agent.class);
        when(agent.getOwnerId()).thenReturn("owner-1");
        when(agentRepo.findById(7L)).thenReturn(Optional.of(agent));

        listener.onAgentPublished(new AgentPublishedEvent(
                7L, 70L, 1L, "owner-1", Instant.now()));

        verifyNoInteractions(dispatcher);
    }

    @Test
    void swallows_missing_agent_silently() {
        when(agentRepo.findById(any())).thenReturn(Optional.empty());

        listener.onAgentPublished(new AgentPublishedEvent(
                7L, 70L, 1L, "publisher", Instant.now()));

        verifyNoInteractions(dispatcher);
    }
}

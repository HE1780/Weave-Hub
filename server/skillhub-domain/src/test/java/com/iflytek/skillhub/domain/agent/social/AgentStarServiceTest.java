package com.iflytek.skillhub.domain.agent.social;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.social.event.AgentStarredEvent;
import com.iflytek.skillhub.domain.agent.social.event.AgentUnstarredEvent;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentStarServiceTest {
    @Mock AgentStarRepository starRepository;
    @Mock AgentRepository agentRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks AgentStarService service;

    private Agent agent() {
        return new Agent(1L, "agent-1", "Agent 1", "owner-1", AgentVisibility.PUBLIC);
    }

    @Test
    void star_agent_creates_record_and_publishes_event() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent()));
        when(starRepository.findByAgentIdAndUserId(1L, "10")).thenReturn(Optional.empty());
        when(starRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.star(1L, "10");

        verify(starRepository).save(any(AgentStar.class));
        verify(eventPublisher).publishEvent(any(AgentStarredEvent.class));
    }

    @Test
    void star_agent_already_starred_is_idempotent() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent()));
        when(starRepository.findByAgentIdAndUserId(1L, "10"))
            .thenReturn(Optional.of(new AgentStar(1L, "10")));

        service.star(1L, "10");

        verify(starRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void unstar_agent_deletes_record_and_publishes_event() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent()));
        AgentStar existing = new AgentStar(1L, "10");
        when(starRepository.findByAgentIdAndUserId(1L, "10")).thenReturn(Optional.of(existing));

        service.unstar(1L, "10");

        verify(starRepository).delete(existing);
        verify(eventPublisher).publishEvent(any(AgentUnstarredEvent.class));
    }

    @Test
    void unstar_agent_not_starred_is_noop() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent()));
        when(starRepository.findByAgentIdAndUserId(1L, "10")).thenReturn(Optional.empty());

        service.unstar(1L, "10");

        verify(starRepository, never()).delete(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void isStarred_returns_true_when_exists() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent()));
        when(starRepository.findByAgentIdAndUserId(1L, "10"))
            .thenReturn(Optional.of(new AgentStar(1L, "10")));
        assertThat(service.isStarred(1L, "10")).isTrue();
    }

    @Test
    void star_agent_throws_when_agent_missing() {
        when(agentRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.star(99L, "10"))
                .isInstanceOf(DomainNotFoundException.class);
    }
}

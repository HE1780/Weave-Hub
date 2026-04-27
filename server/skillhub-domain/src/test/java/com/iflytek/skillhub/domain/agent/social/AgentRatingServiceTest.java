package com.iflytek.skillhub.domain.agent.social;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.social.event.AgentRatedEvent;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
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
class AgentRatingServiceTest {
    @Mock AgentRatingRepository ratingRepository;
    @Mock AgentRepository agentRepository;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks AgentRatingService service;

    private Agent agent() {
        return new Agent(1L, "agent-1", "Agent 1", "owner-1", AgentVisibility.PUBLIC);
    }

    @Test
    void rate_creates_new_rating() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent()));
        when(ratingRepository.findByAgentIdAndUserId(1L, "10")).thenReturn(Optional.empty());
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.rate(1L, "10", (short) 4);

        verify(ratingRepository).save(argThat(r -> r.getScore() == 4));
        verify(eventPublisher).publishEvent(any(AgentRatedEvent.class));
    }

    @Test
    void rate_updates_existing_rating() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent()));
        AgentRating existing = new AgentRating(1L, "10", (short) 3);
        when(ratingRepository.findByAgentIdAndUserId(1L, "10")).thenReturn(Optional.of(existing));
        when(ratingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.rate(1L, "10", (short) 5);

        assertThat(existing.getScore()).isEqualTo((short) 5);
        verify(ratingRepository).save(existing);
        verify(eventPublisher).publishEvent(any(AgentRatedEvent.class));
    }

    @Test
    void rate_invalid_score_throws() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent()));
        assertThatThrownBy(() -> service.rate(1L, "10", (short) 0))
            .isInstanceOf(DomainBadRequestException.class);
        assertThatThrownBy(() -> service.rate(1L, "10", (short) 6))
            .isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void getUserRating_returns_score() {
        when(agentRepository.findById(1L)).thenReturn(Optional.of(agent()));
        AgentRating existing = new AgentRating(1L, "10", (short) 4);
        when(ratingRepository.findByAgentIdAndUserId(1L, "10")).thenReturn(Optional.of(existing));
        assertThat(service.getUserRating(1L, "10")).hasValue((short) 4);
    }

    @Test
    void getUserRating_throws_when_agent_missing() {
        when(agentRepository.findById(99L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getUserRating(99L, "10"))
                .isInstanceOf(DomainNotFoundException.class);
    }
}

package com.iflytek.skillhub.domain.agent.social;

import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.social.event.AgentRatedEvent;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Domain service for creating or updating user ratings on agents and emitting
 * the corresponding social event.
 * Mirrors {@link com.iflytek.skillhub.domain.social.SkillRatingService}.
 */
@Service
public class AgentRatingService {
    private final AgentRatingRepository ratingRepository;
    private final AgentRepository agentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AgentRatingService(AgentRatingRepository ratingRepository,
                              AgentRepository agentRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.ratingRepository = ratingRepository;
        this.agentRepository = agentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void rate(Long agentId, String userId, short score) {
        ensureAgentExists(agentId);
        if (score < 1 || score > 5) {
            throw new DomainBadRequestException("error.rating.score.invalid");
        }
        Optional<AgentRating> existing = ratingRepository.findByAgentIdAndUserId(agentId, userId);
        if (existing.isPresent()) {
            existing.get().updateScore(score);
            ratingRepository.save(existing.get());
        } else {
            ratingRepository.save(new AgentRating(agentId, userId, score));
        }
        eventPublisher.publishEvent(new AgentRatedEvent(agentId, userId, score));
    }

    public Optional<Short> getUserRating(Long agentId, String userId) {
        ensureAgentExists(agentId);
        return ratingRepository.findByAgentIdAndUserId(agentId, userId)
            .map(AgentRating::getScore);
    }

    private void ensureAgentExists(Long agentId) {
        if (agentRepository.findById(agentId).isEmpty()) {
            throw new DomainNotFoundException("agent.not_found", agentId);
        }
    }
}

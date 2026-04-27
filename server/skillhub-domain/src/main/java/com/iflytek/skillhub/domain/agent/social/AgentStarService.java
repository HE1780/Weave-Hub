package com.iflytek.skillhub.domain.agent.social;

import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.social.event.AgentStarredEvent;
import com.iflytek.skillhub.domain.agent.social.event.AgentUnstarredEvent;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Domain service for starring and unstarring agents in an idempotent manner.
 * Mirrors {@link com.iflytek.skillhub.domain.social.SkillStarService}.
 */
@Service
public class AgentStarService {
    private final AgentStarRepository starRepository;
    private final AgentRepository agentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AgentStarService(AgentStarRepository starRepository,
                            AgentRepository agentRepository,
                            ApplicationEventPublisher eventPublisher) {
        this.starRepository = starRepository;
        this.agentRepository = agentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void star(Long agentId, String userId) {
        ensureAgentExists(agentId);
        if (starRepository.findByAgentIdAndUserId(agentId, userId).isPresent()) {
            return; // idempotent
        }
        starRepository.save(new AgentStar(agentId, userId));
        eventPublisher.publishEvent(new AgentStarredEvent(agentId, userId));
    }

    @Transactional
    public void unstar(Long agentId, String userId) {
        ensureAgentExists(agentId);
        starRepository.findByAgentIdAndUserId(agentId, userId).ifPresent(star -> {
            starRepository.delete(star);
            eventPublisher.publishEvent(new AgentUnstarredEvent(agentId, userId));
        });
    }

    public boolean isStarred(Long agentId, String userId) {
        ensureAgentExists(agentId);
        return starRepository.findByAgentIdAndUserId(agentId, userId).isPresent();
    }

    private void ensureAgentExists(Long agentId) {
        if (agentRepository.findById(agentId).isEmpty()) {
            throw new DomainNotFoundException("agent.not_found", agentId);
        }
    }
}

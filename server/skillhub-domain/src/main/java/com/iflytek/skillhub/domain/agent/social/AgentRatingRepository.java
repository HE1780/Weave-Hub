package com.iflytek.skillhub.domain.agent.social;

import java.util.Optional;

/**
 * Domain repository contract for per-user ratings and rating aggregates on one agent.
 */
public interface AgentRatingRepository {
    AgentRating save(AgentRating rating);
    Optional<AgentRating> findByAgentIdAndUserId(Long agentId, String userId);
    double averageScoreByAgentId(Long agentId);
    int countByAgentId(Long agentId);
    void deleteByAgentId(Long agentId);
}

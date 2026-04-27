package com.iflytek.skillhub.domain.agent.social;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Domain repository contract for agent star relationships and starred-agent pagination.
 */
public interface AgentStarRepository {
    AgentStar save(AgentStar star);
    Optional<AgentStar> findByAgentIdAndUserId(Long agentId, String userId);
    void delete(AgentStar star);
    void deleteByAgentId(Long agentId);
    Page<AgentStar> findByUserId(String userId, Pageable pageable);
    long countByAgentId(Long agentId);
}

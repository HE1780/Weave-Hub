package com.iflytek.skillhub.domain.agent;

import java.util.Optional;

/**
 * Domain repository contract for per-agent-version counters.
 * Mirrors {@link com.iflytek.skillhub.domain.skill.SkillVersionStatsRepository}.
 */
public interface AgentVersionStatsRepository {
    Optional<AgentVersionStats> findByAgentVersionId(Long agentVersionId);
    void incrementDownloadCount(Long agentVersionId, Long agentId);
    void deleteByAgentId(Long agentId);
}

package com.iflytek.skillhub.domain.agent;

import java.util.List;
import java.util.Optional;

/**
 * Domain repository contract for persisted agent tags. Mirrors
 * {@link com.iflytek.skillhub.domain.skill.SkillTagRepository}.
 */
public interface AgentTagRepository {
    Optional<AgentTag> findByAgentIdAndTagName(Long agentId, String tagName);
    List<AgentTag> findByAgentId(Long agentId);
    AgentTag save(AgentTag tag);
    void delete(AgentTag tag);
    void deleteByAgentId(Long agentId);
}

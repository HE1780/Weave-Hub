package com.iflytek.skillhub.domain.label;

import java.util.List;
import java.util.Optional;

public interface AgentLabelRepository {
    List<AgentLabel> findByAgentId(Long agentId);
    List<AgentLabel> findByAgentIdIn(List<Long> agentIds);
    List<AgentLabel> findByLabelId(Long labelId);
    Optional<AgentLabel> findByAgentIdAndLabelId(Long agentId, Long labelId);
    long countByAgentId(Long agentId);
    AgentLabel save(AgentLabel agentLabel);
    void delete(AgentLabel agentLabel);
}

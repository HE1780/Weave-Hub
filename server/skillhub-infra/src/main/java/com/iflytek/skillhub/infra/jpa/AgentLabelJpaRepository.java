package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.label.AgentLabel;
import com.iflytek.skillhub.domain.label.AgentLabelRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentLabelJpaRepository extends JpaRepository<AgentLabel, Long>, AgentLabelRepository {
    List<AgentLabel> findByAgentId(Long agentId);
    List<AgentLabel> findByAgentIdIn(List<Long> agentIds);
    List<AgentLabel> findByLabelId(Long labelId);
    Optional<AgentLabel> findByAgentIdAndLabelId(Long agentId, Long labelId);
    long countByAgentId(Long agentId);
}

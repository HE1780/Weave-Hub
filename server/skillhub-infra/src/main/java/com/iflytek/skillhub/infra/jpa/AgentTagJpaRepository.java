package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.agent.AgentTag;
import com.iflytek.skillhub.domain.agent.AgentTagRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA-backed repository for tags associated with an agent. Mirrors
 * {@link SkillTagJpaRepository}.
 */
@Repository
public interface AgentTagJpaRepository extends JpaRepository<AgentTag, Long>, AgentTagRepository {
    Optional<AgentTag> findByAgentIdAndTagName(Long agentId, String tagName);
    List<AgentTag> findByAgentId(Long agentId);
    void deleteByAgentId(Long agentId);
}

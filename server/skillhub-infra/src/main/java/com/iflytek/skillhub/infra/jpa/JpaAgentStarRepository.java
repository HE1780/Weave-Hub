package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.agent.social.AgentStar;
import com.iflytek.skillhub.domain.agent.social.AgentStarRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA-backed repository for agent star relationships and user star listings.
 * Mirrors {@link JpaSkillStarRepository}.
 */
@Repository
public interface JpaAgentStarRepository extends JpaRepository<AgentStar, Long>, AgentStarRepository {
    Optional<AgentStar> findByAgentIdAndUserId(Long agentId, String userId);
    void deleteByAgentId(Long agentId);
    Page<AgentStar> findByUserId(String userId, Pageable pageable);
    long countByAgentId(Long agentId);
}

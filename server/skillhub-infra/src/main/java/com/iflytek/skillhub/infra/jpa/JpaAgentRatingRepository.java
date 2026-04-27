package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.agent.social.AgentRating;
import com.iflytek.skillhub.domain.agent.social.AgentRatingRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * JPA-backed repository for per-user agent ratings and their derived aggregates.
 * Mirrors {@link JpaSkillRatingRepository}.
 */
@Repository
public interface JpaAgentRatingRepository extends JpaRepository<AgentRating, Long>, AgentRatingRepository {
    Optional<AgentRating> findByAgentIdAndUserId(Long agentId, String userId);

    @Query("SELECT COALESCE(AVG(r.score), 0) FROM AgentRating r WHERE r.agentId = :agentId")
    double averageScoreByAgentId(Long agentId);

    int countByAgentId(Long agentId);

    void deleteByAgentId(Long agentId);
}

package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.agent.AgentVersionStats;
import com.iflytek.skillhub.domain.agent.AgentVersionStatsRepository;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * JPA-backed repository for per-agent-version statistics, atomic download
 * counter increments. Mirrors {@link SkillVersionStatsJpaRepository}.
 */
@Repository
public interface AgentVersionStatsJpaRepository extends JpaRepository<AgentVersionStats, Long>, AgentVersionStatsRepository {

    @Override
    default Optional<AgentVersionStats> findByAgentVersionId(Long agentVersionId) {
        return findById(agentVersionId);
    }

    @Override
    @Modifying
    @Transactional
    @Query(
            value = """
                    INSERT INTO agent_version_stats (agent_version_id, agent_id, download_count, updated_at)
                    VALUES (:agentVersionId, :agentId, 1, CURRENT_TIMESTAMP)
                    ON CONFLICT (agent_version_id)
                    DO UPDATE SET download_count = agent_version_stats.download_count + 1,
                                  updated_at = CURRENT_TIMESTAMP
                    """,
            nativeQuery = true
    )
    void incrementDownloadCount(@Param("agentVersionId") Long agentVersionId, @Param("agentId") Long agentId);

    @Override
    @Modifying
    @Transactional
    @Query("DELETE FROM AgentVersionStats s WHERE s.agentId = :agentId")
    void deleteByAgentId(@Param("agentId") Long agentId);
}

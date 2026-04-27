package com.iflytek.skillhub.projection;

import com.iflytek.skillhub.domain.agent.social.AgentRatingRepository;
import com.iflytek.skillhub.domain.agent.social.AgentStarRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Maintains denormalized social counters on the agent read model.
 * Mirrors {@link SkillEngagementProjectionService}.
 */
@Service
public class AgentEngagementProjectionService {

    private final JdbcTemplate jdbcTemplate;
    private final AgentStarRepository starRepository;
    private final AgentRatingRepository ratingRepository;

    public AgentEngagementProjectionService(JdbcTemplate jdbcTemplate,
                                            AgentStarRepository starRepository,
                                            AgentRatingRepository ratingRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.starRepository = starRepository;
        this.ratingRepository = ratingRepository;
    }

    public void refreshStarCount(Long agentId) {
        long count = starRepository.countByAgentId(agentId);
        jdbcTemplate.update("UPDATE agent SET star_count = ? WHERE id = ?", (int) count, agentId);
    }

    public void refreshRatingStats(Long agentId) {
        double avg = ratingRepository.averageScoreByAgentId(agentId);
        int count = ratingRepository.countByAgentId(agentId);
        jdbcTemplate.update(
                "UPDATE agent SET rating_avg = ?, rating_count = ? WHERE id = ?",
                avg,
                count,
                agentId
        );
    }
}

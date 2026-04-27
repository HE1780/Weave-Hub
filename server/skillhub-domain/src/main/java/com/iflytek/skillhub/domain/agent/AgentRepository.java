package com.iflytek.skillhub.domain.agent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/**
 * Domain port for agent persistence. Implemented in skillhub-infra by a
 * Spring Data JPA repository.
 */
public interface AgentRepository {

    Optional<Agent> findById(Long id);

    List<Agent> findByIdIn(List<Long> ids);

    Optional<Agent> findByNamespaceIdAndSlug(Long namespaceId, String slug);

    Page<Agent> findByNamespaceIdAndStatus(Long namespaceId, AgentStatus status, Pageable pageable);

    Page<Agent> findByVisibilityAndStatus(AgentVisibility visibility, AgentStatus status, Pageable pageable);

    /**
     * Returns ACTIVE agents matching an optional case-insensitive keyword (display_name or
     * description) and an optional namespace filter. Visibility is intentionally NOT filtered
     * here because the rule depends on caller identity; the service layer applies
     * {@link AgentVisibilityChecker} on top of this result.
     */
    Page<Agent> searchPublic(String keyword, Long namespaceId, Pageable pageable);

    Page<Agent> findByOwnerId(String ownerId, Pageable pageable);

    Agent save(Agent agent);

    void delete(Agent agent);

    void flush();

    /**
     * Atomically increments the agent's denormalized download_count column.
     * Mirrors SkillRepository.incrementDownloadCount.
     */
    void incrementDownloadCount(Long agentId);
}

package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * JPA implementation of AgentRepository. Mirrors SkillJpaRepository.
 */
@Repository
public interface AgentJpaRepository extends JpaRepository<Agent, Long>, AgentRepository {

    @Override
    List<Agent> findByIdIn(List<Long> ids);

    @Override
    Optional<Agent> findByNamespaceIdAndSlug(Long namespaceId, String slug);

    @Override
    Page<Agent> findByNamespaceIdAndStatus(Long namespaceId, AgentStatus status, Pageable pageable);

    @Override
    Page<Agent> findByVisibilityAndStatus(AgentVisibility visibility, AgentStatus status, Pageable pageable);

    @Override
    Page<Agent> findByOwnerId(String ownerId, Pageable pageable);

    @Override
    @Query("""
            SELECT a FROM Agent a
            WHERE a.status = com.iflytek.skillhub.domain.agent.AgentStatus.ACTIVE
              AND (:keyword IS NULL OR :keyword = ''
                   OR LOWER(a.displayName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(a.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
              AND (:namespaceId IS NULL OR a.namespaceId = :namespaceId)
            """)
    Page<Agent> searchPublic(@Param("keyword") String keyword,
                             @Param("namespaceId") Long namespaceId,
                             Pageable pageable);

    @Override
    @Modifying
    @Transactional
    @Query("UPDATE Agent a SET a.downloadCount = a.downloadCount + 1 WHERE a.id = :agentId")
    void incrementDownloadCount(@Param("agentId") Long agentId);
}

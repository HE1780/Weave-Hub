package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}

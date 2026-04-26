package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgentVersionJpaRepository
        extends JpaRepository<AgentVersion, Long>, AgentVersionRepository {

    @Override
    Optional<AgentVersion> findByAgentIdAndVersion(Long agentId, String version);

    @Override
    List<AgentVersion> findByAgentIdOrderBySubmittedAtDesc(Long agentId);

    @Override
    Optional<AgentVersion> findFirstByAgentIdAndStatusOrderByPublishedAtDesc(
            Long agentId, AgentVersionStatus status);

    @Override
    Page<AgentVersion> findByAgentIdAndStatus(
            Long agentId, AgentVersionStatus status, Pageable pageable);
}

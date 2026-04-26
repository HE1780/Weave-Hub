package com.iflytek.skillhub.domain.agent;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface AgentVersionRepository {

    Optional<AgentVersion> findById(Long id);

    Optional<AgentVersion> findByAgentIdAndVersion(Long agentId, String version);

    List<AgentVersion> findByAgentIdOrderBySubmittedAtDesc(Long agentId);

    Optional<AgentVersion> findFirstByAgentIdAndStatusOrderByPublishedAtDesc(
            Long agentId, AgentVersionStatus status);

    Page<AgentVersion> findByAgentIdAndStatus(
            Long agentId, AgentVersionStatus status, Pageable pageable);

    AgentVersion save(AgentVersion agentVersion);

    void flush();
}

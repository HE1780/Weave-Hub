package com.iflytek.skillhub.domain.agent.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface AgentVersionCommentRepository {
    Page<AgentVersionComment> findActiveByVersionId(Long agentVersionId, Pageable pageable);
    Optional<AgentVersionComment> findById(Long id);
    AgentVersionComment save(AgentVersionComment comment);
}

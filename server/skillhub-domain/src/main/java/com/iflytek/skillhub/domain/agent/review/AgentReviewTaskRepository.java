package com.iflytek.skillhub.domain.agent.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface AgentReviewTaskRepository {

    Optional<AgentReviewTask> findById(Long id);

    Optional<AgentReviewTask> findByAgentVersionId(Long agentVersionId);

    Page<AgentReviewTask> findByNamespaceIdAndStatus(
            Long namespaceId, AgentReviewTaskStatus status, Pageable pageable);

    AgentReviewTask save(AgentReviewTask task);

    void flush();
}

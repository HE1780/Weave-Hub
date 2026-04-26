package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.agent.review.AgentReviewTask;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTaskRepository;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTaskStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AgentReviewTaskJpaRepository
        extends JpaRepository<AgentReviewTask, Long>, AgentReviewTaskRepository {

    @Override
    Optional<AgentReviewTask> findByAgentVersionId(Long agentVersionId);

    @Override
    Page<AgentReviewTask> findByNamespaceIdAndStatus(
            Long namespaceId, AgentReviewTaskStatus status, Pageable pageable);
}

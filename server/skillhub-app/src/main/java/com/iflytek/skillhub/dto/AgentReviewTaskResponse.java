package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.agent.review.AgentReviewTask;

import java.time.Instant;

public record AgentReviewTaskResponse(
        Long id,
        Long agentVersionId,
        Long namespaceId,
        String status,
        String submittedBy,
        String reviewedBy,
        String reviewComment,
        Instant submittedAt,
        Instant reviewedAt
) {
    public static AgentReviewTaskResponse from(AgentReviewTask task) {
        return new AgentReviewTaskResponse(
                task.getId(),
                task.getAgentVersionId(),
                task.getNamespaceId(),
                task.getStatus().name(),
                task.getSubmittedBy(),
                task.getReviewedBy(),
                task.getReviewComment(),
                task.getSubmittedAt(),
                task.getReviewedAt()
        );
    }
}

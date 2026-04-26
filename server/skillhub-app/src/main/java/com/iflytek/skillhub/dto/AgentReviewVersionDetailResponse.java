package com.iflytek.skillhub.dto;

/**
 * Full review-screen payload: the task, its agent metadata, and the inline soul/workflow content.
 */
public record AgentReviewVersionDetailResponse(
        AgentReviewTaskResponse task,
        AgentResponse agent,
        AgentVersionResponse version
) {
}

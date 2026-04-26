package com.iflytek.skillhub.dto;

/**
 * Request body for approve/reject endpoints. {@code comment} is optional.
 */
public record AgentReviewActionRequest(String comment) {
}

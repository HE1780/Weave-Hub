package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotNull;

public record AgentRatingRequest(
        @NotNull(message = "{error.badRequest}")
        Short score
) {
}

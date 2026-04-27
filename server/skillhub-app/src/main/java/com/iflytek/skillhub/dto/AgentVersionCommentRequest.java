package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentVersionCommentRequest(
        @NotBlank(message = "error.comment.body.empty")
        @Size(max = 8192, message = "error.comment.body.tooLong")
        String body
) {}

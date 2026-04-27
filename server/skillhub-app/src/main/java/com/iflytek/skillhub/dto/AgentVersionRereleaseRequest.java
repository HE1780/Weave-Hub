package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AgentVersionRereleaseRequest(
        @NotBlank(message = "{error.badRequest}")
        @Size(max = 64, message = "{error.badRequest}")
        String targetVersion
) {
}

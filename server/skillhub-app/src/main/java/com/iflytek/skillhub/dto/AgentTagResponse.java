package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.agent.AgentTag;

import java.time.Instant;

public record AgentTagResponse(
        Long id,
        String tagName,
        Long versionId,
        Instant createdAt
) {
    public static AgentTagResponse from(AgentTag tag) {
        return new AgentTagResponse(
                tag.getId(),
                tag.getTagName(),
                tag.getVersionId(),
                tag.getCreatedAt()
        );
    }
}

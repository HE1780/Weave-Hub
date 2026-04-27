package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.agent.social.AgentVersionComment;
import com.iflytek.skillhub.domain.social.CommentPermissions;

import java.time.Instant;

public record AgentVersionCommentResponse(
        Long id,
        Long agentVersionId,
        AuthorRef author,
        String body,
        boolean pinned,
        Instant createdAt,
        Instant lastEditedAt,
        boolean deleted,
        CommentPermissions permissions
) {
    public record AuthorRef(String userId, String displayName, String avatarUrl) {}

    public static AgentVersionCommentResponse from(
            AgentVersionComment c, AuthorRef author, CommentPermissions perms) {
        return new AgentVersionCommentResponse(
                c.getId(),
                c.getAgentVersionId(),
                author,
                c.getBody(),
                c.isPinned(),
                c.getCreatedAt(),
                c.getLastEditedAt(),
                c.isDeleted(),
                perms
        );
    }
}

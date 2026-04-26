package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.social.CommentPermissions;
import com.iflytek.skillhub.domain.social.SkillVersionComment;

import java.time.Instant;

public record SkillVersionCommentResponse(
        Long id,
        Long skillVersionId,
        AuthorRef author,
        String body,
        boolean pinned,
        Instant createdAt,
        Instant lastEditedAt,
        boolean deleted,
        CommentPermissions permissions
) {
    public record AuthorRef(String userId, String displayName, String avatarUrl) {}

    public static SkillVersionCommentResponse from(
            SkillVersionComment c, AuthorRef author, CommentPermissions perms) {
        return new SkillVersionCommentResponse(
                c.getId(),
                c.getSkillVersionId(),
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

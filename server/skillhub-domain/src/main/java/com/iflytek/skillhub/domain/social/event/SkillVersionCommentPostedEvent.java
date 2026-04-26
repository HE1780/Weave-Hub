package com.iflytek.skillhub.domain.social.event;

public record SkillVersionCommentPostedEvent(
        Long commentId,
        Long skillVersionId,
        String authorUserId,
        String bodyExcerpt
) {}

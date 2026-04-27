package com.iflytek.skillhub.domain.agent.social.event;

public record AgentVersionCommentPostedEvent(
        Long commentId,
        Long agentVersionId,
        String authorUserId,
        String bodyExcerpt
) {}

package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.social.event.SkillVersionCommentPostedEvent;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Component
public class SkillVersionCommentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(SkillVersionCommentNotificationListener.class);
    private static final String ENTITY_TYPE = "SKILL_VERSION_COMMENT";

    private final NotificationDispatcher dispatcher;
    private final SkillVersionRepository versionRepo;

    public SkillVersionCommentNotificationListener(
            NotificationDispatcher dispatcher, SkillVersionRepository versionRepo) {
        this.dispatcher = dispatcher;
        this.versionRepo = versionRepo;
    }

    @TransactionalEventListener
    public void onCommentPosted(SkillVersionCommentPostedEvent event) {
        try {
            Optional<SkillVersion> v = versionRepo.findById(event.skillVersionId());
            if (v.isEmpty()) {
                return;
            }
            String recipient = v.get().getCreatedBy();
            if (recipient == null || recipient.equals(event.authorUserId())) {
                return;
            }
            String bodyJson = String.format(
                    "{\"commentId\":%d,\"skillVersionId\":%d,\"authorUserId\":\"%s\",\"excerpt\":\"%s\"}",
                    event.commentId(),
                    event.skillVersionId(),
                    escapeJson(event.authorUserId()),
                    escapeJson(event.bodyExcerpt())
            );
            dispatcher.dispatch(
                    recipient,
                    NotificationCategory.COMMENT,
                    "COMMENT_POSTED",
                    "comment.notification.title",
                    bodyJson,
                    ENTITY_TYPE,
                    event.commentId()
            );
        } catch (Exception e) {
            log.warn("Failed to dispatch COMMENT_POSTED notification for commentId={}", event.commentId(), e);
        }
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}

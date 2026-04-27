package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.social.event.AgentVersionCommentPostedEvent;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

/**
 * Mirrors {@link SkillVersionCommentNotificationListener}: dispatches
 * {@code COMMENT_POSTED} to the agent version's submitter when someone
 * else posts a comment on the version.
 */
@Component
public class AgentVersionCommentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(AgentVersionCommentNotificationListener.class);
    private static final String ENTITY_TYPE = "AGENT_VERSION_COMMENT";

    private final NotificationDispatcher dispatcher;
    private final AgentVersionRepository versionRepo;

    public AgentVersionCommentNotificationListener(
            NotificationDispatcher dispatcher, AgentVersionRepository versionRepo) {
        this.dispatcher = dispatcher;
        this.versionRepo = versionRepo;
    }

    @TransactionalEventListener
    public void onCommentPosted(AgentVersionCommentPostedEvent event) {
        try {
            Optional<AgentVersion> v = versionRepo.findById(event.agentVersionId());
            if (v.isEmpty()) {
                return;
            }
            String recipient = v.get().getSubmittedBy();
            if (recipient == null || recipient.equals(event.authorUserId())) {
                return;
            }
            String bodyJson = String.format(
                    "{\"commentId\":%d,\"agentVersionId\":%d,\"authorUserId\":\"%s\",\"excerpt\":\"%s\"}",
                    event.commentId(),
                    event.agentVersionId(),
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
            log.warn("Failed to dispatch COMMENT_POSTED notification for agent commentId={}", event.commentId(), e);
        }
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}

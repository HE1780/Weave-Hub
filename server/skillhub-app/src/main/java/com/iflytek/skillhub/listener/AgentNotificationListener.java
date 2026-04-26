package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.event.AgentPublishedEvent;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Component
public class AgentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(AgentNotificationListener.class);
    private static final String ENTITY_TYPE = "AGENT_VERSION";

    private final NotificationDispatcher dispatcher;
    private final AgentRepository agentRepository;

    public AgentNotificationListener(NotificationDispatcher dispatcher,
                                     AgentRepository agentRepository) {
        this.dispatcher = dispatcher;
        this.agentRepository = agentRepository;
    }

    @TransactionalEventListener
    public void onAgentPublished(AgentPublishedEvent event) {
        try {
            Optional<Agent> agent = agentRepository.findById(event.agentId());
            if (agent.isEmpty()) {
                return;
            }
            String recipient = agent.get().getOwnerId();
            if (recipient == null || recipient.equals(event.publisherId())) {
                // Skip self-notify on auto-publish (private path).
                return;
            }
            String bodyJson = String.format(
                    "{\"agentId\":%d,\"agentVersionId\":%d,\"namespaceId\":%d,\"publisherId\":\"%s\"}",
                    event.agentId(),
                    event.agentVersionId(),
                    event.namespaceId(),
                    escapeJson(event.publisherId())
            );
            dispatcher.dispatch(
                    recipient,
                    NotificationCategory.AGENT,
                    "AGENT_PUBLISHED",
                    "agent.notification.published.title",
                    bodyJson,
                    ENTITY_TYPE,
                    event.agentVersionId()
            );
        } catch (Exception e) {
            log.warn("Failed to dispatch AGENT_PUBLISHED notification for agentVersionId={}",
                    event.agentVersionId(), e);
        }
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}

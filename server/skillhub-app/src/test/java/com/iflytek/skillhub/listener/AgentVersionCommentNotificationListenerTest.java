package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.social.event.AgentVersionCommentPostedEvent;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.service.NotificationDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AgentVersionCommentNotificationListenerTest {

    private NotificationDispatcher dispatcher;
    private AgentVersionRepository versionRepo;
    private AgentVersionCommentNotificationListener listener;

    @BeforeEach
    void setUp() {
        dispatcher = mock(NotificationDispatcher.class);
        versionRepo = mock(AgentVersionRepository.class);
        listener = new AgentVersionCommentNotificationListener(dispatcher, versionRepo);
    }

    @Test
    void notifiesVersionSubmitterOnNewComment() {
        AgentVersion v = mock(AgentVersion.class);
        when(v.getSubmittedBy()).thenReturn("author");
        when(versionRepo.findById(99L)).thenReturn(Optional.of(v));

        listener.onCommentPosted(new AgentVersionCommentPostedEvent(1L, 99L, "commenter", "hi"));

        verify(dispatcher).dispatch(
                eq("author"),
                eq(NotificationCategory.COMMENT),
                eq("COMMENT_POSTED"),
                anyString(),
                anyString(),
                eq("AGENT_VERSION_COMMENT"),
                eq(1L)
        );
    }

    @Test
    void skipsSelfNotify() {
        AgentVersion v = mock(AgentVersion.class);
        when(v.getSubmittedBy()).thenReturn("alice");
        when(versionRepo.findById(99L)).thenReturn(Optional.of(v));

        listener.onCommentPosted(new AgentVersionCommentPostedEvent(1L, 99L, "alice", "hi"));

        verifyNoInteractions(dispatcher);
    }

    @Test
    void skipsWhenVersionMissing() {
        when(versionRepo.findById(99L)).thenReturn(Optional.empty());

        listener.onCommentPosted(new AgentVersionCommentPostedEvent(1L, 99L, "commenter", "hi"));

        verifyNoInteractions(dispatcher);
    }

    @Test
    void swallowsDispatcherFailure() {
        AgentVersion v = mock(AgentVersion.class);
        when(v.getSubmittedBy()).thenReturn("author");
        when(versionRepo.findById(99L)).thenReturn(Optional.of(v));
        doThrow(new RuntimeException("smtp down"))
                .when(dispatcher).dispatch(any(), any(), any(), any(), any(), any(), any());

        listener.onCommentPosted(new AgentVersionCommentPostedEvent(1L, 99L, "commenter", "hi"));
        // No exception escapes — verified by the test completing.
    }
}

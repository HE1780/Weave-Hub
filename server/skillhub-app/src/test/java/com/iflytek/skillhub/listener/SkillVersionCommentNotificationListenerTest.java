package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.social.event.SkillVersionCommentPostedEvent;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.service.NotificationDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SkillVersionCommentNotificationListenerTest {

    private NotificationDispatcher dispatcher;
    private SkillVersionRepository versionRepo;
    private SkillVersionCommentNotificationListener listener;

    @BeforeEach
    void setUp() {
        dispatcher = mock(NotificationDispatcher.class);
        versionRepo = mock(SkillVersionRepository.class);
        listener = new SkillVersionCommentNotificationListener(dispatcher, versionRepo);
    }

    @Test
    void notifiesVersionAuthorOnNewComment() {
        SkillVersion v = mock(SkillVersion.class);
        when(v.getCreatedBy()).thenReturn("author");
        when(versionRepo.findById(99L)).thenReturn(Optional.of(v));

        listener.onCommentPosted(new SkillVersionCommentPostedEvent(1L, 99L, "commenter", "hi"));

        verify(dispatcher).dispatch(
                eq("author"),
                eq(NotificationCategory.COMMENT),
                eq("COMMENT_POSTED"),
                anyString(),
                anyString(),
                eq("SKILL_VERSION_COMMENT"),
                eq(1L)
        );
    }

    @Test
    void skipsSelfNotify() {
        SkillVersion v = mock(SkillVersion.class);
        when(v.getCreatedBy()).thenReturn("alice");
        when(versionRepo.findById(99L)).thenReturn(Optional.of(v));

        listener.onCommentPosted(new SkillVersionCommentPostedEvent(1L, 99L, "alice", "hi"));

        verifyNoInteractions(dispatcher);
    }

    @Test
    void swallowsDispatcherFailure() {
        SkillVersion v = mock(SkillVersion.class);
        when(v.getCreatedBy()).thenReturn("author");
        when(versionRepo.findById(99L)).thenReturn(Optional.of(v));
        doThrow(new RuntimeException("smtp down"))
                .when(dispatcher).dispatch(any(), any(), any(), any(), any(), any(), any());

        listener.onCommentPosted(new SkillVersionCommentPostedEvent(1L, 99L, "commenter", "hi"));
        // No exception escapes — verified by the test completing.
    }
}

package com.iflytek.skillhub.domain.agent;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentVersionTest {

    private AgentVersion newDraft() {
        return new AgentVersion(
                1L, "1.0.0", "user-1",
                "manifest", "soul", "workflow",
                "object-key", 1024L);
    }

    @Test
    void initial_status_is_DRAFT_with_no_publishedAt() {
        AgentVersion v = newDraft();
        assertEquals(AgentVersionStatus.DRAFT, v.getStatus());
        assertNull(v.getPublishedAt());
        assertNotNull(v.getSubmittedAt());
    }

    @Test
    void submitForReview_from_DRAFT_transitions_to_PENDING_REVIEW() {
        AgentVersion v = newDraft();
        v.submitForReview();
        assertEquals(AgentVersionStatus.PENDING_REVIEW, v.getStatus());
        assertNull(v.getPublishedAt());
    }

    @Test
    void autoPublish_from_DRAFT_sets_PUBLISHED_and_publishedAt() {
        AgentVersion v = newDraft();
        v.autoPublish();
        assertEquals(AgentVersionStatus.PUBLISHED, v.getStatus());
        assertNotNull(v.getPublishedAt());
    }

    @Test
    void approve_from_PENDING_REVIEW_sets_PUBLISHED_and_publishedAt() {
        AgentVersion v = newDraft();
        v.submitForReview();
        v.approve();
        assertEquals(AgentVersionStatus.PUBLISHED, v.getStatus());
        assertNotNull(v.getPublishedAt());
    }

    @Test
    void reject_from_PENDING_REVIEW_sets_REJECTED() {
        AgentVersion v = newDraft();
        v.submitForReview();
        v.reject();
        assertEquals(AgentVersionStatus.REJECTED, v.getStatus());
    }

    @Test
    void resubmitDraft_from_REJECTED_returns_to_DRAFT() {
        AgentVersion v = newDraft();
        v.submitForReview();
        v.reject();
        v.resubmitDraft();
        assertEquals(AgentVersionStatus.DRAFT, v.getStatus());
    }

    @Test
    void approve_from_DRAFT_throws() {
        AgentVersion v = newDraft();
        assertThrows(DomainBadRequestException.class, v::approve);
    }

    @Test
    void submitForReview_after_already_submitted_throws() {
        AgentVersion v = newDraft();
        v.submitForReview();
        assertThrows(DomainBadRequestException.class, v::submitForReview);
    }

    @Test
    void archive_from_non_PUBLISHED_throws() {
        AgentVersion v = newDraft();
        assertThrows(DomainBadRequestException.class, v::archive);
    }

    @Test
    void archive_from_PUBLISHED_works() {
        AgentVersion v = newDraft();
        v.autoPublish();
        v.archive();
        assertEquals(AgentVersionStatus.ARCHIVED, v.getStatus());
    }
}

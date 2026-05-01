package com.iflytek.skillhub.domain.agent;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentVersionTest {

    private AgentVersion newScanning() {
        return new AgentVersion(
                1L, "1.0.0", "user-1",
                "manifest", "soul", "workflow",
                "object-key", 1024L);
    }

    private AgentVersion newUploaded() {
        AgentVersion v = newScanning();
        v.markScanPassed();
        return v;
    }

    @Test
    void initial_status_is_SCANNING_with_no_publishedAt() {
        AgentVersion v = newScanning();
        assertEquals(AgentVersionStatus.SCANNING, v.getStatus());
        assertNull(v.getPublishedAt());
        assertNotNull(v.getSubmittedAt());
    }

    @Test
    void markScanPassed_from_SCANNING_transitions_to_UPLOADED() {
        AgentVersion v = newScanning();
        v.markScanPassed();
        assertEquals(AgentVersionStatus.UPLOADED, v.getStatus());
    }

    @Test
    void markScanFailed_from_SCANNING_transitions_to_SCAN_FAILED() {
        AgentVersion v = newScanning();
        v.markScanFailed();
        assertEquals(AgentVersionStatus.SCAN_FAILED, v.getStatus());
    }

    @Test
    void retryScan_from_SCAN_FAILED_transitions_back_to_SCANNING() {
        AgentVersion v = newScanning();
        v.markScanFailed();
        v.retryScan();
        assertEquals(AgentVersionStatus.SCANNING, v.getStatus());
    }

    @Test
    void submitForReview_from_UPLOADED_transitions_to_PENDING_REVIEW_and_records_visibility() {
        AgentVersion v = newUploaded();
        v.submitForReview(AgentVisibility.PUBLIC);
        assertEquals(AgentVersionStatus.PENDING_REVIEW, v.getStatus());
        assertEquals(AgentVisibility.PUBLIC, v.getRequestedVisibility());
        assertNull(v.getPublishedAt());
    }

    @Test
    void autoPublish_from_UPLOADED_sets_PUBLISHED_and_publishedAt() {
        AgentVersion v = newUploaded();
        v.autoPublish();
        assertEquals(AgentVersionStatus.PUBLISHED, v.getStatus());
        assertNotNull(v.getPublishedAt());
    }

    @Test
    void approve_from_PENDING_REVIEW_sets_PUBLISHED_and_publishedAt() {
        AgentVersion v = newUploaded();
        v.submitForReview(AgentVisibility.NAMESPACE_ONLY);
        v.approve();
        assertEquals(AgentVersionStatus.PUBLISHED, v.getStatus());
        assertNotNull(v.getPublishedAt());
    }

    @Test
    void reject_from_PENDING_REVIEW_sets_REJECTED() {
        AgentVersion v = newUploaded();
        v.submitForReview(AgentVisibility.PUBLIC);
        v.reject();
        assertEquals(AgentVersionStatus.REJECTED, v.getStatus());
    }

    @Test
    void resubmitDraft_from_REJECTED_returns_to_DRAFT() {
        AgentVersion v = newUploaded();
        v.submitForReview(AgentVisibility.PUBLIC);
        v.reject();
        v.resubmitDraft();
        assertEquals(AgentVersionStatus.DRAFT, v.getStatus());
    }

    @Test
    void withdrawReview_from_PENDING_REVIEW_returns_to_UPLOADED() {
        AgentVersion v = newUploaded();
        v.submitForReview(AgentVisibility.PUBLIC);
        v.withdrawReview();
        assertEquals(AgentVersionStatus.UPLOADED, v.getStatus());
    }

    @Test
    void approve_from_SCANNING_throws() {
        AgentVersion v = newScanning();
        assertThrows(DomainBadRequestException.class, v::approve);
    }

    @Test
    void submitForReview_from_SCANNING_throws() {
        AgentVersion v = newScanning();
        assertThrows(DomainBadRequestException.class,
                () -> v.submitForReview(AgentVisibility.PUBLIC));
    }

    @Test
    void submitForReview_after_already_submitted_throws() {
        AgentVersion v = newUploaded();
        v.submitForReview(AgentVisibility.PUBLIC);
        assertThrows(DomainBadRequestException.class,
                () -> v.submitForReview(AgentVisibility.PUBLIC));
    }

    @Test
    void archive_from_non_PUBLISHED_throws() {
        AgentVersion v = newScanning();
        assertThrows(DomainBadRequestException.class, v::archive);
    }

    @Test
    void archive_from_PUBLISHED_works() {
        AgentVersion v = newUploaded();
        v.autoPublish();
        v.archive();
        assertEquals(AgentVersionStatus.ARCHIVED, v.getStatus());
    }

    @Test
    void yank_from_PUBLISHED_records_metadata() {
        AgentVersion v = newUploaded();
        v.autoPublish();
        v.yank("policy violation", "admin-1");
        assertEquals(AgentVersionStatus.YANKED, v.getStatus());
        assertEquals("admin-1", v.getYankedBy());
        assertEquals("policy violation", v.getYankReason());
        assertNotNull(v.getYankedAt());
    }

    @Test
    void yank_from_non_PUBLISHED_throws() {
        AgentVersion v = newUploaded();
        assertThrows(DomainBadRequestException.class,
                () -> v.yank("nope", "admin-1"));
    }
}

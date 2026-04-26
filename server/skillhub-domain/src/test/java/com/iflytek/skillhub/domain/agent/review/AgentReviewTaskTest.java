package com.iflytek.skillhub.domain.agent.review;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentReviewTaskTest {

    private AgentReviewTask newPending() {
        return new AgentReviewTask(100L, 1L, "user-1");
    }

    @Test
    void initial_status_is_PENDING() {
        AgentReviewTask t = newPending();
        assertEquals(AgentReviewTaskStatus.PENDING, t.getStatus());
        assertNull(t.getReviewedAt());
        assertNull(t.getReviewedBy());
    }

    @Test
    void approve_sets_APPROVED_and_records_reviewer() {
        AgentReviewTask t = newPending();
        t.approve("admin-1", "looks good");
        assertEquals(AgentReviewTaskStatus.APPROVED, t.getStatus());
        assertEquals("admin-1", t.getReviewedBy());
        assertEquals("looks good", t.getReviewComment());
        assertNotNull(t.getReviewedAt());
    }

    @Test
    void reject_sets_REJECTED_and_records_reviewer() {
        AgentReviewTask t = newPending();
        t.reject("admin-1", "incorrect workflow");
        assertEquals(AgentReviewTaskStatus.REJECTED, t.getStatus());
        assertEquals("admin-1", t.getReviewedBy());
        assertEquals("incorrect workflow", t.getReviewComment());
        assertNotNull(t.getReviewedAt());
    }

    @Test
    void approve_after_already_approved_throws() {
        AgentReviewTask t = newPending();
        t.approve("admin-1", null);
        assertThrows(DomainBadRequestException.class, () -> t.approve("admin-2", null));
    }

    @Test
    void reject_after_already_approved_throws() {
        AgentReviewTask t = newPending();
        t.approve("admin-1", null);
        assertThrows(DomainBadRequestException.class, () -> t.reject("admin-2", "no"));
    }
}

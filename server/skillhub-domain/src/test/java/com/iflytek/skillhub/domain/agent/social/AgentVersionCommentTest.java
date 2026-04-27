package com.iflytek.skillhub.domain.agent.social;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AgentVersionCommentTest {

    @Test
    void rejectsEmptyBody() {
        assertThrows(DomainBadRequestException.class,
            () -> new AgentVersionComment(1L, "u1", ""));
    }

    @Test
    void rejectsBlankBody() {
        assertThrows(DomainBadRequestException.class,
            () -> new AgentVersionComment(1L, "u1", "   \n\t "));
    }

    @Test
    void rejectsBodyOver8192Chars() {
        String body = "a".repeat(8193);
        assertThrows(DomainBadRequestException.class,
            () -> new AgentVersionComment(1L, "u1", body));
    }

    @Test
    void acceptsBodyAt8192Chars() {
        String body = "a".repeat(8192);
        AgentVersionComment c = new AgentVersionComment(1L, "u1", body);
        assertEquals(8192, c.getBody().length());
        assertFalse(c.isPinned());
        assertFalse(c.isDeleted());
        assertNull(c.getLastEditedAt());
    }

    @Test
    void editUpdatesBodyAndStampsLastEditedAt() {
        AgentVersionComment c = new AgentVersionComment(1L, "u1", "v1");
        c.edit("v2");
        assertEquals("v2", c.getBody());
        assertNotNull(c.getLastEditedAt());
    }

    @Test
    void editRejectsTooLongBody() {
        AgentVersionComment c = new AgentVersionComment(1L, "u1", "v1");
        assertThrows(DomainBadRequestException.class, () -> c.edit("a".repeat(8193)));
    }

    @Test
    void softDeleteSetsBothFields() {
        AgentVersionComment c = new AgentVersionComment(1L, "u1", "v1");
        c.softDelete("u2");
        assertTrue(c.isDeleted());
        assertEquals("u2", c.getDeletedBy());
        assertNotNull(c.getDeletedAt());
    }

    @Test
    void pinTogglesFlag() {
        AgentVersionComment c = new AgentVersionComment(1L, "u1", "v1");
        c.setPinned(true);
        assertTrue(c.isPinned());
        c.setPinned(false);
        assertFalse(c.isPinned());
    }
}

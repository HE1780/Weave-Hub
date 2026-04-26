package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillVersionCommentTest {

    @Test
    void rejectsEmptyBody() {
        assertThrows(DomainBadRequestException.class,
            () -> new SkillVersionComment(1L, "u1", ""));
    }

    @Test
    void rejectsBlankBody() {
        assertThrows(DomainBadRequestException.class,
            () -> new SkillVersionComment(1L, "u1", "   \n\t "));
    }

    @Test
    void rejectsBodyOver8192Chars() {
        String body = "a".repeat(8193);
        assertThrows(DomainBadRequestException.class,
            () -> new SkillVersionComment(1L, "u1", body));
    }

    @Test
    void acceptsBodyAt8192Chars() {
        String body = "a".repeat(8192);
        SkillVersionComment c = new SkillVersionComment(1L, "u1", body);
        assertEquals(8192, c.getBody().length());
        assertFalse(c.isPinned());
        assertFalse(c.isDeleted());
        assertNull(c.getLastEditedAt());
    }

    @Test
    void editUpdatesBodyAndStampsLastEditedAt() {
        SkillVersionComment c = new SkillVersionComment(1L, "u1", "v1");
        c.edit("v2");
        assertEquals("v2", c.getBody());
        assertNotNull(c.getLastEditedAt());
    }

    @Test
    void editRejectsTooLongBody() {
        SkillVersionComment c = new SkillVersionComment(1L, "u1", "v1");
        assertThrows(DomainBadRequestException.class, () -> c.edit("a".repeat(8193)));
    }

    @Test
    void softDeleteSetsBothFields() {
        SkillVersionComment c = new SkillVersionComment(1L, "u1", "v1");
        c.softDelete("u2");
        assertTrue(c.isDeleted());
        assertEquals("u2", c.getDeletedBy());
        assertNotNull(c.getDeletedAt());
    }

    @Test
    void pinTogglesFlag() {
        SkillVersionComment c = new SkillVersionComment(1L, "u1", "v1");
        c.setPinned(true);
        assertTrue(c.isPinned());
        c.setPinned(false);
        assertFalse(c.isPinned());
    }
}

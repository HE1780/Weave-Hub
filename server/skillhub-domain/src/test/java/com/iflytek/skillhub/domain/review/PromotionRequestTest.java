package com.iflytek.skillhub.domain.review;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PromotionRequestTest {

    @Test
    void forSkillSetsSkillFieldsAndType() {
        PromotionRequest r = PromotionRequest.forSkill(11L, 22L, 33L, "user-1");
        assertEquals(SourceType.SKILL, r.getSourceType());
        assertEquals(11L, r.getSourceSkillId());
        assertEquals(22L, r.getSourceVersionId());
        assertEquals(33L, r.getTargetNamespaceId());
        assertEquals("user-1", r.getSubmittedBy());
        assertNull(r.getSourceAgentId());
        assertNull(r.getSourceAgentVersionId());
    }

    @Test
    void forAgentSetsAgentFieldsAndType() {
        PromotionRequest r = PromotionRequest.forAgent(11L, 22L, 33L, "user-1");
        assertEquals(SourceType.AGENT, r.getSourceType());
        assertEquals(11L, r.getSourceAgentId());
        assertEquals(22L, r.getSourceAgentVersionId());
        assertEquals(33L, r.getTargetNamespaceId());
        assertEquals("user-1", r.getSubmittedBy());
        assertNull(r.getSourceSkillId());
        assertNull(r.getSourceVersionId());
    }

    @Test
    void setTargetEntityIdWritesSkillSlot() {
        PromotionRequest r = PromotionRequest.forSkill(1L, 2L, 3L, "u");
        r.setTargetEntityId(99L, SourceType.SKILL);
        assertEquals(99L, r.getTargetSkillId());
        assertNull(r.getTargetAgentId());
    }

    @Test
    void setTargetEntityIdWritesAgentSlot() {
        PromotionRequest r = PromotionRequest.forAgent(1L, 2L, 3L, "u");
        r.setTargetEntityId(99L, SourceType.AGENT);
        assertEquals(99L, r.getTargetAgentId());
        assertNull(r.getTargetSkillId());
    }

    @Test
    void legacyConstructorDefaultsToSkillSourceType() {
        PromotionRequest r = new PromotionRequest(11L, 22L, 33L, "user-1");
        assertEquals(SourceType.SKILL, r.getSourceType());
        assertEquals(11L, r.getSourceSkillId());
    }
}

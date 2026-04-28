package com.iflytek.skillhub.domain.review;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceTypeTest {
    @Test
    void enumValuesArePresent() {
        assertEquals(SourceType.SKILL, SourceType.valueOf("SKILL"));
        assertEquals(SourceType.AGENT, SourceType.valueOf("AGENT"));
        assertEquals(2, SourceType.values().length);
    }
}

package com.iflytek.skillhub.notification.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NotificationCategoryTest {
    @Test
    void commentCategoryExists() {
        assertNotNull(NotificationCategory.valueOf("COMMENT"));
    }
}

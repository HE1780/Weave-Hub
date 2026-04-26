package com.iflytek.skillhub.domain.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AgentTest {

    @Test
    void constructor_initializes_required_fields_with_defaults() {
        Agent agent = new Agent(1L, "my-agent", "My Agent", "user-1", AgentVisibility.PRIVATE);

        assertEquals(1L, agent.getNamespaceId());
        assertEquals("my-agent", agent.getSlug());
        assertEquals("My Agent", agent.getDisplayName());
        assertEquals("user-1", agent.getOwnerId());
        assertEquals(AgentVisibility.PRIVATE, agent.getVisibility());
        assertEquals(AgentStatus.ACTIVE, agent.getStatus());
        assertNotNull(agent.getCreatedAt());
        assertNotNull(agent.getUpdatedAt());
    }

    @Test
    void archive_flips_status_and_touches_updatedAt() throws InterruptedException {
        Agent agent = new Agent(1L, "a", "A", "u", AgentVisibility.PUBLIC);
        var before = agent.getUpdatedAt();
        Thread.sleep(2);

        agent.archive();

        assertEquals(AgentStatus.ARCHIVED, agent.getStatus());
        assertTrue(agent.getUpdatedAt().isAfter(before));
    }

    @Test
    void setDisplayName_updates_field_and_timestamp() throws InterruptedException {
        Agent agent = new Agent(1L, "a", "Original", "u", AgentVisibility.PUBLIC);
        var before = agent.getUpdatedAt();
        Thread.sleep(2);

        agent.setDisplayName("Renamed");

        assertEquals("Renamed", agent.getDisplayName());
        assertTrue(agent.getUpdatedAt().isAfter(before));
    }
}

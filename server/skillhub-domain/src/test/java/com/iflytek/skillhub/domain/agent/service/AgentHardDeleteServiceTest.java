package com.iflytek.skillhub.domain.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.social.AgentRatingRepository;
import com.iflytek.skillhub.domain.agent.social.AgentStarRepository;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentHardDeleteServiceTest {

    @Mock private AgentRepository agentRepository;
    @Mock private AgentVersionRepository agentVersionRepository;
    @Mock private AgentStarRepository agentStarRepository;
    @Mock private AgentRatingRepository agentRatingRepository;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private AgentHardDeleteService service;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Agent agent;

    @BeforeEach
    void setUp() throws Exception {
        // wire concrete ObjectMapper since @InjectMocks can't fill a non-mock dep
        Field f = AgentHardDeleteService.class.getDeclaredField("objectMapper");
        f.setAccessible(true);
        f.set(service, objectMapper);

        agent = new Agent(1L, "planner", "Planner", "owner-1", AgentVisibility.PUBLIC);
        Field idField = Agent.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(agent, 7L);
    }

    private AgentVersion version(long id, String key) throws Exception {
        AgentVersion v = new AgentVersion(7L, "1.0.0", "owner-1",
                "manifest", "soul", "wf", key, 100L);
        Field idField = AgentVersion.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(v, id);
        return v;
    }

    @Test
    void hardDelete_cascades_stars_ratings_versions_and_agent() throws Exception {
        when(agentVersionRepository.findByAgentIdOrderBySubmittedAtDesc(7L))
                .thenReturn(List.of(version(20L, "packages/7/20/bundle.zip")));

        service.hardDeleteAgent(agent, "owner-1", "1.2.3.4", "ua/1");

        verify(agentStarRepository).deleteByAgentId(7L);
        verify(agentRatingRepository).deleteByAgentId(7L);
        verify(agentVersionRepository).deleteByAgentId(7L);
        verify(agentRepository).delete(agent);
        verify(auditLogService).record(
                eq("owner-1"),
                eq("DELETE_AGENT_HARD"),
                eq("AGENT"),
                eq(7L),
                any(),
                eq("1.2.3.4"),
                eq("ua/1"),
                argThat(json -> json != null && json.contains("\"slug\":\"planner\""))
        );
    }

    @Test
    void hardDelete_with_no_versions_skips_storage_call() throws Exception {
        when(agentVersionRepository.findByAgentIdOrderBySubmittedAtDesc(7L)).thenReturn(List.of());

        service.hardDeleteAgent(agent, "owner-1", null, null);

        verify(agentStarRepository).deleteByAgentId(7L);
        verify(agentRatingRepository).deleteByAgentId(7L);
        verify(agentVersionRepository).deleteByAgentId(7L);
        verify(agentRepository).delete(agent);
        verify(objectStorageService, never()).deleteObjects(any());
    }

    @Test
    void hardDelete_with_blank_storage_keys_does_not_invoke_storage_delete() throws Exception {
        when(agentVersionRepository.findByAgentIdOrderBySubmittedAtDesc(7L))
                .thenReturn(List.of(version(20L, ""), version(21L, null)));

        service.hardDeleteAgent(agent, "owner-1", null, null);

        // No transaction synchronization in test → tries immediately, but with empty key list it short-circuits.
        verify(objectStorageService, never()).deleteObjects(any());
    }
}

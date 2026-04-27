package com.iflytek.skillhub.domain.agent.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentMetadata;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTask;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTaskRepository;
import com.iflytek.skillhub.domain.event.AgentPublishedEvent;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.PrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentPublishServiceTest {

    @Mock private AgentRepository agentRepository;
    @Mock private AgentVersionRepository agentVersionRepository;
    @Mock private AgentReviewTaskRepository agentReviewTaskRepository;
    @Mock private PrePublishValidator prePublishValidator;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private AgentPublishService service;

    private AgentMetadata metadata(String name, String version) {
        return new AgentMetadata(name, "desc", version,
                AgentMetadata.DEFAULT_SOUL_FILE,
                AgentMetadata.DEFAULT_WORKFLOW_FILE,
                List.of(), "", Map.of());
    }

    private void setId(Agent agent, long id) throws Exception {
        Field f = Agent.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(agent, id);
    }

    private void setId(AgentVersion v, long id) throws Exception {
        Field f = AgentVersion.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(v, id);
    }

    @BeforeEach
    void setUp() {
        // Make save() return its argument with no transformation (mimicking JPA pass-through).
        // lenient() because not every test exercises a save path before throwing.
        lenient().when(agentRepository.save(any())).thenAnswer(inv -> {
            Agent a = inv.getArgument(0);
            try { setId(a, 7L); } catch (Exception ignored) {}
            return a;
        });
        lenient().when(agentVersionRepository.save(any())).thenAnswer(inv -> {
            AgentVersion v = inv.getArgument(0);
            try { setId(v, 70L); } catch (Exception ignored) {}
            return v;
        });
        lenient().when(prePublishValidator.validateEntries(anyList(), anyString(), anyLong()))
                .thenReturn(ValidationResult.pass());
    }

    @Test
    void fresh_agent_with_PRIVATE_visibility_auto_publishes_and_fires_event() {
        when(agentRepository.findByNamespaceIdAndSlug(1L, "agent-a")).thenReturn(Optional.empty());
        when(agentVersionRepository.findByAgentIdAndVersion(eq(7L), eq("1.0.0")))
                .thenReturn(Optional.empty());

        AgentVersion result = service.publish(
                1L, metadata("agent-a", "1.0.0"),
                AgentVisibility.PRIVATE,
                List.of(),
                "manifest", "soul", "workflow",
                "ns-1/agents/agent-a/1.0.0/bundle.zip", 1024L,
                "owner-1");

        assertEquals(AgentVersionStatus.PUBLISHED, result.getStatus());
        assertNotNull(result.getPublishedAt());

        ArgumentCaptor<AgentPublishedEvent> ev = ArgumentCaptor.forClass(AgentPublishedEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertEquals("owner-1", ev.getValue().publisherId());

        // No review task created
        verify(agentReviewTaskRepository, never()).save(any());
    }

    @Test
    void fresh_agent_with_PUBLIC_visibility_goes_to_PENDING_REVIEW_and_creates_task() {
        when(agentRepository.findByNamespaceIdAndSlug(1L, "agent-b")).thenReturn(Optional.empty());
        when(agentVersionRepository.findByAgentIdAndVersion(eq(7L), eq("1.0.0")))
                .thenReturn(Optional.empty());

        AgentVersion result = service.publish(
                1L, metadata("agent-b", "1.0.0"),
                AgentVisibility.PUBLIC,
                List.of(),
                "m", "s", "w", "key", 1L, "owner-1");

        assertEquals(AgentVersionStatus.PENDING_REVIEW, result.getStatus());
        assertNull(result.getPublishedAt());

        ArgumentCaptor<AgentReviewTask> task = ArgumentCaptor.forClass(AgentReviewTask.class);
        verify(agentReviewTaskRepository).save(task.capture());
        assertEquals(1L, task.getValue().getNamespaceId());

        verify(eventPublisher, never()).publishEvent(any(AgentPublishedEvent.class));
    }

    @Test
    void existing_agent_owned_by_different_user_throws_Forbidden() throws Exception {
        Agent existing = new Agent(1L, "agent-a", "agent-a", "other-owner", AgentVisibility.PUBLIC);
        setId(existing, 7L);
        when(agentRepository.findByNamespaceIdAndSlug(1L, "agent-a")).thenReturn(Optional.of(existing));

        assertThrows(DomainForbiddenException.class, () -> service.publish(
                1L, metadata("agent-a", "1.0.0"),
                AgentVisibility.PUBLIC,
                List.of(),
                "m", "s", "w", "key", 1L, "intruder"));
    }

    @Test
    void duplicate_version_for_existing_agent_throws_BadRequest() throws Exception {
        Agent existing = new Agent(1L, "agent-a", "agent-a", "owner-1", AgentVisibility.PUBLIC);
        setId(existing, 7L);
        when(agentRepository.findByNamespaceIdAndSlug(1L, "agent-a")).thenReturn(Optional.of(existing));
        when(agentVersionRepository.findByAgentIdAndVersion(eq(7L), eq("1.0.0")))
                .thenReturn(Optional.of(mock(AgentVersion.class)));

        assertThrows(DomainBadRequestException.class, () -> service.publish(
                1L, metadata("agent-a", "1.0.0"),
                AgentVisibility.PUBLIC,
                List.of(),
                "m", "s", "w", "key", 1L, "owner-1"));
    }

    @Test
    void blank_publisher_throws_Forbidden() {
        assertThrows(DomainForbiddenException.class, () -> service.publish(
                1L, metadata("agent-a", "1.0.0"),
                AgentVisibility.PRIVATE,
                List.of(),
                "m", "s", "w", "key", 1L, ""));
    }

    @Test
    void missing_version_in_metadata_throws_BadRequest() {
        when(agentRepository.findByNamespaceIdAndSlug(any(), any())).thenReturn(Optional.empty());

        assertThrows(DomainBadRequestException.class, () -> service.publish(
                1L, metadata("agent-a", null),
                AgentVisibility.PRIVATE,
                List.of(),
                "m", "s", "w", "key", 1L, "owner-1"));
    }

    @Test
    void prepublish_validator_warning_aborts_publish() {
        when(prePublishValidator.validateEntries(anyList(), anyString(), anyLong()))
                .thenReturn(ValidationResult.warn(List.of(
                        "soul.md line 4 contains a value that looks like an API key.")));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () ->
                service.publish(
                        1L, metadata("agent-a", "1.0.0"),
                        AgentVisibility.PRIVATE,
                        List.of(new PackageEntry("soul.md", "x".getBytes(), 1, "text/markdown")),
                        "m", "s", "w", "key", 1L, "owner-1"));
        assertTrue(ex.getMessage().contains("API key"));

        verify(agentRepository, never()).save(any());
        verify(agentVersionRepository, never()).save(any());
    }

    @Test
    void prepublish_validator_failure_aborts_publish() {
        when(prePublishValidator.validateEntries(anyList(), anyString(), anyLong()))
                .thenReturn(ValidationResult.fail("schema invalid"));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () ->
                service.publish(
                        1L, metadata("agent-a", "1.0.0"),
                        AgentVisibility.PRIVATE,
                        List.of(),
                        "m", "s", "w", "key", 1L, "owner-1"));
        assertTrue(ex.getMessage().contains("schema invalid"));

        verify(agentRepository, never()).save(any());
    }
}

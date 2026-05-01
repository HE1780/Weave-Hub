package com.iflytek.skillhub.domain.agent.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentMetadata;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.scan.AgentSecurityScanService;
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
    @Mock private AgentSecurityScanService agentSecurityScanService;
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

    /**
     * Stub the scanner placeholder behavior — it pulls SCANNING -> UPLOADED on
     * the persisted version. The real placeholder is one line; we replicate it
     * here so we can independently verify reload behavior.
     */
    private void stubScanFlipsToUploaded() {
        doAnswer(inv -> {
            Long versionId = inv.getArgument(0);
            // findById is called both inside the scanner and after the scan in publish().
            // Look up the most-recently-saved version with this id and flip it.
            return null;
        }).when(agentSecurityScanService).triggerScan(anyLong(), anyString());
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

    /**
     * After publish() saves the SCANNING version and flushes, it calls
     * triggerScan and then findById. We stub findById to return a version that
     * has already been flipped to UPLOADED — mirroring what
     * AgentSecurityScanService.markScanPassed does in production.
     */
    private void stubFindByIdReturnsUploaded() {
        lenient().when(agentVersionRepository.findById(70L)).thenAnswer(inv -> {
            AgentVersion uploaded = new AgentVersion(7L, "1.0.0", "owner-1",
                    "m", "s", "w", "key", 1L);
            try { setId(uploaded, 70L); } catch (Exception ignored) {}
            uploaded.markScanPassed();
            return Optional.of(uploaded);
        });
    }

    @Test
    void fresh_agent_publish_lands_in_UPLOADED_after_scan_and_skips_event_when_not_forced() {
        when(agentRepository.findByNamespaceIdAndSlug(1L, "agent-a")).thenReturn(Optional.empty());
        when(agentVersionRepository.findByAgentIdAndVersion(eq(7L), eq("1.0.0")))
                .thenReturn(Optional.empty());
        stubFindByIdReturnsUploaded();

        AgentVersion result = service.publish(
                1L, metadata("agent-a", "1.0.0"),
                AgentVisibility.PRIVATE,
                List.of(),
                "manifest", "soul", "workflow",
                "ns-1/agents/agent-a/1.0.0/bundle.zip", 1024L,
                "owner-1",
                List.of(), false, false);

        assertEquals(AgentVersionStatus.UPLOADED, result.getStatus());
        assertNull(result.getPublishedAt());
        verify(agentSecurityScanService).triggerScan(eq(70L), eq("owner-1"));
        verify(eventPublisher, never()).publishEvent(any(AgentPublishedEvent.class));
    }

    @Test
    void forceAutoPublish_takes_UPLOADED_to_PUBLISHED_and_fires_event() {
        when(agentRepository.findByNamespaceIdAndSlug(1L, "agent-b")).thenReturn(Optional.empty());
        when(agentVersionRepository.findByAgentIdAndVersion(eq(7L), eq("1.0.0")))
                .thenReturn(Optional.empty());
        stubFindByIdReturnsUploaded();

        AgentVersion result = service.publish(
                1L, metadata("agent-b", "1.0.0"),
                AgentVisibility.PUBLIC,
                List.of(),
                "m", "s", "w", "key", 1L, "super-1",
                List.of(), false, true);

        assertEquals(AgentVersionStatus.PUBLISHED, result.getStatus());
        assertNotNull(result.getPublishedAt());

        ArgumentCaptor<AgentPublishedEvent> ev = ArgumentCaptor.forClass(AgentPublishedEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertEquals("super-1", ev.getValue().publisherId());
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
                "m", "s", "w", "key", 1L, "intruder",
                List.of(), false, false));
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
                "m", "s", "w", "key", 1L, "owner-1",
                List.of(), false, false));
    }

    @Test
    void blank_publisher_throws_Forbidden() {
        assertThrows(DomainForbiddenException.class, () -> service.publish(
                1L, metadata("agent-a", "1.0.0"),
                AgentVisibility.PRIVATE,
                List.of(),
                "m", "s", "w", "key", 1L, "",
                List.of(), false, false));
    }

    @Test
    void missing_version_in_metadata_throws_BadRequest() {
        when(agentRepository.findByNamespaceIdAndSlug(any(), any())).thenReturn(Optional.empty());

        assertThrows(DomainBadRequestException.class, () -> service.publish(
                1L, metadata("agent-a", null),
                AgentVisibility.PRIVATE,
                List.of(),
                "m", "s", "w", "key", 1L, "owner-1",
                List.of(), false, false));
    }

    @Test
    void prepublish_validator_warning_aborts_publish_without_confirmWarnings() {
        when(prePublishValidator.validateEntries(anyList(), anyString(), anyLong()))
                .thenReturn(ValidationResult.warn(List.of(
                        "soul.md line 4 contains a value that looks like an API key.")));

        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () ->
                service.publish(
                        1L, metadata("agent-a", "1.0.0"),
                        AgentVisibility.PRIVATE,
                        List.of(new PackageEntry("soul.md", "x".getBytes(), 1, "text/markdown")),
                        "m", "s", "w", "key", 1L, "owner-1",
                        List.of(), false, false));
        assertTrue(ex.getMessage().contains("API key"));
        assertTrue(ex.getMessage().contains("confirmWarnings=true"));

        verify(agentRepository, never()).save(any());
        verify(agentVersionRepository, never()).save(any());
        verify(agentSecurityScanService, never()).triggerScan(anyLong(), anyString());
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
                        "m", "s", "w", "key", 1L, "owner-1",
                        List.of(), false, false));
        assertTrue(ex.getMessage().contains("schema invalid"));

        verify(agentRepository, never()).save(any());
    }

    @Test
    void package_warnings_abort_publish_without_confirmWarnings() {
        DomainBadRequestException ex = assertThrows(DomainBadRequestException.class, () ->
                service.publish(
                        1L, metadata("agent-a", "1.0.0"),
                        AgentVisibility.PRIVATE,
                        List.of(),
                        "m", "s", "w", "key", 1L, "owner-1",
                        List.of("Disallowed file extension: extra.bin"), false, false));
        assertTrue(ex.getMessage().contains("Disallowed file extension"));
        assertTrue(ex.getMessage().contains("confirmWarnings=true"));

        verify(agentRepository, never()).save(any());
    }

    @Test
    void package_warnings_pass_through_with_confirmWarnings_true() {
        when(agentRepository.findByNamespaceIdAndSlug(1L, "agent-c")).thenReturn(Optional.empty());
        when(agentVersionRepository.findByAgentIdAndVersion(eq(7L), eq("1.0.0")))
                .thenReturn(Optional.empty());
        stubFindByIdReturnsUploaded();

        AgentVersion result = service.publish(
                1L, metadata("agent-c", "1.0.0"),
                AgentVisibility.PRIVATE,
                List.of(),
                "m", "s", "w", "key", 1L, "owner-1",
                List.of("Disallowed file extension: extra.bin"), true, false);

        assertEquals(AgentVersionStatus.UPLOADED, result.getStatus());
    }
}

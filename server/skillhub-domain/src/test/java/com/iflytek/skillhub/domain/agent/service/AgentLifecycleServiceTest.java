package com.iflytek.skillhub.domain.agent.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentStatus;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTaskRepository;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentLifecycleServiceTest {

    @Mock private AgentRepository agentRepository;
    @Mock private AgentService agentService;
    @Mock private AgentVersionRepository agentVersionRepository;
    @Mock private AgentReviewTaskRepository agentReviewTaskRepository;
    @Mock private ObjectStorageService objectStorageService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private AgentLifecycleService service;

    private Agent agent;

    private void setId(Agent a, long id) throws Exception {
        Field f = Agent.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(a, id);
    }

    @BeforeEach
    void setUp() throws Exception {
        agent = new Agent(1L, "agent-a", "Agent A", "owner-1", AgentVisibility.PUBLIC);
        setId(agent, 7L);
        lenient().when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void archive_by_owner_marks_archived() {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentService.canManageLifecycle(eq(agent), eq("owner-1"), anyMap())).thenReturn(true);

        Agent result = service.archive(7L, "owner-1", Map.of());

        assertEquals(AgentStatus.ARCHIVED, result.getStatus());
        verify(agentRepository).save(agent);
    }

    @Test
    void archive_by_namespace_admin_succeeds() {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentService.canManageLifecycle(eq(agent), eq("admin-2"), anyMap())).thenReturn(true);

        Agent result = service.archive(7L, "admin-2", Map.of(1L, NamespaceRole.ADMIN));

        assertEquals(AgentStatus.ARCHIVED, result.getStatus());
    }

    @Test
    void archive_by_namespace_owner_succeeds() {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentService.canManageLifecycle(eq(agent), eq("ns-owner"), anyMap())).thenReturn(true);

        Agent result = service.archive(7L, "ns-owner", Map.of(1L, NamespaceRole.OWNER));

        assertEquals(AgentStatus.ARCHIVED, result.getStatus());
    }

    @Test
    void archive_by_unrelated_member_throws_Forbidden() {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentService.canManageLifecycle(eq(agent), eq("stranger"), anyMap())).thenReturn(false);

        assertThrows(DomainForbiddenException.class, () ->
                service.archive(7L, "stranger", Map.of(1L, NamespaceRole.MEMBER)));
        verify(agentRepository, never()).save(any());
    }

    @Test
    void archive_with_null_roles_treats_as_empty() {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentService.canManageLifecycle(eq(agent), eq("stranger"), any())).thenReturn(false);

        assertThrows(DomainForbiddenException.class, () ->
                service.archive(7L, "stranger", null));
    }

    @Test
    void archive_unknown_id_throws_NotFound() {
        when(agentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () ->
                service.archive(99L, "owner-1", Map.of()));
    }

    @Test
    void unarchive_by_owner_returns_to_active() {
        agent.archive();
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentService.canManageLifecycle(eq(agent), eq("owner-1"), anyMap())).thenReturn(true);

        Agent result = service.unarchive(7L, "owner-1", Map.of());

        assertEquals(AgentStatus.ACTIVE, result.getStatus());
        verify(agentRepository).save(agent);
    }

    @Test
    void unarchive_by_unrelated_user_throws_Forbidden() {
        agent.archive();
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentService.canManageLifecycle(eq(agent), eq("stranger"), anyMap())).thenReturn(false);

        assertThrows(DomainForbiddenException.class, () ->
                service.unarchive(7L, "stranger", Map.of()));
    }

    @Test
    void withdrawReview_pending_version_returns_to_DRAFT_and_deletes_review_task() throws Exception {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentService.canManageLifecycle(eq(agent), eq("owner-1"), anyMap())).thenReturn(true);

        com.iflytek.skillhub.domain.agent.AgentVersion v =
                new com.iflytek.skillhub.domain.agent.AgentVersion(
                        7L, "1.0.0", "owner-1", "manifest", "soul", "wf", null, 100L);
        java.lang.reflect.Field statusField =
                com.iflytek.skillhub.domain.agent.AgentVersion.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(v, com.iflytek.skillhub.domain.agent.AgentVersionStatus.PENDING_REVIEW);

        when(agentVersionRepository.findByAgentIdAndVersion(7L, "1.0.0")).thenReturn(Optional.of(v));
        when(agentVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        com.iflytek.skillhub.domain.agent.review.AgentReviewTask task =
                new com.iflytek.skillhub.domain.agent.review.AgentReviewTask(99L, 1L, "owner-1");
        when(agentReviewTaskRepository.findByAgentVersionId(any())).thenReturn(Optional.of(task));

        com.iflytek.skillhub.domain.agent.AgentVersion result =
                service.withdrawReview(7L, "1.0.0", "owner-1", Map.of());

        assertEquals(com.iflytek.skillhub.domain.agent.AgentVersionStatus.DRAFT, result.getStatus());
        verify(agentReviewTaskRepository).delete(task);
    }

    @Test
    void withdrawReview_non_pending_version_throws_BadRequest() throws Exception {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentService.canManageLifecycle(eq(agent), eq("owner-1"), anyMap())).thenReturn(true);

        com.iflytek.skillhub.domain.agent.AgentVersion v =
                new com.iflytek.skillhub.domain.agent.AgentVersion(
                        7L, "1.0.0", "owner-1", "manifest", "soul", "wf", null, 100L);
        java.lang.reflect.Field statusField =
                com.iflytek.skillhub.domain.agent.AgentVersion.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(v, com.iflytek.skillhub.domain.agent.AgentVersionStatus.PUBLISHED);

        when(agentVersionRepository.findByAgentIdAndVersion(7L, "1.0.0")).thenReturn(Optional.of(v));

        assertThrows(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class, () ->
                service.withdrawReview(7L, "1.0.0", "owner-1", Map.of()));
    }

    @Test
    void rereleaseVersion_clones_published_source_to_new_version() throws Exception {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentService.canManageLifecycle(eq(agent), eq("owner-1"), anyMap())).thenReturn(true);

        com.iflytek.skillhub.domain.agent.AgentVersion source =
                new com.iflytek.skillhub.domain.agent.AgentVersion(
                        7L, "1.0.0", "owner-1",
                        "---\nname: agent-a\nversion: 1.0.0\n---\nbody",
                        "soul-content", "workflow-content",
                        "packages/7/20/bundle.zip", 1234L);
        java.lang.reflect.Field statusField =
                com.iflytek.skillhub.domain.agent.AgentVersion.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(source, com.iflytek.skillhub.domain.agent.AgentVersionStatus.PUBLISHED);

        when(agentVersionRepository.findByAgentIdAndVersion(7L, "1.0.0")).thenReturn(Optional.of(source));
        when(agentVersionRepository.findByAgentIdAndVersion(7L, "1.1.0")).thenReturn(Optional.empty());
        when(agentVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        com.iflytek.skillhub.domain.agent.AgentVersion result =
                service.rereleaseVersion(7L, "1.0.0", "1.1.0", "owner-1", Map.of());

        assertEquals("1.1.0", result.getVersion());
        // PUBLIC visibility → PENDING_REVIEW + new review task
        assertEquals(com.iflytek.skillhub.domain.agent.AgentVersionStatus.PENDING_REVIEW, result.getStatus());
        verify(agentReviewTaskRepository).save(any(com.iflytek.skillhub.domain.agent.review.AgentReviewTask.class));
        // manifest version field rewritten
        org.assertj.core.api.Assertions.assertThat(result.getManifestYaml()).contains("version: 1.1.0");
    }

    @Test
    void rereleaseVersion_target_collides_with_existing_throws_BadRequest() throws Exception {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentService.canManageLifecycle(eq(agent), eq("owner-1"), anyMap())).thenReturn(true);

        com.iflytek.skillhub.domain.agent.AgentVersion source =
                new com.iflytek.skillhub.domain.agent.AgentVersion(
                        7L, "1.0.0", "owner-1", "manifest", "soul", "wf", null, 100L);
        java.lang.reflect.Field statusField =
                com.iflytek.skillhub.domain.agent.AgentVersion.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(source, com.iflytek.skillhub.domain.agent.AgentVersionStatus.PUBLISHED);

        com.iflytek.skillhub.domain.agent.AgentVersion existing =
                new com.iflytek.skillhub.domain.agent.AgentVersion(
                        7L, "1.1.0", "owner-1", "manifest", "soul", "wf", null, 100L);

        when(agentVersionRepository.findByAgentIdAndVersion(7L, "1.0.0")).thenReturn(Optional.of(source));
        when(agentVersionRepository.findByAgentIdAndVersion(7L, "1.1.0")).thenReturn(Optional.of(existing));

        assertThrows(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class, () ->
                service.rereleaseVersion(7L, "1.0.0", "1.1.0", "owner-1", Map.of()));
    }

    @Test
    void rereleaseVersion_non_published_source_throws_BadRequest() throws Exception {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentService.canManageLifecycle(eq(agent), eq("owner-1"), anyMap())).thenReturn(true);

        com.iflytek.skillhub.domain.agent.AgentVersion source =
                new com.iflytek.skillhub.domain.agent.AgentVersion(
                        7L, "1.0.0", "owner-1", "manifest", "soul", "wf", null, 100L);
        // status = DRAFT by default

        when(agentVersionRepository.findByAgentIdAndVersion(7L, "1.0.0")).thenReturn(Optional.of(source));

        assertThrows(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class, () ->
                service.rereleaseVersion(7L, "1.0.0", "1.1.0", "owner-1", Map.of()));
    }

    @Test
    void deleteVersion_DRAFT_status_succeeds_and_removes_storage() throws Exception {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentService.canManageLifecycle(eq(agent), eq("owner-1"), anyMap())).thenReturn(true);

        com.iflytek.skillhub.domain.agent.AgentVersion v =
                new com.iflytek.skillhub.domain.agent.AgentVersion(
                        7L, "1.0.0", "owner-1", "manifest", "soul", "wf",
                        "packages/7/20/bundle.zip", 100L);
        // status defaults to DRAFT
        when(agentVersionRepository.findByAgentIdAndVersion(7L, "1.0.0")).thenReturn(Optional.of(v));

        com.iflytek.skillhub.domain.agent.AgentVersion result =
                service.deleteVersion(7L, "1.0.0", "owner-1", Map.of());

        assertEquals("1.0.0", result.getVersion());
        verify(agentVersionRepository).delete(v);
        // No active transaction in unit test → fires immediately with the captured key
        verify(objectStorageService).deleteObjects(any());
    }

    @Test
    void deleteVersion_PUBLISHED_status_throws_BadRequest() throws Exception {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentService.canManageLifecycle(eq(agent), eq("owner-1"), anyMap())).thenReturn(true);

        com.iflytek.skillhub.domain.agent.AgentVersion v =
                new com.iflytek.skillhub.domain.agent.AgentVersion(
                        7L, "1.0.0", "owner-1", "manifest", "soul", "wf", null, 100L);
        java.lang.reflect.Field statusField =
                com.iflytek.skillhub.domain.agent.AgentVersion.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(v, com.iflytek.skillhub.domain.agent.AgentVersionStatus.PUBLISHED);

        when(agentVersionRepository.findByAgentIdAndVersion(7L, "1.0.0")).thenReturn(Optional.of(v));

        assertThrows(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class, () ->
                service.deleteVersion(7L, "1.0.0", "owner-1", Map.of()));
        verify(agentVersionRepository, never()).delete(any());
    }

    @Test
    void deleteVersion_PENDING_REVIEW_status_throws_BadRequest_with_withdraw_hint() throws Exception {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentService.canManageLifecycle(eq(agent), eq("owner-1"), anyMap())).thenReturn(true);

        com.iflytek.skillhub.domain.agent.AgentVersion v =
                new com.iflytek.skillhub.domain.agent.AgentVersion(
                        7L, "1.0.0", "owner-1", "manifest", "soul", "wf", null, 100L);
        java.lang.reflect.Field statusField =
                com.iflytek.skillhub.domain.agent.AgentVersion.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(v, com.iflytek.skillhub.domain.agent.AgentVersionStatus.PENDING_REVIEW);

        when(agentVersionRepository.findByAgentIdAndVersion(7L, "1.0.0")).thenReturn(Optional.of(v));

        assertThrows(com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException.class, () ->
                service.deleteVersion(7L, "1.0.0", "owner-1", Map.of()));
    }

    @Test
    void deleteVersion_unknown_version_throws_NotFound() {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentService.canManageLifecycle(eq(agent), eq("owner-1"), anyMap())).thenReturn(true);
        when(agentVersionRepository.findByAgentIdAndVersion(7L, "9.9.9")).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class, () ->
                service.deleteVersion(7L, "9.9.9", "owner-1", Map.of()));
    }
}

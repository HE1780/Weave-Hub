package com.iflytek.skillhub.domain.agent.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTask;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTaskRepository;
import com.iflytek.skillhub.domain.event.AgentPublishedEvent;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentReviewSubmitServiceTest {

    @Mock private AgentRepository agentRepository;
    @Mock private AgentVersionRepository agentVersionRepository;
    @Mock private AgentReviewTaskRepository agentReviewTaskRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private AgentReviewSubmitService service;

    private Agent agent;
    private AgentVersion version;

    private void setId(Object target, String fieldName, long id) throws Exception {
        Field f = target.getClass().getDeclaredField(fieldName);
        f.setAccessible(true);
        f.set(target, id);
    }

    @BeforeEach
    void setUp() throws Exception {
        agent = new Agent(1L, "agent-a", "Agent A", "owner-1", AgentVisibility.PUBLIC);
        setId(agent, "id", 7L);
        version = new AgentVersion(7L, "1.0.0", "owner-1",
                "manifest", "soul", "workflow", "key", 1L);
        setId(version, "id", 70L);
        version.markScanPassed(); // SCANNING -> UPLOADED
        lenient().when(agentVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void submitForReview_owner_transitions_to_PENDING_REVIEW_and_creates_task() {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentVersionRepository.findById(70L)).thenReturn(Optional.of(version));

        AgentVersion result = service.submitForReview(
                7L, 70L, AgentVisibility.PUBLIC, "owner-1", Map.of());

        assertEquals(AgentVersionStatus.PENDING_REVIEW, result.getStatus());
        assertEquals(AgentVisibility.PUBLIC, result.getRequestedVisibility());
        ArgumentCaptor<AgentReviewTask> task = ArgumentCaptor.forClass(AgentReviewTask.class);
        verify(agentReviewTaskRepository).save(task.capture());
        assertEquals(1L, task.getValue().getNamespaceId());
        assertEquals("owner-1", task.getValue().getSubmittedBy());
    }

    @Test
    void submitForReview_namespace_admin_succeeds() {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentVersionRepository.findById(70L)).thenReturn(Optional.of(version));

        service.submitForReview(7L, 70L, AgentVisibility.NAMESPACE_ONLY,
                "admin-x", Map.of(1L, NamespaceRole.ADMIN));

        verify(agentReviewTaskRepository).save(any(AgentReviewTask.class));
    }

    @Test
    void submitForReview_unrelated_user_throws_Forbidden() {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));

        assertThrows(DomainForbiddenException.class, () -> service.submitForReview(
                7L, 70L, AgentVisibility.PUBLIC, "stranger", Map.of()));
        verify(agentReviewTaskRepository, never()).save(any());
    }

    @Test
    void submitForReview_throws_when_version_not_UPLOADED() throws Exception {
        Field statusField = AgentVersion.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(version, AgentVersionStatus.SCANNING);
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentVersionRepository.findById(70L)).thenReturn(Optional.of(version));

        assertThrows(DomainBadRequestException.class, () -> service.submitForReview(
                7L, 70L, AgentVisibility.PUBLIC, "owner-1", Map.of()));
    }

    @Test
    void submitForReview_throws_when_version_belongs_to_other_agent() throws Exception {
        AgentVersion stranger = new AgentVersion(99L, "1.0.0", "owner-1",
                "m", "s", "w", null, 1L);
        setId(stranger, "id", 70L);
        stranger.markScanPassed();
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));
        when(agentVersionRepository.findById(70L)).thenReturn(Optional.of(stranger));

        assertThrows(DomainBadRequestException.class, () -> service.submitForReview(
                7L, 70L, AgentVisibility.PUBLIC, "owner-1", Map.of()));
    }

    @Test
    void confirmPublish_PRIVATE_agent_transitions_to_PUBLISHED_and_fires_event() throws Exception {
        Agent privateAgent = new Agent(1L, "agent-p", "Agent P", "owner-1", AgentVisibility.PRIVATE);
        setId(privateAgent, "id", 7L);
        when(agentRepository.findById(7L)).thenReturn(Optional.of(privateAgent));
        when(agentVersionRepository.findById(70L)).thenReturn(Optional.of(version));

        AgentVersion result = service.confirmPublish(7L, 70L, "owner-1", Map.of());

        assertEquals(AgentVersionStatus.PUBLISHED, result.getStatus());
        assertNotNull(result.getPublishedAt());
        assertEquals(70L, privateAgent.getLatestVersionId());
        verify(eventPublisher).publishEvent(any(AgentPublishedEvent.class));
    }

    @Test
    void confirmPublish_throws_when_agent_not_PRIVATE() {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent)); // PUBLIC

        assertThrows(DomainBadRequestException.class,
                () -> service.confirmPublish(7L, 70L, "owner-1", Map.of()));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void confirmPublish_throws_when_version_not_UPLOADED() throws Exception {
        Agent privateAgent = new Agent(1L, "agent-p", "Agent P", "owner-1", AgentVisibility.PRIVATE);
        setId(privateAgent, "id", 7L);
        Field statusField = AgentVersion.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(version, AgentVersionStatus.SCANNING);
        when(agentRepository.findById(7L)).thenReturn(Optional.of(privateAgent));
        when(agentVersionRepository.findById(70L)).thenReturn(Optional.of(version));

        assertThrows(DomainBadRequestException.class,
                () -> service.confirmPublish(7L, 70L, "owner-1", Map.of()));
    }
}

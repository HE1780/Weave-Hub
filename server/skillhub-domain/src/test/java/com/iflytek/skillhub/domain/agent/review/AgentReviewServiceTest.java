package com.iflytek.skillhub.domain.agent.review;

import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import com.iflytek.skillhub.domain.event.AgentPublishedEvent;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentReviewServiceTest {

    @Mock private AgentReviewTaskRepository reviewTaskRepository;
    @Mock private AgentVersionRepository agentVersionRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private AgentReviewService service;

    private AgentReviewTask pendingTask() {
        return new AgentReviewTask(70L, 1L, "owner-1");
    }

    private AgentVersion pendingVersion() throws Exception {
        AgentVersion v = new AgentVersion(7L, "1.0.0", "owner-1",
                "manifest", "soul", "workflow", "key", 1L);
        Field idField = AgentVersion.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(v, 70L);
        Field statusField = AgentVersion.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(v, AgentVersionStatus.PENDING_REVIEW);
        return v;
    }

    @BeforeEach
    void setUp() {
        lenient().when(reviewTaskRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(agentVersionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void approve_flips_task_and_version_and_fires_event() throws Exception {
        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(pendingTask()));
        when(agentVersionRepository.findById(70L)).thenReturn(Optional.of(pendingVersion()));

        Map<Long, NamespaceRole> roles = Map.of(1L, NamespaceRole.ADMIN);
        AgentReviewTask result = service.approve(100L, "admin-1", "ok", roles, Set.of());

        assertEquals(AgentReviewTaskStatus.APPROVED, result.getStatus());
        assertEquals("admin-1", result.getReviewedBy());

        ArgumentCaptor<AgentPublishedEvent> ev = ArgumentCaptor.forClass(AgentPublishedEvent.class);
        verify(eventPublisher).publishEvent(ev.capture());
        assertEquals(7L, ev.getValue().agentId());
    }

    @Test
    void reject_flips_task_and_version_no_event() throws Exception {
        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(pendingTask()));
        when(agentVersionRepository.findById(70L)).thenReturn(Optional.of(pendingVersion()));

        Map<Long, NamespaceRole> roles = Map.of(1L, NamespaceRole.ADMIN);
        AgentReviewTask result = service.reject(100L, "admin-1", "wrong workflow", roles, Set.of());

        assertEquals(AgentReviewTaskStatus.REJECTED, result.getStatus());
        verify(eventPublisher, never()).publishEvent(any(AgentPublishedEvent.class));
    }

    @Test
    void approve_by_non_admin_member_throws_Forbidden() {
        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(pendingTask()));

        Map<Long, NamespaceRole> roles = Map.of(1L, NamespaceRole.MEMBER);
        assertThrows(DomainForbiddenException.class,
                () -> service.approve(100L, "user-x", "looks good", roles, Set.of()));
    }

    @Test
    void approve_missing_task_throws_NotFound() {
        when(reviewTaskRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class,
                () -> service.approve(999L, "admin-1", null,
                        Map.of(1L, NamespaceRole.ADMIN), Set.of()));
    }

    @Test
    void getById_blocks_non_reviewer() {
        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(pendingTask()));

        assertThrows(DomainForbiddenException.class,
                () -> service.getById(100L, Map.of(), Set.of()));
    }

    @Test
    void getById_allows_super_admin() {
        when(reviewTaskRepository.findById(100L)).thenReturn(Optional.of(pendingTask()));

        AgentReviewTask t = service.getById(100L, Map.of(), Set.of("SUPER_ADMIN"));
        assertNotNull(t);
    }
}

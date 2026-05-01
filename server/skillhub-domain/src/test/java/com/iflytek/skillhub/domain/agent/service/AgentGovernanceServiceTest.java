package com.iflytek.skillhub.domain.agent.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentGovernanceServiceTest {

    @Mock private AgentRepository agentRepository;
    @Mock private AgentLifecycleService agentLifecycleService;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private AgentGovernanceService service;

    private Agent agent;

    @BeforeEach
    void setUp() throws Exception {
        agent = new Agent(1L, "agent-a", "Agent A", "owner-1", AgentVisibility.PUBLIC);
        Field f = Agent.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(agent, 7L);
        lenient().when(agentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void hideAgent_sets_hidden_flag_with_metadata_and_writes_audit_log() {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));

        Agent result = service.hideAgent(7L, "admin-1", "1.2.3.4", "JUnit", "policy violation");

        assertTrue(result.isHidden());
        assertEquals("admin-1", result.getHiddenBy());
        assertEquals("policy violation", result.getHideReason());
        assertNotNull(result.getHiddenAt());
        verify(auditLogService).record(eq("admin-1"), eq("HIDE_AGENT"), eq("AGENT"), eq(7L),
                eq(null), eq("1.2.3.4"), eq("JUnit"), eq("{\"reason\":\"policy violation\"}"));
    }

    @Test
    void hideAgent_with_blank_reason_writes_null_audit_payload() {
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));

        service.hideAgent(7L, "admin-1", "1.2.3.4", "JUnit", "  ");

        verify(auditLogService).record(eq("admin-1"), eq("HIDE_AGENT"), eq("AGENT"), eq(7L),
                eq(null), eq("1.2.3.4"), eq("JUnit"), eq(null));
    }

    @Test
    void hideAgent_throws_when_agent_missing() {
        when(agentRepository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class,
                () -> service.hideAgent(7L, "admin-1", null, null, null));
        verify(auditLogService, never()).record(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void unhideAgent_clears_hidden_metadata_and_writes_audit_log() {
        agent.hide("admin-0", "old reason");
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent));

        Agent result = service.unhideAgent(7L, "admin-1", "1.2.3.4", "JUnit");

        assertFalse(result.isHidden());
        assertNull(result.getHiddenAt());
        assertNull(result.getHiddenBy());
        assertNull(result.getHideReason());
        verify(auditLogService).record(eq("admin-1"), eq("UNHIDE_AGENT"), eq("AGENT"), eq(7L),
                eq(null), eq("1.2.3.4"), eq("JUnit"), eq(null));
    }

    @Test
    void yankVersion_delegates_to_lifecycle_service() {
        AgentVersion expected = mock(AgentVersion.class);
        when(agentLifecycleService.yankVersion(eq(70L), eq("admin-1"), eq("1.2.3.4"), eq("JUnit"), eq("nope")))
                .thenReturn(expected);

        AgentVersion result = service.yankVersion(70L, "admin-1", "1.2.3.4", "JUnit", "nope");

        assertSame(expected, result);
        verify(agentLifecycleService).yankVersion(70L, "admin-1", "1.2.3.4", "JUnit", "nope");
    }
}

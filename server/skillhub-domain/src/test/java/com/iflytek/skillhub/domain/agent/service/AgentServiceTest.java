package com.iflytek.skillhub.domain.agent.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.AgentVisibilityChecker;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AgentServiceTest {

    @Mock
    private AgentRepository agentRepository;

    @Mock
    private AgentVersionRepository agentVersionRepository;

    @Mock
    private AgentVisibilityChecker visibilityChecker;

    @InjectMocks
    private AgentService service;

    private Agent agent;

    @BeforeEach
    void setUp() {
        agent = new Agent(1L, "my-agent", "My Agent", "owner-1", AgentVisibility.PRIVATE);
    }

    @Test
    void getByNamespaceAndSlug_returns_agent_when_visible() {
        when(agentRepository.findByNamespaceIdAndSlug(1L, "my-agent")).thenReturn(Optional.of(agent));
        when(visibilityChecker.canAccess(eq(agent), eq("owner-1"), any(), any())).thenReturn(true);

        Agent result = service.getByNamespaceAndSlug(1L, "my-agent", "owner-1", Map.of(), Set.of());

        assertSame(agent, result);
    }

    @Test
    void getByNamespaceAndSlug_throws_NotFound_when_not_visible() {
        when(agentRepository.findByNamespaceIdAndSlug(1L, "my-agent")).thenReturn(Optional.of(agent));
        when(visibilityChecker.canAccess(eq(agent), any(), any(), any())).thenReturn(false);

        assertThrows(DomainNotFoundException.class,
                () -> service.getByNamespaceAndSlug(1L, "my-agent", "stranger", Map.of(), Set.of()));
    }

    @Test
    void getByNamespaceAndSlug_throws_NotFound_when_missing() {
        when(agentRepository.findByNamespaceIdAndSlug(anyLong(), anyString())).thenReturn(Optional.empty());

        assertThrows(DomainNotFoundException.class,
                () -> service.getByNamespaceAndSlug(1L, "missing", "owner-1", Map.of(), Set.of()));
    }

    @Test
    void listVersions_returns_all_for_owner() {
        AgentVersion published = mock(AgentVersion.class);
        AgentVersion draft = mock(AgentVersion.class);
        when(agentVersionRepository.findByAgentIdOrderBySubmittedAtDesc(any()))
                .thenReturn(List.of(published, draft));

        List<AgentVersion> result = service.listVersions(agent, "owner-1", Map.of(), Set.of());

        assertEquals(2, result.size());
    }

    @Test
    void listVersions_filters_to_PUBLISHED_for_anonymous() {
        AgentVersion published = mock(AgentVersion.class);
        AgentVersion draft = mock(AgentVersion.class);
        when(published.getStatus()).thenReturn(AgentVersionStatus.PUBLISHED);
        when(draft.getStatus()).thenReturn(AgentVersionStatus.DRAFT);
        when(agentVersionRepository.findByAgentIdOrderBySubmittedAtDesc(any()))
                .thenReturn(List.of(published, draft));

        List<AgentVersion> result = service.listVersions(agent, null, Map.of(), Set.of());

        assertEquals(1, result.size());
        assertSame(published, result.get(0));
    }

    @Test
    void listVersions_returns_all_for_namespace_admin() {
        AgentVersion published = mock(AgentVersion.class);
        AgentVersion draft = mock(AgentVersion.class);
        when(agentVersionRepository.findByAgentIdOrderBySubmittedAtDesc(any()))
                .thenReturn(List.of(published, draft));

        Map<Long, NamespaceRole> roles = Map.of(1L, NamespaceRole.ADMIN);
        List<AgentVersion> result = service.listVersions(agent, "stranger", roles, Set.of());

        assertEquals(2, result.size());
    }
}

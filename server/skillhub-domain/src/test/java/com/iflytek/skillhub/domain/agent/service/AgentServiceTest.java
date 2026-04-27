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

import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

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

    @Test
    void searchPublic_returns_only_PUBLIC_for_anonymous_caller() {
        Agent publicAgent = new Agent(1L, "pub", "Pub", "owner-1", AgentVisibility.PUBLIC);
        Agent privateAgent = new Agent(1L, "prv", "Prv", "owner-1", AgentVisibility.PRIVATE);
        Pageable pageable = PageRequest.of(0, 20);
        when(agentRepository.searchPublic(any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(publicAgent, privateAgent), pageable, 2));
        when(visibilityChecker.canAccess(eq(publicAgent), isNull(), any(), any())).thenReturn(true);
        when(visibilityChecker.canAccess(eq(privateAgent), isNull(), any(), any())).thenReturn(false);

        var result = service.searchPublic(null, null, null, null, Map.of(), Set.of(), pageable);

        assertEquals(1, result.getContent().size());
        assertSame(publicAgent, result.getContent().get(0));
    }

    @Test
    void searchPublic_returns_PRIVATE_owned_by_caller() {
        Agent privateAgent = new Agent(1L, "prv", "Prv", "owner-1", AgentVisibility.PRIVATE);
        Pageable pageable = PageRequest.of(0, 20);
        when(agentRepository.searchPublic(any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(privateAgent), pageable, 1));
        when(visibilityChecker.canAccess(eq(privateAgent), eq("owner-1"), any(), any())).thenReturn(true);

        var result = service.searchPublic(null, null, null, "owner-1", Map.of(), Set.of(), pageable);

        assertEquals(1, result.getContent().size());
        assertSame(privateAgent, result.getContent().get(0));
    }

    @Test
    void searchPublic_returns_NAMESPACE_ONLY_for_namespace_member() {
        Agent nsAgent = new Agent(7L, "ns", "Ns", "other", AgentVisibility.NAMESPACE_ONLY);
        Pageable pageable = PageRequest.of(0, 20);
        Map<Long, NamespaceRole> roles = Map.of(7L, NamespaceRole.MEMBER);
        when(agentRepository.searchPublic(any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(nsAgent), pageable, 1));
        when(visibilityChecker.canAccess(eq(nsAgent), eq("alice"), eq(roles), any())).thenReturn(true);

        var result = service.searchPublic(null, null, null, "alice", roles, Set.of(), pageable);

        assertEquals(1, result.getContent().size());
        assertSame(nsAgent, result.getContent().get(0));
    }

    @Test
    void searchPublic_visibility_filter_PRIVATE_excludes_PUBLIC_items() {
        Agent publicAgent = new Agent(1L, "pub", "Pub", "owner-1", AgentVisibility.PUBLIC);
        Agent privateAgent = new Agent(1L, "prv", "Prv", "owner-1", AgentVisibility.PRIVATE);
        Pageable pageable = PageRequest.of(0, 20);
        when(agentRepository.searchPublic(any(), any(), eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(publicAgent, privateAgent), pageable, 2));
        when(visibilityChecker.canAccess(any(), eq("owner-1"), any(), any())).thenReturn(true);

        var result = service.searchPublic(
                null, null, AgentVisibility.PRIVATE, "owner-1", Map.of(), Set.of(), pageable);

        assertEquals(1, result.getContent().size());
        assertSame(privateAgent, result.getContent().get(0));
    }
}

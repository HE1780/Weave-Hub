package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.service.AgentService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AgentService agentService;
    @MockBean private NamespaceRepository namespaceRepository;

    private Namespace ns(long id, String slug) throws Exception {
        Namespace n = new Namespace(slug, slug, "system");
        Field f = Namespace.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(n, id);
        return n;
    }

    private Agent agent(long id, long namespaceId, String slug) throws Exception {
        Agent a = new Agent(namespaceId, slug, slug, "owner-1", AgentVisibility.PUBLIC);
        Field f = Agent.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(a, id);
        return a;
    }

    private AgentVersion version(long id, long agentId) throws Exception {
        AgentVersion v = new AgentVersion(agentId, "1.0.0", "owner-1",
                "manifest", "soul", "wf", "key", 1L);
        Field idField = AgentVersion.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(v, id);
        Field statusField = AgentVersion.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(v, AgentVersionStatus.PUBLISHED);
        return v;
    }

    @BeforeEach
    void setUp() throws Exception {
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns(1L, "global")));
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns(1L, "global")));
    }

    @Test
    void list_public_anonymous_returns_paged_response() throws Exception {
        when(agentService.searchPublic(isNull(), isNull(), isNull(), isNull(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(agent(7L, 1L, "agent-a")), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/web/agents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].slug").value("agent-a"))
                .andExpect(jsonPath("$.data.items[0].namespace").value("global"));
    }

    @Test
    void list_with_q_propagates_keyword_to_service() throws Exception {
        when(agentService.searchPublic(eq("hello"), isNull(), isNull(), isNull(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(agent(7L, 1L, "agent-a")), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/web/agents").param("q", "hello"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].slug").value("agent-a"));
    }

    @Test
    void list_with_namespace_slug_resolves_to_id_and_passes_to_service() throws Exception {
        when(namespaceRepository.findBySlug("team-x")).thenReturn(Optional.of(ns(42L, "team-x")));
        when(agentService.searchPublic(isNull(), eq(42L), isNull(), isNull(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(agent(7L, 42L, "agent-a")), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/web/agents").param("namespace", "team-x"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].slug").value("agent-a"));
    }

    @Test
    void list_with_unknown_namespace_returns_404() throws Exception {
        when(namespaceRepository.findBySlug("ghost")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/web/agents").param("namespace", "ghost"))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_with_visibility_PUBLIC_propagates_filter_to_service() throws Exception {
        when(agentService.searchPublic(isNull(), isNull(), eq(AgentVisibility.PUBLIC), isNull(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(agent(7L, 1L, "agent-a")), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/web/agents").param("visibility", "PUBLIC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].slug").value("agent-a"));
    }

    @Test
    void list_with_invalid_visibility_returns_400() throws Exception {
        mockMvc.perform(get("/api/web/agents").param("visibility", "BANANA"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getOne_anonymous_returns_agent() throws Exception {
        when(agentService.getByNamespaceAndSlug(eq(1L), eq("agent-a"), isNull(), any(), any()))
                .thenReturn(agent(7L, 1L, "agent-a"));

        mockMvc.perform(get("/api/web/agents/global/agent-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slug").value("agent-a"))
                .andExpect(jsonPath("$.data.visibility").value("PUBLIC"));
    }

    @Test
    void getOne_propagates_NotFound_when_agent_missing() throws Exception {
        when(agentService.getByNamespaceAndSlug(any(), any(), any(), any(), any()))
                .thenThrow(new DomainNotFoundException("Agent not found"));

        mockMvc.perform(get("/api/web/agents/global/missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_versions_returns_summary_projection() throws Exception {
        when(agentService.getByNamespaceAndSlug(eq(1L), eq("agent-a"), isNull(), any(), any()))
                .thenReturn(agent(7L, 1L, "agent-a"));
        when(agentService.listVersions(any(), isNull(), any(), any()))
                .thenReturn(List.of(version(70L, 7L)));

        mockMvc.perform(get("/api/web/agents/global/agent-a/versions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].version").value("1.0.0"))
                // summary projection should NOT include the heavy inline fields
                .andExpect(jsonPath("$.data[0].soulMd").doesNotExist())
                .andExpect(jsonPath("$.data[0].workflowYaml").doesNotExist());
    }

    @Test
    void get_specific_version_returns_full_projection() throws Exception {
        when(agentService.getByNamespaceAndSlug(eq(1L), eq("agent-a"), isNull(), any(), any()))
                .thenReturn(agent(7L, 1L, "agent-a"));
        when(agentService.listVersions(any(), isNull(), any(), any()))
                .thenReturn(List.of(version(70L, 7L)));

        mockMvc.perform(get("/api/web/agents/global/agent-a/versions/1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.soulMd").value("soul"))
                .andExpect(jsonPath("$.data.workflowYaml").value("wf"));
    }
}

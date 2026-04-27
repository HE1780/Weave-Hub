package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.service.AgentHardDeleteService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentDeleteControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AgentHardDeleteService agentHardDeleteService;
    @MockBean private AgentRepository agentRepository;
    @MockBean private NamespaceRepository namespaceRepository;
    @MockBean private NamespaceMemberRepository namespaceMemberRepository;

    private Namespace ns() throws Exception {
        Namespace n = new Namespace("global", "global", "system");
        Field f = Namespace.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(n, 1L);
        return n;
    }

    private Agent agent(String ownerId) throws Exception {
        Agent a = new Agent(1L, "planner", "Planner", ownerId, AgentVisibility.PUBLIC);
        Field f = Agent.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(a, 10L);
        return a;
    }

    private UsernamePasswordAuthenticationToken auth(String userId, Set<String> roles) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId, "tester", userId + "@example.com", "https://example.com/avatar.png",
                "github", roles
        );
        return new UsernamePasswordAuthenticationToken(
                principal, null, roles.stream()
                        .map(r -> (org.springframework.security.core.GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                        .toList()
        );
    }

    @BeforeEach
    void setUp() throws Exception {
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns()));
    }

    @Test
    void delete_agent_by_owner_returns_deleted_true() throws Exception {
        when(agentRepository.findByNamespaceIdAndSlug(1L, "planner"))
                .thenReturn(Optional.of(agent("owner-1")));

        mockMvc.perform(delete("/api/v1/agents/global/planner")
                        .with(authentication(auth("owner-1", Set.of())))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.agentId").value(10));

        verify(agentHardDeleteService).hardDeleteAgent(any(), eq("owner-1"), any(), any());
    }

    @Test
    void delete_agent_by_super_admin_succeeds() throws Exception {
        when(agentRepository.findByNamespaceIdAndSlug(1L, "planner"))
                .thenReturn(Optional.of(agent("owner-1")));

        mockMvc.perform(delete("/api/v1/agents/global/planner")
                        .with(authentication(auth("admin-7", Set.of("SUPER_ADMIN"))))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));

        verify(agentHardDeleteService).hardDeleteAgent(any(), eq("admin-7"), any(), any());
    }

    @Test
    void delete_agent_by_unrelated_user_returns_403() throws Exception {
        when(agentRepository.findByNamespaceIdAndSlug(1L, "planner"))
                .thenReturn(Optional.of(agent("owner-1")));

        mockMvc.perform(delete("/api/v1/agents/global/planner")
                        .with(authentication(auth("stranger", Set.of())))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        verify(agentHardDeleteService, never()).hardDeleteAgent(any(), any(), any(), any());
    }

    @Test
    void delete_unknown_agent_is_idempotent_and_returns_deleted_false() throws Exception {
        when(agentRepository.findByNamespaceIdAndSlug(1L, "ghost")).thenReturn(Optional.empty());

        mockMvc.perform(delete("/api/v1/agents/global/ghost")
                        .with(authentication(auth("anyone", Set.of())))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(false));

        verify(agentHardDeleteService, never()).hardDeleteAgent(any(), any(), any(), any());
    }

    @Test
    void delete_agent_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(delete("/api/v1/agents/global/planner").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void api_web_delete_agent_without_csrf_allows_session_auth() throws Exception {
        when(agentRepository.findByNamespaceIdAndSlug(1L, "planner"))
                .thenReturn(Optional.of(agent("owner-1")));

        mockMvc.perform(delete("/api/web/agents/global/planner")
                        .with(authentication(auth("owner-1", Set.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.deleted").value(true));
    }
}

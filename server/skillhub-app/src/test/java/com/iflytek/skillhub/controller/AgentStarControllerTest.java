package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.social.AgentStarService;
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

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentStarControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AgentStarService agentStarService;
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

    private Agent agent() throws Exception {
        Agent a = new Agent(1L, "planner", "Planner", "owner-1", AgentVisibility.PUBLIC);
        Field f = Agent.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(a, 10L);
        return a;
    }

    private UsernamePasswordAuthenticationToken auth() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-42", "tester", "tester@example.com", "https://example.com/avatar.png",
                "github", Set.of("SUPER_ADMIN")
        );
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
    }

    @BeforeEach
    void setUp() throws Exception {
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns()));
        when(agentRepository.findByNamespaceIdAndSlug(1L, "planner")).thenReturn(Optional.of(agent()));
    }

    @Test
    void star_agent_returns_envelope() throws Exception {
        mockMvc.perform(put("/api/v1/agents/global/planner/star")
                        .with(authentication(auth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(agentStarService).star(eq(10L), eq("user-42"));
    }

    @Test
    void unstar_agent_returns_envelope() throws Exception {
        mockMvc.perform(delete("/api/v1/agents/global/planner/star")
                        .with(authentication(auth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(agentStarService).unstar(eq(10L), eq("user-42"));
    }

    @Test
    void star_agent_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(put("/api/v1/agents/global/planner/star").with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void check_starred_returns_true() throws Exception {
        when(agentStarService.isStarred(eq(10L), eq("user-42"))).thenReturn(true);

        mockMvc.perform(get("/api/v1/agents/global/planner/star")
                        .with(authentication(auth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(true));
    }

    @Test
    void check_starred_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/agents/global/planner/star"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void api_web_star_agent_without_csrf_allows_session_auth() throws Exception {
        mockMvc.perform(put("/api/web/agents/global/planner/star")
                        .with(authentication(auth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(agentStarService).star(eq(10L), eq("user-42"));
    }
}

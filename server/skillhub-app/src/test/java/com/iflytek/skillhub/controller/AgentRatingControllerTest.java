package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.social.AgentRatingService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
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
class AgentRatingControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AgentRatingService agentRatingService;
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
    void rate_agent_returns_envelope() throws Exception {
        mockMvc.perform(put("/api/v1/agents/global/planner/rating")
                        .with(authentication(auth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\": 4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(agentRatingService).rate(eq(10L), eq("user-42"), eq((short) 4));
    }

    @Test
    void get_user_rating_returns_score() throws Exception {
        when(agentRatingService.getUserRating(eq(10L), eq("user-42")))
                .thenReturn(Optional.of((short) 4));

        mockMvc.perform(get("/api/v1/agents/global/planner/rating")
                        .with(authentication(auth()))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.score").value(4))
                .andExpect(jsonPath("$.data.rated").value(true));
    }

    @Test
    void rate_agent_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(put("/api/v1/agents/global/planner/rating")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"score\": 4}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void get_user_rating_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(get("/api/v1/agents/global/planner/rating"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}

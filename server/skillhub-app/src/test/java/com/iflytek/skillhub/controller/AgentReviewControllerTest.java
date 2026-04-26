package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.review.AgentReviewService;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTask;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTaskStatus;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentReviewControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AgentReviewService reviewService;
    @MockBean private AgentRepository agentRepository;
    @MockBean private AgentVersionRepository agentVersionRepository;
    @MockBean private NamespaceRepository namespaceRepository;

    private UsernamePasswordAuthenticationToken auth(String userId, Set<String> platformRoles) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId, userId, userId + "@example.com", null, "test", platformRoles);
        var authorities = platformRoles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return new UsernamePasswordAuthenticationToken(principal, "n/a", authorities);
    }

    private AgentReviewTask pending(long taskId) throws Exception {
        AgentReviewTask t = new AgentReviewTask(70L, 1L, "owner-1");
        Field f = AgentReviewTask.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(t, taskId);
        return t;
    }

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

    private MockHttpServletRequestBuilder withRoles(MockHttpServletRequestBuilder b,
                                                    Map<Long, NamespaceRole> roles) {
        return b.requestAttr("userNsRoles", roles);
    }

    @Test
    void list_returns_page_for_admin() throws Exception {
        when(reviewService.listForReviewer(eq(1L), eq(AgentReviewTaskStatus.PENDING), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(pending(100L)), PageRequest.of(0, 20), 1));

        mockMvc.perform(withRoles(get("/api/web/agents/reviews")
                        .param("namespaceId", "1"), Map.of(1L, NamespaceRole.ADMIN))
                        .with(authentication(auth("admin-1", Set.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value(100));
    }

    @Test
    void list_rejects_anonymous_with_401() throws Exception {
        mockMvc.perform(get("/api/web/agents/reviews").param("namespaceId", "1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void approve_happy_path_returns_200_and_status_APPROVED() throws Exception {
        AgentReviewTask approvedTask = pending(100L);
        approvedTask.approve("admin-1", "ok");
        when(reviewService.approve(eq(100L), eq("admin-1"), eq("ok"), any(), any()))
                .thenReturn(approvedTask);

        mockMvc.perform(withRoles(post("/api/web/agents/reviews/100/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"ok\"}"), Map.of(1L, NamespaceRole.ADMIN))
                        .with(authentication(auth("admin-1", Set.of())))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));
    }

    @Test
    void reject_happy_path_returns_200_and_status_REJECTED() throws Exception {
        AgentReviewTask rejectedTask = pending(100L);
        rejectedTask.reject("admin-1", "wrong");
        when(reviewService.reject(eq(100L), eq("admin-1"), eq("wrong"), any(), any()))
                .thenReturn(rejectedTask);

        mockMvc.perform(withRoles(post("/api/web/agents/reviews/100/reject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"wrong\"}"), Map.of(1L, NamespaceRole.ADMIN))
                        .with(authentication(auth("admin-1", Set.of())))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void getOne_returns_404_when_service_throws_NotFound() throws Exception {
        when(reviewService.getById(eq(999L), any(), any()))
                .thenThrow(new DomainNotFoundException("Review task not found"));

        mockMvc.perform(get("/api/web/agents/reviews/999")
                        .with(authentication(auth("admin-1", Set.of()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void approve_anonymously_returns_401() throws Exception {
        mockMvc.perform(post("/api/web/agents/reviews/100/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getDetail_returns_full_projection() throws Exception {
        when(reviewService.getById(eq(100L), any(), any())).thenReturn(pending(100L));
        when(agentVersionRepository.findById(70L)).thenReturn(Optional.of(version(70L, 7L)));
        when(agentRepository.findById(7L)).thenReturn(Optional.of(agent(7L, 1L, "agent-a")));
        when(namespaceRepository.findById(1L)).thenReturn(Optional.of(ns(1L, "global")));

        mockMvc.perform(get("/api/web/agents/reviews/100/detail")
                        .with(authentication(auth("admin-1", Set.of()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.task.id").value(100))
                .andExpect(jsonPath("$.data.agent.slug").value("agent-a"))
                .andExpect(jsonPath("$.data.agent.namespace").value("global"))
                .andExpect(jsonPath("$.data.version.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.version.soulMd").value("soul"))
                .andExpect(jsonPath("$.data.version.workflowYaml").value("wf"));
    }

    @Test
    void getDetail_returns_404_when_version_missing() throws Exception {
        when(reviewService.getById(eq(100L), any(), any())).thenReturn(pending(100L));
        when(agentVersionRepository.findById(70L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/web/agents/reviews/100/detail")
                        .with(authentication(auth("admin-1", Set.of()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDetail_returns_404_when_agent_missing() throws Exception {
        when(reviewService.getById(eq(100L), any(), any())).thenReturn(pending(100L));
        when(agentVersionRepository.findById(70L)).thenReturn(Optional.of(version(70L, 7L)));
        when(agentRepository.findById(7L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/web/agents/reviews/100/detail")
                        .with(authentication(auth("admin-1", Set.of()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void getDetail_anonymous_returns_401() throws Exception {
        mockMvc.perform(get("/api/web/agents/reviews/100/detail"))
                .andExpect(status().isUnauthorized());
    }
}

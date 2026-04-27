package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.service.AgentLifecycleService;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class AgentLifecycleControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AgentLifecycleService agentLifecycleService;
    @MockBean private NamespaceRepository namespaceRepository;
    @MockBean private AgentRepository agentRepository;
    @MockBean private AuditLogService auditLogService;
    @MockBean private DeviceAuthService deviceAuthService;

    private Namespace namespace(long id, String slug) {
        Namespace n = new Namespace(slug, slug, "owner");
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }

    private Agent agent(long id, long namespaceId, String slug, AgentStatus status) {
        Agent a = new Agent(namespaceId, slug, slug, "owner-1", AgentVisibility.PUBLIC);
        ReflectionTestUtils.setField(a, "id", id);
        ReflectionTestUtils.setField(a, "status", status);
        return a;
    }

    @Test
    void archiveAgent_returns_unified_envelope_and_records_audit() throws Exception {
        Namespace ns = namespace(1L, "global");
        Agent active = agent(7L, 1L, "agent-a", AgentStatus.ACTIVE);
        Agent archived = agent(7L, 1L, "agent-a", AgentStatus.ARCHIVED);

        given(namespaceRepository.findBySlug("global")).willReturn(Optional.of(ns));
        given(agentRepository.findByNamespaceIdAndSlug(1L, "agent-a")).willReturn(Optional.of(active));
        given(agentLifecycleService.archive(eq(7L), eq("usr_1"), anyMap())).willReturn(archived);

        mockMvc.perform(post("/api/web/agents/global/agent-a/archive")
                        .requestAttr("userId", "usr_1")
                        .requestAttr("userNsRoles", Map.of(1L, NamespaceRole.ADMIN))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"cleanup\"}")
                        .with(user("usr_1"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.agentId").value(7))
                .andExpect(jsonPath("$.data.action").value("ARCHIVE"))
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
    }

    @Test
    void unarchiveAgent_returns_unified_envelope() throws Exception {
        Namespace ns = namespace(1L, "global");
        Agent archived = agent(7L, 1L, "agent-a", AgentStatus.ARCHIVED);
        Agent restored = agent(7L, 1L, "agent-a", AgentStatus.ACTIVE);

        given(namespaceRepository.findBySlug("global")).willReturn(Optional.of(ns));
        given(agentRepository.findByNamespaceIdAndSlug(1L, "agent-a")).willReturn(Optional.of(archived));
        given(agentLifecycleService.unarchive(eq(7L), eq("usr_1"), anyMap())).willReturn(restored);

        mockMvc.perform(post("/api/web/agents/global/agent-a/unarchive")
                        .requestAttr("userId", "usr_1")
                        .requestAttr("userNsRoles", Map.of(1L, NamespaceRole.ADMIN))
                        .with(user("usr_1"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.agentId").value(7))
                .andExpect(jsonPath("$.data.action").value("UNARCHIVE"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void archiveAgent_returns_404_for_unknown_namespace() throws Exception {
        given(namespaceRepository.findBySlug("nope")).willReturn(Optional.empty());

        mockMvc.perform(post("/api/web/agents/nope/agent-a/archive")
                        .requestAttr("userId", "usr_1")
                        .with(user("usr_1"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void archiveAgent_returns_404_for_unknown_slug() throws Exception {
        Namespace ns = namespace(1L, "global");
        given(namespaceRepository.findBySlug("global")).willReturn(Optional.of(ns));
        given(agentRepository.findByNamespaceIdAndSlug(1L, "ghost")).willReturn(Optional.empty());

        mockMvc.perform(post("/api/web/agents/global/ghost/archive")
                        .requestAttr("userId", "usr_1")
                        .with(user("usr_1"))
                        .with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void archiveAgent_returns_403_when_caller_lacks_permission() throws Exception {
        Namespace ns = namespace(1L, "global");
        Agent active = agent(7L, 1L, "agent-a", AgentStatus.ACTIVE);

        given(namespaceRepository.findBySlug("global")).willReturn(Optional.of(ns));
        given(agentRepository.findByNamespaceIdAndSlug(1L, "agent-a")).willReturn(Optional.of(active));
        given(agentLifecycleService.archive(eq(7L), eq("stranger"), anyMap()))
                .willThrow(new DomainForbiddenException("error.agent.lifecycle.noPermission"));

        mockMvc.perform(post("/api/web/agents/global/agent-a/archive")
                        .requestAttr("userId", "stranger")
                        .with(user("stranger"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void archiveAgent_rejects_anonymous_with_401() throws Exception {
        mockMvc.perform(post("/api/web/agents/global/agent-a/archive")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void archiveAgent_accepts_at_prefixed_namespace_slug() throws Exception {
        Namespace ns = namespace(1L, "global");
        Agent active = agent(7L, 1L, "agent-a", AgentStatus.ACTIVE);
        Agent archived = agent(7L, 1L, "agent-a", AgentStatus.ARCHIVED);

        given(namespaceRepository.findBySlug("global")).willReturn(Optional.of(ns));
        given(agentRepository.findByNamespaceIdAndSlug(1L, "agent-a")).willReturn(Optional.of(active));
        given(agentLifecycleService.archive(eq(7L), eq("usr_1"), anyMap())).willReturn(archived);

        mockMvc.perform(post("/api/web/agents/@global/agent-a/archive")
                        .requestAttr("userId", "usr_1")
                        .with(user("usr_1"))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.action").value("ARCHIVE"));
    }
}

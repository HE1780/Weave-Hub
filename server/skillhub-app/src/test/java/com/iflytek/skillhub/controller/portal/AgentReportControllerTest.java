package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.report.AgentReport;
import com.iflytek.skillhub.domain.agent.report.AgentReportService;
import com.iflytek.skillhub.domain.agent.report.AgentReportStatus;
import com.iflytek.skillhub.domain.agent.service.AgentService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentReportControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private NamespaceRepository namespaceRepository;
    @MockBean private AgentService agentService;
    @MockBean private AgentReportService agentReportService;

    private Namespace namespace;
    private Agent agent;

    @BeforeEach
    void setUp() {
        namespace = new Namespace("global", "Global", "owner");
        ReflectionTestUtils.setField(namespace, "id", 1L);
        agent = new Agent(1L, "demo-agent", "demo-agent", "owner-1", AgentVisibility.PUBLIC);
        ReflectionTestUtils.setField(agent, "id", 10L);

        given(namespaceRepository.findBySlug("global")).willReturn(Optional.of(namespace));
        given(agentService.getByNamespaceAndSlug(eq(1L), eq("demo-agent"), any(), any(), any()))
                .willReturn(agent);
    }

    @Test
    void submitReport_returnsCreatedEnvelope() throws Exception {
        AgentReport report = new AgentReport(10L, 1L, "user-1", "Spam", "details");
        ReflectionTestUtils.setField(report, "id", 99L);
        ReflectionTestUtils.setField(report, "status", AgentReportStatus.PENDING);

        given(agentReportService.submitReport(eq(10L), eq("user-1"), eq("Spam"), eq("details"),
                nullable(String.class), nullable(String.class))).willReturn(report);

        mockMvc.perform(post("/api/web/agents/global/demo-agent/reports")
                        .with(authentication(auth("user-1")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Spam\",\"details\":\"details\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.reportId").value(99))
                .andExpect(jsonPath("$.data.status").value("PENDING"));
    }

    @Test
    void submitReport_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(post("/api/web/agents/global/demo-agent/reports")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Spam\"}"))
                .andExpect(status().isUnauthorized());
        verify(agentReportService, never()).submitReport(any(), any(), any(), any(), any(), any());
    }

    @Test
    void submitReport_unknown_namespace_returns_400() throws Exception {
        given(namespaceRepository.findBySlug("ghost")).willReturn(Optional.empty());

        mockMvc.perform(post("/api/web/agents/ghost/demo-agent/reports")
                        .with(authentication(auth("user-1")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Spam\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitReport_blank_reason_propagates_domain_400() throws Exception {
        given(agentReportService.submitReport(eq(10L), eq("user-1"), eq("  "),
                nullable(String.class), nullable(String.class), nullable(String.class)))
                .willThrow(new DomainBadRequestException("error.agent.report.reason.required"));

        mockMvc.perform(post("/api/web/agents/global/demo-agent/reports")
                        .with(authentication(auth("user-1")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitReport_self_report_propagates_domain_400() throws Exception {
        given(agentReportService.submitReport(eq(10L), eq("owner-1"), any(),
                nullable(String.class), nullable(String.class), nullable(String.class)))
                .willThrow(new DomainBadRequestException("error.agent.report.self"));

        mockMvc.perform(post("/api/web/agents/global/demo-agent/reports")
                        .with(authentication(auth("owner-1")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"trolling myself\"}"))
                .andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken auth(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId, userId, userId + "@example.com", null, "test", Set.of());
        return new UsernamePasswordAuthenticationToken(
                principal, "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}

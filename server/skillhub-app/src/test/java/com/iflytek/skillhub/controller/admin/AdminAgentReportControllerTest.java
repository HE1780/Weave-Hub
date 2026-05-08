package com.iflytek.skillhub.controller.admin;

import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.TestRedisConfig;
import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.agent.report.AgentReport;
import com.iflytek.skillhub.domain.agent.report.AgentReportDisposition;
import com.iflytek.skillhub.domain.agent.report.AgentReportService;
import com.iflytek.skillhub.domain.agent.report.AgentReportStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.dto.AdminAgentReportSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.AdminAgentReportAppService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestRedisConfig.class)
class AdminAgentReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminAgentReportAppService adminAgentReportAppService;

    @MockBean
    private AgentReportService agentReportService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    @Test
    void listReports_returnsPagedReports() throws Exception {
        when(adminAgentReportAppService.listReports("PENDING", 0, 20))
                .thenReturn(new PageResponse<>(
                        List.of(new AdminAgentReportSummaryResponse(
                                99L,
                                10L,
                                "global",
                                "demo-agent",
                                "Demo Agent",
                                "user-1",
                                "Spam",
                                "details",
                                "PENDING",
                                null,
                                null,
                                Instant.parse("2026-04-29T12:00:00Z"),
                                null
                        )),
                        1,
                        0,
                        20
                ));

        mockMvc.perform(get("/api/v1/admin/agent-reports")
                        .param("status", "PENDING")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items[0].id").value(99))
                .andExpect(jsonPath("$.data.items[0].agentSlug").value("demo-agent"))
                .andExpect(jsonPath("$.data.items[0].createdAt").value("2026-04-29T12:00:00Z"));
    }

    @Test
    void resolveReport_returnsUpdatedEnvelope() throws Exception {
        AgentReport report = new AgentReport(10L, 1L, "user-1", "Spam", "details");
        ReflectionTestUtils.setField(report, "id", 99L);
        report.setStatus(AgentReportStatus.RESOLVED);
        when(agentReportService.resolveReport(
                org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.eq("admin"),
                org.mockito.ArgumentMatchers.eq(AgentReportDisposition.RESOLVE_AND_ARCHIVE),
                org.mockito.ArgumentMatchers.eq("handled"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(report);

        mockMvc.perform(post("/api/v1/admin/agent-reports/99/resolve")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"comment\":\"handled\",\"disposition\":\"RESOLVE_AND_ARCHIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(99))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    @Test
    void dismissReport_returnsUpdatedEnvelope() throws Exception {
        AgentReport report = new AgentReport(10L, 1L, "user-1", "Spam", "details");
        ReflectionTestUtils.setField(report, "id", 99L);
        report.setStatus(AgentReportStatus.DISMISSED);
        when(agentReportService.dismissReport(
                org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.eq("admin"),
                org.mockito.ArgumentMatchers.eq("nope"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(report);

        mockMvc.perform(post("/api/v1/admin/agent-reports/99/dismiss")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"comment\":\"nope\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(99))
                .andExpect(jsonPath("$.data.status").value("DISMISSED"));
    }

    @Test
    void listReports_withAuditorRole_returns403() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "auditor", "auditor", "auditor@example.com", "", "github", Set.of("AUDITOR")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_AUDITOR"))
        );

        mockMvc.perform(get("/api/v1/admin/agent-reports")
                        .param("status", "PENDING")
                        .with(authentication(auth)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void listReports_unauthorizedAnonymous() throws Exception {
        mockMvc.perform(get("/api/v1/admin/agent-reports")
                        .param("status", "PENDING"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resolveReport_notFoundReturns400() throws Exception {
        when(agentReportService.resolveReport(
                org.mockito.ArgumentMatchers.eq(404L),
                org.mockito.ArgumentMatchers.eq("admin"),
                org.mockito.ArgumentMatchers.eq(AgentReportDisposition.RESOLVE_AND_ARCHIVE),
                org.mockito.ArgumentMatchers.eq("handled"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenThrow(new DomainNotFoundException("error.agent.report.notFound", 404L));

        mockMvc.perform(post("/api/v1/admin/agent-reports/404/resolve")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"comment\":\"handled\",\"disposition\":\"RESOLVE_AND_ARCHIVE\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolveReport_forbiddenForNonAdmin() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "User", "user@example.com", "", "github", Set.of("USER")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        mockMvc.perform(post("/api/v1/admin/agent-reports/1/resolve")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"comment\":\"handled\",\"disposition\":\"RESOLVE_AND_ARCHIVE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void dismissReport_forbiddenForNonAdmin() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "user-1", "User", "user@example.com", "", "github", Set.of("USER")
        );
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );

        mockMvc.perform(post("/api/v1/admin/agent-reports/1/dismiss")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"comment\":\"nope\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    /**
     * ADR 0005: RESOLVE_AND_HIDE requires SUPER_ADMIN. SKILL_ADMIN should be
     * blocked even though it can hit the endpoint for other dispositions.
     */
    @Test
    void resolveReport_withHideDispositionAndSkillAdmin_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/admin/agent-reports/99/resolve")
                        .with(authentication(adminAuth()))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"comment\":\"handled\",\"disposition\":\"RESOLVE_AND_HIDE\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void resolveReport_withHideDispositionAndSuperAdmin_returns200() throws Exception {
        AgentReport report = new AgentReport(10L, 1L, "user-1", "Spam", "details");
        ReflectionTestUtils.setField(report, "id", 99L);
        report.setStatus(AgentReportStatus.RESOLVED);
        when(agentReportService.resolveReport(
                org.mockito.ArgumentMatchers.eq(99L),
                org.mockito.ArgumentMatchers.eq("super-admin"),
                org.mockito.ArgumentMatchers.eq(AgentReportDisposition.RESOLVE_AND_HIDE),
                org.mockito.ArgumentMatchers.eq("hide it"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(report);

        mockMvc.perform(post("/api/v1/admin/agent-reports/99/resolve")
                        .with(authentication(superAdminAuth()))
                        .with(csrf())
                        .contentType(APPLICATION_JSON)
                        .content("{\"comment\":\"hide it\",\"disposition\":\"RESOLVE_AND_HIDE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(99))
                .andExpect(jsonPath("$.data.status").value("RESOLVED"));
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "admin", "admin", "admin@example.com", "", "github", Set.of("SKILL_ADMIN")
        );
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_SKILL_ADMIN"))
        );
    }

    private UsernamePasswordAuthenticationToken superAdminAuth() {
        PlatformPrincipal principal = new PlatformPrincipal(
                "super-admin", "super-admin", "admin@example.com", "", "github", Set.of("SUPER_ADMIN")
        );
        return new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"))
        );
    }
}

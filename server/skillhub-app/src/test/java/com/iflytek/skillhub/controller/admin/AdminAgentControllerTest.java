package com.iflytek.skillhub.controller.admin;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.iflytek.skillhub.auth.device.DeviceAuthService;
import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.service.AgentGovernanceService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAgentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentGovernanceService agentGovernanceService;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @MockBean
    private DeviceAuthService deviceAuthService;

    private Agent buildAgent(long id) throws Exception {
        Agent agent = new Agent(1L, "demo", "Demo", "owner-1", AgentVisibility.PUBLIC);
        Field f = Agent.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(agent, id);
        return agent;
    }

    private AgentVersion buildVersion(long id, AgentVersionStatus status) throws Exception {
        AgentVersion v = new AgentVersion(7L, "1.0.0", "owner-1",
                "manifest", "soul", "workflow", "key", 1L);
        Field idField = AgentVersion.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(v, id);
        Field statusField = AgentVersion.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(v, status);
        return v;
    }

    @Test
    void hideAgent_returnsUpdatedResponse() throws Exception {
        Agent agent = buildAgent(10L);
        given(agentGovernanceService.hideAgent(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("admin"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("policy"))).willReturn(agent);

        PlatformPrincipal principal = new PlatformPrincipal("admin", "admin", "a@example.com", "",
                "github", Set.of("SUPER_ADMIN"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));

        mockMvc.perform(post("/api/v1/admin/agents/10/hide")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"policy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.agentId").value(10))
                .andExpect(jsonPath("$.data.action").value("HIDE"));
    }

    @Test
    void unhideAgent_returnsUpdatedResponse() throws Exception {
        Agent agent = buildAgent(10L);
        given(agentGovernanceService.unhideAgent(
                org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq("admin"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).willReturn(agent);

        PlatformPrincipal principal = new PlatformPrincipal("admin", "admin", "a@example.com", "",
                "github", Set.of("SUPER_ADMIN"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SUPER_ADMIN")));

        mockMvc.perform(post("/api/v1/admin/agents/10/unhide")
                        .with(authentication(auth))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.agentId").value(10))
                .andExpect(jsonPath("$.data.action").value("UNHIDE"));
    }

    @Test
    void yankVersion_returnsUpdatedResponse() throws Exception {
        AgentVersion version = buildVersion(33L, AgentVersionStatus.YANKED);
        given(agentGovernanceService.yankVersion(
                org.mockito.ArgumentMatchers.eq(33L),
                org.mockito.ArgumentMatchers.eq("admin"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("broken"))).willReturn(version);

        PlatformPrincipal principal = new PlatformPrincipal("admin", "admin", "a@example.com", "",
                "github", Set.of("SKILL_ADMIN"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SKILL_ADMIN")));

        mockMvc.perform(post("/api/v1/admin/agents/versions/33/yank")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"broken\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.versionId").value(33))
                .andExpect(jsonPath("$.data.action").value("YANK"))
                .andExpect(jsonPath("$.data.status").value("YANKED"));
    }

    @Test
    void hideAgent_withUserAdminRole_returns403() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal("admin", "admin", "a@example.com", "",
                "github", Set.of("USER_ADMIN"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER_ADMIN")));

        mockMvc.perform(post("/api/v1/admin/agents/10/hide")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"policy\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void hideAgent_withSkillAdminRole_returns403() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal("admin", "admin", "a@example.com", "",
                "github", Set.of("SKILL_ADMIN"));
        var auth = new UsernamePasswordAuthenticationToken(principal, null,
                List.of(new SimpleGrantedAuthority("ROLE_SKILL_ADMIN")));

        mockMvc.perform(post("/api/v1/admin/agents/10/hide")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"policy\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }
}

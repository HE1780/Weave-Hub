package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.agent.service.AgentDownloadService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentDownloadControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AgentDownloadService agentDownloadService;
    @MockBean private NamespaceMemberRepository namespaceMemberRepository;

    private AgentDownloadService.DownloadResult bundle(String filename, String body) {
        byte[] bytes = body.getBytes();
        return new AgentDownloadService.DownloadResult(
                () -> new ByteArrayInputStream(bytes),
                filename,
                bytes.length,
                "application/zip",
                null
        );
    }

    @Test
    void downloadLatest_anonymous_streamsBundle() throws Exception {
        given(agentDownloadService.downloadLatest(eq("global"), eq("planner"), isNull(), any()))
                .willReturn(bundle("Planner-1.0.0.zip", "hello"));

        mockMvc.perform(get("/api/web/agents/global/planner/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Planner-1.0.0.zip\""))
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(content().bytes("hello".getBytes()));
    }

    @Test
    void downloadVersion_authenticated_streamsBundle() throws Exception {
        given(agentDownloadService.downloadVersion(eq("global"), eq("planner"), eq("0.9.0"), eq("user-1"), any()))
                .willReturn(bundle("Planner-0.9.0.zip", "draft"));

        mockMvc.perform(get("/api/web/agents/global/planner/versions/0.9.0/download")
                        .with(authentication(auth("user-1"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Planner-0.9.0.zip\""));
    }

    @Test
    void downloadByTag_anonymous_streamsBundle() throws Exception {
        given(agentDownloadService.downloadByTag(eq("global"), eq("planner"), eq("stable"), isNull(), any()))
                .willReturn(bundle("Planner-1.2.0.zip", "tagged"));

        mockMvc.perform(get("/api/web/agents/global/planner/tags/stable/download"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"Planner-1.2.0.zip\""));
    }

    @Test
    void downloadVersion_forbidden_returns403() throws Exception {
        when(agentDownloadService.downloadVersion(any(), any(), any(), any(), any()))
                .thenThrow(new DomainForbiddenException("error.agent.access.denied"));

        mockMvc.perform(get("/api/web/agents/global/planner/versions/0.9.0/download")
                        .with(authentication(auth("user-x"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void downloadLatest_unavailable_returns400() throws Exception {
        when(agentDownloadService.downloadLatest(any(), any(), any(), any()))
                .thenThrow(new DomainBadRequestException("error.agent.version.latest.unavailable"));

        mockMvc.perform(get("/api/web/agents/global/planner/download"))
                .andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken auth(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId, userId, userId + "@example.com", null, "test", Set.of());
        return new UsernamePasswordAuthenticationToken(
                principal, "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}

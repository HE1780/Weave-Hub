package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.dto.AgentLabelDto;
import com.iflytek.skillhub.dto.MessageResponse;
import com.iflytek.skillhub.service.AgentLabelAppService;
import com.iflytek.skillhub.service.AuditRequestContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentLabelControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AgentLabelAppService agentLabelAppService;
    @MockBean private NamespaceMemberRepository namespaceMemberRepository;

    @Test
    void list_anonymous_returns_labels() throws Exception {
        given(agentLabelAppService.listAgentLabels(eq("global"), eq("planner"), isNull(), any()))
                .willReturn(List.of(new AgentLabelDto("ai-writing", "RECOMMENDED", "AI Writing")));

        mockMvc.perform(get("/api/web/agents/global/planner/labels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].slug").value("ai-writing"))
                .andExpect(jsonPath("$.data[0].type").value("RECOMMENDED"))
                .andExpect(jsonPath("$.data[0].displayName").value("AI Writing"));
    }

    @Test
    void attach_with_authenticated_user_returns_updated_envelope() throws Exception {
        given(agentLabelAppService.attachLabel(
                eq("global"), eq("planner"), eq("ai-writing"), eq("user-owner"), any(), any(AuditRequestContext.class)))
                .willReturn(new AgentLabelDto("ai-writing", "RECOMMENDED", "AI Writing"));

        mockMvc.perform(put("/api/web/agents/global/planner/labels/ai-writing")
                        .with(authentication(auth("user-owner")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.slug").value("ai-writing"));
    }

    @Test
    void attach_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(put("/api/web/agents/global/planner/labels/ai-writing").with(csrf()))
                .andExpect(status().isUnauthorized());
        verify(agentLabelAppService, never()).attachLabel(any(), any(), any(), any(), any(), any());
    }

    @Test
    void attach_forbidden_propagates_403() throws Exception {
        given(agentLabelAppService.attachLabel(
                any(), any(), any(), eq("user-other"), any(), any(AuditRequestContext.class)))
                .willThrow(new DomainForbiddenException("label.agent.no_permission"));

        mockMvc.perform(put("/api/web/agents/global/planner/labels/ai-writing")
                        .with(authentication(auth("user-other")))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void detach_returns_deleted_envelope() throws Exception {
        given(agentLabelAppService.detachLabel(
                eq("global"), eq("planner"), eq("ai-writing"), eq("user-owner"), any(), any(AuditRequestContext.class)))
                .willReturn(new MessageResponse("Label detached"));

        mockMvc.perform(delete("/api/web/agents/global/planner/labels/ai-writing")
                        .with(authentication(auth("user-owner")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void detach_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(delete("/api/web/agents/global/planner/labels/ai-writing").with(csrf()))
                .andExpect(status().isUnauthorized());
        verify(agentLabelAppService, never()).detachLabel(any(), any(), any(), any(), any(), any());
    }

    private UsernamePasswordAuthenticationToken auth(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId, userId, userId + "@example.com", null, "test", Set.of());
        return new UsernamePasswordAuthenticationToken(
                principal, "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}

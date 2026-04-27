package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.agent.AgentTag;
import com.iflytek.skillhub.domain.agent.service.AgentTagService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
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
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentTagControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private AgentTagService agentTagService;
    @MockBean private NamespaceMemberRepository namespaceMemberRepository;

    private AgentTag tag(long id, String name, long versionId) {
        AgentTag t = new AgentTag(7L, name, versionId, "admin-1");
        ReflectionTestUtils.setField(t, "id", id);
        return t;
    }

    @Test
    void list_anonymous_returns_tags() throws Exception {
        given(agentTagService.listTags(eq("global"), eq("planner"), isNull(), any()))
                .willReturn(List.of(tag(1L, "stable", 100L), tag(2L, "latest", 200L)));

        mockMvc.perform(get("/api/web/agents/global/planner/tags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].tagName").value("stable"))
                .andExpect(jsonPath("$.data[0].versionId").value(100))
                .andExpect(jsonPath("$.data[1].tagName").value("latest"));
    }

    @Test
    void create_with_admin_returns_updated_envelope() throws Exception {
        given(agentTagService.createOrMoveTag(eq("global"), eq("planner"), eq("stable"), eq("1.0.0"), eq("admin-1")))
                .willReturn(tag(99L, "stable", 200L));

        mockMvc.perform(put("/api/web/agents/global/planner/tags/stable")
                        .with(authentication(auth("admin-1")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagName\":\"stable\",\"targetVersion\":\"1.0.0\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tagName").value("stable"))
                .andExpect(jsonPath("$.data.versionId").value(200));
    }

    @Test
    void create_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(put("/api/web/agents/global/planner/tags/stable")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagName\":\"stable\",\"targetVersion\":\"1.0.0\"}"))
                .andExpect(status().isUnauthorized());
        verify(agentTagService, never()).createOrMoveTag(any(), any(), any(), any(), any());
    }

    @Test
    void create_latest_reserved_returns_400() throws Exception {
        given(agentTagService.createOrMoveTag(any(), any(), eq("latest"), any(), any()))
                .willThrow(new DomainBadRequestException("error.agent.tag.latest.reserved"));

        mockMvc.perform(put("/api/web/agents/global/planner/tags/latest")
                        .with(authentication(auth("admin-1")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagName\":\"latest\",\"targetVersion\":\"1.0.0\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_non_admin_returns_403() throws Exception {
        given(agentTagService.createOrMoveTag(any(), any(), any(), any(), eq("user-1")))
                .willThrow(new DomainForbiddenException("error.namespace.admin.required"));

        mockMvc.perform(put("/api/web/agents/global/planner/tags/stable")
                        .with(authentication(auth("user-1")))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tagName\":\"stable\",\"targetVersion\":\"1.0.0\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void delete_returns_deleted_envelope() throws Exception {
        doNothing().when(agentTagService).deleteTag("global", "planner", "stable", "admin-1");

        mockMvc.perform(delete("/api/web/agents/global/planner/tags/stable")
                        .with(authentication(auth("admin-1")))
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        verify(agentTagService).deleteTag("global", "planner", "stable", "admin-1");
    }

    @Test
    void delete_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(delete("/api/web/agents/global/planner/tags/stable")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        verify(agentTagService, never()).deleteTag(any(), any(), any(), any());
    }

    @Test
    void delete_propagates_404_when_tag_missing() throws Exception {
        doThrow(new DomainBadRequestException("error.agent.tag.notFound"))
                .when(agentTagService).deleteTag(any(), any(), eq("ghost"), any());

        mockMvc.perform(delete("/api/web/agents/global/planner/tags/ghost")
                        .with(authentication(auth("admin-1")))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    private UsernamePasswordAuthenticationToken auth(String userId) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId, userId, userId + "@example.com", null, "test", Set.of());
        return new UsernamePasswordAuthenticationToken(
                principal, "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }
}

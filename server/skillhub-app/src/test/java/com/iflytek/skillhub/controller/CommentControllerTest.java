package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.social.CommentPermissions;
import com.iflytek.skillhub.domain.social.SkillVersionComment;
import com.iflytek.skillhub.domain.social.SkillVersionCommentService;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
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

import java.util.List;
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
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SkillVersionCommentService service;

    @MockBean
    private UserAccountRepository userAccountRepository;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    private static UsernamePasswordAuthenticationToken aliceAuth() {
        PlatformPrincipal p = new PlatformPrincipal(
                "alice", "Alice", "alice@example.com", null, "github", Set.of());
        return new UsernamePasswordAuthenticationToken(p, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
    }

    private static UserAccount account(String id, String displayName, String avatarUrl) {
        return new UserAccount(id, displayName, id + "@example.com", avatarUrl);
    }

    @Test
    void edit_returns_updated_envelope() throws Exception {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v2");
        when(service.edit(eq(123L), eq("alice"), eq("v2")))
                .thenReturn(new SkillVersionCommentService.CommentWithPerms(c,
                        new CommentPermissions(true, true, false)));
        when(userAccountRepository.findById("alice"))
                .thenReturn(Optional.of(account("alice", "Alice", null)));

        mockMvc.perform(patch("/api/web/comments/123")
                        .with(authentication(aliceAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"v2\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.body").value("v2"))
                .andExpect(jsonPath("$.data.permissions.canEdit").value(true));
    }

    @Test
    void edit_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(patch("/api/web/comments/123")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"v2\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void edit_forbidden_when_service_throws_forbidden() throws Exception {
        when(service.edit(any(), any(), any()))
                .thenThrow(new DomainForbiddenException("error.comment.edit.forbidden"));

        mockMvc.perform(patch("/api/web/comments/123")
                        .with(authentication(aliceAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"v2\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void edit_invalid_body_returns_400() throws Exception {
        when(service.edit(any(), any(), any()))
                .thenThrow(new DomainBadRequestException("error.comment.body.empty"));

        mockMvc.perform(patch("/api/web/comments/123")
                        .with(authentication(aliceAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"x\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns_204() throws Exception {
        mockMvc.perform(delete("/api/web/comments/123")
                        .with(authentication(aliceAuth()))
                        .with(csrf()))
                .andExpect(status().isNoContent());

        verify(service).delete(123L, "alice");
    }

    @Test
    void delete_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(delete("/api/web/comments/123")
                        .with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void delete_forbidden_for_non_author_non_admin() throws Exception {
        doThrow(new DomainForbiddenException("error.comment.delete.forbidden"))
                .when(service).delete(any(), any());

        mockMvc.perform(delete("/api/web/comments/123")
                        .with(authentication(aliceAuth()))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void pin_returns_updated_envelope() throws Exception {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "hi");
        c.setPinned(true);
        when(service.setPinned(eq(123L), eq("alice"), eq(true)))
                .thenReturn(new SkillVersionCommentService.CommentWithPerms(c,
                        new CommentPermissions(true, true, true)));
        when(userAccountRepository.findById("alice"))
                .thenReturn(Optional.of(account("alice", "Alice", null)));

        mockMvc.perform(post("/api/web/comments/123/pin")
                        .with(authentication(aliceAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pinned\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pinned").value(true));
    }

    @Test
    void pin_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(post("/api/web/comments/123/pin")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pinned\": true}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void pin_returns_404_when_comment_deleted() throws Exception {
        when(service.setPinned(any(), any(), anyBoolean()))
                .thenThrow(new DomainNotFoundException("error.comment.notFound", 123L));

        mockMvc.perform(post("/api/web/comments/123/pin")
                        .with(authentication(aliceAuth()))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pinned\": true}"))
                .andExpect(status().isNotFound());
    }
}

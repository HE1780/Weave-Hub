package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.domain.agent.social.AgentVersionComment;
import com.iflytek.skillhub.domain.agent.social.AgentVersionCommentService;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.social.CommentPermissions;
import com.iflytek.skillhub.domain.user.UserAccount;
import com.iflytek.skillhub.domain.user.UserAccountRepository;
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

import java.util.List;
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
class AgentVersionCommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AgentVersionCommentService service;

    @MockBean
    private UserAccountRepository userAccountRepository;

    @MockBean
    private NamespaceMemberRepository namespaceMemberRepository;

    @Test
    void list_returns_page_anonymously() throws Exception {
        AgentVersionComment c = new AgentVersionComment(99L, "alice", "hi");
        when(service.listForVersion(eq(99L), isNull(), eq(Set.of()), any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(new AgentVersionCommentService.CommentWithPerms(c, CommentPermissions.NONE)),
                        PageRequest.of(0, 20), 1));
        when(userAccountRepository.findByIdIn(List.of("alice")))
                .thenReturn(List.of(account("alice", "Alice", "https://x/a.png")));

        mockMvc.perform(get("/api/web/agent-versions/99/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].body").value("hi"))
                .andExpect(jsonPath("$.data.content[0].author.userId").value("alice"))
                .andExpect(jsonPath("$.data.content[0].author.displayName").value("Alice"))
                .andExpect(jsonPath("$.data.content[0].author.avatarUrl").value("https://x/a.png"));
    }

    @Test
    void list_falls_back_to_author_id_when_lookup_misses() throws Exception {
        AgentVersionComment c = new AgentVersionComment(99L, "ghost", "hi");
        when(service.listForVersion(any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(
                        List.of(new AgentVersionCommentService.CommentWithPerms(c, CommentPermissions.NONE)),
                        PageRequest.of(0, 20), 1));
        when(userAccountRepository.findByIdIn(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/web/agent-versions/99/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].author.userId").value("ghost"))
                .andExpect(jsonPath("$.data.content[0].author.displayName").value("ghost"))
                .andExpect(jsonPath("$.data.content[0].author.avatarUrl").doesNotExist());
    }

    @Test
    void create_persists_with_authenticated_principal() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "alice", "Alice", "alice@example.com", null, "github", Set.of());
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        AgentVersionComment c = new AgentVersionComment(99L, "alice", "hello");
        when(service.post(eq(99L), eq("alice"), eq("hello")))
                .thenReturn(new AgentVersionCommentService.CommentWithPerms(c,
                        new CommentPermissions(true, true, false)));
        when(userAccountRepository.findByIdIn(List.of("alice")))
                .thenReturn(List.of(account("alice", "Alice", null)));

        mockMvc.perform(post("/api/web/agent-versions/99/comments")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"hello\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.body").value("hello"))
                .andExpect(jsonPath("$.data.permissions.canEdit").value(true))
                .andExpect(jsonPath("$.data.permissions.canDelete").value(true))
                .andExpect(jsonPath("$.data.permissions.canPin").value(false));

        verify(service).post(99L, "alice", "hello");
    }

    @Test
    void create_unauthenticated_returns_401() throws Exception {
        mockMvc.perform(post("/api/web/agent-versions/99/comments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"hello\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void create_with_invalid_body_returns_400() throws Exception {
        PlatformPrincipal principal = new PlatformPrincipal(
                "alice", "Alice", "alice@example.com", null, "github", Set.of());
        var auth = new UsernamePasswordAuthenticationToken(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_USER")));

        mockMvc.perform(post("/api/web/agent-versions/99/comments")
                        .with(authentication(auth))
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\": \"   \"}"))
                .andExpect(status().isBadRequest());
        // bean validation @NotBlank short-circuits before the service is called
        verify(service, never()).post(anyLong(), anyString(), anyString());
    }

    private static UserAccount account(String id, String displayName, String avatarUrl) {
        return new UserAccount(id, displayName, id + "@example.com", avatarUrl);
    }
}

package com.iflytek.skillhub.controller;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.support.SkillPackageArchiveExtractor;
import com.iflytek.skillhub.domain.agent.AgentMetadata;
import com.iflytek.skillhub.domain.agent.AgentPackageValidator;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.service.AgentPublishService;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AgentPublishControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockBean private AgentPublishService agentPublishService;
    @MockBean private AgentPackageValidator agentPackageValidator;
    @MockBean private SkillPackageArchiveExtractor archiveExtractor;
    @MockBean private NamespaceRepository namespaceRepository;
    @MockBean private NamespaceMemberRepository namespaceMemberRepository;
    @MockBean private ObjectStorageService objectStorageService;

    @BeforeEach
    void setUp() throws Exception {
        // Default: namespace exists.
        Namespace ns = new Namespace("global", "Global", "system");
        Field f = Namespace.class.getDeclaredField("id");
        f.setAccessible(true);
        f.set(ns, 1L);
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns));
    }

    private MockMultipartFile bundle() {
        return new MockMultipartFile(
                "file", "agent.zip", "application/zip", new byte[]{1, 2, 3});
    }

    private UsernamePasswordAuthenticationToken auth(String userId, Set<String> platformRoles) {
        PlatformPrincipal principal = new PlatformPrincipal(
                userId,
                userId,
                userId + "@example.com",
                null,
                "test",
                platformRoles);
        var authorities = platformRoles.stream()
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        return new UsernamePasswordAuthenticationToken(principal, "n/a", authorities);
    }

    private AgentMetadata fakeMetadata() {
        return new AgentMetadata("agent-a", "desc", "1.0.0",
                AgentMetadata.DEFAULT_SOUL_FILE,
                AgentMetadata.DEFAULT_WORKFLOW_FILE,
                List.of(), "", java.util.Map.of());
    }

    private AgentPackageValidator.ValidationResult okValidation() {
        return new AgentPackageValidator.ValidationResult(List.of(), fakeMetadata());
    }

    private List<PackageEntry> fakeEntries() {
        return List.of(
                new PackageEntry("AGENT.md", "---\nname: agent-a\ndescription: d\nversion: 1.0.0\n---\n".getBytes(), 50, "text/plain"),
                new PackageEntry("soul.md", "soul".getBytes(), 4, "text/plain"),
                new PackageEntry("workflow.yaml", "steps: []\n".getBytes(), 10, "text/plain")
        );
    }

    private AgentVersion fakePublishedVersion() throws Exception {
        AgentVersion v = new AgentVersion(
                7L, "1.0.0", "user-1",
                "manifest", "soul", "workflow",
                "agents/global/agent-a/1.0.0/bundle.zip", 3L);
        Field idField = AgentVersion.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(v, 70L);
        Field statusField = AgentVersion.class.getDeclaredField("status");
        statusField.setAccessible(true);
        statusField.set(v, AgentVersionStatus.PUBLISHED);
        return v;
    }

    @Test
    void publish_returns_201_for_authorized_member() throws Exception {
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(eq(1L), eq("user-1")))
                .thenReturn(Optional.of(mock(NamespaceMember.class)));
        when(archiveExtractor.extract(any())).thenReturn(fakeEntries());
        when(agentPackageValidator.validate(any())).thenReturn(okValidation());
        when(agentPublishService.publish(any(), any(), any(), any(), any(), any(), any(), anyLong(), any()))
                .thenReturn(fakePublishedVersion());

        mockMvc.perform(multipart("/api/web/agents/global/publish")
                        .file(bundle())
                        .param("visibility", "PRIVATE")
                        .with(authentication(auth("user-1", Set.of())))
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.slug").value("agent-a"))
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));
    }

    @Test
    void publish_rejects_anonymous_with_401() throws Exception {
        mockMvc.perform(multipart("/api/web/agents/global/publish")
                        .file(bundle())
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void publish_rejects_non_member_with_403() throws Exception {
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(eq(1L), eq("user-1")))
                .thenReturn(Optional.empty());

        mockMvc.perform(multipart("/api/web/agents/global/publish")
                        .file(bundle())
                        .param("visibility", "PRIVATE")
                        .with(authentication(auth("user-1", Set.of())))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void publish_rejects_invalid_package_with_400() throws Exception {
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(eq(1L), eq("user-1")))
                .thenReturn(Optional.of(mock(NamespaceMember.class)));
        when(archiveExtractor.extract(any())).thenReturn(fakeEntries());
        when(agentPackageValidator.validate(any()))
                .thenReturn(new AgentPackageValidator.ValidationResult(
                        List.of("missing AGENT.md"), null));

        mockMvc.perform(multipart("/api/web/agents/global/publish")
                        .file(bundle())
                        .param("visibility", "PRIVATE")
                        .with(authentication(auth("user-1", Set.of())))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void publish_rejects_unknown_namespace_with_400() throws Exception {
        when(namespaceRepository.findBySlug("nope")).thenReturn(Optional.empty());

        mockMvc.perform(multipart("/api/web/agents/nope/publish")
                        .file(bundle())
                        .param("visibility", "PRIVATE")
                        .with(authentication(auth("user-1", Set.of())))
                        .with(csrf()))
                .andExpect(status().isBadRequest());
    }
}

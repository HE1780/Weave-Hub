package com.iflytek.skillhub.domain.agent.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentTag;
import com.iflytek.skillhub.domain.agent.AgentTagRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.AgentVisibilityChecker;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentTagServiceTest {

    @Mock private NamespaceRepository namespaceRepository;
    @Mock private NamespaceMemberRepository namespaceMemberRepository;
    @Mock private AgentRepository agentRepository;
    @Mock private AgentVersionRepository agentVersionRepository;
    @Mock private AgentTagRepository agentTagRepository;
    @Mock private AgentVisibilityChecker visibilityChecker;

    private AgentTagService service;

    @BeforeEach
    void setUp() {
        service = new AgentTagService(
                namespaceRepository, namespaceMemberRepository, agentRepository,
                agentVersionRepository, agentTagRepository, visibilityChecker);
    }

    private Namespace ns(long id) {
        Namespace n = new Namespace("global", "global", "system");
        setField(n, "id", id);
        return n;
    }

    private Agent agent(long id, long namespaceId) {
        Agent a = new Agent(namespaceId, "planner", "planner", "owner-1", AgentVisibility.PUBLIC);
        setField(a, "id", id);
        return a;
    }

    private AgentVersion publishedVersion(long id, long agentId, String version) {
        AgentVersion v = new AgentVersion(agentId, version, "owner-1", "manifest", "soul", "wf", "key", 1L);
        setField(v, "id", id);
        setField(v, "status", AgentVersionStatus.PUBLISHED);
        return v;
    }

    @Test
    void listTags_returnsRealTagsPlusSyntheticLatest() {
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns(1L)));
        Agent agent = agent(7L, 1L);
        when(agentRepository.findByNamespaceIdAndSlug(1L, "planner")).thenReturn(Optional.of(agent));
        when(visibilityChecker.canAccess(any(), any(), any(), any())).thenReturn(true);
        AgentTag stable = new AgentTag(7L, "stable", 100L, "owner-1");
        when(agentTagRepository.findByAgentId(7L)).thenReturn(List.of(stable));
        AgentVersion published = publishedVersion(200L, 7L, "1.0.0");
        when(agentVersionRepository.findFirstByAgentIdAndStatusOrderByPublishedAtDesc(7L, AgentVersionStatus.PUBLISHED))
                .thenReturn(Optional.of(published));

        List<AgentTag> tags = service.listTags("global", "planner", "user-1", Map.of());

        assertThat(tags).hasSize(2);
        assertThat(tags.get(0).getTagName()).isEqualTo("stable");
        assertThat(tags.get(1).getTagName()).isEqualTo("latest");
        assertThat(tags.get(1).getVersionId()).isEqualTo(200L);
    }

    @Test
    void listTags_omitsLatestWhenNoPublishedVersion() {
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns(1L)));
        Agent agent = agent(7L, 1L);
        when(agentRepository.findByNamespaceIdAndSlug(1L, "planner")).thenReturn(Optional.of(agent));
        when(visibilityChecker.canAccess(any(), any(), any(), any())).thenReturn(true);
        when(agentTagRepository.findByAgentId(7L)).thenReturn(List.of());
        when(agentVersionRepository.findFirstByAgentIdAndStatusOrderByPublishedAtDesc(7L, AgentVersionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        List<AgentTag> tags = service.listTags("global", "planner", "user-1", Map.of());

        assertThat(tags).isEmpty();
    }

    @Test
    void listTags_throwsWhenAgentInvisible() {
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns(1L)));
        Agent agent = agent(7L, 1L);
        when(agentRepository.findByNamespaceIdAndSlug(1L, "planner")).thenReturn(Optional.of(agent));
        when(visibilityChecker.canAccess(any(), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.listTags("global", "planner", "stranger", Map.of()))
                .isInstanceOf(DomainNotFoundException.class);
    }

    @Test
    void createTag_persistsNewTagWhenNoneExists() {
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns(1L)));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "admin-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "admin-1", NamespaceRole.ADMIN)));
        Agent agent = agent(7L, 1L);
        when(agentRepository.findByNamespaceIdAndSlug(1L, "planner")).thenReturn(Optional.of(agent));
        when(visibilityChecker.canAccess(any(), any(), any(), any())).thenReturn(true);
        AgentVersion v = publishedVersion(200L, 7L, "1.0.0");
        when(agentVersionRepository.findByAgentIdAndVersion(7L, "1.0.0")).thenReturn(Optional.of(v));
        when(agentTagRepository.findByAgentIdAndTagName(7L, "stable")).thenReturn(Optional.empty());
        when(agentTagRepository.save(any(AgentTag.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentTag created = service.createOrMoveTag("global", "planner", "stable", "1.0.0", "admin-1");

        assertThat(created.getTagName()).isEqualTo("stable");
        assertThat(created.getVersionId()).isEqualTo(200L);
        assertThat(created.getCreatedBy()).isEqualTo("admin-1");
    }

    @Test
    void createTag_movesExistingTagWhenAlreadyPresent() {
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns(1L)));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "admin-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "admin-1", NamespaceRole.OWNER)));
        Agent agent = agent(7L, 1L);
        when(agentRepository.findByNamespaceIdAndSlug(1L, "planner")).thenReturn(Optional.of(agent));
        when(visibilityChecker.canAccess(any(), any(), any(), any())).thenReturn(true);
        AgentVersion newTarget = publishedVersion(300L, 7L, "1.1.0");
        when(agentVersionRepository.findByAgentIdAndVersion(7L, "1.1.0")).thenReturn(Optional.of(newTarget));
        AgentTag existing = new AgentTag(7L, "stable", 100L, "old-owner");
        when(agentTagRepository.findByAgentIdAndTagName(7L, "stable")).thenReturn(Optional.of(existing));
        when(agentTagRepository.save(any(AgentTag.class))).thenAnswer(inv -> inv.getArgument(0));

        AgentTag moved = service.createOrMoveTag("global", "planner", "stable", "1.1.0", "admin-1");

        assertThat(moved.getVersionId()).isEqualTo(300L);
        assertThat(moved.getCreatedBy()).isEqualTo("old-owner"); // history preserved
    }

    @Test
    void createTag_rejectsLatestReservedName() {
        assertThatThrownBy(() -> service.createOrMoveTag("global", "planner", "latest", "1.0.0", "admin-1"))
                .isInstanceOf(DomainBadRequestException.class);
        verify(agentTagRepository, never()).save(any());
    }

    @Test
    void createTag_requiresNamespaceAdmin() {
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns(1L)));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "user-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "user-1", NamespaceRole.MEMBER)));

        assertThatThrownBy(() -> service.createOrMoveTag("global", "planner", "stable", "1.0.0", "user-1"))
                .isInstanceOf(DomainForbiddenException.class);
        verify(agentTagRepository, never()).save(any());
    }

    @Test
    void createTag_rejectsNonPublishedTarget() {
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns(1L)));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "admin-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "admin-1", NamespaceRole.ADMIN)));
        Agent agent = agent(7L, 1L);
        when(agentRepository.findByNamespaceIdAndSlug(1L, "planner")).thenReturn(Optional.of(agent));
        when(visibilityChecker.canAccess(any(), any(), any(), any())).thenReturn(true);
        AgentVersion draft = new AgentVersion(7L, "1.0.0-draft", "owner-1", "m", "s", "w", "k", 1L);
        setField(draft, "id", 200L);
        // status defaults to DRAFT
        when(agentVersionRepository.findByAgentIdAndVersion(7L, "1.0.0-draft")).thenReturn(Optional.of(draft));

        assertThatThrownBy(() -> service.createOrMoveTag("global", "planner", "stable", "1.0.0-draft", "admin-1"))
                .isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void deleteTag_rejectsLatest() {
        assertThatThrownBy(() -> service.deleteTag("global", "planner", "latest", "admin-1"))
                .isInstanceOf(DomainBadRequestException.class);
        verify(agentTagRepository, never()).delete(any());
    }

    @Test
    void deleteTag_throwsWhenTagMissing() {
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns(1L)));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "admin-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "admin-1", NamespaceRole.ADMIN)));
        Agent agent = agent(7L, 1L);
        when(agentRepository.findByNamespaceIdAndSlug(1L, "planner")).thenReturn(Optional.of(agent));
        when(visibilityChecker.canAccess(any(), any(), any(), any())).thenReturn(true);
        when(agentTagRepository.findByAgentIdAndTagName(7L, "stable")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteTag("global", "planner", "stable", "admin-1"))
                .isInstanceOf(DomainBadRequestException.class);
    }

    @Test
    void deleteTag_succeedsForAdminWithExistingTag() {
        when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(ns(1L)));
        when(namespaceMemberRepository.findByNamespaceIdAndUserId(1L, "admin-1"))
                .thenReturn(Optional.of(new NamespaceMember(1L, "admin-1", NamespaceRole.ADMIN)));
        Agent agent = agent(7L, 1L);
        when(agentRepository.findByNamespaceIdAndSlug(1L, "planner")).thenReturn(Optional.of(agent));
        when(visibilityChecker.canAccess(any(), any(), any(), any())).thenReturn(true);
        AgentTag existing = new AgentTag(7L, "stable", 100L, "owner-1");
        when(agentTagRepository.findByAgentIdAndTagName(7L, "stable")).thenReturn(Optional.of(existing));

        service.deleteTag("global", "planner", "stable", "admin-1");

        verify(agentTagRepository).delete(existing);
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}

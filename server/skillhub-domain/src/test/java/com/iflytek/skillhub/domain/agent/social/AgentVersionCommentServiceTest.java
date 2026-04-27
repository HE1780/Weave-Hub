package com.iflytek.skillhub.domain.agent.social;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVisibilityChecker;
import com.iflytek.skillhub.domain.agent.social.event.AgentVersionCommentPostedEvent;
import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AgentVersionCommentServiceTest {

    private AgentVersionCommentRepository commentRepo;
    private AgentVersionRepository versionRepo;
    private AgentRepository agentRepo;
    private NamespaceMemberRepository memberRepo;
    private AgentVisibilityChecker visibilityChecker;
    private ApplicationEventPublisher events;
    private AgentVersionCommentService service;

    private AgentVersion version;
    private Agent agent;

    @BeforeEach
    void setUp() {
        commentRepo = mock(AgentVersionCommentRepository.class);
        versionRepo = mock(AgentVersionRepository.class);
        agentRepo = mock(AgentRepository.class);
        memberRepo = mock(NamespaceMemberRepository.class);
        visibilityChecker = mock(AgentVisibilityChecker.class);
        events = mock(ApplicationEventPublisher.class);
        service = new AgentVersionCommentService(
                commentRepo, versionRepo, agentRepo, memberRepo, visibilityChecker, events);

        version = mock(AgentVersion.class);
        when(version.getId()).thenReturn(99L);
        when(version.getAgentId()).thenReturn(7L);
        when(version.getSubmittedBy()).thenReturn("authorOfVersion");
        when(versionRepo.findById(99L)).thenReturn(Optional.of(version));

        agent = mock(Agent.class);
        when(agent.getNamespaceId()).thenReturn(42L);
        when(agentRepo.findById(7L)).thenReturn(Optional.of(agent));

        when(memberRepo.findByUserId(anyString())).thenReturn(List.of());
        when(visibilityChecker.canAccess(any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void listReturnsActiveCommentsWithPermissions() {
        AgentVersionComment c = new AgentVersionComment(99L, "alice", "hello");
        when(commentRepo.findActiveByVersionId(eq(99L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c), PageRequest.of(0, 20), 1));

        Page<AgentVersionCommentService.CommentWithPerms> page =
                service.listForVersion(99L, "alice", Set.of(), PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        AgentVersionCommentService.CommentWithPerms row = page.getContent().get(0);
        assertEquals("hello", row.comment().getBody());
        assertTrue(row.permissions().canEdit());
        assertTrue(row.permissions().canDelete());
        assertFalse(row.permissions().canPin());
    }

    @Test
    void listGivesAdminCanPinFlag() {
        AgentVersionComment c = new AgentVersionComment(99L, "alice", "hi");
        when(commentRepo.findActiveByVersionId(eq(99L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c), PageRequest.of(0, 20), 1));
        when(memberRepo.findByUserId("admin1"))
                .thenReturn(List.of(member(42L, "admin1", NamespaceRole.ADMIN)));

        Page<AgentVersionCommentService.CommentWithPerms> page =
                service.listForVersion(99L, "admin1", Set.of(), PageRequest.of(0, 20));

        assertTrue(page.getContent().get(0).permissions().canPin());
        assertTrue(page.getContent().get(0).permissions().canDelete());
        assertFalse(page.getContent().get(0).permissions().canEdit());
    }

    @Test
    void listForbiddenWhenVisibilityCheckFails() {
        when(visibilityChecker.canAccess(any(), any(), any(), any())).thenReturn(false);
        when(commentRepo.findActiveByVersionId(eq(99L), any(Pageable.class)))
                .thenReturn(Page.empty());
        assertThrows(DomainForbiddenException.class,
                () -> service.listForVersion(99L, "evil", Set.of(), PageRequest.of(0, 20)));
    }

    @Test
    void postPersistsAndPublishesEvent() {
        when(commentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        AgentVersionCommentService.CommentWithPerms result =
                service.post(99L, "alice", "hello");
        assertEquals("hello", result.comment().getBody());
        verify(events).publishEvent(any(AgentVersionCommentPostedEvent.class));
    }

    @Test
    void postRejectedWhenCannotSeeVersion() {
        when(visibilityChecker.canAccess(any(), any(), any(), any())).thenReturn(false);
        assertThrows(DomainForbiddenException.class, () -> service.post(99L, "evil", "hi"));
        verify(commentRepo, never()).save(any());
    }

    @Test
    void editAllowedForAuthor() {
        AgentVersionComment c = new AgentVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        when(commentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        AgentVersionCommentService.CommentWithPerms result = service.edit(123L, "alice", "v2");
        assertEquals("v2", result.comment().getBody());
        assertNotNull(result.comment().getLastEditedAt());
    }

    @Test
    void editForbiddenForNonAuthor() {
        AgentVersionComment c = new AgentVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        assertThrows(DomainForbiddenException.class, () -> service.edit(123L, "bob", "v2"));
    }

    @Test
    void editForbiddenAfterDelete() {
        AgentVersionComment c = new AgentVersionComment(99L, "alice", "v1");
        c.softDelete("alice");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        assertThrows(DomainNotFoundException.class, () -> service.edit(123L, "alice", "v2"));
    }

    @Test
    void deleteByAuthorSucceeds() {
        AgentVersionComment c = new AgentVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        when(commentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.delete(123L, "alice");
        assertTrue(c.isDeleted());
        assertEquals("alice", c.getDeletedBy());
    }

    @Test
    void deleteByAdminSucceedsAndRecordsModerator() {
        AgentVersionComment c = new AgentVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        when(commentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(memberRepo.findByUserId("mod1"))
                .thenReturn(List.of(member(42L, "mod1", NamespaceRole.OWNER)));

        service.delete(123L, "mod1");
        assertEquals("mod1", c.getDeletedBy());
        assertEquals("alice", c.getAuthorId());
    }

    @Test
    void deleteForbiddenForRandomUser() {
        AgentVersionComment c = new AgentVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        assertThrows(DomainForbiddenException.class, () -> service.delete(123L, "bob"));
    }

    @Test
    void deleteIsIdempotent() {
        AgentVersionComment c = new AgentVersionComment(99L, "alice", "v1");
        c.softDelete("alice");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        service.delete(123L, "alice");
        verify(commentRepo, never()).save(any());
    }

    @Test
    void pinRequiresAdmin() {
        AgentVersionComment c = new AgentVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        assertThrows(DomainForbiddenException.class,
                () -> service.setPinned(123L, "alice", true));
    }

    @Test
    void pinSucceedsForAdmin() {
        AgentVersionComment c = new AgentVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        when(commentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(memberRepo.findByUserId("admin1"))
                .thenReturn(List.of(member(42L, "admin1", NamespaceRole.ADMIN)));

        AgentVersionCommentService.CommentWithPerms result =
                service.setPinned(123L, "admin1", true);
        assertTrue(result.comment().isPinned());
    }

    @Test
    void pinDeletedReturnsNotFound() {
        AgentVersionComment c = new AgentVersionComment(99L, "alice", "v1");
        c.softDelete("alice");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        assertThrows(DomainNotFoundException.class, () -> service.setPinned(123L, "admin1", true));
    }

    private NamespaceMember member(Long namespaceId, String userId, NamespaceRole role) {
        return new NamespaceMember(namespaceId, userId, role);
    }
}

package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.social.event.SkillVersionCommentPostedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SkillVersionCommentServiceTest {

    private SkillVersionCommentRepository commentRepo;
    private SkillVersionRepository versionRepo;
    private SkillRepository skillRepo;
    private NamespaceMemberRepository memberRepo;
    private VisibilityChecker visibilityChecker;
    private ApplicationEventPublisher events;
    private SkillVersionCommentService service;

    private SkillVersion version;
    private Skill skill;

    @BeforeEach
    void setUp() {
        commentRepo = mock(SkillVersionCommentRepository.class);
        versionRepo = mock(SkillVersionRepository.class);
        skillRepo = mock(SkillRepository.class);
        memberRepo = mock(NamespaceMemberRepository.class);
        visibilityChecker = mock(VisibilityChecker.class);
        events = mock(ApplicationEventPublisher.class);
        service = new SkillVersionCommentService(
                commentRepo, versionRepo, skillRepo, memberRepo, visibilityChecker, events);

        version = mock(SkillVersion.class);
        when(version.getId()).thenReturn(99L);
        when(version.getSkillId()).thenReturn(7L);
        when(version.getCreatedBy()).thenReturn("authorOfVersion");
        when(versionRepo.findById(99L)).thenReturn(Optional.of(version));

        skill = mock(Skill.class);
        when(skill.getNamespaceId()).thenReturn(42L);
        when(skillRepo.findById(7L)).thenReturn(Optional.of(skill));

        when(memberRepo.findByUserId(anyString())).thenReturn(List.of());
        when(visibilityChecker.canAccess(any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void listReturnsActiveCommentsWithPermissions() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "hello");
        when(commentRepo.findActiveByVersionId(eq(99L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c), PageRequest.of(0, 20), 1));

        Page<SkillVersionCommentService.CommentWithPerms> page =
                service.listForVersion(99L, "alice", Set.of(), PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        SkillVersionCommentService.CommentWithPerms row = page.getContent().get(0);
        assertEquals("hello", row.comment().getBody());
        assertTrue(row.permissions().canEdit());      // author
        assertTrue(row.permissions().canDelete());    // author
        assertFalse(row.permissions().canPin());      // not admin
    }

    @Test
    void listGivesAdminCanPinFlag() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "hi");
        when(commentRepo.findActiveByVersionId(eq(99L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c), PageRequest.of(0, 20), 1));
        when(memberRepo.findByUserId("admin1"))
                .thenReturn(List.of(member(42L, "admin1", NamespaceRole.ADMIN)));

        Page<SkillVersionCommentService.CommentWithPerms> page =
                service.listForVersion(99L, "admin1", Set.of(), PageRequest.of(0, 20));

        assertTrue(page.getContent().get(0).permissions().canPin());
        assertTrue(page.getContent().get(0).permissions().canDelete());
        assertFalse(page.getContent().get(0).permissions().canEdit()); // not author
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
        SkillVersionCommentService.CommentWithPerms result =
                service.post(99L, "alice", "hello");
        assertEquals("hello", result.comment().getBody());
        verify(events).publishEvent(any(SkillVersionCommentPostedEvent.class));
    }

    @Test
    void postRejectedWhenCannotSeeVersion() {
        when(visibilityChecker.canAccess(any(), any(), any(), any())).thenReturn(false);
        assertThrows(DomainForbiddenException.class, () -> service.post(99L, "evil", "hi"));
        verify(commentRepo, never()).save(any());
    }

    @Test
    void editAllowedForAuthor() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        when(commentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SkillVersionCommentService.CommentWithPerms result = service.edit(123L, "alice", "v2");
        assertEquals("v2", result.comment().getBody());
        assertNotNull(result.comment().getLastEditedAt());
    }

    @Test
    void editForbiddenForNonAuthor() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        assertThrows(DomainForbiddenException.class, () -> service.edit(123L, "bob", "v2"));
    }

    @Test
    void editForbiddenAfterDelete() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        c.softDelete("alice");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        assertThrows(DomainNotFoundException.class, () -> service.edit(123L, "alice", "v2"));
    }

    @Test
    void deleteByAuthorSucceeds() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        when(commentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.delete(123L, "alice");
        assertTrue(c.isDeleted());
        assertEquals("alice", c.getDeletedBy());
    }

    @Test
    void deleteByAdminSucceedsAndRecordsModerator() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        when(commentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(memberRepo.findByUserId("mod1"))
                .thenReturn(List.of(member(42L, "mod1", NamespaceRole.OWNER)));

        service.delete(123L, "mod1");
        assertEquals("mod1", c.getDeletedBy());
        assertEquals("alice", c.getAuthorId()); // authorship preserved
    }

    @Test
    void deleteForbiddenForRandomUser() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        assertThrows(DomainForbiddenException.class, () -> service.delete(123L, "bob"));
    }

    @Test
    void deleteIsIdempotent() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        c.softDelete("alice");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        service.delete(123L, "alice");
        verify(commentRepo, never()).save(any());
    }

    @Test
    void pinRequiresAdmin() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        assertThrows(DomainForbiddenException.class,
                () -> service.setPinned(123L, "alice", true));
    }

    @Test
    void pinSucceedsForAdmin() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        when(commentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(memberRepo.findByUserId("admin1"))
                .thenReturn(List.of(member(42L, "admin1", NamespaceRole.ADMIN)));

        SkillVersionCommentService.CommentWithPerms result =
                service.setPinned(123L, "admin1", true);
        assertTrue(result.comment().isPinned());
    }

    @Test
    void pinDeletedReturnsNotFound() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        c.softDelete("alice");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        assertThrows(DomainNotFoundException.class, () -> service.setPinned(123L, "admin1", true));
    }

    private NamespaceMember member(Long namespaceId, String userId, NamespaceRole role) {
        return new NamespaceMember(namespaceId, userId, role);
    }
}

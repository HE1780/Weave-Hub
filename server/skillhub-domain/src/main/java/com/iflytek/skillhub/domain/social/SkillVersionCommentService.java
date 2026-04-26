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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SkillVersionCommentService {

    public record CommentWithPerms(SkillVersionComment comment, CommentPermissions permissions) {}

    private static final int EXCERPT_LENGTH = 200;

    private final SkillVersionCommentRepository commentRepo;
    private final SkillVersionRepository versionRepo;
    private final SkillRepository skillRepo;
    private final NamespaceMemberRepository memberRepo;
    private final VisibilityChecker visibilityChecker;
    private final ApplicationEventPublisher events;

    public SkillVersionCommentService(
            SkillVersionCommentRepository commentRepo,
            SkillVersionRepository versionRepo,
            SkillRepository skillRepo,
            NamespaceMemberRepository memberRepo,
            VisibilityChecker visibilityChecker,
            ApplicationEventPublisher events) {
        this.commentRepo = commentRepo;
        this.versionRepo = versionRepo;
        this.skillRepo = skillRepo;
        this.memberRepo = memberRepo;
        this.visibilityChecker = visibilityChecker;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<CommentWithPerms> listForVersion(
            Long versionId, String callerUserId, Set<String> platformRoles, Pageable pageable) {
        Skill skill = loadSkillForVersion(versionId);
        Map<Long, NamespaceRole> roles = loadCallerRoles(callerUserId);
        if (!visibilityChecker.canAccess(skill, callerUserId, roles, platformRoles)) {
            throw new DomainForbiddenException("error.comment.read.forbidden");
        }
        boolean isAdmin = isAdminOf(skill.getNamespaceId(), roles);
        return commentRepo.findActiveByVersionId(versionId, pageable)
                .map(c -> new CommentWithPerms(c, computePerms(c, callerUserId, isAdmin)));
    }

    @Transactional
    public CommentWithPerms post(Long versionId, String callerUserId, String body) {
        SkillVersion version = loadVersion(versionId);
        Skill skill = loadSkill(version.getSkillId());
        Map<Long, NamespaceRole> roles = loadCallerRoles(callerUserId);
        if (!visibilityChecker.canAccess(skill, callerUserId, roles, Set.of())) {
            throw new DomainForbiddenException("error.comment.post.forbidden");
        }
        SkillVersionComment saved = commentRepo.save(new SkillVersionComment(versionId, callerUserId, body));
        boolean isAdmin = isAdminOf(skill.getNamespaceId(), roles);

        events.publishEvent(new SkillVersionCommentPostedEvent(
                saved.getId(), versionId, callerUserId, excerpt(body)));

        return new CommentWithPerms(saved, computePerms(saved, callerUserId, isAdmin));
    }

    @Transactional
    public CommentWithPerms edit(Long commentId, String callerUserId, String newBody) {
        SkillVersionComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> new DomainNotFoundException("error.comment.notFound", commentId));
        if (c.isDeleted()) {
            throw new DomainNotFoundException("error.comment.notFound", commentId);
        }
        if (!callerUserId.equals(c.getAuthorId())) {
            throw new DomainForbiddenException("error.comment.edit.forbidden");
        }
        Skill skill = loadSkillForVersion(c.getSkillVersionId());
        boolean isAdmin = isAdminOf(skill.getNamespaceId(), loadCallerRoles(callerUserId));
        c.edit(newBody);
        SkillVersionComment saved = commentRepo.save(c);
        return new CommentWithPerms(saved, computePerms(saved, callerUserId, isAdmin));
    }

    @Transactional
    public void delete(Long commentId, String callerUserId) {
        SkillVersionComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> new DomainNotFoundException("error.comment.notFound", commentId));
        if (c.isDeleted()) {
            return;  // idempotent
        }
        Skill skill = loadSkillForVersion(c.getSkillVersionId());
        boolean isAuthor = callerUserId.equals(c.getAuthorId());
        boolean isAdmin = isAdminOf(skill.getNamespaceId(), loadCallerRoles(callerUserId));
        if (!isAuthor && !isAdmin) {
            throw new DomainForbiddenException("error.comment.delete.forbidden");
        }
        c.softDelete(callerUserId);
        commentRepo.save(c);
    }

    @Transactional
    public CommentWithPerms setPinned(Long commentId, String callerUserId, boolean pinned) {
        SkillVersionComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> new DomainNotFoundException("error.comment.notFound", commentId));
        if (c.isDeleted()) {
            throw new DomainNotFoundException("error.comment.notFound", commentId);
        }
        Skill skill = loadSkillForVersion(c.getSkillVersionId());
        Map<Long, NamespaceRole> roles = loadCallerRoles(callerUserId);
        if (!isAdminOf(skill.getNamespaceId(), roles)) {
            throw new DomainForbiddenException("error.comment.pin.forbidden");
        }
        c.setPinned(pinned);
        SkillVersionComment saved = commentRepo.save(c);
        return new CommentWithPerms(saved, computePerms(saved, callerUserId, true));
    }

    private CommentPermissions computePerms(SkillVersionComment c, String callerUserId, boolean isAdmin) {
        if (callerUserId == null) {
            return CommentPermissions.NONE;
        }
        boolean isAuthor = callerUserId.equals(c.getAuthorId());
        return new CommentPermissions(
                isAuthor && !c.isDeleted(),
                (isAuthor || isAdmin) && !c.isDeleted(),
                isAdmin && !c.isDeleted()
        );
    }

    private SkillVersion loadVersion(Long versionId) {
        return versionRepo.findById(versionId)
                .orElseThrow(() -> new DomainNotFoundException("error.skillVersion.notFound", versionId));
    }

    private Skill loadSkill(Long skillId) {
        return skillRepo.findById(skillId)
                .orElseThrow(() -> new DomainNotFoundException("error.skill.notFound", skillId));
    }

    private Skill loadSkillForVersion(Long versionId) {
        SkillVersion v = loadVersion(versionId);
        return loadSkill(v.getSkillId());
    }

    private Map<Long, NamespaceRole> loadCallerRoles(String callerUserId) {
        if (callerUserId == null) {
            return Map.of();
        }
        Map<Long, NamespaceRole> result = new HashMap<>();
        for (NamespaceMember m : memberRepo.findByUserId(callerUserId)) {
            result.put(m.getNamespaceId(), m.getRole());
        }
        return result;
    }

    private boolean isAdminOf(Long namespaceId, Map<Long, NamespaceRole> roles) {
        NamespaceRole r = roles.get(namespaceId);
        return r == NamespaceRole.OWNER || r == NamespaceRole.ADMIN;
    }

    private String excerpt(String body) {
        return body.length() <= EXCERPT_LENGTH ? body : body.substring(0, EXCERPT_LENGTH);
    }
}

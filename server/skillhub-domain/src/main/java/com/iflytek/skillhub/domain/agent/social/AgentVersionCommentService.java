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
import com.iflytek.skillhub.domain.social.CommentPermissions;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors {@code SkillVersionCommentService} for agent versions.
 *
 * <p>Permission rules (identical to skill side):
 * <ul>
 *   <li>Read — viewer must be able to access the agent (visibility check).</li>
 *   <li>Post — any logged-in user with read access; anonymous denied.</li>
 *   <li>Edit — author only.</li>
 *   <li>Delete — author OR namespace ADMIN/OWNER (moderation).</li>
 *   <li>Pin — namespace ADMIN/OWNER only.</li>
 * </ul>
 */
@Service
public class AgentVersionCommentService {

    public record CommentWithPerms(AgentVersionComment comment, CommentPermissions permissions) {}

    private static final int EXCERPT_LENGTH = 200;

    private final AgentVersionCommentRepository commentRepo;
    private final AgentVersionRepository versionRepo;
    private final AgentRepository agentRepo;
    private final NamespaceMemberRepository memberRepo;
    private final AgentVisibilityChecker visibilityChecker;
    private final ApplicationEventPublisher events;

    public AgentVersionCommentService(
            AgentVersionCommentRepository commentRepo,
            AgentVersionRepository versionRepo,
            AgentRepository agentRepo,
            NamespaceMemberRepository memberRepo,
            AgentVisibilityChecker visibilityChecker,
            ApplicationEventPublisher events) {
        this.commentRepo = commentRepo;
        this.versionRepo = versionRepo;
        this.agentRepo = agentRepo;
        this.memberRepo = memberRepo;
        this.visibilityChecker = visibilityChecker;
        this.events = events;
    }

    @Transactional(readOnly = true)
    public Page<CommentWithPerms> listForVersion(
            Long versionId, String callerUserId, Set<String> platformRoles, Pageable pageable) {
        Agent agent = loadAgentForVersion(versionId);
        Map<Long, NamespaceRole> roles = loadCallerRoles(callerUserId);
        if (!visibilityChecker.canAccess(agent, callerUserId, roles, platformRoles)) {
            throw new DomainForbiddenException("error.comment.read.forbidden");
        }
        boolean isAdmin = isAdminOf(agent.getNamespaceId(), roles);
        return commentRepo.findActiveByVersionId(versionId, pageable)
                .map(c -> new CommentWithPerms(c, computePerms(c, callerUserId, isAdmin)));
    }

    @Transactional
    public CommentWithPerms post(Long versionId, String callerUserId, String body) {
        AgentVersion version = loadVersion(versionId);
        Agent agent = loadAgent(version.getAgentId());
        Map<Long, NamespaceRole> roles = loadCallerRoles(callerUserId);
        if (!visibilityChecker.canAccess(agent, callerUserId, roles, Set.of())) {
            throw new DomainForbiddenException("error.comment.post.forbidden");
        }
        AgentVersionComment saved = commentRepo.save(new AgentVersionComment(versionId, callerUserId, body));
        boolean isAdmin = isAdminOf(agent.getNamespaceId(), roles);

        events.publishEvent(new AgentVersionCommentPostedEvent(
                saved.getId(), versionId, callerUserId, excerpt(body)));

        return new CommentWithPerms(saved, computePerms(saved, callerUserId, isAdmin));
    }

    @Transactional
    public CommentWithPerms edit(Long commentId, String callerUserId, String newBody) {
        AgentVersionComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> new DomainNotFoundException("error.comment.notFound", commentId));
        if (c.isDeleted()) {
            throw new DomainNotFoundException("error.comment.notFound", commentId);
        }
        if (!callerUserId.equals(c.getAuthorId())) {
            throw new DomainForbiddenException("error.comment.edit.forbidden");
        }
        Agent agent = loadAgentForVersion(c.getAgentVersionId());
        boolean isAdmin = isAdminOf(agent.getNamespaceId(), loadCallerRoles(callerUserId));
        c.edit(newBody);
        AgentVersionComment saved = commentRepo.save(c);
        return new CommentWithPerms(saved, computePerms(saved, callerUserId, isAdmin));
    }

    @Transactional
    public void delete(Long commentId, String callerUserId) {
        AgentVersionComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> new DomainNotFoundException("error.comment.notFound", commentId));
        if (c.isDeleted()) {
            return;  // idempotent
        }
        Agent agent = loadAgentForVersion(c.getAgentVersionId());
        boolean isAuthor = callerUserId.equals(c.getAuthorId());
        boolean isAdmin = isAdminOf(agent.getNamespaceId(), loadCallerRoles(callerUserId));
        if (!isAuthor && !isAdmin) {
            throw new DomainForbiddenException("error.comment.delete.forbidden");
        }
        c.softDelete(callerUserId);
        commentRepo.save(c);
    }

    @Transactional
    public CommentWithPerms setPinned(Long commentId, String callerUserId, boolean pinned) {
        AgentVersionComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> new DomainNotFoundException("error.comment.notFound", commentId));
        if (c.isDeleted()) {
            throw new DomainNotFoundException("error.comment.notFound", commentId);
        }
        Agent agent = loadAgentForVersion(c.getAgentVersionId());
        Map<Long, NamespaceRole> roles = loadCallerRoles(callerUserId);
        if (!isAdminOf(agent.getNamespaceId(), roles)) {
            throw new DomainForbiddenException("error.comment.pin.forbidden");
        }
        c.setPinned(pinned);
        AgentVersionComment saved = commentRepo.save(c);
        return new CommentWithPerms(saved, computePerms(saved, callerUserId, true));
    }

    private CommentPermissions computePerms(AgentVersionComment c, String callerUserId, boolean isAdmin) {
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

    private AgentVersion loadVersion(Long versionId) {
        return versionRepo.findById(versionId)
                .orElseThrow(() -> new DomainNotFoundException("error.agentVersion.notFound", versionId));
    }

    private Agent loadAgent(Long agentId) {
        return agentRepo.findById(agentId)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.notFound", agentId));
    }

    private Agent loadAgentForVersion(Long versionId) {
        AgentVersion v = loadVersion(versionId);
        return loadAgent(v.getAgentId());
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

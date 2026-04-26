package com.iflytek.skillhub.domain.agent.review;

import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.event.AgentPublishedEvent;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Set;

/**
 * Reviewer-side application service. Approval flips the underlying
 * AgentVersion to PUBLISHED and emits AgentPublishedEvent. Rejection just
 * flips both rows and records the reviewer comment.
 *
 * Concurrency: AgentReviewTask carries an @Version optimistic-lock token. Two
 * reviewers acting on the same task race -> the second commit throws
 * OptimisticLockException, which the global exception handler maps to 409.
 */
@Service
public class AgentReviewService {

    private final AgentReviewTaskRepository reviewTaskRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AgentReviewService(AgentReviewTaskRepository reviewTaskRepository,
                              AgentVersionRepository agentVersionRepository,
                              ApplicationEventPublisher eventPublisher) {
        this.reviewTaskRepository = reviewTaskRepository;
        this.agentVersionRepository = agentVersionRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public Page<AgentReviewTask> listForReviewer(Long namespaceId,
                                                 AgentReviewTaskStatus status,
                                                 Map<Long, NamespaceRole> userNamespaceRoles,
                                                 Set<String> platformRoles,
                                                 Pageable pageable) {
        if (!canReview(namespaceId, userNamespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("Reviewer access required");
        }
        return reviewTaskRepository.findByNamespaceIdAndStatus(namespaceId, status, pageable);
    }

    @Transactional(readOnly = true)
    public AgentReviewTask getById(Long taskId,
                                   Map<Long, NamespaceRole> userNamespaceRoles,
                                   Set<String> platformRoles) {
        AgentReviewTask task = reviewTaskRepository.findById(taskId)
                .orElseThrow(() -> new DomainNotFoundException("Review task not found"));
        if (!canReview(task.getNamespaceId(), userNamespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("Reviewer access required");
        }
        return task;
    }

    @Transactional
    public AgentReviewTask approve(Long taskId,
                                   String reviewerUserId,
                                   String comment,
                                   Map<Long, NamespaceRole> userNamespaceRoles,
                                   Set<String> platformRoles) {
        AgentReviewTask task = mustBeReviewable(taskId, userNamespaceRoles, platformRoles);
        AgentVersion version = agentVersionRepository.findById(task.getAgentVersionId())
                .orElseThrow(() -> new DomainBadRequestException(
                        "Underlying agent version missing: " + task.getAgentVersionId()));

        task.approve(reviewerUserId, comment);
        version.approve();
        reviewTaskRepository.save(task);
        agentVersionRepository.save(version);

        eventPublisher.publishEvent(new AgentPublishedEvent(
                version.getAgentId(), version.getId(), task.getNamespaceId(),
                version.getSubmittedBy(), version.getPublishedAt()));

        return task;
    }

    @Transactional
    public AgentReviewTask reject(Long taskId,
                                  String reviewerUserId,
                                  String comment,
                                  Map<Long, NamespaceRole> userNamespaceRoles,
                                  Set<String> platformRoles) {
        AgentReviewTask task = mustBeReviewable(taskId, userNamespaceRoles, platformRoles);
        AgentVersion version = agentVersionRepository.findById(task.getAgentVersionId())
                .orElseThrow(() -> new DomainBadRequestException(
                        "Underlying agent version missing: " + task.getAgentVersionId()));

        task.reject(reviewerUserId, comment);
        version.reject();
        reviewTaskRepository.save(task);
        agentVersionRepository.save(version);

        return task;
    }

    private AgentReviewTask mustBeReviewable(Long taskId,
                                             Map<Long, NamespaceRole> userNamespaceRoles,
                                             Set<String> platformRoles) {
        AgentReviewTask task = reviewTaskRepository.findById(taskId)
                .orElseThrow(() -> new DomainNotFoundException("Review task not found"));
        if (!canReview(task.getNamespaceId(), userNamespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("Reviewer access required");
        }
        return task;
    }

    private boolean canReview(Long namespaceId,
                              Map<Long, NamespaceRole> userNamespaceRoles,
                              Set<String> platformRoles) {
        if (platformRoles != null && platformRoles.contains("SUPER_ADMIN")) {
            return true;
        }
        NamespaceRole role = userNamespaceRoles == null ? null : userNamespaceRoles.get(namespaceId);
        return role == NamespaceRole.ADMIN || role == NamespaceRole.OWNER;
    }
}

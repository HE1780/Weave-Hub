package com.iflytek.skillhub.domain.agent.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTask;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTaskRepository;
import com.iflytek.skillhub.domain.event.AgentPublishedEvent;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Two write paths for an UPLOADED agent version:
 *
 * <ul>
 *   <li>{@link #submitForReview} — author kicks UPLOADED into PENDING_REVIEW for
 *       PUBLIC / NAMESPACE_ONLY visibility, creating an AgentReviewTask.</li>
 *   <li>{@link #confirmPublish} — author of a PRIVATE agent confirms publish
 *       directly from UPLOADED to PUBLISHED without review.</li>
 * </ul>
 *
 * Mirrors {@code SkillReviewSubmitService} surface so the front-end and
 * controllers can reuse the same shape across both aggregates.
 */
@Service
public class AgentReviewSubmitService {

    private final AgentRepository agentRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final AgentReviewTaskRepository agentReviewTaskRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AgentReviewSubmitService(AgentRepository agentRepository,
                                    AgentVersionRepository agentVersionRepository,
                                    AgentReviewTaskRepository agentReviewTaskRepository,
                                    ApplicationEventPublisher eventPublisher) {
        this.agentRepository = agentRepository;
        this.agentVersionRepository = agentVersionRepository;
        this.agentReviewTaskRepository = agentReviewTaskRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AgentVersion submitForReview(Long agentId,
                                        Long versionId,
                                        AgentVisibility targetVisibility,
                                        String actorUserId,
                                        Map<Long, NamespaceRole> userNamespaceRoles) {
        Agent agent = loadAndAuthorize(agentId, actorUserId, userNamespaceRoles);
        AgentVersion version = agentVersionRepository.findById(versionId)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.version.notFound", versionId));
        if (!version.getAgentId().equals(agentId)) {
            throw new DomainBadRequestException("error.agent.version.mismatch");
        }
        if (version.getStatus() != AgentVersionStatus.UPLOADED) {
            throw new DomainBadRequestException(
                    "error.agent.version.submit.notUploaded", version.getVersion());
        }
        if (targetVisibility == null) {
            throw new DomainBadRequestException("error.agent.version.targetVisibility.required");
        }

        version.submitForReview(targetVisibility);
        AgentVersion saved = agentVersionRepository.save(version);
        agentReviewTaskRepository.save(new AgentReviewTask(
                version.getId(), agent.getNamespaceId(), actorUserId));
        return saved;
    }

    @Transactional
    public AgentVersion confirmPublish(Long agentId,
                                       Long versionId,
                                       String actorUserId,
                                       Map<Long, NamespaceRole> userNamespaceRoles) {
        Agent agent = loadAndAuthorize(agentId, actorUserId, userNamespaceRoles);
        if (agent.getVisibility() != AgentVisibility.PRIVATE) {
            throw new DomainBadRequestException("error.agent.confirm.notPrivate");
        }
        AgentVersion version = agentVersionRepository.findById(versionId)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.version.notFound", versionId));
        if (!version.getAgentId().equals(agentId)) {
            throw new DomainBadRequestException("error.agent.version.mismatch");
        }
        if (version.getStatus() != AgentVersionStatus.UPLOADED) {
            throw new DomainBadRequestException(
                    "error.agent.version.confirm.notUploaded", version.getVersion());
        }

        version.autoPublish();
        AgentVersion saved = agentVersionRepository.save(version);
        agent.setLatestVersionId(saved.getId());
        agentRepository.save(agent);
        eventPublisher.publishEvent(new AgentPublishedEvent(
                agent.getId(), saved.getId(), agent.getNamespaceId(),
                actorUserId, saved.getPublishedAt()));
        return saved;
    }

    private Agent loadAndAuthorize(Long agentId,
                                   String actorUserId,
                                   Map<Long, NamespaceRole> userNamespaceRoles) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.notFound", agentId));
        Map<Long, NamespaceRole> roles = userNamespaceRoles == null ? Map.of() : userNamespaceRoles;
        NamespaceRole role = roles.get(agent.getNamespaceId());
        boolean canManage = agent.getOwnerId().equals(actorUserId)
                || role == NamespaceRole.ADMIN
                || role == NamespaceRole.OWNER;
        if (!canManage) {
            throw new DomainForbiddenException("error.agent.lifecycle.noPermission");
        }
        return agent;
    }
}

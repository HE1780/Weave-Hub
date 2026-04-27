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
 * Governance writes for agents — archive / unarchive at the Agent level,
 * and per-version withdraw / rerelease.
 *
 * <p>Permission for archive/unarchive: agent owner OR namespace ADMIN/OWNER
 * (delegated to {@link AgentService#canManageLifecycle}).
 *
 * <p>Permission for withdraw/rerelease: same — only the maintainer set can
 * touch versions on an agent they own.
 */
@Service
public class AgentLifecycleService {

    private final AgentRepository agentRepository;
    private final AgentService agentService;
    private final AgentVersionRepository agentVersionRepository;
    private final AgentReviewTaskRepository agentReviewTaskRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AgentLifecycleService(AgentRepository agentRepository,
                                 AgentService agentService,
                                 AgentVersionRepository agentVersionRepository,
                                 AgentReviewTaskRepository agentReviewTaskRepository,
                                 ApplicationEventPublisher eventPublisher) {
        this.agentRepository = agentRepository;
        this.agentService = agentService;
        this.agentVersionRepository = agentVersionRepository;
        this.agentReviewTaskRepository = agentReviewTaskRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public Agent archive(Long agentId,
                         String actorUserId,
                         Map<Long, NamespaceRole> userNamespaceRoles) {
        Agent agent = loadAndAuthorize(agentId, actorUserId, userNamespaceRoles);
        agent.archive();
        return agentRepository.save(agent);
    }

    @Transactional
    public Agent unarchive(Long agentId,
                           String actorUserId,
                           Map<Long, NamespaceRole> userNamespaceRoles) {
        Agent agent = loadAndAuthorize(agentId, actorUserId, userNamespaceRoles);
        agent.unarchive();
        return agentRepository.save(agent);
    }

    /**
     * Withdraws an agent version from review, returning it to DRAFT so the
     * publisher can edit and resubmit (or simply abandon). Also deletes the
     * pending review task so reviewers don't see it any longer.
     */
    @Transactional
    public AgentVersion withdrawReview(Long agentId,
                                       String version,
                                       String actorUserId,
                                       Map<Long, NamespaceRole> userNamespaceRoles) {
        Agent agent = loadAndAuthorize(agentId, actorUserId, userNamespaceRoles);
        AgentVersion agentVersion = agentVersionRepository
                .findByAgentIdAndVersion(agent.getId(), version)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.version.notFound", version));
        if (agentVersion.getStatus() != AgentVersionStatus.PENDING_REVIEW) {
            throw new DomainBadRequestException("error.agent.version.notPendingReview", version);
        }
        agentReviewTaskRepository.findByAgentVersionId(agentVersion.getId())
                .ifPresent(agentReviewTaskRepository::delete);
        agentVersion.withdrawReview();
        return agentVersionRepository.save(agentVersion);
    }

    /**
     * Rebuilds a new agent version from an already-published version by cloning
     * its inline manifest/soul/workflow content into a new version row with the
     * given target version string. Status follows the agent's visibility:
     * PRIVATE → PUBLISHED, PUBLIC/NAMESPACE_ONLY → PENDING_REVIEW (with a new
     * review task).
     *
     * <p>Does NOT run the pre-publish secret scanner — the source version was
     * already validated when it was first published. Mirrors skill rerelease in
     * intent (clone + rewrite version), simplified for the agent surface
     * (no per-file rows, no precheck-warning confirmation flow since
     * {@code AgentPublishService} treats warnings as hard failures).
     */
    @Transactional
    public AgentVersion rereleaseVersion(Long agentId,
                                         String sourceVersion,
                                         String targetVersion,
                                         String actorUserId,
                                         Map<Long, NamespaceRole> userNamespaceRoles) {
        Agent agent = loadAndAuthorize(agentId, actorUserId, userNamespaceRoles);

        if (targetVersion == null || targetVersion.isBlank()) {
            throw new DomainBadRequestException("error.agent.version.targetRequired");
        }
        if (sourceVersion.equals(targetVersion)) {
            throw new DomainBadRequestException("error.agent.version.targetSameAsSource");
        }

        AgentVersion source = agentVersionRepository
                .findByAgentIdAndVersion(agent.getId(), sourceVersion)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.version.notFound", sourceVersion));
        if (source.getStatus() != AgentVersionStatus.PUBLISHED) {
            throw new DomainBadRequestException("error.agent.version.notPublished", sourceVersion);
        }
        if (agentVersionRepository.findByAgentIdAndVersion(agent.getId(), targetVersion).isPresent()) {
            throw new DomainBadRequestException("error.agent.version.exists", targetVersion);
        }

        AgentVersion fresh = new AgentVersion(
                agent.getId(),
                targetVersion,
                actorUserId,
                rewriteManifestVersion(source.getManifestYaml(), targetVersion),
                source.getSoulMd(),
                source.getWorkflowYaml(),
                null,
                source.getPackageSizeBytes()
        );
        fresh = agentVersionRepository.save(fresh);

        if (agent.getVisibility() == AgentVisibility.PRIVATE) {
            fresh.autoPublish();
            fresh = agentVersionRepository.save(fresh);
            eventPublisher.publishEvent(new AgentPublishedEvent(
                    agent.getId(), fresh.getId(), agent.getNamespaceId(),
                    actorUserId, fresh.getPublishedAt()));
        } else {
            fresh.submitForReview();
            fresh = agentVersionRepository.save(fresh);
            agentReviewTaskRepository.save(new AgentReviewTask(
                    fresh.getId(), agent.getNamespaceId(), actorUserId));
        }

        return fresh;
    }

    /**
     * Best-effort rewrite of the {@code version: x.y.z} line in AGENT.md
     * frontmatter so the persisted manifest reflects the new version. If the
     * line is missing (older or oddly-shaped manifests) the original content
     * is returned unchanged — the canonical version is the {@code version}
     * column on {@code agent_version}, the manifest copy is for display only.
     */
    private String rewriteManifestVersion(String manifest, String targetVersion) {
        if (manifest == null || manifest.isBlank()) {
            return manifest;
        }
        // Match `version: ...` at start-of-line within the YAML frontmatter.
        return manifest.replaceFirst("(?m)^version:\\s*.+$", "version: " + targetVersion);
    }

    private Agent loadAndAuthorize(Long agentId,
                                   String actorUserId,
                                   Map<Long, NamespaceRole> userNamespaceRoles) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.notFound", agentId));
        if (!agentService.canManageLifecycle(agent, actorUserId, userNamespaceRoles)) {
            throw new DomainForbiddenException("error.agent.lifecycle.noPermission");
        }
        return agent;
    }
}

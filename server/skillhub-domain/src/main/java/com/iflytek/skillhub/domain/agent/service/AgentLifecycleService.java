package com.iflytek.skillhub.domain.agent.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTask;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTaskRepository;
import com.iflytek.skillhub.domain.audit.AuditLogService;
import com.iflytek.skillhub.domain.event.AgentPublishedEvent;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.storage.ObjectStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
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

    private static final Logger log = LoggerFactory.getLogger(AgentLifecycleService.class);

    private final AgentRepository agentRepository;
    private final AgentService agentService;
    private final AgentVersionRepository agentVersionRepository;
    private final AgentReviewTaskRepository agentReviewTaskRepository;
    private final ObjectStorageService objectStorageService;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;

    public AgentLifecycleService(AgentRepository agentRepository,
                                 AgentService agentService,
                                 AgentVersionRepository agentVersionRepository,
                                 AgentReviewTaskRepository agentReviewTaskRepository,
                                 ObjectStorageService objectStorageService,
                                 ApplicationEventPublisher eventPublisher,
                                 AuditLogService auditLogService) {
        this.agentRepository = agentRepository;
        this.agentService = agentService;
        this.agentVersionRepository = agentVersionRepository;
        this.agentReviewTaskRepository = agentReviewTaskRepository;
        this.objectStorageService = objectStorageService;
        this.eventPublisher = eventPublisher;
        this.auditLogService = auditLogService;
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
     * Admin-only archive that bypasses the namespace ADMIN/OWNER permission gate.
     * Mirrors {@code SkillGovernanceService#archiveSkillAsAdmin} — used by the
     * report-resolution flow when a moderator picks
     * {@code RESOLVE_AND_ARCHIVE}. Records an {@code ARCHIVE_AGENT} audit log
     * entry with the optional reason supplied by the moderator.
     *
     * <p>Authorization is enforced upstream by the controller's
     * {@code @PreAuthorize}; this method assumes the caller is already a
     * platform admin.
     */
    @Transactional
    public Agent archiveAsAdmin(Long agentId,
                                String actorUserId,
                                String clientIp,
                                String userAgent,
                                String reason) {
        Agent agent = agentRepository.findById(agentId)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.notFound", agentId));
        agent.archive();
        Agent saved = agentRepository.save(agent);
        auditLogService.record(actorUserId, "ARCHIVE_AGENT", "AGENT", agent.getId(), null, clientIp, userAgent,
                reason == null || reason.isBlank() ? null : "{\"reason\":\"" + reason.replace("\"", "\\\"") + "\"}");
        return saved;
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
            fresh.submitForReview(agent.getVisibility());
            fresh = agentVersionRepository.save(fresh);
            agentReviewTaskRepository.save(new AgentReviewTask(
                    fresh.getId(), agent.getNamespaceId(), actorUserId));
        }

        return fresh;
    }

    /**
     * Deletes a single non-PUBLISHED agent version. Mirrors skill version
     * delete: PUBLISHED versions cannot be deleted (they're the public
     * artifact). Allowed sources: DRAFT, REJECTED, ARCHIVED. PENDING_REVIEW
     * must be withdrawn first.
     *
     * <p>Cascades the underlying review task (FK CASCADE on agent_version)
     * and best-effort deletes the package zip from object storage after
     * commit.
     *
     * <p>Unlike skill, agent has no {@code latestVersionId} pointer to
     * patch up — readers fetch the most recent PUBLISHED version on demand.
     */
    @Transactional
    public AgentVersion deleteVersion(Long agentId,
                                      String version,
                                      String actorUserId,
                                      Map<Long, NamespaceRole> userNamespaceRoles) {
        Agent agent = loadAndAuthorize(agentId, actorUserId, userNamespaceRoles);
        AgentVersion target = agentVersionRepository
                .findByAgentIdAndVersion(agent.getId(), version)
                .orElseThrow(() -> new DomainNotFoundException("error.agent.version.notFound", version));
        if (target.getStatus() == AgentVersionStatus.PUBLISHED) {
            throw new DomainBadRequestException("error.agent.version.publishedNotDeletable");
        }
        if (target.getStatus() == AgentVersionStatus.PENDING_REVIEW) {
            throw new DomainBadRequestException("error.agent.version.withdrawFirst");
        }

        String objectKey = target.getPackageObjectKey();
        agentVersionRepository.delete(target);
        if (objectKey != null && !objectKey.isBlank()) {
            deleteStorageAfterCommit(List.of(objectKey));
        }
        return target;
    }

    private void deleteStorageAfterCommit(List<String> keys) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            tryDeleteStorage(keys);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                tryDeleteStorage(keys);
            }
        });
    }

    private void tryDeleteStorage(List<String> keys) {
        try {
            objectStorageService.deleteObjects(keys);
        } catch (RuntimeException ex) {
            log.error("Failed to delete agent version storage objects [keys={}]", keys, ex);
        }
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

package com.iflytek.skillhub.domain.agent.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentMetadata;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.scan.AgentSecurityScanService;
import com.iflytek.skillhub.domain.event.AgentPublishedEvent;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.skill.validation.PackageEntry;
import com.iflytek.skillhub.domain.skill.validation.PrePublishValidator;
import com.iflytek.skillhub.domain.skill.validation.ValidationResult;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Publishes a new AgentVersion. After Step 2 alignment, the publish call always
 * lands the version in {@code SCANNING} and hands off to
 * {@link AgentSecurityScanService}; the version moves to {@code UPLOADED} once
 * the scan passes. Author then drives the next transition via
 * {@link AgentReviewSubmitService} (submitForReview / confirmPublish).
 *
 * <p>SUPER_ADMIN flow: when {@code forceAutoPublish=true}, this service skips
 * the review gate entirely — after the scan settles into UPLOADED, the version
 * is auto-published and {@code Agent.latestVersionId} is updated.
 *
 * <p>Visibility for an existing agent is fixed on first publish — re-publish
 * carries the same visibility forward.
 */
@Service
public class AgentPublishService {

    private final AgentRepository agentRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final AgentSecurityScanService agentSecurityScanService;
    private final PrePublishValidator prePublishValidator;
    private final ApplicationEventPublisher eventPublisher;

    public AgentPublishService(AgentRepository agentRepository,
                               AgentVersionRepository agentVersionRepository,
                               AgentSecurityScanService agentSecurityScanService,
                               PrePublishValidator prePublishValidator,
                               ApplicationEventPublisher eventPublisher) {
        this.agentRepository = agentRepository;
        this.agentVersionRepository = agentVersionRepository;
        this.agentSecurityScanService = agentSecurityScanService;
        this.prePublishValidator = prePublishValidator;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public AgentVersion publish(Long namespaceId,
                                AgentMetadata metadata,
                                AgentVisibility visibility,
                                List<PackageEntry> entries,
                                String manifestYaml,
                                String soulMd,
                                String workflowYaml,
                                String packageObjectKey,
                                Long packageSizeBytes,
                                String publisherUserId,
                                List<String> packageWarnings,
                                boolean confirmWarnings,
                                boolean forceAutoPublish) {
        if (publisherUserId == null || publisherUserId.isBlank()) {
            throw new DomainForbiddenException("Publisher must be authenticated");
        }

        ValidationResult prePublish = prePublishValidator.validateEntries(
                entries == null ? List.of() : entries, publisherUserId, namespaceId);
        if (!prePublish.passed()) {
            throw new DomainBadRequestException(
                    "Agent package failed pre-publish validation: "
                            + String.join(", ", prePublish.errors()));
        }

        List<String> mergedWarnings = new java.util.ArrayList<>();
        if (packageWarnings != null) {
            mergedWarnings.addAll(packageWarnings);
        }
        mergedWarnings.addAll(prePublish.warnings());
        if (!confirmWarnings && !mergedWarnings.isEmpty()) {
            throw new DomainBadRequestException(
                    "Agent package has warnings; resubmit with confirmWarnings=true to proceed: "
                            + String.join(", ", mergedWarnings));
        }

        String slug = metadata.name();
        Optional<Agent> existing = agentRepository.findByNamespaceIdAndSlug(namespaceId, slug);

        Agent agent = existing.orElseGet(() -> {
            Agent fresh = new Agent(namespaceId, slug, slug, publisherUserId, visibility);
            fresh.setDescription(metadata.description());
            return agentRepository.save(fresh);
        });

        if (existing.isPresent() && !agent.getOwnerId().equals(publisherUserId)) {
            throw new DomainForbiddenException(
                    "Agent already owned by a different user (namespace=" + namespaceId + ", slug=" + slug + ")");
        }

        String version = metadata.version();
        if (version == null || version.isBlank()) {
            throw new DomainBadRequestException(
                    "AGENT.md frontmatter must include 'version' for publish");
        }

        agentVersionRepository.findByAgentIdAndVersion(agent.getId(), version).ifPresent(v -> {
            throw new DomainBadRequestException(
                    "Agent version already exists (agentId=" + agent.getId() + ", version=" + version + ")");
        });

        // Persist in SCANNING (entity default). Scanner placeholder synchronously
        // flips it to UPLOADED inside its own @Transactional propagation.
        AgentVersion fresh = new AgentVersion(
                agent.getId(), version, publisherUserId,
                manifestYaml, soulMd, workflowYaml,
                packageObjectKey, packageSizeBytes);
        AgentVersion saved = agentVersionRepository.save(fresh);
        agentVersionRepository.flush();

        agentSecurityScanService.triggerScan(saved.getId(), publisherUserId);

        // Reload to observe the post-scan status (UPLOADED on PASS).
        AgentVersion postScan = agentVersionRepository.findById(saved.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "AgentVersion vanished after scan: " + saved.getId()));

        if (forceAutoPublish) {
            postScan.autoPublish();
            AgentVersion published = agentVersionRepository.save(postScan);
            agent.setLatestVersionId(published.getId());
            agentRepository.save(agent);
            eventPublisher.publishEvent(new AgentPublishedEvent(
                    agent.getId(), published.getId(), namespaceId,
                    publisherUserId, published.getPublishedAt()));
            return published;
        }

        return postScan;
    }
}

package com.iflytek.skillhub.domain.agent.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentMetadata;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVisibility;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTask;
import com.iflytek.skillhub.domain.agent.review.AgentReviewTaskRepository;
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
 * Publishes a new AgentVersion. Decides between auto-publish (PRIVATE visibility)
 * and review-gate (PUBLIC / NAMESPACE_ONLY) and emits the appropriate domain event.
 *
 * Visibility for an existing agent is fixed on first publish — re-publish carries
 * the same visibility forward. Documented as out-of-scope in the plan; switching
 * visibility post-hoc is a future spec.
 */
@Service
public class AgentPublishService {

    private final AgentRepository agentRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final AgentReviewTaskRepository agentReviewTaskRepository;
    private final PrePublishValidator prePublishValidator;
    private final ApplicationEventPublisher eventPublisher;

    public AgentPublishService(AgentRepository agentRepository,
                               AgentVersionRepository agentVersionRepository,
                               AgentReviewTaskRepository agentReviewTaskRepository,
                               PrePublishValidator prePublishValidator,
                               ApplicationEventPublisher eventPublisher) {
        this.agentRepository = agentRepository;
        this.agentVersionRepository = agentVersionRepository;
        this.agentReviewTaskRepository = agentReviewTaskRepository;
        this.prePublishValidator = prePublishValidator;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Publishes a new agent version. Creates the Agent row on first publish,
     * else appends a new version to the existing agent.
     *
     * @param namespaceId      target namespace; reviewer queue is keyed by this
     * @param metadata         parsed AGENT.md (provides slug via name + display fields)
     * @param visibility       requested visibility; ignored on subsequent publishes
     * @param entries          unpacked package entries used for the secret/precheck scan
     * @param manifestYaml     raw AGENT.md content (stored verbatim for the review screen)
     * @param soulMd           raw soul.md content
     * @param workflowYaml     raw workflow.yaml content
     * @param packageObjectKey object-storage key for the canonical zip
     * @param packageSizeBytes bytes
     * @param publisherUserId  authenticated user submitting the publish
     * @param packageWarnings  warnings collected upstream by AgentPackageValidator
     *                         (extension allowlist, magic-byte mismatch); merged with
     *                         pre-publish secret-scan warnings for the confirm-required path
     * @param confirmWarnings  caller acknowledges warnings; without this, any warning aborts
     */
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
                                boolean confirmWarnings) {
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

        AgentVersion newVersion = new AgentVersion(
                agent.getId(), version, publisherUserId,
                manifestYaml, soulMd, workflowYaml,
                packageObjectKey, packageSizeBytes);
        newVersion = agentVersionRepository.save(newVersion);

        if (agent.getVisibility() == AgentVisibility.PRIVATE) {
            newVersion.autoPublish();
            agentVersionRepository.save(newVersion);
            eventPublisher.publishEvent(new AgentPublishedEvent(
                    agent.getId(), newVersion.getId(), namespaceId,
                    publisherUserId, newVersion.getPublishedAt()));
        } else {
            newVersion.submitForReview();
            agentVersionRepository.save(newVersion);
            AgentReviewTask task = new AgentReviewTask(
                    newVersion.getId(), namespaceId, publisherUserId);
            agentReviewTaskRepository.save(task);
        }

        return newVersion;
    }
}

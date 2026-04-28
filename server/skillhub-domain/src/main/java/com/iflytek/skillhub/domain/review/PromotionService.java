package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.AgentVersion;
import com.iflytek.skillhub.domain.agent.AgentVersionRepository;
import com.iflytek.skillhub.domain.agent.AgentVersionStatus;
import com.iflytek.skillhub.domain.event.PromotionApprovedEvent;
import com.iflytek.skillhub.domain.event.PromotionRejectedEvent;
import com.iflytek.skillhub.domain.event.PromotionSubmittedEvent;
import com.iflytek.skillhub.domain.governance.GovernanceNotificationService;
import com.iflytek.skillhub.domain.review.materialization.MaterializationResult;
import com.iflytek.skillhub.domain.review.materialization.PromotionMaterializer;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.*;
import jakarta.persistence.EntityManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ConcurrentModificationException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles promotion requests that copy approved skills into the global
 * namespace.
 *
 * <p>Promotion is intentionally modeled separately from normal review because
 * it creates or updates a distinct target skill lineage.
 */
@Service
public class PromotionService {

    private final PromotionRequestRepository promotionRequestRepository;
    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final AgentRepository agentRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final NamespaceRepository namespaceRepository;
    private final ReviewPermissionChecker permissionChecker;
    private final ApplicationEventPublisher eventPublisher;
    private final GovernanceNotificationService governanceNotificationService;
    private final EntityManager entityManager;
    private final Clock clock;
    private final Map<SourceType, PromotionMaterializer> materializers;

    public PromotionService(PromotionRequestRepository promotionRequestRepository,
                            SkillRepository skillRepository,
                            SkillVersionRepository skillVersionRepository,
                            AgentRepository agentRepository,
                            AgentVersionRepository agentVersionRepository,
                            NamespaceRepository namespaceRepository,
                            ReviewPermissionChecker permissionChecker,
                            ApplicationEventPublisher eventPublisher,
                            GovernanceNotificationService governanceNotificationService,
                            EntityManager entityManager,
                            Clock clock,
                            List<PromotionMaterializer> materializerBeans) {
        this.promotionRequestRepository = promotionRequestRepository;
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.agentRepository = agentRepository;
        this.agentVersionRepository = agentVersionRepository;
        this.namespaceRepository = namespaceRepository;
        this.permissionChecker = permissionChecker;
        this.eventPublisher = eventPublisher;
        this.governanceNotificationService = governanceNotificationService;
        this.entityManager = entityManager;
        this.clock = clock;
        Map<SourceType, PromotionMaterializer> map = new EnumMap<>(SourceType.class);
        for (PromotionMaterializer m : materializerBeans) {
            map.put(m.supportedSourceType(), m);
        }
        this.materializers = java.util.Collections.unmodifiableMap(map);
    }

    /**
     * Submits a promotion request for a published source version using both
     * namespace and platform roles for authorization.
     */
    @Transactional
    public PromotionRequest submitPromotion(Long sourceSkillId, Long sourceVersionId,
                                            Long targetNamespaceId, String userId,
                                            Map<Long, NamespaceRole> userNamespaceRoles,
                                            Set<String> platformRoles) {
        Skill sourceSkill = skillRepository.findById(sourceSkillId)
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", sourceSkillId));

        SkillVersion sourceVersion = skillVersionRepository.findById(sourceVersionId)
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", sourceVersionId));

        if (!sourceVersion.getSkillId().equals(sourceSkillId)) {
            throw new DomainBadRequestException("promotion.version_skill_mismatch", sourceVersionId, sourceSkillId);
        }

        if (sourceVersion.getStatus() != SkillVersionStatus.PUBLISHED) {
            throw new DomainBadRequestException("promotion.version_not_published", sourceVersionId);
        }

        Namespace sourceNamespace = namespaceRepository.findById(sourceSkill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", sourceSkill.getNamespaceId()));
        assertNamespaceActive(sourceNamespace);

        if (!permissionChecker.canSubmitPromotion(sourceSkill, userId, userNamespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("promotion.submit.no_permission");
        }

        Namespace targetNamespace = namespaceRepository.findById(targetNamespaceId)
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", targetNamespaceId));

        if (targetNamespace.getType() != NamespaceType.GLOBAL) {
            throw new DomainBadRequestException("promotion.target_not_global", targetNamespaceId);
        }

        promotionRequestRepository.findBySourceSkillIdAndStatus(sourceSkillId, ReviewTaskStatus.PENDING)
                .ifPresent(existing -> {
                    throw new DomainBadRequestException("promotion.duplicate_pending", sourceVersionId);
                });
        promotionRequestRepository.findBySourceSkillIdAndStatus(sourceSkillId, ReviewTaskStatus.APPROVED)
                .ifPresent(existing -> {
                    throw new DomainBadRequestException("promotion.already_promoted", sourceSkillId);
                });

        PromotionRequest request = new PromotionRequest(sourceSkillId, sourceVersionId, targetNamespaceId, userId);
        PromotionRequest saved = promotionRequestRepository.save(request);
        eventPublisher.publishEvent(new PromotionSubmittedEvent(
                saved.getId(), saved.getSourceSkillId(), saved.getSourceVersionId(),
                saved.getSubmittedBy()));
        return saved;
    }

    @Transactional
    public PromotionRequest submitPromotion(Long sourceSkillId, Long sourceVersionId,
                                            Long targetNamespaceId, String userId,
                                            Map<Long, NamespaceRole> userNamespaceRoles) {
        Skill sourceSkill = skillRepository.findById(sourceSkillId)
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", sourceSkillId));

        SkillVersion sourceVersion = skillVersionRepository.findById(sourceVersionId)
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", sourceVersionId));

        if (!sourceVersion.getSkillId().equals(sourceSkillId)) {
            throw new DomainBadRequestException("promotion.version_skill_mismatch", sourceVersionId, sourceSkillId);
        }

        if (sourceVersion.getStatus() != SkillVersionStatus.PUBLISHED) {
            throw new DomainBadRequestException("promotion.version_not_published", sourceVersionId);
        }

        Namespace sourceNamespace = namespaceRepository.findById(sourceSkill.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", sourceSkill.getNamespaceId()));
        assertNamespaceActive(sourceNamespace);

        if (!permissionChecker.canSubmitPromotion(sourceSkill, userId, userNamespaceRoles)) {
            throw new DomainForbiddenException("promotion.submit.no_permission");
        }

        Namespace targetNamespace = namespaceRepository.findById(targetNamespaceId)
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", targetNamespaceId));

        if (targetNamespace.getType() != NamespaceType.GLOBAL) {
            throw new DomainBadRequestException("promotion.target_not_global", targetNamespaceId);
        }

        promotionRequestRepository.findBySourceSkillIdAndStatus(sourceSkillId, ReviewTaskStatus.PENDING)
                .ifPresent(existing -> {
                    throw new DomainBadRequestException("promotion.duplicate_pending", sourceVersionId);
                });
        promotionRequestRepository.findBySourceSkillIdAndStatus(sourceSkillId, ReviewTaskStatus.APPROVED)
                .ifPresent(existing -> {
                    throw new DomainBadRequestException("promotion.already_promoted", sourceSkillId);
                });

        PromotionRequest request = new PromotionRequest(sourceSkillId, sourceVersionId, targetNamespaceId, userId);
        PromotionRequest saved = promotionRequestRepository.save(request);
        eventPublisher.publishEvent(new PromotionSubmittedEvent(
                saved.getId(), saved.getSourceSkillId(), saved.getSourceVersionId(),
                saved.getSubmittedBy()));
        return saved;
    }

    /**
     * Submits an agent promotion request. Mirrors {@link #submitPromotion} for the
     * agent source path. PromotionSubmittedEvent's existing slots are reused —
     * field 2 (skillId) carries the agent id, field 3 (versionId) the agent
     * version id. The listener (NotificationEventListener) only reads the
     * promotionId, so the field names are documentary.
     */
    @Transactional
    public PromotionRequest submitAgentPromotion(Long sourceAgentId, Long sourceAgentVersionId,
                                                 Long targetNamespaceId, String userId,
                                                 Map<Long, NamespaceRole> userNamespaceRoles,
                                                 Set<String> platformRoles) {
        Agent sourceAgent = agentRepository.findById(sourceAgentId)
                .orElseThrow(() -> new DomainNotFoundException("agent.not_found", sourceAgentId));

        AgentVersion sourceVersion = agentVersionRepository.findById(sourceAgentVersionId)
                .orElseThrow(() -> new DomainNotFoundException("agent_version.not_found", sourceAgentVersionId));

        if (!sourceVersion.getAgentId().equals(sourceAgentId)) {
            throw new DomainBadRequestException("promotion.version_agent_mismatch",
                    sourceAgentVersionId, sourceAgentId);
        }

        if (sourceVersion.getStatus() != AgentVersionStatus.PUBLISHED) {
            throw new DomainBadRequestException("promotion.version_not_published", sourceAgentVersionId);
        }

        Namespace sourceNamespace = namespaceRepository.findById(sourceAgent.getNamespaceId())
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", sourceAgent.getNamespaceId()));
        assertNamespaceActive(sourceNamespace);

        if (!permissionChecker.canSubmitPromotion(sourceAgent, userId, userNamespaceRoles, platformRoles)) {
            throw new DomainForbiddenException("promotion.submit.no_permission");
        }

        Namespace targetNamespace = namespaceRepository.findById(targetNamespaceId)
                .orElseThrow(() -> new DomainNotFoundException("namespace.not_found", targetNamespaceId));

        if (targetNamespace.getType() != NamespaceType.GLOBAL) {
            throw new DomainBadRequestException("promotion.target_not_global", targetNamespaceId);
        }

        promotionRequestRepository.findBySourceAgentIdAndStatus(sourceAgentId, ReviewTaskStatus.PENDING)
                .ifPresent(existing -> {
                    throw new DomainBadRequestException("promotion.duplicate_pending", sourceAgentVersionId);
                });

        PromotionRequest request = PromotionRequest.forAgent(
                sourceAgentId, sourceAgentVersionId, targetNamespaceId, userId);
        PromotionRequest saved = promotionRequestRepository.save(request);
        eventPublisher.publishEvent(new PromotionSubmittedEvent(
                saved.getId(), saved.getSourceAgentId(), saved.getSourceAgentVersionId(),
                saved.getSubmittedBy()));
        return saved;
    }

    /**
     * Approves a promotion request and materializes a published copy of the
     * source version in the target global namespace.
     */
    @Transactional
    public PromotionRequest approvePromotion(Long promotionId, String reviewerId,
                                             String comment, Set<String> platformRoles) {
        PromotionRequest request = promotionRequestRepository.findById(promotionId)
                .orElseThrow(() -> new DomainNotFoundException("promotion.not_found", promotionId));

        if (request.getStatus() != ReviewTaskStatus.PENDING) {
            throw new DomainBadRequestException("promotion.not_pending", promotionId);
        }

        if (!permissionChecker.canReviewPromotion(request, reviewerId, platformRoles)) {
            throw new DomainForbiddenException("promotion.no_permission");
        }

        int updated = promotionRequestRepository.updateStatusWithVersion(
                promotionId, ReviewTaskStatus.APPROVED, reviewerId, comment, null, request.getVersion());
        if (updated == 0) {
            throw new ConcurrentModificationException("Promotion request was modified concurrently");
        }
        PromotionRequest approvedRequest = promotionRequestRepository.findById(promotionId)
                .orElseThrow(() -> new DomainNotFoundException("promotion.not_found", promotionId));

        // Delegate materialization to the strategy registered for this source type.
        // SkillPromotionMaterializer handles SKILL; AgentPromotionMaterializer handles AGENT.
        // Each owns its own repository dependencies + emits its own *PublishedEvent.
        PromotionMaterializer materializer = materializers.get(approvedRequest.getSourceType());
        if (materializer == null) {
            throw new IllegalStateException(
                    "No PromotionMaterializer registered for " + approvedRequest.getSourceType());
        }
        MaterializationResult result = materializer.materialize(approvedRequest);
        approvedRequest.setTargetEntityId(result.targetEntityId(), approvedRequest.getSourceType());
        PromotionRequest savedRequest = promotionRequestRepository.save(approvedRequest);

        eventPublisher.publishEvent(new PromotionApprovedEvent(
                approvedRequest.getId(), approvedRequest.getSourceSkillId(),
                reviewerId, approvedRequest.getSubmittedBy()));
        governanceNotificationService.notifyUser(
                approvedRequest.getSubmittedBy(),
                "PROMOTION",
                "PROMOTION_REQUEST",
                promotionId,
                "Promotion approved",
                "{\"status\":\"APPROVED\"}"
        );

        return savedRequest;
    }

    /**
     * Rejects a pending promotion request without changing the source skill.
     */
    @Transactional
    public PromotionRequest rejectPromotion(Long promotionId, String reviewerId,
                                            String comment, Set<String> platformRoles) {
        PromotionRequest request = promotionRequestRepository.findById(promotionId)
                .orElseThrow(() -> new DomainNotFoundException("promotion.not_found", promotionId));

        if (request.getStatus() != ReviewTaskStatus.PENDING) {
            throw new DomainBadRequestException("promotion.not_pending", promotionId);
        }

        if (!permissionChecker.canReviewPromotion(request, reviewerId, platformRoles)) {
            throw new DomainForbiddenException("promotion.no_permission");
        }

        int updated = promotionRequestRepository.updateStatusWithVersion(
                promotionId, ReviewTaskStatus.REJECTED, reviewerId, comment, null, request.getVersion());
        if (updated == 0) {
            throw new ConcurrentModificationException("Promotion request was modified concurrently");
        }
        syncPromotionRequestState(request, ReviewTaskStatus.REJECTED, reviewerId, comment);
        entityManager.detach(request);
        eventPublisher.publishEvent(new PromotionRejectedEvent(
                request.getId(), request.getSourceSkillId(),
                reviewerId, request.getSubmittedBy(), comment));
        governanceNotificationService.notifyUser(
                request.getSubmittedBy(),
                "PROMOTION",
                "PROMOTION_REQUEST",
                promotionId,
                "Promotion rejected",
                "{\"status\":\"REJECTED\"}"
        );

        return request;
    }

    public boolean canViewPromotion(PromotionRequest request, String userId, Set<String> platformRoles) {
        return permissionChecker.canViewPromotion(request, userId, platformRoles);
    }

    private void assertNamespaceActive(Namespace namespace) {
        if (namespace.getStatus() == NamespaceStatus.FROZEN) {
            throw new DomainBadRequestException("error.namespace.frozen", namespace.getSlug());
        }
        if (namespace.getStatus() == NamespaceStatus.ARCHIVED) {
            throw new DomainBadRequestException("error.namespace.archived", namespace.getSlug());
        }
    }

    private Instant currentTime() {
        return Instant.now(clock);
    }

    private void syncPromotionRequestState(PromotionRequest request,
                                           ReviewTaskStatus status,
                                           String reviewedBy,
                                           String comment) {
        request.setStatus(status);
        request.setReviewedBy(reviewedBy);
        request.setReviewComment(comment);
        request.setReviewedAt(currentTime());
    }
}

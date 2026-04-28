package com.iflytek.skillhub.domain.review.materialization;

import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.SourceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.*;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Materializes a promoted Skill into the target namespace. Extracted from
 * PromotionService.approvePromotion to support polymorphic dispatch via
 * {@link PromotionMaterializer}. Behavior is preserved verbatim from the
 * pre-extraction code: copy Skill row, copy SkillVersion (status=PUBLISHED),
 * update latestVersionId, copy SkillFile rows reusing storageKey, publish
 * SkillPublishedEvent.
 */
@Component
public class SkillPromotionMaterializer implements PromotionMaterializer {

    private final SkillRepository skillRepository;
    private final SkillVersionRepository skillVersionRepository;
    private final SkillFileRepository skillFileRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public SkillPromotionMaterializer(SkillRepository skillRepository,
                                      SkillVersionRepository skillVersionRepository,
                                      SkillFileRepository skillFileRepository,
                                      ApplicationEventPublisher eventPublisher,
                                      Clock clock) {
        this.skillRepository = skillRepository;
        this.skillVersionRepository = skillVersionRepository;
        this.skillFileRepository = skillFileRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public SourceType supportedSourceType() {
        return SourceType.SKILL;
    }

    @Override
    public MaterializationResult materialize(PromotionRequest request) {
        Skill sourceSkill = skillRepository.findById(request.getSourceSkillId())
                .orElseThrow(() -> new DomainNotFoundException("skill.not_found", request.getSourceSkillId()));
        SkillVersion sourceVersion = skillVersionRepository.findById(request.getSourceVersionId())
                .orElseThrow(() -> new DomainNotFoundException("skill_version.not_found", request.getSourceVersionId()));

        skillRepository.findByNamespaceIdAndSlugAndOwnerId(
                request.getTargetNamespaceId(),
                sourceSkill.getSlug(),
                sourceSkill.getOwnerId()
        ).ifPresent(existing -> {
            throw new DomainBadRequestException("promotion.target_skill_conflict", sourceSkill.getSlug());
        });

        Skill newSkill = new Skill(request.getTargetNamespaceId(), sourceSkill.getSlug(),
                sourceSkill.getOwnerId(), SkillVisibility.PUBLIC);
        newSkill.setDisplayName(sourceSkill.getDisplayName());
        newSkill.setSummary(sourceSkill.getSummary());
        newSkill.setSourceSkillId(sourceSkill.getId());
        newSkill.setCreatedBy(request.getSubmittedBy());
        newSkill.setUpdatedBy(request.getSubmittedBy());

        try {
            newSkill = skillRepository.save(newSkill);
        } catch (DataIntegrityViolationException ex) {
            DomainBadRequestException wrapped = new DomainBadRequestException(
                    "promotion.target_skill_conflict", sourceSkill.getSlug());
            wrapped.initCause(ex);
            throw wrapped;
        }

        SkillVersion newVersion = new SkillVersion(newSkill.getId(), sourceVersion.getVersion(),
                sourceVersion.getCreatedBy());
        newVersion.setStatus(SkillVersionStatus.PUBLISHED);
        newVersion.setPublishedAt(Instant.now(clock));
        newVersion.setRequestedVisibility(SkillVisibility.PUBLIC);
        newVersion.setChangelog(sourceVersion.getChangelog());
        newVersion.setParsedMetadataJson(sourceVersion.getParsedMetadataJson());
        newVersion.setManifestJson(sourceVersion.getManifestJson());
        newVersion.setFileCount(sourceVersion.getFileCount());
        newVersion.setTotalSize(sourceVersion.getTotalSize());
        newVersion = skillVersionRepository.save(newVersion);

        newSkill.setLatestVersionId(newVersion.getId());
        skillRepository.save(newSkill);

        List<SkillFile> sourceFiles = skillFileRepository.findByVersionId(request.getSourceVersionId());
        Long newVersionId = newVersion.getId();
        List<SkillFile> copied = sourceFiles.stream()
                .map(f -> new SkillFile(newVersionId, f.getFilePath(), f.getFileSize(),
                        f.getContentType(), f.getSha256(), f.getStorageKey()))
                .toList();
        skillFileRepository.saveAll(copied);

        eventPublisher.publishEvent(new SkillPublishedEvent(
                newSkill.getId(), newVersion.getId(), request.getSubmittedBy()));

        return new MaterializationResult(newSkill.getId());
    }
}

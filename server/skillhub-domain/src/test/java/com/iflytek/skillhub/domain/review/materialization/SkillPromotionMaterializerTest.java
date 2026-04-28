package com.iflytek.skillhub.domain.review.materialization;

import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.SourceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SkillPromotionMaterializerTest {

    private SkillRepository skillRepository;
    private SkillVersionRepository skillVersionRepository;
    private SkillFileRepository skillFileRepository;
    private ApplicationEventPublisher eventPublisher;
    private SkillPromotionMaterializer materializer;

    @BeforeEach
    void setup() {
        skillRepository = mock(SkillRepository.class);
        skillVersionRepository = mock(SkillVersionRepository.class);
        skillFileRepository = mock(SkillFileRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        materializer = new SkillPromotionMaterializer(
                skillRepository, skillVersionRepository, skillFileRepository,
                eventPublisher, Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void supportsSkillSourceType() {
        assertEquals(SourceType.SKILL, materializer.supportedSourceType());
    }

    @Test
    void materializeCreatesNewSkillAndVersionAndCopiesFiles() {
        Skill source = new Skill(1L, "review-bot", "owner-1", SkillVisibility.PUBLIC);
        source.setDisplayName("Review Bot");
        SkillVersion sv = new SkillVersion(10L, "1.0.0", "owner-1");
        sv.setStatus(SkillVersionStatus.PUBLISHED);

        when(skillRepository.findById(10L)).thenReturn(Optional.of(source));
        when(skillVersionRepository.findById(20L)).thenReturn(Optional.of(sv));
        when(skillRepository.findByNamespaceIdAndSlugAndOwnerId(99L, "review-bot", "owner-1"))
                .thenReturn(Optional.empty());

        Skill saved = new Skill(99L, "review-bot", "owner-1", SkillVisibility.PUBLIC);
        when(skillRepository.save(any(Skill.class))).thenReturn(saved);
        SkillVersion savedVersion = new SkillVersion(saved.getId(), "1.0.0", "owner-1");
        when(skillVersionRepository.save(any(SkillVersion.class))).thenReturn(savedVersion);
        when(skillFileRepository.findByVersionId(20L)).thenReturn(List.of());

        PromotionRequest req = PromotionRequest.forSkill(10L, 20L, 99L, "user-1");

        MaterializationResult result = materializer.materialize(req);

        assertNotNull(result);
        verify(skillRepository, atLeastOnce()).save(any(Skill.class));
        verify(skillVersionRepository).save(any(SkillVersion.class));
        verify(eventPublisher).publishEvent(any(SkillPublishedEvent.class));
    }

    @Test
    void materializeThrowsOnSlugCollision() {
        Skill source = new Skill(1L, "review-bot", "owner-1", SkillVisibility.PUBLIC);
        SkillVersion sv = new SkillVersion(10L, "1.0.0", "owner-1");
        sv.setStatus(SkillVersionStatus.PUBLISHED);
        when(skillRepository.findById(10L)).thenReturn(Optional.of(sv == null ? null : source));
        when(skillVersionRepository.findById(20L)).thenReturn(Optional.of(sv));
        when(skillRepository.findByNamespaceIdAndSlugAndOwnerId(99L, "review-bot", "owner-1"))
                .thenReturn(Optional.of(source));

        PromotionRequest req = PromotionRequest.forSkill(10L, 20L, 99L, "user-1");

        assertThrows(DomainBadRequestException.class, () -> materializer.materialize(req));
    }
}

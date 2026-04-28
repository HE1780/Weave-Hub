package com.iflytek.skillhub.domain.review;

import com.iflytek.skillhub.domain.governance.GovernanceNotificationService;
import com.iflytek.skillhub.domain.review.materialization.MaterializationResult;
import com.iflytek.skillhub.domain.review.materialization.PromotionMaterializer;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceStatus;
import com.iflytek.skillhub.domain.namespace.NamespaceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PromotionServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-18T11:00:00Z"), ZoneOffset.UTC);

    @Mock private PromotionRequestRepository promotionRequestRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private SkillVersionRepository skillVersionRepository;
    @Mock private NamespaceRepository namespaceRepository;
    @Mock private ReviewPermissionChecker permissionChecker;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private GovernanceNotificationService governanceNotificationService;
    @Mock private EntityManager entityManager;
    @Mock private PromotionMaterializer skillMaterializer;

    private PromotionService promotionService;

    private static final Long SOURCE_SKILL_ID = 10L;
    private static final Long SOURCE_VERSION_ID = 20L;
    private static final Long TARGET_NAMESPACE_ID = 30L;
    private static final String USER_ID = "user-100";
    private static final String REVIEWER_ID = "user-200";
    private static final Long PROMOTION_ID = 1L;
    private static final Long NEW_SKILL_ID = 50L;
    private static final Long NEW_VERSION_ID = 60L;

    @BeforeEach
    void setUp() {
        // Stub the materializer's supportedSourceType up-front so the registry built in
        // PromotionService's constructor knows which slot the bean fills. lenient because
        // not every test path exercises approve.
        org.mockito.Mockito.lenient().when(skillMaterializer.supportedSourceType()).thenReturn(SourceType.SKILL);
        promotionService = new PromotionService(
                promotionRequestRepository, skillRepository, skillVersionRepository,
                namespaceRepository, permissionChecker, eventPublisher,
                governanceNotificationService, entityManager, CLOCK,
                List.of(skillMaterializer));
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Skill createSourceSkill() {
        Skill skill = new Skill(5L, "my-skill", USER_ID, SkillVisibility.NAMESPACE_ONLY);
        setField(skill, "id", SOURCE_SKILL_ID);
        skill.setDisplayName("My Skill");
        skill.setSummary("A test skill");
        return skill;
    }

    private SkillVersion createPublishedVersion() {
        SkillVersion sv = new SkillVersion(SOURCE_SKILL_ID, "1.0.0", USER_ID);
        setField(sv, "id", SOURCE_VERSION_ID);
        sv.setStatus(SkillVersionStatus.PUBLISHED);
        sv.setChangelog("Initial release");
        sv.setParsedMetadataJson("{\"name\":\"test\"}");
        sv.setManifestJson("{\"version\":\"1.0.0\"}");
        sv.setFileCount(3);
        sv.setTotalSize(1024L);
        return sv;
    }

    private Namespace createGlobalNamespace() {
        Namespace ns = new Namespace("global", "Global", "user-1");
        setField(ns, "id", TARGET_NAMESPACE_ID);
        ns.setType(NamespaceType.GLOBAL);
        return ns;
    }

    private Namespace createTeamNamespace() {
        Namespace ns = new Namespace("team-a", "Team A", "user-1");
        setField(ns, "id", TARGET_NAMESPACE_ID);
        // default type is TEAM
        return ns;
    }

    private Namespace createSourceNamespace() {
        Namespace ns = new Namespace("team-a", "Team A", "user-1");
        setField(ns, "id", 5L);
        return ns;
    }

    private PromotionRequest createPendingPromotion() {
        PromotionRequest pr = new PromotionRequest(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID);
        setField(pr, "id", PROMOTION_ID);
        return pr;
    }

    private PromotionRequest approvedPromotion(PromotionRequest original, String comment) {
        PromotionRequest approved = createPendingPromotion();
        approved.setStatus(ReviewTaskStatus.APPROVED);
        approved.setReviewedBy(REVIEWER_ID);
        approved.setReviewComment(comment);
        approved.setReviewedAt(Instant.now(CLOCK));
        setField(approved, "version", original.getVersion() + 1);
        return approved;
    }

    private List<SkillFile> createSourceFiles() {
        return List.of(
                new SkillFile(SOURCE_VERSION_ID, "main.py", 500L, "text/x-python", "sha1", "storage/key1"),
                new SkillFile(SOURCE_VERSION_ID, "config.json", 200L, "application/json", "sha2", "storage/key2")
        );
    }

    @Nested
    class SubmitPromotion {

        @Test
        void shouldSubmitPromotionSuccessfully() {
            Skill sourceSkill = createSourceSkill();
            SkillVersion sourceVersion = createPublishedVersion();
            Namespace globalNs = createGlobalNamespace();

            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(sourceVersion));
            when(namespaceRepository.findById(sourceSkill.getNamespaceId())).thenReturn(Optional.of(createSourceNamespace()));
            when(permissionChecker.canSubmitPromotion(sourceSkill, USER_ID, Map.of())).thenReturn(true);
            when(namespaceRepository.findById(TARGET_NAMESPACE_ID)).thenReturn(Optional.of(globalNs));
            when(promotionRequestRepository.findBySourceSkillIdAndStatus(SOURCE_SKILL_ID, ReviewTaskStatus.PENDING))
                    .thenReturn(Optional.empty());
            when(promotionRequestRepository.findBySourceSkillIdAndStatus(SOURCE_SKILL_ID, ReviewTaskStatus.APPROVED))
                    .thenReturn(Optional.empty());
            when(promotionRequestRepository.save(any(PromotionRequest.class)))
                    .thenAnswer(inv -> {
                        PromotionRequest pr = inv.getArgument(0);
                        setField(pr, "id", PROMOTION_ID);
                        return pr;
                    });

            PromotionRequest result = promotionService.submitPromotion(
                    SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of());

            assertNotNull(result);
            assertEquals(SOURCE_SKILL_ID, result.getSourceSkillId());
            assertEquals(SOURCE_VERSION_ID, result.getSourceVersionId());
            assertEquals(TARGET_NAMESPACE_ID, result.getTargetNamespaceId());
            assertEquals(USER_ID, result.getSubmittedBy());
            verify(promotionRequestRepository).save(any(PromotionRequest.class));
        }

        @Test
        void shouldThrowWhenSourceSkillNotFound() {
            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }

        @Test
        void shouldThrowWhenSourceVersionNotFound() {
            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(createSourceSkill()));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }

        @Test
        void shouldThrowWhenVersionDoesNotBelongToSkill() {
            Skill sourceSkill = createSourceSkill();
            SkillVersion sv = createPublishedVersion();
            setField(sv, "skillId", 999L); // different skill

            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(sv));

            assertThrows(DomainBadRequestException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }

        @Test
        void shouldThrowWhenVersionNotPublished() {
            Skill sourceSkill = createSourceSkill();
            SkillVersion sv = createPublishedVersion();
            sv.setStatus(SkillVersionStatus.DRAFT);

            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(sv));

            assertThrows(DomainBadRequestException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }

        @Test
        void shouldThrowWhenTargetNamespaceNotFound() {
            Skill sourceSkill = createSourceSkill();
            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(createPublishedVersion()));
            when(namespaceRepository.findById(sourceSkill.getNamespaceId())).thenReturn(Optional.of(createSourceNamespace()));
            when(permissionChecker.canSubmitPromotion(sourceSkill, USER_ID, Map.of())).thenReturn(true);
            when(namespaceRepository.findById(TARGET_NAMESPACE_ID)).thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }

        @Test
        void shouldThrowWhenTargetNamespaceNotGlobal() {
            Skill sourceSkill = createSourceSkill();
            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(createPublishedVersion()));
            when(namespaceRepository.findById(sourceSkill.getNamespaceId())).thenReturn(Optional.of(createSourceNamespace()));
            when(permissionChecker.canSubmitPromotion(sourceSkill, USER_ID, Map.of())).thenReturn(true);
            when(namespaceRepository.findById(TARGET_NAMESPACE_ID)).thenReturn(Optional.of(createTeamNamespace()));

            assertThrows(DomainBadRequestException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }

        @Test
        void shouldThrowWhenDuplicatePendingExists() {
            Skill sourceSkill = createSourceSkill();
            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(createPublishedVersion()));
            when(namespaceRepository.findById(sourceSkill.getNamespaceId())).thenReturn(Optional.of(createSourceNamespace()));
            when(permissionChecker.canSubmitPromotion(sourceSkill, USER_ID, Map.of())).thenReturn(true);
            when(namespaceRepository.findById(TARGET_NAMESPACE_ID)).thenReturn(Optional.of(createGlobalNamespace()));
            when(promotionRequestRepository.findBySourceSkillIdAndStatus(SOURCE_SKILL_ID, ReviewTaskStatus.PENDING))
                    .thenReturn(Optional.of(createPendingPromotion()));

            assertThrows(DomainBadRequestException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }

        @Test
        void shouldThrowWhenSkillAlreadyPromoted() {
            Skill sourceSkill = createSourceSkill();
            PromotionRequest approvedPromotion = createPendingPromotion();
            setField(approvedPromotion, "status", ReviewTaskStatus.APPROVED);

            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(createPublishedVersion()));
            when(namespaceRepository.findById(sourceSkill.getNamespaceId())).thenReturn(Optional.of(createSourceNamespace()));
            when(permissionChecker.canSubmitPromotion(sourceSkill, USER_ID, Map.of())).thenReturn(true);
            when(namespaceRepository.findById(TARGET_NAMESPACE_ID)).thenReturn(Optional.of(createGlobalNamespace()));
            when(promotionRequestRepository.findBySourceSkillIdAndStatus(SOURCE_SKILL_ID, ReviewTaskStatus.PENDING))
                    .thenReturn(Optional.empty());
            when(promotionRequestRepository.findBySourceSkillIdAndStatus(SOURCE_SKILL_ID, ReviewTaskStatus.APPROVED))
                    .thenReturn(Optional.of(approvedPromotion));

            assertThrows(DomainBadRequestException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }

        @Test
        void shouldThrowWhenSubmitterIsNotOwnerOrNamespaceAdmin() {
            Skill sourceSkill = createSourceSkill();
            SkillVersion sourceVersion = createPublishedVersion();

            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(sourceVersion));
            when(namespaceRepository.findById(sourceSkill.getNamespaceId())).thenReturn(Optional.of(createSourceNamespace()));
            when(permissionChecker.canSubmitPromotion(
                    sourceSkill,
                    "user-999",
                    Map.of(sourceSkill.getNamespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.MEMBER)))
                    .thenReturn(false);

            assertThrows(DomainForbiddenException.class,
                    () -> promotionService.submitPromotion(
                            SOURCE_SKILL_ID,
                            SOURCE_VERSION_ID,
                            TARGET_NAMESPACE_ID,
                            "user-999",
                            Map.of(sourceSkill.getNamespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.MEMBER)
                    ));
            verify(promotionRequestRepository, never()).save(any(PromotionRequest.class));
        }

        @Test
        void shouldAllowNamespaceAdminToSubmitPromotionForForeignSkill() {
            Skill sourceSkill = createSourceSkill();
            SkillVersion sourceVersion = createPublishedVersion();
            Namespace globalNs = createGlobalNamespace();

            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(sourceVersion));
            when(namespaceRepository.findById(sourceSkill.getNamespaceId())).thenReturn(Optional.of(createSourceNamespace()));
            when(permissionChecker.canSubmitPromotion(
                    sourceSkill,
                    "user-999",
                    Map.of(sourceSkill.getNamespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.ADMIN)))
                    .thenReturn(true);
            when(namespaceRepository.findById(TARGET_NAMESPACE_ID)).thenReturn(Optional.of(globalNs));
            when(promotionRequestRepository.findBySourceSkillIdAndStatus(SOURCE_SKILL_ID, ReviewTaskStatus.PENDING))
                    .thenReturn(Optional.empty());
            when(promotionRequestRepository.findBySourceSkillIdAndStatus(SOURCE_SKILL_ID, ReviewTaskStatus.APPROVED))
                    .thenReturn(Optional.empty());
            when(promotionRequestRepository.save(any(PromotionRequest.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            PromotionRequest result = promotionService.submitPromotion(
                    SOURCE_SKILL_ID,
                    SOURCE_VERSION_ID,
                    TARGET_NAMESPACE_ID,
                    "user-999",
                    Map.of(sourceSkill.getNamespaceId(), com.iflytek.skillhub.domain.namespace.NamespaceRole.ADMIN)
            );

            assertNotNull(result);
        }

        @Test
        void shouldRejectSubmitWhenSourceNamespaceFrozen() {
            Skill sourceSkill = createSourceSkill();
            SkillVersion sourceVersion = createPublishedVersion();
            Namespace sourceNamespace = new Namespace("team-a", "Team A", "user-1");
            setField(sourceNamespace, "id", sourceSkill.getNamespaceId());
            sourceNamespace.setStatus(NamespaceStatus.FROZEN);

            when(skillRepository.findById(SOURCE_SKILL_ID)).thenReturn(Optional.of(sourceSkill));
            when(skillVersionRepository.findById(SOURCE_VERSION_ID)).thenReturn(Optional.of(sourceVersion));
            when(namespaceRepository.findById(sourceSkill.getNamespaceId())).thenReturn(Optional.of(sourceNamespace));

            assertThrows(DomainBadRequestException.class,
                    () -> promotionService.submitPromotion(SOURCE_SKILL_ID, SOURCE_VERSION_ID, TARGET_NAMESPACE_ID, USER_ID, Map.of()));
        }
    }

    @Nested
    class ReviewPromotion {

        @Test
        void shouldNotifySubmitterWhenPromotionApproved() {
            PromotionRequest request = createPendingPromotion();
            PromotionRequest approvedRequest = approvedPromotion(request, "ok");

            when(promotionRequestRepository.findById(PROMOTION_ID))
                    .thenReturn(Optional.of(request), Optional.of(approvedRequest));
            when(permissionChecker.canReviewPromotion(request, REVIEWER_ID, Set.of("SKILL_ADMIN"))).thenReturn(true);
            when(promotionRequestRepository.updateStatusWithVersion(
                    PROMOTION_ID, ReviewTaskStatus.APPROVED, REVIEWER_ID, "ok", null, request.getVersion()))
                    .thenReturn(1);
            when(skillMaterializer.materialize(approvedRequest))
                    .thenReturn(new MaterializationResult(NEW_SKILL_ID));
            when(promotionRequestRepository.save(approvedRequest)).thenReturn(approvedRequest);

            promotionService.approvePromotion(PROMOTION_ID, REVIEWER_ID, "ok", Set.of("SKILL_ADMIN"));

            verify(governanceNotificationService).notifyUser(eq(USER_ID), eq("PROMOTION"), eq("PROMOTION_REQUEST"), eq(PROMOTION_ID), eq("Promotion approved"), any());
        }

        @Test
        void shouldNotifySubmitterWhenPromotionRejected() {
            PromotionRequest request = createPendingPromotion();

            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(request));
            when(permissionChecker.canReviewPromotion(request, REVIEWER_ID, Set.of("SKILL_ADMIN"))).thenReturn(true);
            when(promotionRequestRepository.updateStatusWithVersion(
                    PROMOTION_ID, ReviewTaskStatus.REJECTED, REVIEWER_ID, "no", null, request.getVersion()))
                    .thenReturn(1);
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(request));

            promotionService.rejectPromotion(PROMOTION_ID, REVIEWER_ID, "no", Set.of("SKILL_ADMIN"));

            verify(governanceNotificationService).notifyUser(eq(USER_ID), eq("PROMOTION"), eq("PROMOTION_REQUEST"), eq(PROMOTION_ID), eq("Promotion rejected"), any());
        }
    }

    @Nested
    class ApprovePromotion {

        @Test
        void shouldApprovePromotionSuccessfully() {
            // Field-level materialization assertions live in SkillPromotionMaterializerTest now.
            // PromotionService is responsible for: state machine transition + dispatch + writing
            // targetEntityId back + publishing PromotionApprovedEvent + dispatching notification.
            PromotionRequest pr = createPendingPromotion();
            PromotionRequest approvedRequest = approvedPromotion(pr, "LGTM");

            when(promotionRequestRepository.findById(PROMOTION_ID))
                    .thenReturn(Optional.of(pr), Optional.of(approvedRequest));
            when(permissionChecker.canReviewPromotion(pr, REVIEWER_ID, Set.of("SKILL_ADMIN"))).thenReturn(true);
            when(promotionRequestRepository.updateStatusWithVersion(
                    PROMOTION_ID, ReviewTaskStatus.APPROVED, REVIEWER_ID, "LGTM", null, pr.getVersion()))
                    .thenReturn(1);
            when(skillMaterializer.materialize(approvedRequest))
                    .thenReturn(new MaterializationResult(NEW_SKILL_ID));
            when(promotionRequestRepository.save(approvedRequest)).thenReturn(approvedRequest);

            PromotionRequest result = promotionService.approvePromotion(
                    PROMOTION_ID, REVIEWER_ID, "LGTM", Set.of("SKILL_ADMIN"));

            assertNotNull(result);
            assertEquals(ReviewTaskStatus.APPROVED, result.getStatus());
            assertEquals(REVIEWER_ID, result.getReviewedBy());
            assertEquals("LGTM", result.getReviewComment());
            assertEquals(Instant.now(CLOCK), result.getReviewedAt());

            verify(skillMaterializer).materialize(approvedRequest);
            verify(promotionRequestRepository).save(approvedRequest);
            assertEquals(NEW_SKILL_ID, approvedRequest.getTargetSkillId());
        }

        @Test
        void shouldThrowWhenPromotionNotFound() {
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class,
                    () -> promotionService.approvePromotion(PROMOTION_ID, REVIEWER_ID, "ok", Set.of("SKILL_ADMIN")));
        }

        @Test
        void shouldThrowWhenNotPending() {
            PromotionRequest pr = createPendingPromotion();
            setField(pr, "status", ReviewTaskStatus.APPROVED);
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(pr));

            assertThrows(DomainBadRequestException.class,
                    () -> promotionService.approvePromotion(PROMOTION_ID, REVIEWER_ID, "ok", Set.of("SKILL_ADMIN")));
        }

        @Test
        void shouldThrowWhenNoPermission() {
            PromotionRequest pr = createPendingPromotion();
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(pr));
            when(permissionChecker.canReviewPromotion(pr, REVIEWER_ID, Set.of())).thenReturn(false);

            assertThrows(DomainForbiddenException.class,
                    () -> promotionService.approvePromotion(PROMOTION_ID, REVIEWER_ID, "ok", Set.of()));
        }

        @Test
        void shouldThrowOnConcurrentModification() {
            PromotionRequest pr = createPendingPromotion();
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(pr));
            when(permissionChecker.canReviewPromotion(pr, REVIEWER_ID, Set.of("SKILL_ADMIN"))).thenReturn(true);
            when(promotionRequestRepository.updateStatusWithVersion(
                    any(), any(), any(), any(), any(), any())).thenReturn(0);

            assertThrows(ConcurrentModificationException.class,
                    () -> promotionService.approvePromotion(PROMOTION_ID, REVIEWER_ID, "ok", Set.of("SKILL_ADMIN")));
        }

        @Test
        void shouldPropagateMaterializerBadRequestException() {
            // PromotionService delegates to SkillPromotionMaterializer, which is responsible
            // for throwing DomainBadRequestException on slug-collision (its own test verifies
            // that). Here we verify PromotionService propagates the exception unchanged.
            PromotionRequest pr = createPendingPromotion();
            PromotionRequest approvedRequest = approvedPromotion(pr, "ok");

            when(promotionRequestRepository.findById(PROMOTION_ID))
                    .thenReturn(Optional.of(pr), Optional.of(approvedRequest));
            when(permissionChecker.canReviewPromotion(pr, REVIEWER_ID, Set.of("SKILL_ADMIN"))).thenReturn(true);
            when(promotionRequestRepository.updateStatusWithVersion(
                    PROMOTION_ID, ReviewTaskStatus.APPROVED, REVIEWER_ID, "ok", null, pr.getVersion()))
                    .thenReturn(1);
            when(skillMaterializer.materialize(approvedRequest))
                    .thenThrow(new DomainBadRequestException("promotion.target_skill_conflict", "my-skill"));

            DomainBadRequestException ex = assertThrows(DomainBadRequestException.class,
                    () -> promotionService.approvePromotion(PROMOTION_ID, REVIEWER_ID, "ok", Set.of("SKILL_ADMIN")));

            assertEquals("promotion.target_skill_conflict", ex.messageCode());
        }

        @Test
        void shouldThrowWhenNoMaterializerForSourceType() {
            // Defensive: if a promotion's sourceType has no registered materializer, the
            // service should fail loudly rather than silently no-op.
            PromotionRequest pr = createPendingPromotion();
            PromotionRequest approvedRequest = approvedPromotion(pr, "ok");
            // Force the approved request's sourceType to AGENT — but our test setup only
            // registered a SKILL materializer.
            setField(approvedRequest, "sourceType", SourceType.AGENT);

            when(promotionRequestRepository.findById(PROMOTION_ID))
                    .thenReturn(Optional.of(pr), Optional.of(approvedRequest));
            when(permissionChecker.canReviewPromotion(pr, REVIEWER_ID, Set.of("SKILL_ADMIN"))).thenReturn(true);
            when(promotionRequestRepository.updateStatusWithVersion(
                    any(), any(), any(), any(), any(), any())).thenReturn(1);

            assertThrows(IllegalStateException.class,
                    () -> promotionService.approvePromotion(PROMOTION_ID, REVIEWER_ID, "ok", Set.of("SKILL_ADMIN")));
        }

    }

    @Nested
    class RejectPromotion {

        @Test
        void shouldRejectPromotionSuccessfully() {
            PromotionRequest pr = createPendingPromotion();
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(pr));
            when(permissionChecker.canReviewPromotion(pr, REVIEWER_ID, Set.of("SKILL_ADMIN"))).thenReturn(true);
            when(promotionRequestRepository.updateStatusWithVersion(
                    PROMOTION_ID, ReviewTaskStatus.REJECTED, REVIEWER_ID, "Not ready", null, pr.getVersion()))
                    .thenReturn(1);
            PromotionRequest result = promotionService.rejectPromotion(
                    PROMOTION_ID, REVIEWER_ID, "Not ready", Set.of("SKILL_ADMIN"));

            assertNotNull(result);
            assertEquals(ReviewTaskStatus.REJECTED, result.getStatus());
            assertEquals(REVIEWER_ID, result.getReviewedBy());
            assertEquals("Not ready", result.getReviewComment());
            assertEquals(Instant.now(CLOCK), result.getReviewedAt());
            verify(promotionRequestRepository).updateStatusWithVersion(
                    PROMOTION_ID, ReviewTaskStatus.REJECTED, REVIEWER_ID, "Not ready", null, pr.getVersion());
            verify(eventPublisher, never()).publishEvent(any());
        }

        @Test
        void shouldThrowWhenPromotionNotFound() {
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.empty());

            assertThrows(DomainNotFoundException.class,
                    () -> promotionService.rejectPromotion(PROMOTION_ID, REVIEWER_ID, "no", Set.of("SKILL_ADMIN")));
        }

        @Test
        void shouldThrowWhenNotPending() {
            PromotionRequest pr = createPendingPromotion();
            setField(pr, "status", ReviewTaskStatus.REJECTED);
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(pr));

            assertThrows(DomainBadRequestException.class,
                    () -> promotionService.rejectPromotion(PROMOTION_ID, REVIEWER_ID, "no", Set.of("SKILL_ADMIN")));
        }

        @Test
        void shouldThrowWhenNoPermission() {
            PromotionRequest pr = createPendingPromotion();
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(pr));
            when(permissionChecker.canReviewPromotion(pr, REVIEWER_ID, Set.of())).thenReturn(false);

            assertThrows(DomainForbiddenException.class,
                    () -> promotionService.rejectPromotion(PROMOTION_ID, REVIEWER_ID, "no", Set.of()));
        }

        @Test
        void shouldThrowOnConcurrentModification() {
            PromotionRequest pr = createPendingPromotion();
            when(promotionRequestRepository.findById(PROMOTION_ID)).thenReturn(Optional.of(pr));
            when(permissionChecker.canReviewPromotion(pr, REVIEWER_ID, Set.of("SKILL_ADMIN"))).thenReturn(true);
            when(promotionRequestRepository.updateStatusWithVersion(any(), any(), any(), any(), any(), any()))
                    .thenReturn(0);

            assertThrows(ConcurrentModificationException.class,
                    () -> promotionService.rejectPromotion(PROMOTION_ID, REVIEWER_ID, "no", Set.of("SKILL_ADMIN")));
        }
    }
}

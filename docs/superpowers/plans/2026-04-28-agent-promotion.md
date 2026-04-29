# A9 — Agent Promotion Implementation Plan

> ✅ **SHIPPED — 已完成 2026-04-28。** 19 task 全部落地(commits `b3cba0c5` → `ddb82067`),backend 554/554 + web 684/684 passing。**ADR:** [docs/adr/0004-agent-promotion.md](../../adr/0004-agent-promotion.md)。详见 [docs/plans/2026-04-29-spec-status-ledger.md](../../plans/2026-04-29-spec-status-ledger.md)。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Extend the existing skill-only promotion subsystem to also promote Agents, using a discriminator column + strategy-pattern materializer so the state machine, authorization, audit, events, and notifications are reused unchanged.

**Architecture:** Schema stays single-table (`promotion_request`); a `source_type` discriminator + parallel nullable `source_agent_*`/`target_agent_id` columns capture the agent path. `PromotionService` keeps its state/auth/event/notify behavior; only `approvePromotion()` delegates to one of two `PromotionMaterializer` strategy beans (`SkillPromotionMaterializer`, extracted from current code; `AgentPromotionMaterializer`, new). Frontend reuses the unified review queue with a source-type badge; agent-detail gains a "Promote" button.

**Tech Stack:** Java 17 + Spring Boot 3 (server), PostgreSQL + Flyway (migrations), JUnit 5 + Mockito (backend tests), React 18 + Vite + TanStack Query + i18next (web), Vitest + Testing Library (web tests).

**Spec:** [docs/superpowers/specs/2026-04-28-agent-promotion-design.md](../specs/2026-04-28-agent-promotion-design.md)

**Critical reality-check note for the implementer:**
- The next available Flyway version is **V49** (V48 is already taken by `V48__agent_download_counters.sql`). All migrations in this plan use V49.
- Existing `PromotionService.submitPromotion` uses `findBySourceSkillIdAndStatus` (not `findBySourceVersionIdAndStatus`) for the duplicate-pending check. The agent path mirrors this with `findBySourceAgentIdAndStatus`.
- `Agent` and `AgentVersion` constructors are full-arg — there are no setters for slug/namespace/ownerId on Agent and no `setStatus` on AgentVersion. Materialization uses `autoPublish()` to transition the new AgentVersion's state.

---

## Task 1: Add SourceType enum

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/SourceType.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/SourceTypeTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.iflytek.skillhub.domain.review;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceTypeTest {
    @Test
    void enumValuesArePresent() {
        assertEquals(SourceType.SKILL, SourceType.valueOf("SKILL"));
        assertEquals(SourceType.AGENT, SourceType.valueOf("AGENT"));
        assertEquals(2, SourceType.values().length);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl server/skillhub-domain test -Dtest=SourceTypeTest`
Expected: FAIL with "cannot find symbol class SourceType"

- [ ] **Step 3: Create the enum**

```java
package com.iflytek.skillhub.domain.review;

public enum SourceType {
    SKILL, AGENT
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl server/skillhub-domain test -Dtest=SourceTypeTest`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/SourceType.java \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/SourceTypeTest.java
git commit -m "feat(domain): add SourceType enum (SKILL | AGENT) for promotion discriminator"
```

---

## Task 2: SKIPPED — AgentPublishedEvent already exists

**Plan correction (2026-04-28):** `AgentPublishedEvent` already exists in the codebase at `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/AgentPublishedEvent.java` with shape `(Long agentId, Long agentVersionId, Long namespaceId, String publisherId, Instant publishedAt)` — published today by `AgentPublishService`, `AgentLifecycleService`, and `AgentReviewService`.

**Action**: Skip this task. Reuse the existing event. Task 9 (AgentPromotionMaterializer) is updated to call the existing 5-arg constructor.

A revert commit (`git revert 2efd73c8`) restored the file after an early exploratory pass overwrote it with a narrower 3-field shape that broke compile in 3 callers.

---

## Task 3: V49 schema migration — add discriminator + agent columns

**Files:**
- Create: `server/skillhub-app/src/main/resources/db/migration/V49__agent_promotion.sql`

- [ ] **Step 1: Create the migration**

```sql
-- A9 Agent Promotion: extend promotion_request to support agent source.
-- Adds discriminator + nullable parallel agent columns, a CHECK constraint
-- enforcing the discriminator semantics, and reworks the partial unique
-- index that previously assumed skill-only.

ALTER TABLE promotion_request
  ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT 'SKILL',
  ADD COLUMN source_agent_id BIGINT NULL REFERENCES agent(id),
  ADD COLUMN source_agent_version_id BIGINT NULL REFERENCES agent_version(id),
  ADD COLUMN target_agent_id BIGINT NULL REFERENCES agent(id);

ALTER TABLE promotion_request
  ALTER COLUMN source_skill_id DROP NOT NULL,
  ALTER COLUMN source_version_id DROP NOT NULL;

ALTER TABLE promotion_request
  ADD CONSTRAINT promotion_request_source_consistency CHECK (
    (source_type = 'SKILL' AND source_skill_id IS NOT NULL
                            AND source_version_id IS NOT NULL
                            AND source_agent_id IS NULL
                            AND source_agent_version_id IS NULL)
    OR
    (source_type = 'AGENT' AND source_agent_id IS NOT NULL
                            AND source_agent_version_id IS NOT NULL
                            AND source_skill_id IS NULL
                            AND source_version_id IS NULL)
  );

DROP INDEX IF EXISTS idx_promotion_request_version_pending;

CREATE UNIQUE INDEX promotion_request_pending_skill_version_uq
  ON promotion_request(source_version_id)
  WHERE status = 'PENDING' AND source_type = 'SKILL';

CREATE UNIQUE INDEX promotion_request_pending_agent_version_uq
  ON promotion_request(source_agent_version_id)
  WHERE status = 'PENDING' AND source_type = 'AGENT';
```

**Index-name note:** The actual existing index in V3 is named `idx_promotion_request_version_pending` (verified by `grep "UNIQUE INDEX" server/skillhub-app/src/main/resources/db/migration/V3__phase3_review_social_tables.sql`). The DROP statement above uses that exact name — do NOT change it. If you alter the name, the old index will silently survive and create constraint duplication.

- [ ] **Step 2: Verify Flyway can parse & run**

Run: `mvn -pl server/skillhub-app spring-boot:run` (in another terminal — let it boot, then Ctrl-C). Or, if a faster local DB harness exists:

Run: `mvn -pl server/skillhub-app test -Dtest=*Flyway*`
Expected: Migration applies cleanly. If it fails on the `DROP INDEX`, double-check the index name in V3.

- [ ] **Step 3: Commit**

```bash
git add server/skillhub-app/src/main/resources/db/migration/V49__agent_promotion.sql
git commit -m "feat(db): V49 promotion_request discriminator + agent columns + CHECK"
```

---

## Task 4: Extend PromotionRequest entity (fields, factories, helpers)

The default constructor (line 51) currently takes the four skill-side fields. We keep it for backward compat (existing callers) but add static factories and the new agent-side columns.

**Files:**
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionRequest.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/PromotionRequestTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.iflytek.skillhub.domain.review;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PromotionRequestTest {

    @Test
    void forSkillSetsSkillFieldsAndType() {
        PromotionRequest r = PromotionRequest.forSkill(11L, 22L, 33L, "user-1");
        assertEquals(SourceType.SKILL, r.getSourceType());
        assertEquals(11L, r.getSourceSkillId());
        assertEquals(22L, r.getSourceVersionId());
        assertEquals(33L, r.getTargetNamespaceId());
        assertEquals("user-1", r.getSubmittedBy());
        assertNull(r.getSourceAgentId());
        assertNull(r.getSourceAgentVersionId());
    }

    @Test
    void forAgentSetsAgentFieldsAndType() {
        PromotionRequest r = PromotionRequest.forAgent(11L, 22L, 33L, "user-1");
        assertEquals(SourceType.AGENT, r.getSourceType());
        assertEquals(11L, r.getSourceAgentId());
        assertEquals(22L, r.getSourceAgentVersionId());
        assertEquals(33L, r.getTargetNamespaceId());
        assertEquals("user-1", r.getSubmittedBy());
        assertNull(r.getSourceSkillId());
        assertNull(r.getSourceVersionId());
    }

    @Test
    void setTargetEntityIdWritesSkillSlot() {
        PromotionRequest r = PromotionRequest.forSkill(1L, 2L, 3L, "u");
        r.setTargetEntityId(99L, SourceType.SKILL);
        assertEquals(99L, r.getTargetSkillId());
        assertNull(r.getTargetAgentId());
    }

    @Test
    void setTargetEntityIdWritesAgentSlot() {
        PromotionRequest r = PromotionRequest.forAgent(1L, 2L, 3L, "u");
        r.setTargetEntityId(99L, SourceType.AGENT);
        assertEquals(99L, r.getTargetAgentId());
        assertNull(r.getTargetSkillId());
    }

    @Test
    void legacyConstructorDefaultsToSkillSourceType() {
        PromotionRequest r = new PromotionRequest(11L, 22L, 33L, "user-1");
        assertEquals(SourceType.SKILL, r.getSourceType());
        assertEquals(11L, r.getSourceSkillId());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl server/skillhub-domain test -Dtest=PromotionRequestTest`
Expected: FAIL — `forSkill` / `forAgent` / `setTargetEntityId` / `getSourceType` etc. not found.

- [ ] **Step 3: Replace `PromotionRequest.java` with**

```java
package com.iflytek.skillhub.domain.review;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "promotion_request")
public class PromotionRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 16)
    private SourceType sourceType = SourceType.SKILL;

    @Column(name = "source_skill_id")
    private Long sourceSkillId;

    @Column(name = "source_version_id")
    private Long sourceVersionId;

    @Column(name = "source_agent_id")
    private Long sourceAgentId;

    @Column(name = "source_agent_version_id")
    private Long sourceAgentVersionId;

    @Column(name = "target_namespace_id", nullable = false)
    private Long targetNamespaceId;

    @Column(name = "target_skill_id")
    private Long targetSkillId;

    @Column(name = "target_agent_id")
    private Long targetAgentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewTaskStatus status = ReviewTaskStatus.PENDING;

    @Version
    @Column(nullable = false)
    private Integer version = 1;

    @Column(name = "submitted_by", nullable = false)
    private String submittedBy;

    @Column(name = "reviewed_by")
    private String reviewedBy;

    @Column(name = "review_comment", columnDefinition = "TEXT")
    private String reviewComment;

    @Column(name = "submitted_at", nullable = false)
    private Instant submittedAt = Instant.now();

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    protected PromotionRequest() {}

    /**
     * Legacy skill-only constructor — kept for backward compatibility.
     * Defaults sourceType to SKILL.
     */
    public PromotionRequest(Long sourceSkillId, Long sourceVersionId,
                            Long targetNamespaceId, String submittedBy) {
        this.sourceType = SourceType.SKILL;
        this.sourceSkillId = sourceSkillId;
        this.sourceVersionId = sourceVersionId;
        this.targetNamespaceId = targetNamespaceId;
        this.submittedBy = submittedBy;
    }

    public static PromotionRequest forSkill(Long sourceSkillId, Long sourceVersionId,
                                            Long targetNamespaceId, String submittedBy) {
        PromotionRequest r = new PromotionRequest();
        r.sourceType = SourceType.SKILL;
        r.sourceSkillId = sourceSkillId;
        r.sourceVersionId = sourceVersionId;
        r.targetNamespaceId = targetNamespaceId;
        r.submittedBy = submittedBy;
        return r;
    }

    public static PromotionRequest forAgent(Long sourceAgentId, Long sourceAgentVersionId,
                                            Long targetNamespaceId, String submittedBy) {
        PromotionRequest r = new PromotionRequest();
        r.sourceType = SourceType.AGENT;
        r.sourceAgentId = sourceAgentId;
        r.sourceAgentVersionId = sourceAgentVersionId;
        r.targetNamespaceId = targetNamespaceId;
        r.submittedBy = submittedBy;
        return r;
    }

    public Long getId() { return id; }
    public SourceType getSourceType() { return sourceType; }
    public Long getSourceSkillId() { return sourceSkillId; }
    public Long getSourceVersionId() { return sourceVersionId; }
    public Long getSourceAgentId() { return sourceAgentId; }
    public Long getSourceAgentVersionId() { return sourceAgentVersionId; }
    public Long getTargetNamespaceId() { return targetNamespaceId; }
    public Long getTargetSkillId() { return targetSkillId; }
    public Long getTargetAgentId() { return targetAgentId; }

    public void setTargetSkillId(Long targetSkillId) { this.targetSkillId = targetSkillId; }
    public void setTargetAgentId(Long targetAgentId) { this.targetAgentId = targetAgentId; }

    /**
     * Writes the materialized target entity id into the slot matching the
     * promotion's source type.
     */
    public void setTargetEntityId(Long id, SourceType type) {
        if (type == SourceType.SKILL) {
            this.targetSkillId = id;
        } else {
            this.targetAgentId = id;
        }
    }

    public ReviewTaskStatus getStatus() { return status; }
    public void setStatus(ReviewTaskStatus status) { this.status = status; }

    public Integer getVersion() { return version; }

    public String getSubmittedBy() { return submittedBy; }
    public String getReviewedBy() { return reviewedBy; }
    public void setReviewedBy(String reviewedBy) { this.reviewedBy = reviewedBy; }

    public String getReviewComment() { return reviewComment; }
    public void setReviewComment(String reviewComment) { this.reviewComment = reviewComment; }

    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(Instant reviewedAt) { this.reviewedAt = reviewedAt; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl server/skillhub-domain test -Dtest=PromotionRequestTest`
Expected: PASS (5 tests)

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionRequest.java \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/PromotionRequestTest.java
git commit -m "feat(domain): PromotionRequest gains forAgent factory + sourceType discriminator"
```

---

## Task 5: Add agent-source repository methods to PromotionRequestRepository

**Files:**
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionRequestRepository.java`
- Modify: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/review/PromotionRequestJpaRepository.java` (or wherever the JPA impl lives — find with `grep -rl "PromotionRequestRepository" server/skillhub-infra/src/main/java/`)

- [ ] **Step 1: Find the JPA implementation**

Run: `grep -rln "implements PromotionRequestRepository\|extends.*PromotionRequest" server/skillhub-infra/src/main/java/`
Expected output: one file path. Open it.

- [ ] **Step 2: Read the existing repository interface**

```bash
cat server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionRequestRepository.java
```

Note the existing method names. The new methods to add are:

```java
Optional<PromotionRequest> findBySourceAgentIdAndStatus(Long agentId, ReviewTaskStatus status);
Page<PromotionRequest> findByStatusAndSourceType(
    ReviewTaskStatus status, SourceType sourceType, Pageable pageable);
```

**Repository-shape note (verified 2026-04-28)**: The existing list endpoints use `findByStatus(status, pageable)` — there is NO namespace dimension. The new method mirrors that shape, just adding a `sourceType` filter. The plan originally proposed `findByTargetNamespaceIdAndStatusAndSourceType` but the current `PromotionService` and `PromotionPortalAppService` don't filter by namespace. Tasks 13 (controller) is updated below to match.

- [ ] **Step 3: Add to the domain interface**

Add the two method declarations above to `PromotionRequestRepository`. Add the import `import com.iflytek.skillhub.domain.review.SourceType;` if needed (same package — not needed). Add `import org.springframework.data.domain.Page;` and `Pageable` if not already present.

- [ ] **Step 4: Add to the JPA implementation**

If the JPA impl is a Spring Data interface (extends `JpaRepository`), Spring derives the methods from the names — just declare them. If it's a hand-written class, add JPQL/criteria implementations:

```java
@Override
public Optional<PromotionRequest> findBySourceAgentIdAndStatus(Long agentId, ReviewTaskStatus status) {
    // For Spring Data repository, this is method-name-derived. Otherwise:
    return entityManager.createQuery(
        "SELECT p FROM PromotionRequest p WHERE p.sourceAgentId = :agentId AND p.status = :status",
        PromotionRequest.class)
        .setParameter("agentId", agentId)
        .setParameter("status", status)
        .getResultStream().findFirst();
}

@Override
public Page<PromotionRequest> findByStatusAndSourceType(
        ReviewTaskStatus status, SourceType sourceType, Pageable pageable) {
    // Spring-Data-derived from method name; no manual JPQL needed for the JpaRepository case.
}
```

- [ ] **Step 5: Add a repository test exercising the new methods**

If a `PromotionRequestRepositoryTest` exists in `server/skillhub-infra/src/test/java/.../review/`, extend it. Otherwise create one. Two cases:

```java
@Test
void findBySourceAgentIdAndStatusReturnsMatchingPending() {
    PromotionRequest r = PromotionRequest.forAgent(101L, 202L, 1L, "u");
    promotionRequestRepository.save(r);
    Optional<PromotionRequest> found = promotionRequestRepository
            .findBySourceAgentIdAndStatus(101L, ReviewTaskStatus.PENDING);
    assertTrue(found.isPresent());
    assertEquals(SourceType.AGENT, found.get().getSourceType());
}

@Test
void findByStatusAndSourceTypeFiltersByType() {
    promotionRequestRepository.save(PromotionRequest.forSkill(1L, 2L, 99L, "u"));
    promotionRequestRepository.save(PromotionRequest.forAgent(3L, 4L, 99L, "u"));

    Page<PromotionRequest> agents = promotionRequestRepository
            .findByStatusAndSourceType(
                    ReviewTaskStatus.PENDING, SourceType.AGENT, Pageable.unpaged());
    assertEquals(1, agents.getTotalElements());
    assertEquals(SourceType.AGENT, agents.getContent().get(0).getSourceType());
}
```

If no integration-test infrastructure exists for repositories in this codebase (check by running `find server -name "*RepositoryTest.java" -path "*/skillhub-infra/*"`), defer this test per the spec §9.3 logic — note in the commit message and rely on the materializer + service tests for coverage.

- [ ] **Step 6: Run tests + compile**

Run: `mvn -pl server/skillhub-domain,server/skillhub-infra test`
Expected: All existing tests + the new ones pass.

- [ ] **Step 7: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionRequestRepository.java \
        server/skillhub-infra/  # whatever JPA file changed
git commit -m "feat(domain): repository methods for agent-source promotion lookups"
```

---

## Task 6: Add PromotionMaterializer interface + result record

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/materialization/PromotionMaterializer.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/materialization/MaterializationResult.java`

- [ ] **Step 1: Create the result record**

```java
package com.iflytek.skillhub.domain.review.materialization;

public record MaterializationResult(Long targetEntityId) {}
```

- [ ] **Step 2: Create the interface**

```java
package com.iflytek.skillhub.domain.review.materialization;

import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.SourceType;

public interface PromotionMaterializer {
    SourceType supportedSourceType();
    MaterializationResult materialize(PromotionRequest request);
}
```

- [ ] **Step 3: Verify it compiles**

Run: `mvn -pl server/skillhub-domain compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/materialization/
git commit -m "feat(domain): PromotionMaterializer strategy interface + MaterializationResult"
```

---

## Task 7: Extract SkillPromotionMaterializer from PromotionService

This extraction is **behavior-preserving**. We move lines 210–250 of `PromotionService.approvePromotion()` into a new `SkillPromotionMaterializer` class. The `PromotionService` keeps the call but delegates.

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/materialization/SkillPromotionMaterializer.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/materialization/SkillPromotionMaterializerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.iflytek.skillhub.domain.review.materialization;

import com.iflytek.skillhub.domain.event.SkillPublishedEvent;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.SourceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.skill.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;

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
        when(skillRepository.findById(10L)).thenReturn(Optional.of(source));
        when(skillVersionRepository.findById(20L)).thenReturn(Optional.of(sv));
        when(skillRepository.findByNamespaceIdAndSlugAndOwnerId(99L, "review-bot", "owner-1"))
                .thenReturn(Optional.of(source));

        PromotionRequest req = PromotionRequest.forSkill(10L, 20L, 99L, "user-1");

        assertThrows(DomainBadRequestException.class, () -> materializer.materialize(req));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl server/skillhub-domain test -Dtest=SkillPromotionMaterializerTest`
Expected: FAIL — `SkillPromotionMaterializer` not found.

- [ ] **Step 3: Create the materializer**

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl server/skillhub-domain test -Dtest=SkillPromotionMaterializerTest`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/materialization/SkillPromotionMaterializer.java \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/materialization/SkillPromotionMaterializerTest.java
git commit -m "feat(domain): extract SkillPromotionMaterializer (behavior-preserving from PromotionService)"
```

---

## Task 8: SKIPPED — investigation complete, see facts below

**Plan correction (2026-04-28):** Pre-execution audit confirmed the following entity shapes; bake these directly into Task 9. No separate investigation step needed.

**Confirmed facts (verified at the indicated file paths):**

- `AgentVersionStats(Long agentVersionId, Long agentId)` — single 2-arg constructor; `downloadCount` defaults to 0 internally. (`server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentVersionStats.java`)
- `AgentTagRepository.findByAgentId(Long agentId)` returns `List<AgentTag>`. `AgentTag` constructor `(Long agentId, String tagName, Long versionId, String createdBy)`. (Confirmed via `AgentTagRepository.java`.)
- `AgentLabelRepository.findByAgentId(Long agentId)` returns `List<AgentLabel>`. `AgentLabel` constructor `(Long agentId, Long labelId, String createdBy)` — note the second parameter is **`labelId`** (not `labelDefinitionId`). (Confirmed via `AgentLabel.java:30`.)
- `LabelDefinition` **has no namespace concept** — fields are `id`, `slug`, `type`, `visibleInFilter`, `sortOrder`, `createdBy`, timestamps. There is no `namespaceId`, no `isPlatform()`, no scope discriminator. All labels are platform-scope today. (Confirmed via `LabelDefinition.java`.)

**Implication for the materializer:** The spec §6.4 step 6 originally said "filter labels by namespace scope". Since LabelDefinition has no namespace concept, the filter is a no-op — copy ALL source agent labels to the new agent. If namespace-scoped labels become a thing later, that's a separate spec.

Spec §6.4 has been updated to reflect this; spec §3 lists "label namespace scoping" as an explicit non-goal.

---

## Task 9: Add AgentPromotionMaterializer

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/materialization/AgentPromotionMaterializer.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/materialization/AgentPromotionMaterializerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.iflytek.skillhub.domain.review.materialization;

import com.iflytek.skillhub.domain.agent.*;
import com.iflytek.skillhub.domain.event.AgentPublishedEvent;
import com.iflytek.skillhub.domain.label.AgentLabel;
import com.iflytek.skillhub.domain.label.AgentLabelRepository;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.SourceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
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

class AgentPromotionMaterializerTest {

    private AgentRepository agentRepository;
    private AgentVersionRepository agentVersionRepository;
    private AgentVersionStatsRepository agentVersionStatsRepository;
    private AgentTagRepository agentTagRepository;
    private AgentLabelRepository agentLabelRepository;
    private ApplicationEventPublisher eventPublisher;
    private AgentPromotionMaterializer materializer;

    @BeforeEach
    void setup() {
        agentRepository = mock(AgentRepository.class);
        agentVersionRepository = mock(AgentVersionRepository.class);
        agentVersionStatsRepository = mock(AgentVersionStatsRepository.class);
        agentTagRepository = mock(AgentTagRepository.class);
        agentLabelRepository = mock(AgentLabelRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        materializer = new AgentPromotionMaterializer(
                agentRepository, agentVersionRepository, agentVersionStatsRepository,
                agentTagRepository, agentLabelRepository, eventPublisher,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void supportsAgentSourceType() {
        assertEquals(SourceType.AGENT, materializer.supportedSourceType());
    }

    @Test
    void materializeCreatesNewAgentVersionAndStatsAndPublishesEvent() {
        Agent source = new Agent(1L, "review-bot", "Review Bot", "owner-1", AgentVisibility.PRIVATE);
        AgentVersion sourceVersion = new AgentVersion(10L, "1.0.0", "owner-1",
                "manifest", "soul", "workflow", "objects/abc", 1024L);
        // mark as PUBLISHED via state machine to satisfy real lifecycle constraints
        sourceVersion.autoPublish();

        Agent saved = new Agent(99L, "review-bot", "Review Bot", "owner-1", AgentVisibility.PUBLIC);
        AgentVersion savedVersion = new AgentVersion(saved.getId() == null ? 999L : saved.getId(),
                "1.0.0", "owner-1", "manifest", "soul", "workflow", "objects/abc", 1024L);

        when(agentRepository.findById(10L)).thenReturn(Optional.of(source));
        when(agentVersionRepository.findById(20L)).thenReturn(Optional.of(sourceVersion));
        when(agentRepository.findByNamespaceIdAndSlug(99L, "review-bot")).thenReturn(Optional.empty());
        when(agentRepository.save(any(Agent.class))).thenReturn(saved);
        when(agentVersionRepository.save(any(AgentVersion.class))).thenReturn(savedVersion);
        when(agentTagRepository.findByAgentId(10L)).thenReturn(List.of());
        when(agentLabelRepository.findByAgentId(10L)).thenReturn(List.of());

        PromotionRequest req = PromotionRequest.forAgent(10L, 20L, 99L, "user-1");

        MaterializationResult result = materializer.materialize(req);

        assertNotNull(result);
        verify(agentRepository).save(any(Agent.class));
        verify(agentVersionRepository).save(any(AgentVersion.class));
        verify(agentVersionStatsRepository).save(any(AgentVersionStats.class));
        verify(eventPublisher).publishEvent(any(AgentPublishedEvent.class));
    }

    @Test
    void materializeThrowsOnSlugCollision() {
        Agent source = new Agent(1L, "review-bot", "Review Bot", "owner-1", AgentVisibility.PRIVATE);
        AgentVersion sourceVersion = new AgentVersion(10L, "1.0.0", "owner-1",
                "manifest", "soul", "workflow", "objects/abc", 1024L);
        sourceVersion.autoPublish();

        when(agentRepository.findById(10L)).thenReturn(Optional.of(source));
        when(agentVersionRepository.findById(20L)).thenReturn(Optional.of(sourceVersion));
        when(agentRepository.findByNamespaceIdAndSlug(99L, "review-bot"))
                .thenReturn(Optional.of(source));

        PromotionRequest req = PromotionRequest.forAgent(10L, 20L, 99L, "user-1");

        assertThrows(DomainBadRequestException.class, () -> materializer.materialize(req));
    }

    @Test
    void copiesAllAgentLabelsToNewAgent() {
        Agent source = new Agent(1L, "review-bot", "Review Bot", "owner-1", AgentVisibility.PRIVATE);
        AgentVersion sourceVersion = new AgentVersion(10L, "1.0.0", "owner-1",
                "manifest", "soul", "workflow", "objects/abc", 1024L);
        sourceVersion.autoPublish();

        AgentLabel a = new AgentLabel(source.getId(), 100L, "owner-1");
        AgentLabel b = new AgentLabel(source.getId(), 101L, "owner-1");

        when(agentRepository.findById(10L)).thenReturn(Optional.of(source));
        when(agentVersionRepository.findById(20L)).thenReturn(Optional.of(sourceVersion));
        when(agentRepository.findByNamespaceIdAndSlug(99L, "review-bot")).thenReturn(Optional.empty());
        Agent saved = new Agent(99L, "review-bot", "Review Bot", "owner-1", AgentVisibility.PUBLIC);
        when(agentRepository.save(any(Agent.class))).thenReturn(saved);
        when(agentVersionRepository.save(any(AgentVersion.class)))
                .thenReturn(new AgentVersion(saved.getId(), "1.0.0", "owner-1",
                        "manifest", "soul", "workflow", "objects/abc", 1024L));
        when(agentTagRepository.findByAgentId(10L)).thenReturn(List.of());
        when(agentLabelRepository.findByAgentId(10L)).thenReturn(List.of(a, b));

        PromotionRequest req = PromotionRequest.forAgent(10L, 20L, 99L, "user-1");
        materializer.materialize(req);

        // All source labels copied to the new agent (LabelDefinition has no namespace concept;
        // there is no scope filter to apply).
        verify(agentLabelRepository, times(2)).save(any(AgentLabel.class));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl server/skillhub-domain test -Dtest=AgentPromotionMaterializerTest`
Expected: FAIL — `AgentPromotionMaterializer` not found.

- [ ] **Step 3: Create the materializer**

```java
package com.iflytek.skillhub.domain.review.materialization;

import com.iflytek.skillhub.domain.agent.*;
import com.iflytek.skillhub.domain.event.AgentPublishedEvent;
import com.iflytek.skillhub.domain.label.AgentLabel;
import com.iflytek.skillhub.domain.label.AgentLabelRepository;
import com.iflytek.skillhub.domain.review.PromotionRequest;
import com.iflytek.skillhub.domain.review.SourceType;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;

@Component
public class AgentPromotionMaterializer implements PromotionMaterializer {

    private final AgentRepository agentRepository;
    private final AgentVersionRepository agentVersionRepository;
    private final AgentVersionStatsRepository agentVersionStatsRepository;
    private final AgentTagRepository agentTagRepository;
    private final AgentLabelRepository agentLabelRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AgentPromotionMaterializer(AgentRepository agentRepository,
                                      AgentVersionRepository agentVersionRepository,
                                      AgentVersionStatsRepository agentVersionStatsRepository,
                                      AgentTagRepository agentTagRepository,
                                      AgentLabelRepository agentLabelRepository,
                                      ApplicationEventPublisher eventPublisher,
                                      Clock clock) {
        this.agentRepository = agentRepository;
        this.agentVersionRepository = agentVersionRepository;
        this.agentVersionStatsRepository = agentVersionStatsRepository;
        this.agentTagRepository = agentTagRepository;
        this.agentLabelRepository = agentLabelRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Override
    public SourceType supportedSourceType() {
        return SourceType.AGENT;
    }

    @Override
    public MaterializationResult materialize(PromotionRequest request) {
        Agent source = agentRepository.findById(request.getSourceAgentId())
                .orElseThrow(() -> new DomainNotFoundException("agent.not_found", request.getSourceAgentId()));
        AgentVersion sourceVersion = agentVersionRepository.findById(request.getSourceAgentVersionId())
                .orElseThrow(() -> new DomainNotFoundException("agent_version.not_found", request.getSourceAgentVersionId()));

        if (sourceVersion.getStatus() != AgentVersionStatus.PUBLISHED) {
            throw new DomainBadRequestException("promotion.version_not_published", request.getSourceAgentVersionId());
        }

        agentRepository.findByNamespaceIdAndSlug(request.getTargetNamespaceId(), source.getSlug())
                .ifPresent(existing -> {
                    throw new DomainBadRequestException("promotion.target_agent_conflict", source.getSlug());
                });

        Agent newAgent = new Agent(request.getTargetNamespaceId(), source.getSlug(),
                source.getDisplayName(), source.getOwnerId(), AgentVisibility.PUBLIC);
        if (source.getDescription() != null) {
            newAgent.setDescription(source.getDescription());
        }
        newAgent = agentRepository.save(newAgent);

        AgentVersion newVersion = new AgentVersion(newAgent.getId(), sourceVersion.getVersion(),
                request.getSubmittedBy(), sourceVersion.getManifestYaml(),
                sourceVersion.getSoulMd(), sourceVersion.getWorkflowYaml(),
                sourceVersion.getPackageObjectKey(), sourceVersion.getPackageSizeBytes());
        newVersion.autoPublish();   // DRAFT → PUBLISHED + publishedAt
        newVersion = agentVersionRepository.save(newVersion);

        // Reset stats for the materialized agent — never inherit source download counts
        agentVersionStatsRepository.save(new AgentVersionStats(newVersion.getId(), newAgent.getId()));

        // Copy labels — LabelDefinition has no namespace concept (all labels are platform-scope
        // today). Copy all source labels to the new agent verbatim. If LabelDefinition gains a
        // namespace scope later, this is the natural place to filter.
        List<AgentLabel> sourceLabels = agentLabelRepository.findByAgentId(source.getId());
        Long newAgentId = newAgent.getId();
        for (AgentLabel link : sourceLabels) {
            agentLabelRepository.save(new AgentLabel(newAgentId, link.getLabelId(),
                    request.getSubmittedBy()));
        }

        // Copy tags — they are owned by the agent itself, all follow.
        List<AgentTag> sourceTags = agentTagRepository.findByAgentId(source.getId());
        Long newVersionId = newVersion.getId();
        for (AgentTag t : sourceTags) {
            agentTagRepository.save(new AgentTag(newAgentId, t.getTagName(), newVersionId,
                    request.getSubmittedBy()));
        }

        eventPublisher.publishEvent(new AgentPublishedEvent(
                newAgent.getId(), newVersion.getId(), request.getTargetNamespaceId(),
                request.getSubmittedBy(), newVersion.getPublishedAt()));

        return new MaterializationResult(newAgent.getId());
    }
}
```

**Note**: `AgentPublishedEvent` is the existing 5-arg event already in the codebase — `(agentId, agentVersionId, namespaceId, publisherId, publishedAt)`. Do NOT redefine it.

**Note**: The `AgentLabel` and `AgentTag` constructor signatures above are inferred from the schema migrations (V46 = agent_tag with `agent_id, tag_name, version_id, created_by`; V47 = agent_label with `agent_id, label_id, created_by`). If the actual constructors differ (verified in Task 8), adjust here. If the entities use full-arg static factories, use those instead.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -pl server/skillhub-domain test -Dtest=AgentPromotionMaterializerTest`
Expected: PASS (4 tests).

(No fallback path needed — `AgentLabel.getLabelId()` is the verified accessor.)

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/materialization/AgentPromotionMaterializer.java \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/materialization/AgentPromotionMaterializerTest.java
git commit -m "feat(domain): AgentPromotionMaterializer (mirrors skill flow; resets stats; filters labels by namespace scope)"
```

---

## Task 10: Refactor PromotionService.approvePromotion to use the materializer registry

**Files:**
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionService.java`
- Modify: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/PromotionServiceTest.java`

The PromotionService changes:
1. Constructor now also injects `List<PromotionMaterializer>` and indexes them into a `Map<SourceType, PromotionMaterializer>`.
2. `approvePromotion()` body lines 210–250 (the materialization block) is replaced with `materializer.materialize(request)` + `request.setTargetEntityId(result.targetEntityId(), request.getSourceType())`.
3. The `SkillRepository`, `SkillVersionRepository`, `SkillFileRepository` constructor parameters are **removed** (the SkillPromotionMaterializer now owns them) — only `assertTargetSkillNotExists` was using `skillRepository`, and that's now inside the materializer too. Verify `submitPromotion()` doesn't still need `skillRepository` for the source lookup — it does (line 82). Keep it.

So actually: Constructor still injects skill repos for `submitPromotion`'s skill path. It now ALSO injects `List<PromotionMaterializer>`. The `assertTargetSkillNotExists` private method is deleted (moved into materializer).

- [ ] **Step 1: Update PromotionService**

Open `PromotionService.java`. Add imports:

```java
import com.iflytek.skillhub.domain.review.materialization.MaterializationResult;
import com.iflytek.skillhub.domain.review.materialization.PromotionMaterializer;
import java.util.EnumMap;
```

Update the constructor — add a `List<PromotionMaterializer>` parameter and index it:

```java
private final java.util.Map<SourceType, PromotionMaterializer> materializers;

public PromotionService(PromotionRequestRepository promotionRequestRepository,
                        SkillRepository skillRepository,
                        SkillVersionRepository skillVersionRepository,
                        // SkillFileRepository removed — owned by SkillPromotionMaterializer now
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
    this.namespaceRepository = namespaceRepository;
    this.permissionChecker = permissionChecker;
    this.eventPublisher = eventPublisher;
    this.governanceNotificationService = governanceNotificationService;
    this.entityManager = entityManager;
    this.clock = clock;
    java.util.Map<SourceType, PromotionMaterializer> map = new EnumMap<>(SourceType.class);
    for (PromotionMaterializer m : materializerBeans) {
        map.put(m.supportedSourceType(), m);
    }
    this.materializers = java.util.Collections.unmodifiableMap(map);
}
```

**Remove** the `skillFileRepository` field declaration as well.

Replace `approvePromotion()` body lines 211–250 (the materialization block — from "Create new skill in global namespace" through "skillFileRepository.saveAll(copiedFiles);") with:

```java
PromotionMaterializer materializer = materializers.get(approvedRequest.getSourceType());
if (materializer == null) {
    throw new IllegalStateException(
            "No PromotionMaterializer registered for " + approvedRequest.getSourceType());
}
MaterializationResult result = materializer.materialize(approvedRequest);
approvedRequest.setTargetEntityId(result.targetEntityId(), approvedRequest.getSourceType());
PromotionRequest savedRequest = promotionRequestRepository.save(approvedRequest);
```

**Delete** the now-unused private method `assertTargetSkillNotExists` and `duplicateTargetSkillConflict` from `PromotionService`. (The materializer owns slug-collision throws now.)

**Update the event publish** lines 256–268 — keep them; the `SkillPublishedEvent` is now published by the SkillPromotionMaterializer. Remove the `eventPublisher.publishEvent(new SkillPublishedEvent(...))` line from PromotionService since the materializer publishes it. Keep `PromotionApprovedEvent` and `governanceNotificationService.notifyUser(...)`.

The final approve method body should be approximately:

```java
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
```

**Note**: `PromotionApprovedEvent` currently passes `getSourceSkillId()` as one of its args. For agent-source promotions this will be `null`. That's a pre-existing event contract issue — for A9 we leave it. A follow-up task could widen the event signature; not in this plan's scope.

- [ ] **Step 2: Update PromotionServiceTest to inject materializers**

Open the existing test. Wherever the test instantiates `PromotionService(...)`, add a new last argument: `List.of(skillMaterializer, agentMaterializer)` where each is a `mock(PromotionMaterializer.class)` configured to return its supported type.

Add to test setup:

```java
private PromotionMaterializer skillMaterializer;
private PromotionMaterializer agentMaterializer;

// in @BeforeEach:
skillMaterializer = mock(PromotionMaterializer.class);
when(skillMaterializer.supportedSourceType()).thenReturn(SourceType.SKILL);
when(skillMaterializer.materialize(any(PromotionRequest.class)))
        .thenReturn(new MaterializationResult(999L));
agentMaterializer = mock(PromotionMaterializer.class);
when(agentMaterializer.supportedSourceType()).thenReturn(SourceType.AGENT);
when(agentMaterializer.materialize(any(PromotionRequest.class)))
        .thenReturn(new MaterializationResult(888L));
```

And update `new PromotionService(...)` to pass `List.of(skillMaterializer, agentMaterializer)` as the new last argument. Remove `skillFileRepository` from the call too.

Add new tests:

```java
@Test
void approvePromotionDispatchesToSkillMaterializer() {
    // setup a pending skill promotion request returned by repo, then call approve.
    // verify skillMaterializer.materialize() invoked once and request.targetSkillId set to 999.
}

@Test
void approvePromotionDispatchesToAgentMaterializer() {
    // setup a pending AGENT promotion request, then call approve.
    // verify agentMaterializer.materialize() invoked once and request.targetAgentId set to 888.
}
```

(Fill in the existing test patterns from `PromotionServiceTest.java` — copy its repo-mocking style.)

- [ ] **Step 3: Run all promotion tests**

Run: `mvn -pl server/skillhub-domain test -Dtest=PromotionServiceTest,PromotionRequestTest,SkillPromotionMaterializerTest,AgentPromotionMaterializerTest`
Expected: All pass.

- [ ] **Step 4: Run the full backend test suite**

Run: `mvn -pl server/skillhub-domain,server/skillhub-app test`
Expected: All existing tests pass + the new ones. Some app-layer tests that wire `PromotionService` may need their setup adjusted to include the materializer list.

If any existing test fails because of constructor change, update it minimally: add `List.of(...)` and remove `skillFileRepository` arg.

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionService.java \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/PromotionServiceTest.java
git commit -m "refactor(domain): PromotionService.approvePromotion delegates to PromotionMaterializer registry"
```

---

## Task 11: Add agent path to PromotionService.submitPromotion

The existing `submitPromotion` (two overloads — one with platformRoles, one without) is skill-only. Add an agent-source variant that mirrors the validation pattern.

**Files:**
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionService.java`
- Modify: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/PromotionServiceTest.java`

- [ ] **Step 1: Write failing tests**

Add to `PromotionServiceTest`:

```java
@Test
void submitAgentPromotionCreatesPendingRequest() {
    Agent source = new Agent(1L, "review-bot", "Review Bot", "owner-1", AgentVisibility.PRIVATE);
    AgentVersion sourceVersion = new AgentVersion(10L, "1.0.0", "owner-1",
            "manifest", "soul", "workflow", "objects/abc", 1024L);
    sourceVersion.autoPublish();

    when(agentRepository.findById(10L)).thenReturn(Optional.of(source));
    when(agentVersionRepository.findById(20L)).thenReturn(Optional.of(sourceVersion));
    when(namespaceRepository.findById(1L)).thenReturn(Optional.of(activePersonalNs(1L)));
    when(namespaceRepository.findById(99L)).thenReturn(Optional.of(activeGlobalNs(99L)));
    when(promotionRequestRepository.findBySourceAgentIdAndStatus(10L, ReviewTaskStatus.PENDING))
            .thenReturn(Optional.empty());
    when(permissionChecker.canSubmitPromotion(any(Agent.class), eq("user-1"), any(), any()))
            .thenReturn(true);
    when(promotionRequestRepository.save(any(PromotionRequest.class)))
            .thenAnswer(inv -> inv.getArgument(0));

    PromotionRequest result = promotionService.submitAgentPromotion(
            10L, 20L, 99L, "user-1", Map.of(), Set.of("SKILL_ADMIN"));

    assertEquals(SourceType.AGENT, result.getSourceType());
    assertEquals(10L, result.getSourceAgentId());
    assertEquals(20L, result.getSourceAgentVersionId());
    verify(promotionRequestRepository).save(any(PromotionRequest.class));
}

@Test
void submitAgentPromotionRejectsNonPublishedVersion() {
    Agent source = new Agent(1L, "review-bot", "Review Bot", "owner-1", AgentVisibility.PRIVATE);
    AgentVersion draft = new AgentVersion(10L, "1.0.0", "owner-1",
            "manifest", "soul", "workflow", "objects/abc", 1024L);
    // status remains DRAFT

    when(agentRepository.findById(10L)).thenReturn(Optional.of(source));
    when(agentVersionRepository.findById(20L)).thenReturn(Optional.of(draft));

    assertThrows(DomainBadRequestException.class, () ->
        promotionService.submitAgentPromotion(10L, 20L, 99L, "user-1", Map.of(), Set.of("SKILL_ADMIN")));
}

@Test
void submitAgentPromotionRejectsDuplicatePending() {
    Agent source = new Agent(1L, "review-bot", "Review Bot", "owner-1", AgentVisibility.PRIVATE);
    AgentVersion sourceVersion = new AgentVersion(10L, "1.0.0", "owner-1",
            "manifest", "soul", "workflow", "objects/abc", 1024L);
    sourceVersion.autoPublish();

    when(agentRepository.findById(10L)).thenReturn(Optional.of(source));
    when(agentVersionRepository.findById(20L)).thenReturn(Optional.of(sourceVersion));
    when(namespaceRepository.findById(1L)).thenReturn(Optional.of(activePersonalNs(1L)));
    when(namespaceRepository.findById(99L)).thenReturn(Optional.of(activeGlobalNs(99L)));
    when(permissionChecker.canSubmitPromotion(any(Agent.class), any(), any(), any())).thenReturn(true);
    when(promotionRequestRepository.findBySourceAgentIdAndStatus(10L, ReviewTaskStatus.PENDING))
            .thenReturn(Optional.of(PromotionRequest.forAgent(10L, 20L, 99L, "other-user")));

    assertThrows(DomainBadRequestException.class, () ->
        promotionService.submitAgentPromotion(10L, 20L, 99L, "user-1", Map.of(), Set.of("SKILL_ADMIN")));
}
```

(Helpers `activePersonalNs(id)`, `activeGlobalNs(id)` — copy/extend whatever fixture pattern the existing PromotionServiceTest uses. If none, write small helpers using the existing `Namespace` constructor.)

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -pl server/skillhub-domain test -Dtest=PromotionServiceTest`
Expected: 3 new tests fail with "method submitAgentPromotion not found".

- [ ] **Step 3: Add the method to PromotionService**

Add field declarations + constructor params for `AgentRepository agentRepository` and `AgentVersionRepository agentVersionRepository`. Then the method:

```java
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
```

**Pre-flight (verified 2026-04-28)**:
- `ReviewPermissionChecker.canSubmitPromotion` exists only for `Skill` (`canSubmitPromotion(Skill, String, Map, Set)` and `canSubmitPromotion(Skill, String, Map)` — see `ReviewPermissionChecker.java:93-104`). It internally delegates to `canSubmitForReview(skill, ...)` which only reads `skill.getOwnerId()` and `skill.getNamespaceId()` — both `Agent` has identical getters. **Add an `Agent` overload as Step 3 BEFORE the new `submitAgentPromotion` body.**

  Add to `ReviewPermissionChecker.java`:

  ```java
  public boolean canSubmitPromotion(Agent sourceAgent,
                                    String userId,
                                    Map<Long, NamespaceRole> userNamespaceRoles,
                                    Set<String> platformRoles) {
      if (sourceAgent.getOwnerId().equals(userId)) {
          return true;
      }
      if (hasPlatformReviewRole(platformRoles)) {
          return true;
      }
      NamespaceRole role = userNamespaceRoles.get(sourceAgent.getNamespaceId());
      return role == NamespaceRole.ADMIN || role == NamespaceRole.OWNER;
  }
  ```

  (Add `import com.iflytek.skillhub.domain.agent.Agent;` at the top.) `canReviewPromotion` is already source-agnostic (operates on `PromotionRequest`); no change needed.

- `PromotionSubmittedEvent` is the existing record `(Long promotionId, Long skillId, Long versionId, String submitterId)` with 2 production callers (NotificationEventListener + 2 test invocations). **Do NOT rename the fields.** Reuse the existing slots verbatim — for agent submissions, `skillId` carries the agent id and `versionId` carries the agent version id. The listener (`NotificationEventListener.onPromotionSubmitted`) only reads `promotionId` to dereference the request, so the field names are documentary only. This is the minimum-disruption path.

  The agent-side publish call in `submitAgentPromotion` becomes:

  ```java
  eventPublisher.publishEvent(new PromotionSubmittedEvent(
          saved.getId(), saved.getSourceAgentId(), saved.getSourceAgentVersionId(),
          saved.getSubmittedBy()));
  ```

  (This is exactly what's already in the code block above — just confirming the design choice.)

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -pl server/skillhub-domain test -Dtest=PromotionServiceTest`
Expected: All tests pass.

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/review/PromotionService.java \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/review/PromotionServiceTest.java \
        # any ReviewPermissionChecker / PromotionSubmittedEvent changes
git commit -m "feat(domain): submitAgentPromotion validates source agent version + checks duplicate-pending"
```

---

## Task 12: Extend PromotionRequestDto + PromotionResponseDto

**Files:**
- Modify: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/PromotionRequestDto.java`
- Modify: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/PromotionResponseDto.java`

- [ ] **Step 1: Find both DTO files**

Run: `find server/skillhub-app -name "PromotionRequestDto.java" -o -name "PromotionResponseDto.java"`
Expected: 2 paths.

- [ ] **Step 2: Replace PromotionRequestDto**

```java
package com.iflytek.skillhub.dto;  // adjust package to match existing

import com.iflytek.skillhub.domain.review.SourceType;

public record PromotionRequestDto(
        SourceType sourceType,
        Long sourceSkillId,
        Long sourceVersionId,
        Long sourceAgentId,
        Long sourceAgentVersionId,
        Long targetNamespaceId
) {
    public PromotionRequestDto {
        if (sourceType == null) {
            sourceType = SourceType.SKILL;
        }
    }
}
```

- [ ] **Step 3: Extend PromotionResponseDto**

Add four new fields (`sourceType`, `sourceAgentId`, `sourceAgentSlug`, `sourceAgentVersion`, `targetAgentId`) to the existing record. Keep all 15 existing fields.

```java
public record PromotionResponseDto(
        Long id,
        SourceType sourceType,
        // skill source (null when sourceType=AGENT)
        Long sourceSkillId,
        String sourceNamespace,
        String sourceSkillSlug,
        String sourceVersion,
        // agent source (null when sourceType=SKILL)
        Long sourceAgentId,
        String sourceAgentSlug,
        String sourceAgentVersion,
        // target
        String targetNamespace,
        Long targetSkillId,
        Long targetAgentId,
        String status,
        String submittedBy,
        String submittedByName,
        String reviewedBy,
        String reviewedByName,
        String reviewComment,
        java.time.Instant submittedAt,
        java.time.Instant reviewedAt
) {}
```

- [ ] **Step 4: Update wherever PromotionResponseDto is constructed**

Run: `grep -rn "new PromotionResponseDto" server/skillhub-app/src/main/java/`
Find each call site and update it to pass the new fields. For skill-source rows, the agent fields are `null`. The mapper will need to dereference the Agent + AgentVersion if `sourceType=AGENT`.

Find the existing mapping function (likely in `PromotionService`'s controller-facing layer or in a mapper class) and add agent-source dereferencing:

```java
PromotionResponseDto map(PromotionRequest req) {
    if (req.getSourceType() == SourceType.SKILL) {
        Skill src = skillRepository.findById(req.getSourceSkillId()).orElse(null);
        SkillVersion sv = skillVersionRepository.findById(req.getSourceVersionId()).orElse(null);
        return new PromotionResponseDto(
            req.getId(), SourceType.SKILL,
            req.getSourceSkillId(), src != null ? sourceNamespaceSlug(src) : null,
            src != null ? src.getSlug() : null, sv != null ? sv.getVersion() : null,
            null, null, null,
            targetNamespaceSlug(req), req.getTargetSkillId(), null,
            req.getStatus().name(), req.getSubmittedBy(), submitterName(req),
            req.getReviewedBy(), reviewerName(req), req.getReviewComment(),
            req.getSubmittedAt(), req.getReviewedAt()
        );
    } else {
        Agent src = agentRepository.findById(req.getSourceAgentId()).orElse(null);
        AgentVersion av = agentVersionRepository.findById(req.getSourceAgentVersionId()).orElse(null);
        return new PromotionResponseDto(
            req.getId(), SourceType.AGENT,
            null, null, null, null,
            req.getSourceAgentId(), src != null ? src.getSlug() : null,
            av != null ? av.getVersion() : null,
            targetNamespaceSlug(req), null, req.getTargetAgentId(),
            req.getStatus().name(), req.getSubmittedBy(), submitterName(req),
            req.getReviewedBy(), reviewerName(req), req.getReviewComment(),
            req.getSubmittedAt(), req.getReviewedAt()
        );
    }
}
```

(Adjust to whatever the existing mapper structure looks like — just add the AGENT branch.)

- [ ] **Step 5: Compile and run existing controller tests**

Run: `mvn -pl server/skillhub-app test -Dtest=PromotionControllerTest`
Expected: existing tests pass (the response DTO is additive; old fields unchanged). New nullable fields in JSON serialize as `null` for skill-source rows.

- [ ] **Step 6: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/PromotionRequestDto.java \
        server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/PromotionResponseDto.java \
        # any mapper file changed
git commit -m "feat(api): PromotionRequestDto + PromotionResponseDto support agent source"
```

---

## Task 13: Extend PromotionController for agent submit + sourceType filter

**Files:**
- Modify: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/PromotionController.java`
- Modify: existing `PromotionControllerTest.java`

- [ ] **Step 1: Update controller's submit endpoint to dispatch by source type**

Find the existing `POST /api/v1/promotions` handler. Update it to switch on `dto.sourceType()`:

```java
@PostMapping
public PromotionResponseDto submit(@RequestBody PromotionRequestDto dto, Authentication auth) {
    String userId = auth.getName();
    Set<String> platformRoles = extractPlatformRoles(auth);
    Map<Long, NamespaceRole> nsRoles = extractNamespaceRoles(auth);

    PromotionRequest result;
    if (dto.sourceType() == SourceType.AGENT) {
        if (dto.sourceAgentId() == null || dto.sourceAgentVersionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "AGENT promotion requires sourceAgentId and sourceAgentVersionId");
        }
        result = promotionService.submitAgentPromotion(
                dto.sourceAgentId(), dto.sourceAgentVersionId(),
                dto.targetNamespaceId(), userId, nsRoles, platformRoles);
    } else {
        if (dto.sourceSkillId() == null || dto.sourceVersionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "SKILL promotion requires sourceSkillId and sourceVersionId");
        }
        result = promotionService.submitPromotion(
                dto.sourceSkillId(), dto.sourceVersionId(),
                dto.targetNamespaceId(), userId, nsRoles, platformRoles);
    }
    return promotionMapper.toResponseDto(result);
}
```

- [ ] **Step 2: Add optional sourceType filter to list endpoints**

The two list endpoints (`GET /api/v1/promotions`, `GET /api/v1/promotions/pending`) gain an optional query param:

```java
@GetMapping
public Page<PromotionResponseDto> list(
        @RequestParam(required = false) ReviewTaskStatus status,
        @RequestParam(required = false) SourceType sourceType,
        Pageable pageable,
        Authentication auth) {
    if (sourceType != null) {
        Page<PromotionRequest> page = promotionRequestRepository
                .findByStatusAndSourceType(
                        status != null ? status : ReviewTaskStatus.PENDING,
                        sourceType, pageable);
        return page.map(promotionMapper::toResponseDto);
    }
    // existing path
}
```

(The existing list endpoints in `PromotionPortalAppService` use `findByStatus` with no namespace dimension; the new method mirrors that shape with the added `sourceType` filter.)

- [ ] **Step 3: Add controller tests**

```java
@Test
void submitAgentPromotionDispatchesToAgentSubmitMethod() throws Exception {
    String body = """
        {"sourceType":"AGENT","sourceAgentId":10,"sourceAgentVersionId":20,"targetNamespaceId":99}
        """;
    mockMvc.perform(post("/api/v1/promotions").contentType(MediaType.APPLICATION_JSON).content(body)
            .with(authenticated("user-1")))
            .andExpect(status().isOk());
    verify(promotionService).submitAgentPromotion(10L, 20L, 99L, "user-1", Map.of(), Set.of(...));
}

@Test
void submitWithMismatchedTypeAndIdsReturns400() throws Exception {
    String body = """
        {"sourceType":"AGENT","sourceSkillId":10,"sourceVersionId":20,"targetNamespaceId":99}
        """;
    mockMvc.perform(post("/api/v1/promotions").contentType(MediaType.APPLICATION_JSON).content(body)
            .with(authenticated("user-1")))
            .andExpect(status().isBadRequest());
}

@Test
void submitWithoutSourceTypeDefaultsToSkill() throws Exception {
    String body = """
        {"sourceSkillId":10,"sourceVersionId":20,"targetNamespaceId":99}
        """;
    mockMvc.perform(post("/api/v1/promotions").contentType(MediaType.APPLICATION_JSON).content(body)
            .with(authenticated("user-1")))
            .andExpect(status().isOk());
    verify(promotionService).submitPromotion(10L, 20L, 99L, "user-1", Map.of(), Set.of(...));
}

@Test
void listWithSourceTypeAgentFilter() throws Exception {
    mockMvc.perform(get("/api/v1/promotions?sourceType=AGENT").with(authenticated("user-1")))
            .andExpect(status().isOk());
    verify(promotionRequestRepository).findByStatusAndSourceType(
            any(ReviewTaskStatus.class), eq(SourceType.AGENT), any(Pageable.class));
}
```

- [ ] **Step 4: Run controller tests**

Run: `mvn -pl server/skillhub-app test -Dtest=PromotionControllerTest`
Expected: All pass.

- [ ] **Step 5: Run full backend suite to confirm no regressions**

Run: `mvn -pl server/skillhub-domain,server/skillhub-app,server/skillhub-infra test`
Expected: All passing. Backend should sit at ≥574 tests (was 554).

- [ ] **Step 6: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/PromotionController.java \
        server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/portal/PromotionControllerTest.java
git commit -m "feat(api): PromotionController dispatches agent submit + sourceType list filter"
```

---

## Task 14: Web — extend types + API client

**Files:**
- Modify: `web/src/shared/api/promotion.ts` (or wherever the typed API client lives — find with `grep -rln "promotionApi" web/src/shared/`)
- Modify: `web/src/shared/api/types.ts` or generated types

- [ ] **Step 1: Locate the API client**

Run: `grep -rln "promotionApi" web/src/shared/ web/src/features/ | head -5`
Expected: paths to the promotion API client + hooks dir.

- [ ] **Step 2: Add the type**

```ts
// in the types file
export type PromotionDtoSourceType = 'SKILL' | 'AGENT'

export interface PromotionRequestDto {
  sourceType?: PromotionDtoSourceType
  sourceSkillId?: number
  sourceVersionId?: number
  sourceAgentId?: number
  sourceAgentVersionId?: number
  targetNamespaceId: number
}

export interface PromotionResponseDto {
  id: number
  sourceType: PromotionDtoSourceType
  sourceSkillId?: number | null
  sourceNamespace?: string | null
  sourceSkillSlug?: string | null
  sourceVersion?: string | null
  sourceAgentId?: number | null
  sourceAgentSlug?: string | null
  sourceAgentVersion?: string | null
  targetNamespace: string
  targetSkillId?: number | null
  targetAgentId?: number | null
  status: 'PENDING' | 'APPROVED' | 'REJECTED'
  submittedBy: string
  submittedByName?: string
  reviewedBy?: string | null
  reviewedByName?: string | null
  reviewComment?: string | null
  submittedAt: string
  reviewedAt?: string | null
}
```

- [ ] **Step 3: Add submitAgent helper to promotionApi**

```ts
export const promotionApi = {
  // ... existing methods unchanged ...

  submitAgent: (sourceAgentId: number, sourceAgentVersionId: number, targetNamespaceId: number) =>
    httpClient.post<PromotionResponseDto>('/promotions', {
      sourceType: 'AGENT',
      sourceAgentId,
      sourceAgentVersionId,
      targetNamespaceId,
    }),

  list: (status?: string, sourceType?: PromotionDtoSourceType) =>
    httpClient.get<Page<PromotionResponseDto>>('/promotions', {
      params: { status, sourceType },
    }),
}
```

- [ ] **Step 4: Verify typecheck**

Run: `cd web && pnpm typecheck`
Expected: no errors.

- [ ] **Step 5: Commit**

```bash
git add web/src/shared/api/
git commit -m "feat(web): promotionApi.submitAgent + sourceType filter"
```

---

## Task 15: Web — add useSubmitAgentPromotion hook

**Files:**
- Create: `web/src/features/promotion/use-submit-agent-promotion.ts`
- Test: `web/src/features/promotion/use-submit-agent-promotion.test.tsx`

- [ ] **Step 1: Find the existing skill-side hook for the pattern**

Run: `find web/src/features -name "use-submit-promotion*" -o -name "use-promotion*"`
Read one to match the existing mutation style (toast, error handling, query invalidation).

- [ ] **Step 2: Write the failing test**

```tsx
import { renderHook, waitFor } from '@testing-library/react'
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { useSubmitAgentPromotion } from './use-submit-agent-promotion'
import { promotionApi } from '@/shared/api/promotion'
import { createWrapper } from '@/shared/test/create-wrapper'

vi.mock('@/shared/api/promotion', () => ({
  promotionApi: { submitAgent: vi.fn() },
}))

const submitAgentMock = promotionApi.submitAgent as unknown as ReturnType<typeof vi.fn>

beforeEach(() => {
  submitAgentMock.mockReset()
})

describe('useSubmitAgentPromotion', () => {
  it('calls promotionApi.submitAgent with correct args', async () => {
    submitAgentMock.mockResolvedValue({ id: 1, sourceType: 'AGENT' })
    const wrapper = createWrapper()
    const { result } = renderHook(() => useSubmitAgentPromotion(), { wrapper })

    result.current.mutate({ sourceAgentId: 10, sourceAgentVersionId: 20, targetNamespaceId: 99 })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(submitAgentMock).toHaveBeenCalledWith(10, 20, 99)
  })
})
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd web && pnpm test -- src/features/promotion/use-submit-agent-promotion.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 4: Implement the hook**

```ts
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { promotionApi } from '@/shared/api/promotion'

export interface SubmitAgentPromotionArgs {
  sourceAgentId: number
  sourceAgentVersionId: number
  targetNamespaceId: number
}

export function useSubmitAgentPromotion() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ sourceAgentId, sourceAgentVersionId, targetNamespaceId }: SubmitAgentPromotionArgs) =>
      promotionApi.submitAgent(sourceAgentId, sourceAgentVersionId, targetNamespaceId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['promotions'] })
      qc.invalidateQueries({ queryKey: ['governance'] })
    },
  })
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd web && pnpm test -- src/features/promotion/use-submit-agent-promotion.test.tsx`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add web/src/features/promotion/use-submit-agent-promotion.ts \
        web/src/features/promotion/use-submit-agent-promotion.test.tsx
git commit -m "feat(web): useSubmitAgentPromotion mutation"
```

---

## Task 16: Web — add PromoteAgentButton component

**Files:**
- Create: `web/src/features/agent/promotion/promote-agent-button.tsx`
- Test: `web/src/features/agent/promotion/promote-agent-button.test.tsx`

- [ ] **Step 1: Read an analogous skill button**

Run: `grep -rln "submitPromotion\|PromoteSkill\|promote.*button" web/src/features/skill/ web/src/pages/skill-detail.tsx`
Read whatever component (or inline JSX) implements the skill promote button. Match its visual style and pattern.

- [ ] **Step 2: Write the failing test**

```tsx
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { render, screen } from '@testing-library/react'
import { PromoteAgentButton } from './promote-agent-button'

const mutateMock = vi.fn()
vi.mock('@/features/promotion/use-submit-agent-promotion', () => ({
  useSubmitAgentPromotion: () => ({ mutate: mutateMock, isPending: false }),
}))

const useAuthMock = vi.fn()
vi.mock('@/features/auth/use-auth', () => ({ useAuth: () => useAuthMock() }))

beforeEach(() => {
  mutateMock.mockReset()
  useAuthMock.mockReset()
})

describe('PromoteAgentButton', () => {
  it('does not render when user lacks SKILL_ADMIN role', () => {
    useAuthMock.mockReturnValue({ user: { id: 'u', platformRoles: [] } })
    const { container } = render(
      <PromoteAgentButton agentId={1} versionId={2} versionStatus="PUBLISHED"
                          isInGlobalNamespace={false} hasPendingPromotion={false} />)
    expect(container.firstChild).toBeNull()
  })

  it('does not render when version is not PUBLISHED', () => {
    useAuthMock.mockReturnValue({ user: { id: 'u', platformRoles: ['SKILL_ADMIN'] } })
    const { container } = render(
      <PromoteAgentButton agentId={1} versionId={2} versionStatus="DRAFT"
                          isInGlobalNamespace={false} hasPendingPromotion={false} />)
    expect(container.firstChild).toBeNull()
  })

  it('does not render when agent already in global namespace', () => {
    useAuthMock.mockReturnValue({ user: { id: 'u', platformRoles: ['SKILL_ADMIN'] } })
    const { container } = render(
      <PromoteAgentButton agentId={1} versionId={2} versionStatus="PUBLISHED"
                          isInGlobalNamespace={true} hasPendingPromotion={false} />)
    expect(container.firstChild).toBeNull()
  })

  it('does not render when there is a pending promotion', () => {
    useAuthMock.mockReturnValue({ user: { id: 'u', platformRoles: ['SKILL_ADMIN'] } })
    const { container } = render(
      <PromoteAgentButton agentId={1} versionId={2} versionStatus="PUBLISHED"
                          isInGlobalNamespace={false} hasPendingPromotion={true} />)
    expect(container.firstChild).toBeNull()
  })

  it('renders for admin and dispatches mutation on click', async () => {
    useAuthMock.mockReturnValue({ user: { id: 'u', platformRoles: ['SKILL_ADMIN'] } })
    const { user } = await import('@testing-library/user-event').then(m => ({ user: m.userEvent.setup() }))
    render(<PromoteAgentButton agentId={1} versionId={2} versionStatus="PUBLISHED"
                               isInGlobalNamespace={false} hasPendingPromotion={false}
                               targetNamespaceId={99} />)
    const btn = screen.getByRole('button', { name: /promote/i })
    await user.click(btn)
    // confirm dialog appears, click confirm
    const confirm = screen.getByRole('button', { name: /confirm/i })
    await user.click(confirm)
    expect(mutateMock).toHaveBeenCalledWith({ sourceAgentId: 1, sourceAgentVersionId: 2, targetNamespaceId: 99 })
  })
})
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd web && pnpm test -- src/features/agent/promotion/promote-agent-button.test.tsx`
Expected: FAIL — module not found.

- [ ] **Step 4: Implement the component**

```tsx
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/shared/ui/button'
import { ConfirmDialog } from '@/shared/components/confirm-dialog'  // adjust path
import { useAuth } from '@/features/auth/use-auth'
import { useSubmitAgentPromotion } from '@/features/promotion/use-submit-agent-promotion'

interface PromoteAgentButtonProps {
  agentId: number
  versionId: number
  versionStatus: string
  isInGlobalNamespace: boolean
  hasPendingPromotion: boolean
  targetNamespaceId?: number
}

const ADMIN_ROLES = new Set(['SKILL_ADMIN', 'SUPER_ADMIN'])

export function PromoteAgentButton({
  agentId, versionId, versionStatus, isInGlobalNamespace, hasPendingPromotion, targetNamespaceId,
}: PromoteAgentButtonProps) {
  const { t } = useTranslation()
  const { user } = useAuth()
  const [open, setOpen] = useState(false)
  const submit = useSubmitAgentPromotion()

  const isAdmin = user?.platformRoles?.some(r => ADMIN_ROLES.has(r)) ?? false
  if (!isAdmin) return null
  if (versionStatus !== 'PUBLISHED') return null
  if (isInGlobalNamespace) return null
  if (hasPendingPromotion) return null

  const onConfirm = () => {
    if (targetNamespaceId == null) return
    submit.mutate({ sourceAgentId: agentId, sourceAgentVersionId: versionId, targetNamespaceId })
    setOpen(false)
  }

  return (
    <>
      <Button onClick={() => setOpen(true)} disabled={submit.isPending}>
        {t('agents.promote.label')}
      </Button>
      <ConfirmDialog
        open={open}
        title={t('agents.promote.confirmTitle')}
        description={t('agents.promote.confirmBody')}
        confirmLabel={t('common.confirm')}
        cancelLabel={t('common.cancel')}
        onConfirm={onConfirm}
        onCancel={() => setOpen(false)}
      />
    </>
  )
}
```

(Adjust ConfirmDialog to match the existing component API in this codebase. If no ConfirmDialog exists, use a `<Dialog>` primitive from the shared UI library.)

- [ ] **Step 5: Add i18n keys**

Open `web/src/shared/i18n/en.json` (and `zh.json`) and add:

```json
{
  "agents": {
    "promote": {
      "label": "Promote to global",
      "confirmTitle": "Promote agent",
      "confirmBody": "This will submit the current version for promotion to the global namespace."
    }
  }
}
```

(zh: "提升到全局" / "提升智能体" / "此操作会将当前版本提交到全局命名空间。")

- [ ] **Step 6: Run test to verify it passes**

Run: `cd web && pnpm test -- src/features/agent/promotion/promote-agent-button.test.tsx`
Expected: PASS (5 tests).

- [ ] **Step 7: Commit**

```bash
git add web/src/features/agent/promotion/ web/src/shared/i18n/
git commit -m "feat(web): PromoteAgentButton component + i18n"
```

---

## Task 17: Web — mount PromoteAgentButton in agent-detail.tsx

**Files:**
- Modify: `web/src/pages/agent-detail.tsx`
- Modify: `web/src/pages/agent-detail.test.tsx`

- [ ] **Step 1: Read agent-detail.tsx to find sidebar action group**

```bash
grep -n "AgentStarButton\|agent-star\|Star\|sidebar" web/src/pages/agent-detail.tsx | head -10
```

Find the area where AgentStarButton + AgentRatingInput render. The PromoteAgentButton sits next to them.

- [ ] **Step 2: Add import + render**

```tsx
import { PromoteAgentButton } from '@/features/agent/promotion/promote-agent-button'

// ... in the sidebar JSX, near AgentStarButton:
<PromoteAgentButton
  agentId={agent.id}
  versionId={agent.versionId /* or the active-version object's id */}
  versionStatus={agent.version /* version status string */}
  isInGlobalNamespace={agent.namespaceType === 'GLOBAL'}
  hasPendingPromotion={agent.hasPendingPromotion ?? false}
  targetNamespaceId={GLOBAL_NAMESPACE_ID /* import or fetch the global namespace id */}
/>
```

**Note**: The `hasPendingPromotion` flag and `GLOBAL_NAMESPACE_ID` may not exist in the current agent detail payload. Two options:
1. Extend the backend `AgentResponse` DTO to include `hasPendingPromotion` (small backend change — add field, populated via a `promotionRequestRepository.findBySourceAgentIdAndStatus(agent.id, PENDING).isPresent()` lookup in the agent detail mapper). Adds ~10 LOC backend + DTO update + 1 web test.
2. Do a separate query in the button: when the button's parent renders, call `usePromotionList(PENDING, AGENT)` filtered to this agent. Adds an extra HTTP hit.

**Choose option 1** — cleaner DX, no extra requests. Add this as inline subtasks here:

- [ ] **Step 3 (sub-task): Backend — extend AgentResponse with hasPendingPromotion**

Find AgentResponse DTO. Add the field. In its mapper (`AgentMapper` or wherever), populate via:

```java
boolean hasPending = promotionRequestRepository
        .findBySourceAgentIdAndStatus(agent.getId(), ReviewTaskStatus.PENDING).isPresent();
```

Update the constructor / record. Compile + run AgentControllerTest.

- [ ] **Step 4 (sub-task): Backend — find or define GLOBAL_NAMESPACE_ID**

Run: `grep -rn "GLOBAL_NAMESPACE\|NamespaceType.GLOBAL" server/skillhub-app/src/main/java/ | head -5`

The "global" namespace likely has a known slug ('global' or similar). Frontend can resolve it via an existing `useGlobalNamespace()` hook (if exists) or by passing the slug directly. If neither exists, add a simple endpoint `GET /api/v1/namespaces/global` returning the global namespace's id+slug. (5 LOC backend + 1 hook web.)

Or simpler: the `targetNamespaceId` could be derived from the existing `useMyNamespaces()` result (filter to `type=GLOBAL`).

- [ ] **Step 5: Wire it into agent-detail and add test coverage**

Update agent-detail to pass the props correctly. Update agent-detail.test.tsx to mock the new useGlobalNamespace (or whatever) and ensure PromoteAgentButton receives the props. (Lightweight test — just verify the component is rendered with expected props for an admin user.)

- [ ] **Step 6: Run web + backend test suites**

Run: `cd web && pnpm test -- --run` and `mvn -pl server/skillhub-app test`
Expected: All passing. Web should be at ~688+, backend at ~575+.

- [ ] **Step 7: Commit**

```bash
git add web/src/pages/agent-detail.tsx web/src/pages/agent-detail.test.tsx \
        server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/AgentResponse.java \
        # other backend files for hasPendingPromotion
git commit -m "feat(agent): expose hasPendingPromotion + mount PromoteAgentButton on agent-detail"
```

---

## Task 18: Web — render source-type badge + branch links in promotions.tsx

**Files:**
- Modify: `web/src/pages/dashboard/promotions.tsx`
- Modify: `web/src/pages/dashboard/promotions.test.ts` (or similar)

- [ ] **Step 1: Read existing promotions.tsx to locate the row-rendering function**

```bash
cat web/src/pages/dashboard/promotions.tsx
```

Find where each row's "namespace/skill-slug @ version" is rendered. Add a branch:

- [ ] **Step 2: Update row to branch on sourceType**

```tsx
function PromotionRow({ item }: { item: PromotionResponseDto }) {
  const isAgent = item.sourceType === 'AGENT'
  const detailPath = isAgent
    ? `/agents/${item.sourceNamespace ?? '?'}/${item.sourceAgentSlug ?? '?'}`
    : `/skills/${item.sourceNamespace}/${item.sourceSkillSlug}/versions/${item.sourceVersion}`
  const slug = isAgent ? item.sourceAgentSlug : item.sourceSkillSlug
  const versionLabel = isAgent ? item.sourceAgentVersion : item.sourceVersion
  return (
    <div className="flex items-center gap-2">
      <SourceTypeBadge type={item.sourceType} />
      <Link to={detailPath}>
        {item.sourceNamespace}/{slug} @ {versionLabel}
      </Link>
    </div>
  )
}

function SourceTypeBadge({ type }: { type: 'SKILL' | 'AGENT' }) {
  const { t } = useTranslation()
  const cls = type === 'AGENT'
    ? 'bg-purple-100 text-purple-800'
    : 'bg-blue-100 text-blue-800'
  return (
    <span className={`inline-block rounded px-2 py-0.5 text-xs font-medium ${cls}`}>
      {type === 'AGENT' ? t('promotions.sourceType.agent') : t('promotions.sourceType.skill')}
    </span>
  )
}
```

- [ ] **Step 3: Add i18n keys**

Open `web/src/shared/i18n/en.json` and `zh.json`. Add:

```json
{
  "promotions": {
    "sourceType": {
      "skill": "Skill",
      "agent": "Agent"
    }
  }
}
```

(zh: "技能" / "智能体".)

- [ ] **Step 4: Update promotions test**

Add cases:

```tsx
it('renders SKILL badge with skill link', () => {
  // mock usePromotionList returning a SKILL-type item
  // render → expect badge text "Skill" and link to /skills/...
})

it('renders AGENT badge with agent link', () => {
  // mock usePromotionList returning an AGENT-type item
  // render → expect badge text "Agent" and link to /agents/...
})
```

- [ ] **Step 5: Run web tests**

Run: `cd web && pnpm test -- --run`
Expected: All passing. Web should be at ~690.

- [ ] **Step 6: Commit**

```bash
git add web/src/pages/dashboard/promotions.tsx \
        web/src/pages/dashboard/promotions.test.ts \
        web/src/shared/i18n/
git commit -m "feat(web): promotions.tsx renders source-type badge + agent-aware links"
```

---

## Task 19: ADR 0004 + final verification

**Files:**
- Create: `docs/adr/0004-agent-promotion.md`

- [ ] **Step 1: Write ADR**

```markdown
# ADR 0004 — Agent Promotion

**Status:** Accepted
**Date:** 2026-04-28
**Context:** A9 — extending the skill-only promotion subsystem to support agents.

## Decision

Use a discriminator-column schema (`source_type`) on `promotion_request` plus
a strategy-pattern materializer (`PromotionMaterializer` interface, registry
keyed by `SourceType`) instead of parallel tables/services or a generic
`source_entity_id` column.

## Consequences

- Reuses ~80% of `PromotionService` (state machine, auth, audit, events,
  notifications) unchanged.
- Frontend reuses one unified review queue with a source-type badge.
- Each materializer is independently testable and depends only on its own
  domain repositories.
- Schema gains a CHECK constraint enforcing the discriminator/id pairing.
- FK integrity preserved on both skill and agent sides (vs the generic
  `source_entity_id` alternative which would lose it).

## Alternatives considered

- **Parallel tables (`agent_promotion_request`)** — rejected: duplicates the
  state machine, auth, and notification wiring; reviewer inbox would need to
  merge two streams.
- **Generic `source_entity_id` + `source_type` (no FK)** — rejected: loses
  referential integrity and complicates joins.
- **Switch in PromotionService instead of strategy** — rejected: would force
  PromotionService to inject all skill + agent repositories (constructor with
  11+ deps), defeating cohesion.

## References

- Spec: [docs/superpowers/specs/2026-04-28-agent-promotion-design.md](../superpowers/specs/2026-04-28-agent-promotion-design.md)
- Plan: [docs/superpowers/plans/2026-04-28-agent-promotion.md](../superpowers/plans/2026-04-28-agent-promotion.md)
- Migration: `server/skillhub-app/src/main/resources/db/migration/V49__agent_promotion.sql`
```

- [ ] **Step 2: Run all backend + web tests one more time**

Run in parallel (two terminals or one chained):

```bash
mvn -pl server/skillhub-domain,server/skillhub-app,server/skillhub-infra test
cd web && pnpm test -- --run && pnpm typecheck && pnpm lint
```

Expected: backend ≥574 tests passing; web ≥690 tests passing; typecheck clean; lint clean.

- [ ] **Step 3: Update memo/memo.md**

Append a new section under the Updates listing what shipped (commits, test counts, deferrals — same shape as the existing 2026-04-27 section).

- [ ] **Step 4: Commit ADR + memo**

```bash
git add docs/adr/0004-agent-promotion.md memo/memo.md
git commit -m "docs(adr): 0004 agent promotion (discriminator + strategy materializer)"
```

- [ ] **Step 5: Verify final state**

Run: `git log --oneline -25`
Expected: ~19 commits forming the A9 series, starting with `SourceType` enum and ending with the ADR.

---

## Self-review checklist (engineer should run after Task 19)

- [ ] Backend test count is 554 + ~20 = ≥574
- [ ] Web test count is 682 + ~8 = ≥690
- [ ] V49 migration applies cleanly to a fresh DB
- [ ] `promotion_request` table has `source_type`, agent columns, and CHECK constraint after migration
- [ ] Both partial unique indexes exist (`promotion_request_pending_skill_version_uq`, `promotion_request_pending_agent_version_uq`)
- [ ] PromotionService is no longer importing `SkillFileRepository`
- [ ] PromotionService constructor takes `List<PromotionMaterializer>` as last arg
- [ ] AgentPromotionMaterializer copies all source AgentLabels verbatim (no filter — LabelDefinition has no namespace scope yet)
- [ ] AgentVersionStats row created with `downloadCount=0` (not copied)
- [ ] PromoteAgentButton hidden in 4 cases (non-admin, non-PUBLISHED, already-global, has-pending)
- [ ] promotions.tsx renders correct link for both SKILL and AGENT rows
- [ ] ADR 0004 exists and references both spec and plan

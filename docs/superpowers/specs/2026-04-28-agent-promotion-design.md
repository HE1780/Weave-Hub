# A9 — Agent Promotion Design

**Date**: 2026-04-28
**Status**: Approved (brainstorm complete)
**ADR**: To be authored as `docs/adr/0004-agent-promotion.md` during implementation
**ADR 0003 clause**: 1.1 (fork-led Agent management)

## 1. Context

The fork has reached parity with Skills on every Agent capability except **Promotion** — submitting an agent for promotion to a global namespace, the queue of pending promotions, approve/reject workflow, and the actual materialization of an approved promotion into a globally-visible Agent.

The existing `PromotionService` (356 LOC) is hardcoded to skills. The brainstorm audit confirmed roughly 80% of its surface (state machine, authorization, audit log, optimistic locking, events, notifications) is genuinely source-agnostic; the remaining 20% (the `approvePromotion()` materialization block) is the only place where skill assumptions are load-bearing.

## 2. Goals

- Enable users to submit an Agent for promotion using the same workflow as Skills.
- Reuse the existing review queue UI as a single unified inbox.
- Preserve the existing skill-side promotion contract — no breaking change for skill clients.
- Keep the schema additive and the `PromotionService` refactor surgical.

## 3. Non-Goals

- Wiring `LandingHotSection` to read top-promoted agents (deferred to a follow-up task).
- Updating the agent search index on promotion (existing P3-3 follow-up).
- Adding source-link traceability (`sourceAgentId`) to the promoted Agent entity.
- Filter tabs on the review queue (YAGNI; can be added later).
- Per-source-type metrics or observability beyond what existing events already provide.

## 4. Architecture

### 4.1 Pattern selection

| Decision | Choice | Rationale |
|---|---|---|
| Q1 — Schema/service shape | **Discriminator column** (single table, polymorphic service) | Reuses 80% of existing logic; matches the codebase's existing notification `entity_type`/`entity_id` discriminator pattern; FK integrity preserved (vs generic `source_entity_id`). |
| Q2 — Materialization semantics | **Mirror Skill exactly** — create a NEW Agent in target namespace; reset stats; no source-link column | Keeps Skill/Agent promotion mentally identical; preserves source for the original owner. |
| Q3 — Schema migration shape | **Add nullable parallel columns** (keep `source_skill_id`, add `source_agent_id`, add discriminator + CHECK) | FK integrity preserved per side; backwards-compatible (existing rows backfill to `source_type='SKILL'`); minimal column rework. |
| Q4 — Materializer organization | **Strategy pattern** — `PromotionMaterializer` interface, two `@Component` impls, registry indexed by `SourceType` | Avoids ballooning `PromotionService` constructor with both skill+agent repository sets; each materializer is independently testable; open-closed for future source types. |
| Q5 — Frontend scope | **Single review queue with source-type badge**; agent-detail "Promote" button mirrors skill side; LandingHotSection deferred | Matches the unified backend; smallest UI surface; reviewers see everything in one inbox. |

### 4.2 Component map

```
┌────────────────────────────────────────────────────────────┐
│              PromotionController (paths unchanged)          │
│  POST /promotions  •  POST /promotions/{id}/approve  • ...  │
└────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌────────────────────────────────────────────────────────────┐
│       PromotionService (state/auth/events/notify reused)    │
│                                                             │
│   approvePromotion() → materializerRegistry.get(sourceType) │
└────────────────────────────────────────────────────────────┘
                              │
              ┌───────────────┴───────────────┐
              ▼                               ▼
   ┌───────────────────────┐      ┌───────────────────────┐
   │ SkillPromotionMaterializer │  │ AgentPromotionMaterializer │
   │  - copy Skill row     │      │  - copy Agent row      │
   │  - copy SkillVersion  │      │  - copy AgentVersion   │
   │  - copy SkillFile rows│      │  - reuse packageObjectKey
   │  - update latestVersionId    │  - new AgentVersionStats
   │  - emit SkillPublishedEvent  │  - copy AgentLabel/Tag │
   └───────────────────────┘      │  - emit AgentPublishedEvent
                                  └───────────────────────┘
```

### 4.3 What stays untouched

- `ReviewTaskStatus` enum (PENDING/APPROVED/REJECTED) — already generic.
- `ReviewPermissionChecker` — already platform-role-based.
- `PromotionSubmittedEvent` / `PromotionApprovedEvent` / `PromotionRejectedEvent` — already generic records.
- Notification dispatch (category=`"PROMOTION"`, entityType=`"PROMOTION_REQUEST"`) — already generic.
- `RouteSecurityPolicyRegistry` — `/api/v1/promotions/**` is already authenticated; the discriminator does not shift any auth boundary.

## 5. Database (V48)

New migration `V48__agent_promotion.sql`:

### 5.1 Add columns

```sql
ALTER TABLE promotion_request
  ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT 'SKILL',
  ADD COLUMN source_agent_id BIGINT NULL REFERENCES agent(id),
  ADD COLUMN source_agent_version_id BIGINT NULL REFERENCES agent_version(id),
  ADD COLUMN target_agent_id BIGINT NULL REFERENCES agent(id);

ALTER TABLE promotion_request
  ALTER COLUMN source_skill_id DROP NOT NULL,
  ALTER COLUMN source_version_id DROP NOT NULL;
```

`target_skill_id` is already nullable.

### 5.2 CHECK constraint

```sql
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
```

### 5.3 Rework partial unique indexes

```sql
DROP INDEX promotion_request_pending_source_version_uq;

CREATE UNIQUE INDEX promotion_request_pending_skill_version_uq
  ON promotion_request(source_version_id)
  WHERE status = 'PENDING' AND source_type = 'SKILL';

CREATE UNIQUE INDEX promotion_request_pending_agent_version_uq
  ON promotion_request(source_agent_version_id)
  WHERE status = 'PENDING' AND source_type = 'AGENT';
```

### 5.4 Backfill

`DEFAULT 'SKILL'` on the new `source_type` column auto-populates existing rows. No data migration script needed.

### 5.5 Naming choices

- `source_type` (not `source_kind`) — consistent with existing notification `entity_type`.
- `VARCHAR(16)` enum (not Postgres `ENUM` type) — consistent with how `ReviewTaskStatus` is stored throughout the codebase.

## 6. Domain layer

### 6.1 New types

- `SourceType` enum (`SKILL` | `AGENT`) — `server/skillhub-domain/.../review/SourceType.java`.
- `PromotionMaterializer` interface — `server/skillhub-domain/.../review/materialization/PromotionMaterializer.java`:
  ```java
  public interface PromotionMaterializer {
    SourceType supportedSourceType();
    MaterializationResult materialize(PromotionRequest request);
  }
  public record MaterializationResult(Long targetEntityId) {}
  ```
- `AgentPublishedEvent` — **already exists** in the codebase at `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/AgentPublishedEvent.java` with shape `(Long agentId, Long agentVersionId, Long namespaceId, String publisherId, Instant publishedAt)`. The materializer reuses this existing event; it is published today by `AgentPublishService`, `AgentLifecycleService`, and `AgentReviewService` whenever an AgentVersion enters PUBLISHED. No new event needed.

### 6.2 `PromotionRequest` entity changes

Adds four fields:

```java
@Enumerated(EnumType.STRING)
@Column(name = "source_type", nullable = false)
private SourceType sourceType;

@Column(name = "source_agent_id")           private Long sourceAgentId;
@Column(name = "source_agent_version_id")   private Long sourceAgentVersionId;
@Column(name = "target_agent_id")           private Long targetAgentId;
```

Existing `sourceSkillId` / `sourceVersionId` / `targetSkillId` JPA annotations drop `nullable=false`.

Two static factories enforce the discriminator invariant at the domain layer (in addition to the DB CHECK):

```java
public static PromotionRequest forSkill(Long sourceSkillId, Long sourceVersionId,
                                        Long targetNamespaceId, String submittedBy);
public static PromotionRequest forAgent(Long sourceAgentId, Long sourceAgentVersionId,
                                        Long targetNamespaceId, String submittedBy);
```

`sourceType` is set in the factory and has no public setter — it is immutable post-construction. A helper `setTargetEntityId(Long id, SourceType type)` writes either `targetSkillId` or `targetAgentId` based on type.

### 6.3 `SkillPromotionMaterializer` (extracted)

Lines 212–254 of the current `PromotionService.approvePromotion()` move verbatim into this class. Behavior is identical to today — same row creation, same `latestVersionId` update, same `SkillFile` copy by `storageKey`, same `SkillPublishedEvent`. Constructor injects only what skill materialization needs:

- `SkillRepository`
- `SkillVersionRepository`
- `SkillFileRepository`
- `DomainEventPublisher`

`supportedSourceType()` returns `SourceType.SKILL`.

### 6.4 `AgentPromotionMaterializer` (new)

Constructor injects:

- `AgentRepository`
- `AgentVersionRepository`
- `AgentVersionStatsRepository` (A10)
- `AgentTagRepository`
- `AgentLabelRepository`
- `DomainEventPublisher`

Materialization sequence:

1. Load source `Agent` + source `AgentVersion`.
2. Slug-uniqueness check in target namespace (`DomainBadRequestException` on collision).
3. Create new `Agent` row in target namespace: copy `slug`, `displayName`, `summary`, `ownerId`; set `visibility=PUBLIC`, `status=ACTIVE`.
4. Create new `AgentVersion` row: copy `version`, `soulMd`, `workflowYaml`, `manifestYaml`, `packageObjectKey` (object-storage shared, no copy), `packageSizeBytes`; set `status=PUBLISHED`, `publishedAt=now`.
5. Create new `AgentVersionStats` row with `downloadCount=0` (do **not** copy source stats).
6. Copy `AgentLabel` association rows. Each source association references a `label_definition` row, which itself is scoped to a namespace. Filter the copy to **only platform-scope labels and labels owned by the target namespace** — namespace-private labels from the source namespace do not follow the agent into global. (Same semantic as how skill labels would behave on promotion if labels were already wired through skill promotion; documenting it here because A4 added agent labels but agent promotion is the first time this filter has to be applied.)
7. Copy `AgentTag` rows from source, repointing `agent_id` and `version_id` to the new entities. All tags follow — they are owned by the agent itself, not by a namespace.
8. Publish `AgentPublishedEvent(newAgent.getId(), newVersion.getId(), targetNamespaceId, request.getSubmittedBy(), newVersion.getPublishedAt())` — the existing 5-arg event.
9. Return `MaterializationResult(newAgent.getId())`.

### 6.5 `PromotionService` changes

Two surgical changes — no broader refactor:

**`submitPromotion()`** gains a `SourceType` parameter and switches on it for source-side validation:

```java
public PromotionRequest submitPromotion(SourceType sourceType, Long sourceEntityId,
                                        Long sourceVersionId, Long targetNamespaceId,
                                        String userId, ...) {
  switch (sourceType) {
    case SKILL -> validateSkillSource(sourceEntityId, sourceVersionId);
    case AGENT -> validateAgentSource(sourceEntityId, sourceVersionId);
  }
  // unchanged: target namespace check, duplicate-pending check, save, event
}
```

The existing skill-only signature is kept as a thin overload calling the new method with `SourceType.SKILL`. The plan will grep for callers and remove the overload in a follow-up commit if no internal callers exist.

**`approvePromotion()`** body lines 212–254 replaced with:

```java
PromotionMaterializer materializer = materializers.get(request.getSourceType());
if (materializer == null) {
  throw new IllegalStateException("No materializer for " + request.getSourceType());
}
MaterializationResult result = materializer.materialize(request);
request.setTargetEntityId(result.targetEntityId(), request.getSourceType());
```

`materializers` is a `Map<SourceType, PromotionMaterializer>` constructed in the `PromotionService` constructor from injected `List<PromotionMaterializer>`. The `PromotionApprovedEvent` dispatch and notification call (lines 258–268) remain unchanged.

`rejectPromotion()`, `canViewPromotion()`, and `ReviewPermissionChecker` are untouched — already source-agnostic.

### 6.6 Repository changes

`PromotionRequestRepository` gains:

```java
Optional<PromotionRequest> findBySourceAgentIdAndStatus(
    Long agentId, ReviewTaskStatus status);

Page<PromotionRequest> findByTargetNamespaceIdAndStatusAndSourceType(
    Long namespaceId, ReviewTaskStatus status, SourceType sourceType, Pageable pageable);
```

(`findBySourceAgentIdAndStatus` mirrors the existing `findBySourceSkillIdAndStatus` — the duplicate-pending check is keyed by the agent/skill id, not the version id, matching how the current skill flow already works.)

Existing methods stay — no rename, no delete.

## 7. Controller + DTOs

### 7.1 Endpoints (paths unchanged)

| Endpoint | Change |
|---|---|
| `POST /api/v1/promotions` | Body shape extended per 7.2 |
| `POST /api/v1/promotions/{id}/approve` | Unchanged |
| `POST /api/v1/promotions/{id}/reject` | Unchanged |
| `GET /api/v1/promotions` | Response shape extended; new optional query param `sourceType` filter |
| `GET /api/v1/promotions/pending` | Same |
| `GET /api/v1/promotions/{id}` | Response shape extended |

### 7.2 Request DTO

```java
public record PromotionRequestDto(
    SourceType sourceType,           // required (defaults to SKILL via compact constructor)
    Long sourceSkillId,              // required when sourceType=SKILL
    Long sourceVersionId,            // required when sourceType=SKILL
    Long sourceAgentId,              // required when sourceType=AGENT
    Long sourceAgentVersionId,       // required when sourceType=AGENT
    Long targetNamespaceId           // always required
) {
  public PromotionRequestDto {
    if (sourceType == null) sourceType = SourceType.SKILL;  // backward compat
  }
}
```

Backward compatibility: existing skill-side clients that omit `sourceType` continue to work through the default. Frontend always sends it explicitly going forward.

Controller validates that the type/ids pairing is consistent (e.g., reject `sourceType=AGENT` with `sourceSkillId` set) with HTTP 400 — defense in depth alongside the DB CHECK.

### 7.3 Response DTO (additive only)

```java
public record PromotionResponseDto(
    Long id,
    SourceType sourceType,           // NEW
    // skill source fields (nullable when sourceType=AGENT)
    Long sourceSkillId, String sourceNamespace, String sourceSkillSlug, String sourceVersion,
    // agent source fields — NEW (nullable when sourceType=SKILL)
    Long sourceAgentId, String sourceAgentSlug, String sourceAgentVersion,
    // target
    String targetNamespace,
    Long targetSkillId,              // nullable when sourceType=AGENT
    Long targetAgentId,              // NEW (nullable when sourceType=SKILL)
    String status,
    String submittedBy, String submittedByName,
    String reviewedBy, String reviewedByName, String reviewComment,
    Instant submittedAt, Instant reviewedAt
) {}
```

Existing skill-side consumers ignore the new agent fields; `sourceType` lets the frontend dereference correctly.

## 8. Frontend

### 8.1 Types and API client

Hand-extend or regenerate types:

- `PromotionDtoSourceType`: `'SKILL' | 'AGENT'`
- `PromotionRequestDto` gains `sourceType`, `sourceAgentId`, `sourceAgentVersionId` (all optional in TS; narrowed at call sites)
- `PromotionResponseDto` gains `sourceType`, `sourceAgentId`, `sourceAgentSlug`, `sourceAgentVersion`, `targetAgentId`

Add helper `promotionApi.submitAgent(sourceAgentId, sourceAgentVersionId, targetNamespaceId)`. The existing skill-side helper stays generic; agent helper is a sibling, matching `agentSocialApi`/`socialApi` naming.

### 8.2 Hooks

- `usePromotionList(status, sourceType?)` — extended; query key gains `sourceType`.
- `useApprovePromotion`, `useRejectPromotion` — unchanged (pass-through by id).
- `useSubmitAgentPromotion` — new mutation; invalidates `['promotions']` and `['governance']`.
- Existing skill-side `useSubmitPromotion` — unchanged.

### 8.3 Agent-side submit UI

[`agent-detail.tsx`](../../../web/src/pages/agent-detail.tsx) gains a "Promote to global" button in the sidebar action group, mirroring the existing skill-detail promote button.

Visibility rules:

- Visible only when current user has platform role `SKILL_ADMIN` or `SUPER_ADMIN`.
- Visible only when version status = `PUBLISHED`.
- Hidden when agent is already in global namespace.
- Hidden when there's an existing PENDING promotion for this version.

The button opens a small confirm dialog (target namespace = global, no other inputs) and dispatches `useSubmitAgentPromotion`. Success → toast + refetch agent detail. Failure → toast with backend error message.

New file: `web/src/features/agent/promotion/promote-agent-button.tsx` + co-located test.

### 8.4 Review queue UI

Single unified queue (per Q5=a). Changes to `PromotionSection` in [`promotions.tsx`](../../../web/src/pages/dashboard/promotions.tsx):

1. **Source row branches on `sourceType`**:
   - SKILL: existing layout — "namespace/skill-slug @ version" link to `/skills/{ns}/{slug}/versions/{v}`.
   - AGENT: same shape — "namespace/agent-slug @ version" link to `/agents/{ns}/{slug}` (agent detail has no per-version routes today).
2. **Type badge** (small chip) rendered before the title — text "Skill" / "Agent". Use inline Tailwind classes for the Skills-blue / Agents-purple convention; tokenization is a separate concern.
3. **Approve/Reject buttons + comment input**: unchanged — they POST by id to type-agnostic endpoints.

i18n keys added under `promotions.*`:

- `promotions.sourceType.skill` = "Skill" / "技能"
- `promotions.sourceType.agent` = "Agent" / "智能体"

### 8.5 Frontend tests

| Component | Test file | Key cases |
|---|---|---|
| `promote-agent-button.tsx` | `.test.tsx` | renders for admin only; hidden when not PUBLISHED; hidden when pending exists; mutation fires with correct args |
| `useSubmitAgentPromotion` | `.test.tsx` | invalidates correct keys; error toast on 4xx |
| `promotions.tsx` | `.test.tsx` (extend) | renders SKILL row with skill link; renders AGENT row with agent link; renders type badge correctly |
| `promotionApi.submitAgent` | unit | sends correct body shape with `sourceType='AGENT'` |

Reuses existing `createWrapper()` test helper. Expected delta: ~6–8 new tests, ~2 updated existing tests.

## 9. Backend tests

### 9.1 Unit tests

| Class | Test file | Key cases |
|---|---|---|
| `SourceType` | `SourceTypeTest.java` | enum values stable; `valueOf` round-trips |
| `PromotionRequest` factories | `PromotionRequestTest.java` | `forSkill()` / `forAgent()` set correct fields; `setTargetEntityId(id, type)` writes correct column |
| `SkillPromotionMaterializer` | extracted from existing `PromotionServiceTest` | unchanged behavior — same assertions |
| `AgentPromotionMaterializer` | new `AgentPromotionMaterializerTest.java` | full materialization sequence; slug collision throws `DomainBadRequestException`; non-PUBLISHED source throws |
| `PromotionService.submitPromotion` | extend existing | agent path creates row with type=AGENT; duplicate-pending check uses agent partial index; non-published agent version rejected |
| `PromotionService.approvePromotion` dispatch | extend existing | unknown sourceType throws `IllegalStateException`; correct materializer invoked per type |

### 9.2 Repository test

`PromotionRequestRepositoryTest` adds:

- `findBySourceAgentVersionIdAndStatus` returns expected rows.
- `findByTargetNamespaceIdAndStatusAndSourceType` filters correctly.

### 9.3 Migration test

If a Flyway migration test pattern exists in the codebase (verify during plan phase), add one that:

1. Applies V48 to a DB with V47 baseline data.
2. Asserts existing rows have `source_type='SKILL'` after backfill.
3. Asserts CHECK constraint rejects ambiguous inserts.
4. Asserts both partial unique indexes work.

If no precedent exists, defer (per the same logic as Task 13 of the comments work — the project memo notes "no precedent → defer rather than invent a new pattern").

### 9.4 Controller test

`PromotionControllerTest` adds:

- POST with `sourceType='AGENT'` + agent ids → 200, service called with correct args.
- POST with mismatched type/ids → 400.
- POST without `sourceType` → defaults to SKILL.
- GET list with `sourceType='AGENT'` filter → service called with filter.
- Response DTO includes new agent fields when source is AGENT.

### 9.5 Test count target

Backend: 554 → ~575 (≈20 new tests). Web: 682 → ~690 (≈8 new tests).

## 10. Errors

All errors map to existing exception types — no new ones.

| Condition | Exception | HTTP |
|---|---|---|
| Slug collision in target namespace | `DomainBadRequestException` | 400 |
| Source agent version not PUBLISHED | `DomainBadRequestException` | 400 |
| Duplicate PENDING promotion for same agent version | `DomainBadRequestException` | 400 (via partial unique index) |
| Source agent / version not found | `DomainNotFoundException` | 404 |
| Caller lacks platform role | `DomainForbiddenException` | 403 |
| `sourceType` enum value invalid | Spring `HttpMessageNotReadableException` | 400 |
| Materializer registry miss (programming error) | `IllegalStateException` | 500 |
| CHECK constraint violation (defense in depth) | `DataIntegrityViolationException` | 500 — indicates DTO validation gap, fix at controller |

## 11. Notifications

Existing dispatch (PromotionService line 261–268) already uses `category="PROMOTION"`, `entityType="PROMOTION_REQUEST"`. No change needed — both skill and agent promotion approvals trigger the same notification to the submitter. The notification body JSON `{"status":"APPROVED"}` / `"REJECTED"` is type-agnostic; the entityId lets the UI dereference and render per-type detail.

Including `sourceType` in the notification body is an explicit non-goal for A9.

## 12. Observability

No new logging or metrics in A9. The natural future hook is a single `@EventListener(PromotionApprovedEvent.class)` bean emitting `promotion.approved.total` tagged by `sourceType` — a 10-LOC follow-up if needed post-launch.

## 13. Rollback plan

Migration is additive. Manual down-script for DR runbook:

```sql
DROP INDEX IF EXISTS promotion_request_pending_agent_version_uq;
DROP INDEX IF EXISTS promotion_request_pending_skill_version_uq;
CREATE UNIQUE INDEX promotion_request_pending_source_version_uq
  ON promotion_request(source_version_id) WHERE status = 'PENDING';
ALTER TABLE promotion_request DROP CONSTRAINT promotion_request_source_consistency;
ALTER TABLE promotion_request DROP COLUMN target_agent_id;
ALTER TABLE promotion_request DROP COLUMN source_agent_version_id;
ALTER TABLE promotion_request DROP COLUMN source_agent_id;
ALTER TABLE promotion_request DROP COLUMN source_type;
ALTER TABLE promotion_request ALTER COLUMN source_skill_id SET NOT NULL;
ALTER TABLE promotion_request ALTER COLUMN source_version_id SET NOT NULL;
```

Pre-condition: any agent-type rows must be deleted first or the `SET NOT NULL` will fail.

## 14. Deliverables

1. `server/.../db/migration/V48__agent_promotion.sql`
2. New domain types: `SourceType`, `PromotionMaterializer`, `MaterializationResult`, `AgentPublishedEvent`
3. `SkillPromotionMaterializer` (extracted), `AgentPromotionMaterializer` (new)
4. `PromotionRequest` entity changes (factories, fields, helper)
5. `PromotionService` surgical changes (submit dispatch, approve registry)
6. `PromotionRequestRepository` two new methods
7. `PromotionController` DTOs extended; new optional query param
8. Web: types, hooks, `promote-agent-button.tsx`, `promotions.tsx` row changes, i18n keys
9. Tests: ~20 backend + ~8 frontend
10. `docs/adr/0004-agent-promotion.md` capturing decisions and links to this spec

## 15. Out of scope (explicit deferrals)

- LandingHotSection wiring for promoted agents.
- Agent search index update on promotion (P3-3).
- Source-link traceability column on the promoted Agent entity.
- Filter tabs on the review queue.
- Per-source-type metrics/observability.
- Source type included in notification body JSON.

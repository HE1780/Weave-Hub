# Agent Publish & Review Pipeline — Implementation Plan

**Plan date:** 2026-04-27
**Status:** Ready for execution. Mirror skill publish/review architecture; no new patterns.
**Related:**
- `docs/adr/0001-agent-package-format.md` (locked package contract)
- `docs/plans/2026-04-26-agents-frontend-mvp.md` (already-shipped read-only UI)

## Goal

Take agents from "demo with mock data" to a complete publish-review-display loop, identical in shape to the skill flow:

1. Author packages an agent (AGENT.md + soul.md + workflow.yaml).
2. Author uploads via `/dashboard/publish/agent`.
3. Backend validates → creates `Agent` + `AgentVersion` rows → if non-PUBLIC namespace, creates an `AgentReviewTask`.
4. Reviewer (namespace admin) reviews via `/dashboard/agent-reviews`.
5. Approval flips `AgentVersion.status` to `PUBLISHED`; rejection sets `REJECTED`.
6. Public list at `/agents` shows only `PUBLISHED` versions; details fetch real backend data.

## Non-goals (deliberate, per ADR §Out of Scope)

- Workflow executor / runtime.
- Cross-version diff UI.
- Agent comments (extending the comment polymorphism comes after this lands).
- Search backend integration (`/agents` keyword search).
- Star / rating / promotion features for agents.

## Architectural decisions (locked)

| Decision | Choice | Rationale |
|---|---|---|
| Schema strategy | **Separate tables** `agent`, `agent_version`, `agent_review_task` — not polymorphic on `skill_version` | Foreign keys stay clean; agent fields (soul, workflow) don't pollute skill rows. Comments-style polymorphism is a *later* refactor when we extend comments to agents. |
| Reuse `ReviewTask` table | **No** — separate `agent_review_task` | The existing `review_task` has `skill_version_id` as a non-null column. Adding nullable + a discriminator would couple two unrelated workflows. |
| Package upload format | Same `.zip` archive shape as skills | Reuse `SkillPackageArchiveExtractor` (rename or generalize). |
| Validation | New `AgentMetadataParser` + `AgentPackageValidator` mirroring the skill ones | Domain logic is different (workflow.yaml parsing). |
| API path prefix | `/api/v1/agents` and `/api/web/agents` (alongside existing skill prefixes) | Consistent with the controller mapping convention. |
| Permissions | Authoring requires namespace `MEMBER`+; review requires `ADMIN`+. Same as skills. | Reuse `NamespacePermissionChecker`. |
| Notifications | New `NotificationCategory.AGENT` with `AGENT_PUBLISHED` and `AGENT_REVIEW_REQUESTED` events | Same shape as the skill events; reuse `NotificationListener` pattern. |
| Visibility | `PUBLIC` / `PRIVATE` / `NAMESPACE` mirroring `SkillVisibility` | Identical semantics. |

## Spec-versus-skill mapping (cheat sheet)

When writing each task, copy the corresponding skill file as a starting point:

| Agent file | Skill counterpart |
|---|---|
| `Agent.java` (entity) | `Skill.java` |
| `AgentVersion.java` | `SkillVersion.java` |
| `AgentRepository.java` | `SkillRepository.java` |
| `AgentVersionRepository.java` | `SkillVersionRepository.java` |
| `AgentReviewTask.java` | `ReviewTask.java` |
| `AgentReviewTaskRepository.java` | `ReviewTaskRepository.java` |
| `AgentMetadataParser.java` | `SkillMetadataParser.java` |
| `AgentPackageValidator.java` | `SkillPackageValidator.java` |
| `AgentPublishService.java` | `SkillPublishService.java` |
| `AgentReviewService.java` | `ReviewService.java` |
| `AgentController.java` | `SkillController.java` |
| `AgentPublishController.java` | `SkillPublishController.java` |
| `AgentReviewController.java` | `ReviewController.java` |
| `AgentVisibilityChecker.java` | `VisibilityChecker.java` |
| Web `useAgents.ts` (real fetch) | `use-search-skills.ts` |
| Web `usePublishAgent.ts` | `use-publish-skill.ts` |
| Web `pages/dashboard/publish-agent.tsx` | `pages/dashboard/publish.tsx` |
| Web `pages/dashboard/agent-reviews.tsx` | `pages/dashboard/reviews.tsx` |
| Web `pages/dashboard/agent-review-detail.tsx` | `pages/dashboard/review-detail.tsx` |

## Phase plan (5 phases, 30 tasks)

Phases are sequential; tasks within a phase are sequential too (later tasks depend on earlier ones in the same phase). Each task is one commit.

### Phase A — Database & domain (tasks 1–9, ~2 days)

Backend-only. After this phase, you can list/get agents from a DB but can't publish or review yet.

### Phase B — Publish flow (tasks 10–14, ~1 day)

Adds the upload + validation pipeline. After this phase, an authenticated user can POST a zip and create a DRAFT `agent_version` row.

### Phase C — Review flow (tasks 15–19, ~1 day)

Adds the review state machine and approve/reject endpoints. After this phase, a namespace admin can approve/reject an agent version and the status transitions are persisted.

### Phase D — Web UI: publish + my agents (tasks 20–24, ~1 day)

Frontend forms for authoring. After this phase, a logged-in user can upload an agent through the browser and see it in their dashboard.

### Phase E — Web UI: review dashboard + real-data list (tasks 25–30, ~1 day)

Reviewer pages and switching `useAgents` from mock to real backend. After this phase, the loop is complete.

---

## Pre-flight verification

Before starting Phase A:

```bash
git log --oneline -3                            # confirm at 6ca23dc8 or later
cd web && pnpm vitest run | tail -5             # 606/606 expected
cd server && ./mvnw test -q 2>&1 | tail -10     # 432/432 expected
```

Both servers should already be running from prior session (port 3000 + 8080).

---

## Phase A — Database & domain

### Task 1: V41 migration — `agent` and `agent_version` tables

**Files:** `server/skillhub-app/src/main/resources/db/migration/V41__agent_tables.sql`

Schema:
```sql
CREATE TABLE agent (
    id BIGSERIAL PRIMARY KEY,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    slug VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description TEXT,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    owner_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (namespace_id, slug),
    CHECK (visibility IN ('PUBLIC', 'PRIVATE', 'NAMESPACE')),
    CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE TABLE agent_version (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    soul_md TEXT,
    workflow_yaml TEXT,
    manifest_yaml TEXT,
    package_object_key VARCHAR(256),
    package_size_bytes BIGINT,
    submitted_by VARCHAR(64) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    UNIQUE (agent_id, version),
    CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'ARCHIVED'))
);

CREATE INDEX idx_agent_namespace ON agent(namespace_id);
CREATE INDEX idx_agent_version_agent ON agent_version(agent_id, status);
```

**Verify:**
```bash
cd server && ./mvnw -pl skillhub-app -DskipTests verify 2>&1 | tail -3   # Flyway runs against test DB
```

**Commit:**
```
feat(db): V41 — agent and agent_version tables
```

---

### Task 2: V42 migration — `agent_review_task` table

**Files:** `server/skillhub-app/src/main/resources/db/migration/V42__agent_review_task.sql`

Schema (mirrors `review_task`):
```sql
CREATE TABLE agent_review_task (
    id BIGSERIAL PRIMARY KEY,
    agent_version_id BIGINT NOT NULL REFERENCES agent_version(id) ON DELETE CASCADE,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    version INTEGER NOT NULL DEFAULT 1,
    submitted_by VARCHAR(64) NOT NULL,
    reviewed_by VARCHAR(64),
    review_comment TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ,
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_agent_review_namespace_status ON agent_review_task(namespace_id, status);
CREATE INDEX idx_agent_review_version ON agent_review_task(agent_version_id);
```

**Commit:**
```
feat(db): V42 — agent_review_task table
```

---

### Task 3: `Agent` entity + `AgentRepository`

**Files:**
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/Agent.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentStatus.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentVisibility.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentRepository.java`

Copy `Skill.java` and `SkillRepository.java`. Adapt fields (no rating/star/download counters in v1 — keep it minimal). Repository methods needed:
- `findById(Long)`, `findByIdIn(List<Long>)`
- `findByNamespaceIdAndSlug(Long, String)`
- `findByNamespaceId(Long, Pageable)`
- `findByVisibility(AgentVisibility, Pageable)` — for the `/api/web/agents` public list

**Test:** `AgentRepositoryTest.java` with `@DataJpaTest`. Three tests: insert/findById, unique (namespace_id, slug) constraint, findByVisibility filter.

**Commit:**
```
feat(domain): Agent entity + repository
```

---

### Task 4: `AgentVersion` entity + `AgentVersionStatus` + repository

**Files:**
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentVersion.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentVersionStatus.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentVersionRepository.java`

Status values: `DRAFT`, `PENDING_REVIEW`, `PUBLISHED`, `REJECTED`, `ARCHIVED`.
Repository methods:
- `findById`
- `findByAgentIdAndVersion`
- `findByAgentIdOrderBySubmittedAtDesc`
- `findByAgentIdAndStatus(Long, AgentVersionStatus)` — to fetch the latest PUBLISHED for the public detail view

**Test:** state-transition unit test on the entity (e.g., `submitForReview()` only valid from DRAFT).

**Commit:**
```
feat(domain): AgentVersion entity + status state machine
```

---

### Task 5: `AgentReviewTask` entity + repository + status enum

**Files:** mirror `ReviewTask.java`, `ReviewTaskRepository.java`, `ReviewTaskStatus.java` under `domain/agent/review/`.

Repository methods:
- `findById`
- `findByAgentVersionId`
- `findByNamespaceIdAndStatus(Long, AgentReviewTaskStatus, Pageable)` — reviewer inbox

**Commit:**
```
feat(domain): AgentReviewTask entity + repository
```

---

### Task 6: `AgentMetadataParser`

**Files:**
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentMetadata.java` (record: name, description, version, soulFile, workflowFile, skills, frontmatter)
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentMetadataParser.java`
- `server/skillhub-domain/src/test/java/.../AgentMetadataParserTest.java`

Reuse the YAML parser dependency that `SkillMetadataParser` already pulls in (SnakeYAML). Parse `AGENT.md` frontmatter exactly as the ADR specifies.

**Test cases (TDD — write tests first):**
1. Parses minimal valid AGENT.md (just `name` + `description`).
2. Parses optional fields (version, soulFile, workflowFile, skills).
3. Throws `DomainBadRequestException` when `name` is missing.
4. Throws when `description` is missing.
5. Preserves unknown keys in `frontmatter` map.
6. Skills list with mixed `{name}` and `{name, version}` entries — both shapes accepted.

**Commit:**
```
feat(domain): AgentMetadataParser with TDD
```

---

### Task 7: `AgentPackageValidator`

**Files:**
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentPackageValidator.java`
- `server/skillhub-domain/src/test/java/.../AgentPackageValidatorTest.java`

Validates:
- All three required files (`AGENT.md`, `soul.md`, `workflow.yaml`) exist at root.
- `workflow.yaml` parses as YAML (don't validate semantics — ADR says executor handles that).
- Forbidden paths (`..`, absolute paths, etc.) — reuse the rules from `SkillPackagePolicy`.
- `manifest.skills[].name` references match an existing `agent`-or-`skill` slug? **No**, ADR forbids cross-validation. Just structural.

**Test cases (TDD):** 6 cases — happy path, each missing required file (×3), bad workflow YAML, path traversal attempt.

**Commit:**
```
feat(domain): AgentPackageValidator with TDD
```

---

### Task 8: `AgentVisibilityChecker`

**Files:**
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentVisibilityChecker.java`
- `server/skillhub-domain/src/test/java/.../AgentVisibilityCheckerTest.java`

Returns `boolean canRead(Agent, principal, namespaceMembership)`. Identical logic to `VisibilityChecker` for skills:
- `PUBLIC` → always true.
- `NAMESPACE` → only namespace members.
- `PRIVATE` → only owner + admins.

**Commit:**
```
feat(domain): AgentVisibilityChecker (mirrors skill VisibilityChecker)
```

---

### Task 9: Phase A integration — `AgentService` (read-only methods)

**Files:**
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/service/AgentService.java`
- `server/skillhub-domain/src/test/java/.../AgentServiceTest.java`

Methods (in this task — write methods come in Phase B):
- `Page<Agent> listPublic(Pageable)` — public visibility only.
- `Optional<Agent> findByNamespaceAndSlug(String namespace, String slug, principal)` — applies visibility check.
- `Optional<AgentVersion> findLatestPublished(Long agentId)`.
- `List<AgentVersion> listVersions(Long agentId, principal)` — only PUBLISHED unless caller is owner/admin.

**Test:** 4-5 tests covering the visibility branch logic.

**Commit:**
```
feat(domain): AgentService read methods + visibility gating
```

---

## Phase B — Publish flow

### Task 10: `AgentPublishedEvent` (domain event)

**Files:** `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/event/AgentPublishedEvent.java`

Mirror `SkillPublishedEvent`. Fields: `agentId`, `agentVersionId`, `namespaceId`, `publisherUserId`, `publishedAt`.

**Commit:**
```
feat(domain): add AgentPublishedEvent
```

---

### Task 11: `AgentPublishService.publish(...)`

**Files:**
- Modify: `AgentService.java` (add `publish` method) OR
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/service/AgentPublishService.java`

Decision: **separate `AgentPublishService`** to match `SkillPublishService` pattern.

Method signature:
```java
public AgentVersion publish(Long namespaceId,
                            String slug,
                            String displayName,
                            String description,
                            AgentVisibility visibility,
                            String version,
                            ParsedPackage parsedPackage,
                            String publisherUserId,
                            String packageObjectKey)
```

Behavior:
1. Find or create `Agent` (insert if `(namespace_id, slug)` doesn't exist; reject if owner mismatch).
2. Reject if `(agent_id, version)` already exists (no overwrite).
3. Insert `AgentVersion` with `status = DRAFT`.
4. Determine review requirement:
   - If `visibility == PRIVATE` → auto-publish (status → `PUBLISHED`, `published_at = now()`), publish `AgentPublishedEvent`.
   - Else (`PUBLIC` or `NAMESPACE`) → status → `PENDING_REVIEW`, create `AgentReviewTask`.
5. Return the new `AgentVersion`.

**Test:** 5 cases — fresh agent, new version of existing agent, owner mismatch rejection, duplicate version rejection, PRIVATE auto-publish vs PUBLIC pending-review path.

**Commit:**
```
feat(domain): AgentPublishService.publish with auto-publish vs review-gate
```

---

### Task 12: `AgentPackageArchiveExtractor`

**Files:** `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/support/AgentPackageArchiveExtractor.java`

Copy `SkillPackageArchiveExtractor`. Key change: it must extract three named files (`AGENT.md`, `soul.md`, `workflow.yaml`) and return them as strings, plus the raw zip for object-storage upload.

Returns: `record ExtractedAgent(String manifestYaml, String soulMd, String workflowYaml, byte[] rawZip)`.

**Test:** 3 cases — all-three-present happy path, missing soul.md → throws, oversized package → throws.

**Commit:**
```
feat(infra): AgentPackageArchiveExtractor with size + structure checks
```

---

### Task 13: DTOs for publish endpoint

**Files:**
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/AgentPublishRequest.java` — wraps `MultipartFile` + visibility query param via controller method args.
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/AgentPublishResponse.java` (record): `agentId`, `agentVersionId`, `slug`, `version`, `status`.
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/AgentResponse.java` (for list/get).
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/AgentVersionResponse.java`.

**Commit:**
```
feat(api): add agent DTOs (publish, list, version)
```

---

### Task 14: `AgentPublishController.POST /{namespace}/publish`

**Files:**
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/AgentPublishController.java`
- `server/skillhub-app/src/test/java/.../AgentPublishControllerTest.java`

Request mapping:
```java
@RequestMapping({"/api/v1/agents", "/api/web/agents"})
```

Endpoint:
```java
@PostMapping("/{namespace}/publish")
public ApiResponse<AgentPublishResponse> publish(
    @PathVariable String namespace,
    @RequestParam("file") MultipartFile file,
    @RequestParam(value = "visibility", defaultValue = "PRIVATE") String visibility,
    @AuthenticationPrincipal PlatformPrincipal principal)
```

Flow:
1. Resolve namespace → namespace_id (reject if unknown / no membership).
2. Extract archive → ExtractedAgent.
3. Parse `AGENT.md` → `AgentMetadata`.
4. Validate package → `AgentPackageValidator`.
5. Upload zip to object storage (reuse the storage service used by skills).
6. Call `agentPublishService.publish(...)`.
7. Return `AgentPublishResponse`.

**Add security policy entries** to `RouteSecurityPolicyRegistry`:
```java
RouteAuthorizationPolicy.authenticated(HttpMethod.POST, "/api/v1/agents/*/publish"),
RouteAuthorizationPolicy.authenticated(HttpMethod.POST, "/api/web/agents/*/publish"),
```

**Test:** controller test with `@MockBean AgentPublishService` — 4 cases: happy path, malformed zip → 400, missing visibility default to PRIVATE, unauthorized → 401.

**Commit:**
```
feat(api): AgentPublishController with security policy
```

---

## Phase C — Review flow

### Task 15: `AgentReviewService`

**Files:**
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/review/AgentReviewService.java`
- Test.

Methods:
- `Page<AgentReviewTask> listForReviewer(Long namespaceId, AgentReviewTaskStatus, Pageable)`
- `AgentReviewTask getById(Long, principal)` — namespace admin gating.
- `AgentReviewTask approve(Long taskId, String reviewerUserId, String comment)` — flips `agent_version.status` → PUBLISHED, `agent_version.published_at = now()`, fires `AgentPublishedEvent`.
- `AgentReviewTask reject(Long taskId, String reviewerUserId, String comment)` — flips `agent_version.status` → REJECTED.
- Concurrency: use the `@Version` optimistic-lock column on `AgentReviewTask` (already in entity from Task 5).

**Test:** 4 state-transition cases + 1 concurrent-modification case.

**Commit:**
```
feat(domain): AgentReviewService approve/reject with optimistic lock
```

---

### Task 16: Review DTOs

**Files:**
- `AgentReviewTaskResponse.java`
- `AgentReviewActionRequest.java` (`{ "comment": "string" }`)
- `AgentReviewVersionDetailResponse.java` — full agent + version + soul + workflow for the review screen.

**Commit:**
```
feat(api): add agent review DTOs
```

---

### Task 17: `AgentReviewController`

**Files:** mirror `ReviewController` patterns.

Endpoints:
```
GET    /api/web/agents/reviews?namespaceId={n}&status=PENDING&page=0&size=20
GET    /api/web/agents/reviews/{taskId}
POST   /api/web/agents/reviews/{taskId}/approve
POST   /api/web/agents/reviews/{taskId}/reject
```

**Security policy** entries — all `authenticated`:
```java
RouteAuthorizationPolicy.authenticated(HttpMethod.GET, "/api/v1/agents/reviews"),
RouteAuthorizationPolicy.authenticated(HttpMethod.GET, "/api/v1/agents/reviews/*"),
RouteAuthorizationPolicy.authenticated(HttpMethod.POST, "/api/v1/agents/reviews/*/approve"),
RouteAuthorizationPolicy.authenticated(HttpMethod.POST, "/api/v1/agents/reviews/*/reject"),
// + the same 4 for /api/web/agents/reviews
```

**Test:** 6 cases — list authorized, approve happy, reject happy, approve already-approved → 409, reject by non-admin → 403, list with unknown namespace → 404.

**Commit:**
```
feat(api): AgentReviewController + security policy
```

---

### Task 18: Notification category + listener

**Files:**
- Modify: `NotificationCategory.java` — add `AGENT_PUBLISHED`, `AGENT_REVIEW_REQUESTED`.
- Create: `AgentNotificationListener.java` — listens for `AgentPublishedEvent` and review-task creation, dispatches notifications to the namespace owner.

Mirror the existing `SkillVersionCommentNotificationListener` pattern (see commit `f0a08e14`).

**Test:** 2 cases — event fires, notification row inserted with right type.

**Commit:**
```
feat(notification): AGENT_PUBLISHED and AGENT_REVIEW_REQUESTED dispatch
```

---

### Task 19: Public read endpoints — `AgentController`

**Files:**
- `AgentController.java`:
  - `GET /api/web/agents` — paged public list (`PUBLISHED` only).
  - `GET /api/web/agents/{namespace}/{slug}` — agent detail (with visibility check).
  - `GET /api/web/agents/{namespace}/{slug}/versions` — version list (PUBLISHED for public; all for owner/admin).
  - `GET /api/web/agents/{namespace}/{slug}/versions/{version}` — single version detail (full soul + workflow body).

**Security policy:**
```java
RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/agents"),
RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/agents"),
RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/agents/*/*"),
RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/agents/*/*"),
RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/agents/*/*/versions"),
RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/agents/*/*/versions"),
RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/v1/agents/*/*/versions/*"),
RouteAuthorizationPolicy.permitAll(HttpMethod.GET, "/api/web/agents/*/*/versions/*"),
```

**Test:** 5 controller-with-mock-service cases.

**Commit:**
```
feat(api): AgentController public read endpoints
```

---

## Phase D — Web UI: publish + my agents

### Task 20: `commentsApi`-style `agentsApi` group in client.ts

**Files:** Modify `web/src/api/client.ts` — append after `commentsApi`:

```ts
export const agentsApi = {
  list: (params: {...}) => fetchJson<...>(...),
  get: (namespace, slug) => ...,
  listVersions: (namespace, slug) => ...,
  getVersion: (namespace, slug, version) => ...,
  publish: (namespace, file, visibility) => ... (FormData),
}
```

Plus `agentReviewsApi`:
```ts
export const agentReviewsApi = {
  list: (namespaceId, status, page, size) => ...,
  get: (taskId) => ...,
  approve: (taskId, comment) => ...,
  reject: (taskId, comment) => ...,
}
```

**Commit:**
```
feat(web): add agentsApi and agentReviewsApi groups
```

---

### Task 21: Switch `useAgents` from mock → real fetch

**Files:** Modify `web/src/features/agent/use-agents.ts` and `use-agent-detail.ts`.

Replace the `MOCK_AGENTS` body with `agentsApi.list(...)` / `agentsApi.get(...)`.

**Keep** `mock-agents.ts` and the existing tests (the test mocks the hook anyway). Add an integration test that verifies the new fetch shape.

**Commit:**
```
feat(web): switch useAgents and useAgentDetail to real backend
```

---

### Task 22: `usePublishAgent` mutation hook

**Files:**
- `web/src/features/agent/use-publish-agent.ts` (+ test)

Mirror `use-publish-skill.ts`. Mutation invalidates `['agents']` query key on success.

**Commit:**
```
feat(web): add usePublishAgent mutation
```

---

### Task 23: `<AgentPublishPage>` component

**Files:**
- `web/src/pages/dashboard/publish-agent.tsx` (+ test)

Visual: copy `web/src/pages/dashboard/publish.tsx`. Fields:
- File upload (`.zip`, must contain AGENT.md/soul.md/workflow.yaml — preview shows file tree).
- Namespace selector (reuse existing namespace dropdown component).
- Visibility radio (PUBLIC / PRIVATE / NAMESPACE).
- Submit → `usePublishAgent.mutate()`.
- Success toast → navigate to `/agents/{namespace}/{slug}`.

**Add route** in `router.tsx`: `/dashboard/publish/agent`.

**i18n keys** under `agentPublish.*` (en + zh).

**Commit:**
```
feat(web): AgentPublishPage with file upload and visibility selector
```

---

### Task 24: My Agents tab in Dashboard

**Files:**
- `web/src/pages/dashboard/my-agents.tsx`
- Add route `/dashboard/my-agents` and a new section in the dashboard sidebar.

Lists current user's authored agents (filter `agentsApi.list({ ownerId: principal.userId })` — add an `ownerId` query param to the list endpoint as part of this task; update Task 19 mentally).

Each row: name, version, status badge (DRAFT / PENDING_REVIEW / PUBLISHED / REJECTED), submit-for-review button (if DRAFT), edit button (if DRAFT or REJECTED).

**Commit:**
```
feat(web): My Agents dashboard tab
```

---

## Phase E — Web UI: review dashboard + real-data list

### Task 25: `useAgentReviews` + `useApproveAgentReview` + `useRejectAgentReview`

**Files:**
- `web/src/features/agent/use-agent-reviews.ts`
- `web/src/features/agent/use-approve-agent-review.ts`
- `web/src/features/agent/use-reject-agent-review.ts`
- 3 corresponding test files.

Mirror existing `useReviews` / `useApproveReview` / `useRejectReview` patterns.

**Commit:**
```
feat(web): add agent review query and mutation hooks
```

---

### Task 26: `<AgentReviewsPage>` (inbox)

**Files:**
- `web/src/pages/dashboard/agent-reviews.tsx` (+ test)
- Add route `/dashboard/agent-reviews`.

Mirror `reviews.tsx`. Filter by namespace, status. Each row links to detail page.

**Commit:**
```
feat(web): AgentReviewsPage (reviewer inbox)
```

---

### Task 27: `<AgentReviewDetailPage>`

**Files:**
- `web/src/pages/dashboard/agent-review-detail.tsx` (+ test)
- Add route `/dashboard/agent-reviews/$taskId`.

Shows agent metadata, soul.md (rendered markdown), workflow.yaml (syntax-highlighted), submit author, submitted-at. Approve / Reject buttons with comment textarea.

**Commit:**
```
feat(web): AgentReviewDetailPage with approve/reject controls
```

---

### Task 28: Agent detail page — switch from mock to real

**Files:** Modify `web/src/pages/agent-detail.tsx`.

Currently reads from mock via `useAgentDetail(name)`. Switch the lookup to `useAgentDetail(namespace, slug)` (params change: agent detail page route should become `/agents/$namespace/$slug` to match backend canonical URL).

**Migration**: keep the old `/agents/$name` route working with a redirect for the demo data; mark deprecated in the route.

**Commit:**
```
feat(web): wire agent-detail page to real backend
```

---

### Task 29: Hero "Publish" CTA → menu

**Files:** Modify `web/src/pages/landing.tsx`.

Replace the single "Publish" button with a small dropdown menu offering "Publish Skill" / "Publish Agent". Reuse the existing `@/shared/ui/dropdown-menu`.

**Commit:**
```
feat(web): split Hero Publish CTA into Skill/Agent dropdown
```

---

### Task 30: Final verification + memo

**Steps:**
1. Backend full test — must stay 432 + ~30 new (agent tests) = ~460 passing.
2. Web full test — must stay 606 + ~30 new (agent tests) = ~640 passing.
3. Web typecheck + lint — clean.
4. **Manual smoke test** through the browser:
   - Log in.
   - Upload a sample agent zip via `/dashboard/publish/agent` with PRIVATE visibility → should auto-publish, redirect to `/agents/{ns}/{slug}`.
   - Upload another with PUBLIC visibility → should land in PENDING_REVIEW.
   - As a namespace admin, visit `/dashboard/agent-reviews` → see the pending task.
   - Approve it → status flips to PUBLISHED.
   - Visit `/agents` → see the published agent in the list.
   - Click into detail → see soul + workflow.
5. Update `memo/memo.md` with what shipped.
6. Push to `origin/main`.

**Sample agent zip** for smoke test: include a fixture in `server/skillhub-app/src/test/resources/agent-fixtures/customer-support.zip` (used by Task 12's parser test too).

**Commit:**
```
docs(memo): record agent publish-review pipeline session
```

---

## Risk register

1. **Object-storage (MinIO) upload coupling.** Skill publish writes the zip to MinIO via a domain port. Agent publish must use the same port. Risk: if the port is skill-specific, we need to generalize it. **Pre-check at start of Task 12** — if the existing port is named `SkillPackageStorage`, decide whether to (a) reuse it as-is (zip is just bytes), or (b) introduce a parallel `AgentPackageStorage`. Pick (a) unless interface forces otherwise.

2. **Bean validation still not wired** (carry-over from skill-version comments session). Validation is enforced at entity / DB CHECK / parser level only. Don't try to wire `@Valid`/`@NotBlank` on agent DTOs — same trap.

3. **`open-in-view: false` is the project default.** All read paths in `AgentService` and `AgentReviewService` must be `@Transactional(readOnly=true)` to prevent LazyInitializationException — same caveat that bit Task 7 of the comments session.

4. **`AgentVersion.workflowYaml` can be large.** Soul + workflow could push toward MB. Use `TEXT` not `VARCHAR`. `package_object_key` stores the canonical zip in MinIO; the inline columns are for fast read paths.

5. **Slug uniqueness race.** Two concurrent uploads with the same `(namespace_id, slug)` should get one success and one 409. The DB `UNIQUE` constraint handles it; the service must catch `DataIntegrityViolationException` and convert to `DomainConflictException`.

6. **Visibility transitions.** A PRIVATE agent that gets re-published as PUBLIC needs review the *second* time (when visibility upgrades). Out-of-scope for v1 — visibility is fixed per agent (set on first publish). Document this in Task 11's javadoc.

7. **`/agents/$name` deprecated route.** Task 28's redirect needs a graceful fallback if `name` is one of the mock fixtures (no namespace). Strategy: if no slash in `$name`, redirect to `/agents/global/$name` and let the backend 404 if missing.

8. **Frontend i18n coverage.** ~40 new keys total across all tasks. Don't bundle — add keys per task to keep PRs reviewable.

---

## Effort summary

| Phase | Tasks | Days | Commits |
|---|---|---|---|
| A — DB + domain | 1–9 | ~2 | 9 |
| B — Publish | 10–14 | ~1 | 5 |
| C — Review | 15–19 | ~1 | 5 |
| D — Web publish | 20–24 | ~1 | 5 |
| E — Web review + integration | 25–30 | ~1 | 6 |
| **Total** | **30 tasks** | **~6 days** | **30 commits** |

## Plan complete

Save to `docs/plans/2026-04-27-agent-publish-review-pipeline.md`.

Two execution options:

1. **Sequential** — one task per commit, run plan in this session.
2. **Phase-by-phase with checkpoints** — pause between phases for user review (recommended given scope).

If picking option 2: stop after Phase A and ask user "Phase A complete (9 commits, all tests green). Continue to Phase B?"

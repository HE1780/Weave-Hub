# 2026-04-29 — Agent followups bundle design

> ✅ **SHIPPED — 实施完成 2026-04-29。** 见 plan [2026-04-29-agent-followups-bundle.md](../plans/2026-04-29-agent-followups-bundle.md)。详见 [docs/plans/2026-04-29-spec-status-ledger.md](../../plans/2026-04-29-spec-status-ledger.md)。

**ADR clause:** ADR 0003 §1.1 (Agent management is fork-owned)
**Source:** `memo/memo.md` 2026-04-28 P0 follow-ups list, items #1–#4
**Plan target:** `docs/plans/2026-04-29-agent-followups-bundle.md` (4 tasks → 4 commits)

## 0. Why this exists

Four independent gaps surfaced by the 2026-04-28 P0 follow-ups audit, all small,
all loosely coupled, none independently large enough to merit its own
brainstorm + ADR + plan cycle. Bundled because they share the same shipping
window (current main, single feat-style merge) and the same admin / dashboard
surface area (`web/src/pages/dashboard/*`).

The bundle is **not** thematic — items 1 & 4 touch the recommendation /
collection paths, item 2 touches list rendering, item 3 touches moderation. The
binding decision is "ship them together because each individually is too small
to warrant ceremony, and together they round out the Agent–Skill parity work
that A0–A10 left unfinished."

## 1. Goals (each item)

| # | Goal | Acceptance |
|---|---|---|
| 1 | Agent promotion no longer requires the frontend to know GLOBAL's namespace id | `PromoteAgentButton` works for any admin (incl. non-GLOBAL members); `targetNamespaceId` is optional in the request body and defaults server-side |
| 2 | Agent list cards show average rating + rating count (matches Skill cards) | A card with `ratingCount > 0` renders `★ <ratingAvg.toFixed(1)>`; cards with zero ratings render no rating chip |
| 3 | Admin moderation dashboard handles agent reports | Reports page has Skill / Agent top-level tabs; Agent tab pages PENDING / RESOLVED / DISMISSED with resolve + dismiss buttons hitting agent-specific admin endpoints |
| 4 | My Stars page shows agents the viewer has starred | Stars page has Skills / Agents top-level tabs; Agents tab paginates the user's starred agents in the same shape as Skills |

## 2. Non-goals

- No new ADR — Agent–Skill parity is already covered by ADR 0003 §1.1.
- No skill-side regressions; existing Skill flows for promotion / cards / reports / stars are unchanged in observable behaviour.
- No promotion-time metadata expansion (e.g., promotion message / scheduling). Item 1 is **only** about removing the frontend GLOBAL-id dependency.
- No rating chip design overhaul — the chip uses the exact same CSS / SVG / layout as Skill card's existing chip.
- No batch report actions or report search / filtering on the moderation dashboard. Single-item resolve / dismiss only.
- No e2e tests. Each item ships with unit + controller + component tests.

## 3. Architecture per item

### Item 1 — Agent promotion default GLOBAL target

**Current state:**
- `AgentResponse` already carries `ratingAvg`, `ratingCount`, `starCount`, etc.
- `PromotionRequestDto` (`server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/PromotionRequestDto.java`) already defaults `sourceType` to SKILL via the compact constructor.
- `PromotionController.submitPromotion` dispatches by `sourceType`. For AGENT, it calls `governanceWorkflowAppService.submitAgentPromotion(sourceAgentId, sourceAgentVersionId, targetNamespaceId, userId)` — `targetNamespaceId` is read directly from the request body.
- `web/src/features/agent/promotion/promote-agent-button.tsx` requires a `globalNamespaceId` prop and hides the button when null. `agent-detail.tsx:125-126` resolves it via `useMyNamespaces` and falls back to null when the viewer is not a GLOBAL member.

**Change:**
1. **Backend** — make `targetNamespaceId` optional for AGENT submissions:
   - In `PromotionController.submitPromotion`'s AGENT branch, when `request.targetNamespaceId() == null`, look up the GLOBAL namespace via `NamespaceRepository.findBySlug("global")` and use its id. If GLOBAL is missing (impossible in practice but defensive), throw `DomainBadRequestException("error.namespace.global.notFound")`.
   - Skill branch is unchanged — Skill flow already does this resolution on the frontend (`use-user-queries.ts:18`), and changing it is out of scope.
   - **Reason for resolving in the controller, not the app service:** keeps the dispatch logic in one place and avoids touching `GovernanceWorkflowAppService.submitAgentPromotion`'s signature. The controller is already the dispatch layer; resolving GLOBAL there is symmetric with how it parses `sourceType`.
2. **Frontend** — strip the GLOBAL-id plumbing:
   - `PromoteAgentButton` removes the `globalNamespaceId: number | null | undefined` prop and the `if (!versionId || !globalNamespaceId) return null` gate (keep the `versionId` half of the gate).
   - `useSubmitAgentPromotion`'s mutation function omits `targetNamespaceId` from the request body.
   - `agent-detail.tsx` removes `useMyNamespaces` import + `globalNamespaceId` derivation + the prop pass-through.
   - The `isInGlobalNamespace` prop **stays** — that gate (don't promote agents already in GLOBAL) still applies and is independently computed from the agent's namespace.

**Tests:**
- New controller test: `PromotionPortalControllerTest.submitAgentPromotion_omitsTargetNamespaceId_resolvesToGlobal` — POST without `targetNamespaceId`, assert backend resolves GLOBAL via `NamespaceRepository.findBySlug("global")` (verified via mock) and dispatches with that id.
- Existing controller test for explicit `targetNamespaceId` keeps passing (backwards compatible).
- Component test: `promote-agent-button.test.tsx` — renders without `globalNamespaceId` prop, button visible for admin.

### Item 2 — Agent list cards: ratingAvg + ratingCount

**Current state:**
- Backend `AgentResponse` already returns `ratingAvg` (BigDecimal) and `ratingCount` (Integer) — list endpoint returns this DTO already.
- Frontend `AgentSummary` (`web/src/api/agent-types.ts`) is missing both fields.
- `useAgents` hook's mapper (`use-agents.ts:39`) maps `starCount` only.
- `AgentCard` (`web/src/features/agent/agent-card.tsx:60`) renders only `starCount`.

**Change:**
1. `AgentSummary` adds optional `ratingAvg?: number` (frontend treats backend's BigDecimal as a number — same as `SkillSummary` does today) and `ratingCount?: number`.
2. `useAgents` mapper passes both through: `ratingAvg: dto.ratingAvg !== undefined && dto.ratingAvg !== null ? Number(dto.ratingAvg) : undefined`, `ratingCount: dto.ratingCount ?? 0`.
3. `AgentCard` renders the rating chip identically to `SkillCard:80-87`: same SVG, same `text-primary` color, same `toFixed(1)`. The chip is conditional on `agent.ratingAvg !== undefined && (agent.ratingCount ?? 0) > 0`.

**Tests:**
- `agent-card.test.tsx` adds two cases: (a) renders rating chip when `ratingCount > 0` with the formatted value, (b) hides chip when `ratingCount === 0`.
- `use-agents.test.tsx` adds one case: verifies `ratingAvg` and `ratingCount` are propagated through the mapper.

### Item 3 — Admin moderation dashboard: agent reports

**Current state:**
- **Skill side** has `AdminSkillReportController` with three endpoints: `GET /api/web/admin/skill-reports?status=...&page=...&size=...`, `POST /api/web/admin/skill-reports/{reportId}/resolve`, `POST /api/web/admin/skill-reports/{reportId}/dismiss`.
- Frontend `useSkillReports / useResolveSkillReport / useDismissSkillReport` hooks (`web/src/features/report/use-skill-reports.ts`) wrap them.
- `web/src/pages/dashboard/reports.tsx` uses status-based Tabs (PENDING / RESOLVED / DISMISSED).
- **Agent side** has only the user-submit endpoint (`AgentReportController.submitReport`). **Domain `AgentReportService` lacks `resolveReport` / `dismissReport`.** `AgentReportRepository.findByStatus` exists.
- `memo/memo.md:32` and `fork-backlog.md:434-447` previously claimed admin endpoints exist — that claim is false; only submit exists. Spec corrects this.

**Change:**

**Backend (new):**
1. `AgentReportService` (domain) — add `Page<AgentReport> listByStatus(AgentReportStatus, Pageable)`, `AgentReport resolveReport(Long reportId, String adminUserId, String comment, ...)` (with optional disposition), `AgentReport dismissReport(Long reportId, String adminUserId, String comment)`. Mirror `SkillReportService` exactly (verified at `server/skillhub-domain/.../SkillReportService.java:87-114`):
   - `resolveReport` has two overloads — `(reportId, actor, comment, clientIp, userAgent)` defaults to `RESOLVE_ONLY`; `(reportId, actor, disposition, comment, clientIp, userAgent)` takes an explicit disposition.
   - **Disposition does trigger lifecycle action automatically** (Skill side calls `skillGovernanceService.hideSkill` for `RESOLVE_AND_HIDE` and `archiveSkillAsAdmin` for `RESOLVE_AND_ARCHIVE`). Mirror this: Agent side calls `AgentLifecycleService.archive(...)` for `RESOLVE_AND_ARCHIVE`. **Hide for agents needs verification in plan**: if `AgentLifecycleService` lacks a hide path, scope decision = either add it (mirror skill) or omit `RESOLVE_AND_HIDE` from agent disposition enum for v1 with a follow-up. Plan task will preflight this.
   - All paths set status RESOLVED / DISMISSED, `handledBy` / `handleComment` / `handledAt`, persist, fire `AgentReportResolvedEvent` / `AgentReportDismissedEvent` (new — mirror skill's `ReportResolvedEvent` / `ReportDismissedEvent`), call `auditLogService.record` with `RESOLVE_AGENT_REPORT` / `DISMISS_AGENT_REPORT` actions, and notify the reporter via `governanceNotificationService` if Agent reporter notifications are wired (preflight in plan; if not wired, skip and add follow-up).
   - `dismissReport` requires status == PENDING.
2. New domain enum `AgentReportDisposition` mirroring `SkillReportDisposition`: `RESOLVE_ONLY`, `RESOLVE_AND_HIDE` (conditional on hide path existing), `RESOLVE_AND_ARCHIVE`.
3. **No new column on `AgentReport`** — Skill side stores disposition only via the auto-triggered lifecycle action's audit log + `handleComment` text, not on the report row. Mirror that.
4. New controller `AdminAgentReportController` at `/api/v1/admin/agent-reports` and `/api/web/admin/agent-reports`:
   - `GET ?status=&page=&size=` — admin lists reports by status. Requires SKILL_ADMIN / SUPER_ADMIN platform role (mirror skill side).
   - `POST /{reportId}/resolve` — body `{ disposition, handleComment }`.
   - `POST /{reportId}/dismiss` — body `{ handleComment }`.
   - Response shape mirrors Skill: page of `AgentReportSummaryResponse { id, agentId, agentNamespace, agentSlug, agentDisplayName, reporterId, reason, details, status, handledBy, handleComment, createdAt, handledAt }`. Backend joins `agent` once to resolve namespace + slug + displayName for the dashboard rows (skill side does this via a query repo; mirror that).
5. Update `RouteSecurityPolicyRegistry` to require platform role on the new admin routes.

**Frontend (new):**
1. `web/src/features/report/use-agent-reports.ts` — three hooks mirroring `use-skill-reports.ts`:
   - `useAgentReports(status: 'PENDING' | 'RESOLVED' | 'DISMISSED')`
   - `useResolveAgentReport()` — invalidates `['agentReports']` on success
   - `useDismissAgentReport()` — same
2. `web/src/api/client.ts` — add `agentReportApi` with `listByStatus(status, page, size)`, `resolve(reportId, body)`, `dismiss(reportId, body)`.
3. `web/src/pages/dashboard/reports.tsx` — wrap the existing inner `Tabs` (status) in an outer `Tabs` (Skill / Agent). Each branch holds its own status tabs and uses its own hooks. Heavy-handed but clean: extract the status-tabs body into a `<SkillReportsPanel />` and `<AgentReportsPanel />` to avoid duplication. The `pendingAction` state currently keyed by `id` becomes typed `{ kind: 'skill' | 'agent', id, action, ... }` to avoid id collisions. The "open record" navigate target differs: Skill → `/space/{ns}/{slug}`, Agent → `/space/{ns}/{slug}` too — both routes already exist, but the Agent space URL uses different routing logic.

**Wait — how does Agent detail route work?** Check before assuming.

→ Verified: `web/src/pages/agent-detail.tsx` is reached via the agent-specific route. Need to map agent reports to that route, not skill `/space/{ns}/{slug}`. Plan task will pin the exact route.

**i18n:**
- Reuse `reports.*` for shared copy (PENDING / RESOLVED / DISMISSED tabs, resolve / dismiss button labels, confirmation copy where it doesn't reference "skill" specifically).
- New keys: `reports.tabSkillReports` / `reports.tabAgentReports` (outer tab labels), `reports.openAgent` (navigate link label, mirroring `reports.openSkill`). Existing `reports.openSkill` stays.
- Confirmation copy that hardcodes "skill" (e.g., "Resolve this skill report?") gets parameterized: `reports.confirmResolve` becomes templated with `{kind: 'skill' | 'agent'}` looked up via i18n nesting (`reports.kind.skill` / `reports.kind.agent`).

**Tests:**
- Backend: `AgentReportServiceTest` adds `resolveReport_setsStatusAndAuditFields`, `dismissReport_setsStatusAndAuditFields`, `resolveReport_failsIfNotPending`, `dismissReport_failsIfNotPending`, `listByStatus_pages`. New `AdminAgentReportControllerTest` mirrors `AdminSkillReportControllerTest` shape: list happy / 401 / 403 + resolve happy / 404 / 403 + dismiss happy / 404 / 403.
- Frontend: `reports.test.tsx` adds Skill/Agent tab switching cases (default Skill; switch to Agent; PENDING list under Agent shows mocked data; resolve / dismiss in Agent panel hits agent-specific mutation).

### Item 4 — My Stars page: Agents tab

**Current state:**
- `MyStarsPage` (`web/src/pages/dashboard/stars.tsx`) is skill-only, uses `useMyStarsPage` (`/api/web/me/stars`).
- Backend `MeController.listMyStars` and `MySkillAppService.listMyStars` are the wired-up path. Both confirmed working as of this session.
- **Backend has no equivalent for agents.** Domain `AgentStarRepository`'s `findByUserId(...Pageable)` does **not exist** — only `findByAgentIdAndUserId` and per-agent star ops do. Need to add it.

**Change:**

**Backend (new):**
1. Domain `AgentStarRepository` — add `Page<AgentStar> findByUserId(String userId, Pageable pageable)`.
2. JPA impl `JpaAgentStarRepository` — add the corresponding Spring Data method (Spring Data derives the query, no implementation needed).
3. Application service: new `MyAgentAppService` (or extend `MySkillAppService` — discuss in plan, my preference: separate file `MyAgentAppService` to keep modules focused; `MySkillAppService` is already 200+ lines).
   - `listMyAgentStars(userId, page, size)` → mirror `MySkillAppService.listMyStars` exactly: page through `agentStarRepository.findByUserId`, batch-fetch agents via `agentRepository.findByIdIn` (add this method if missing — Skill side has the same batch lookup), preserve ordering by star created-at desc, return `PageResponse<AgentSummaryResponse>`.
4. New DTO `AgentSummaryResponse` — mirror `SkillSummaryResponse` shape but with the agent fields. Note: this is a **new DTO**; the existing `AgentResponse` is for detail / lifecycle paths. Bring rating + star + download counters + headline version (the agent's latest published version) — call this out in plan, since it requires checking which fields belong on a "summary" vs "detail" response (mirror Skill's split).
5. New controller endpoint on `MeController`: `GET /api/web/me/agent-stars` (matching Skill's `GET /me/stars` pattern). RouteSecurityPolicyRegistry already gates `/api/web/me/**`.

**Frontend (new):**
1. `web/src/api/client.ts` — `meApi.getAgentStarsPage({ page, size })`.
2. `web/src/shared/hooks/use-user-queries.ts` — `useMyAgentStarsPage()` mirroring `useMyStarsPage`. Keep query key `['agents', 'stars', 'page', params]` (parallel to `['skills', 'stars', 'page', params]`).
3. `web/src/pages/dashboard/stars.tsx`:
   - Wrap content in outer `Tabs` (Skills / Agents). Each panel holds its own `useState(page)` and pagination.
   - Skills panel: existing implementation moved verbatim into a `<SkillsStarsPanel />` subcomponent.
   - Agents panel: new `<AgentsStarsPanel />` mirroring it, calling `useMyAgentStarsPage` and rendering `AgentCard`.
4. i18n: new keys `stars.tabSkills`, `stars.tabAgents`. Existing `stars.empty` parameterized — or, simpler, two keys `stars.emptySkills` / `stars.emptyAgents`. Plan picks: separate keys (matches reports approach above for clarity).

**Tests:**
- Backend: `MyAgentAppServiceTest` mirrors `MySkillAppServiceTest`: paging happy / empty / ordering by star-time-desc / handles deleted agents gracefully (filter out nulls like Skill does on line 87). New `MeControllerTest.listMyAgentStars_paginates` controller test.
- Frontend: `stars.test.tsx` adds tab-switch cases (default Skills; switch to Agents; empty state per tab; rendering agent cards).

## 4. Cross-cutting decisions

### 4.1 What "mirror Skill" means in practice

Per `memo/lessons.md` (2026-04-26 backlog estimate audit), "mirror X" descriptions are treacherous. Concrete guard rails for plan tasks:

- For each "mirror" claim, the plan task **lists the exact Skill-side file paths** the implementer should compare against and the line range (e.g., `MySkillAppService.java:69-91` for item 4's listMyStars).
- Plan tasks for items 3 and 4 must include a "before writing new code, verify these files exist" preflight (per `memo/lessons.md` 2026-04-28 grep-before-create rule). Specifically: `find server -name "AdminAgentReportController.java"` (must be absent), `find server -name "MyAgentAppService.java"` (must be absent).
- Where the mirror is non-obvious (item 3's disposition handling, item 4's AgentSummaryResponse shape), the plan explicitly sets the comparison file and the divergence to expect.

### 4.2 Commit granularity

4 commits — one per item, in the order:

1. Item 1 (smallest, fewest files; backend + frontend in single commit since both must move together to keep tests green)
2. Item 2 (frontend-leaning; backend untouched)
3. Item 3 (largest; backend + frontend in one commit)
4. Item 4 (backend + frontend in one commit)

Each commit message follows the project pattern (`feat(api): ...` / `feat(web): ...` / `feat(api,web): ...`). Plan tasks 1 & 2 ship as a pair if either alone breaks tests, but the audit shows they're independent.

### 4.3 Test baseline policy

Per `memo/lessons.md` (2026-04-27 `mvn test -pl X` pitfall), the plan runs `./mvnw test` from `server/` root after each task — not `-pl`. Frontend regressions are checked per-task with `pnpm vitest run` scoped to the touched files plus a final full-suite run before push.

### 4.4 What about `memo/memo.md` items #5 (P2-4) and #6 (P3-2)

Both confirmed already shipped (commits `67a64cb8` and `5d62a75b` respectively). Spec excludes them. If during implementation a regression in those areas surfaces, that's a separate task (do not silently fix in this bundle).

### 4.5 i18n key strategy

The bundle adds ~5 new i18n keys total (Q3 reuse strategy). All keys go into both `web/src/i18n/locales/{en,zh}.json` simultaneously per existing conventions. No locale-specific keys.

## 5. Risks & mitigations

| Risk | Mitigation |
|---|---|
| `mvn -q compile` masks broken refactors (lessons 2026-04-28) | Plan tasks for backend changes mandate `./mvnw clean compile` before declaring success — explicitly called out in each task's verify step |
| Sub-agent audit hallucinates file states (lessons 2026-04-28) | Plan tasks include explicit file-existence preflights and reference exact line numbers, not "the area near X" |
| Concurrent claude session steals the index (lessons 2026-04-27) | Each task's stage + commit happens in a single Bash call (`git add ... && git commit -m '...'`) |
| Item 3's outer/inner Tabs nesting breaks existing skill-report tests | Plan task 3 starts by extracting the current monolithic `reports.tsx` body into a `<SkillReportsPanel />` and re-runs the existing tests with no behavioral changes; only after tests still pass is the outer Tabs wrapper added |
| Item 4's new `AgentSummaryResponse` shape diverges from `AgentResponse` (which already has rating/star/download counters) and confuses readers | Plan documents the "summary vs detail" split explicitly with a header comment in `AgentSummaryResponse.java` mirroring the equivalent comment on `SkillSummaryResponse` if one exists, otherwise added to both |
| Backend `findByIdIn` for agents may not exist; "mirror Skill" assumes it does | Plan task 4 has an explicit grep step before implementation: `grep -n "findByIdIn" server/skillhub-domain/.../AgentRepository.java` — if missing, task scope expands to add it (~5 lines + 1 test) |

## 6. Out-of-scope / future work

- Promotion conflict types for agents (Skill side has `promotion.duplicate_pending` / `promotion.already_promoted` UI handling — Agent path falls back to generic toast). Not in scope, surfaces as A9 follow-up.
- Report disposition wiring to actual agent lifecycle (auto-archive on RESOLVED with disposition=ARCHIVE_AGENT). Skill side doesn't do this either; both can change together later.
- Agent stars sorting / filtering on the My Stars page (only chronological by star-time desc in v1).
- Star CSV export, report export, etc. — none of these exist for Skill, so YAGNI.

## 7. Validation checklist (post-implementation)

- [ ] `./mvnw test` from `server/` — all green, no new failures
- [ ] `pnpm vitest run` from `web/` — all green
- [ ] `pnpm tsc --noEmit` — clean
- [ ] Manual smoke: log in as SKILL_ADMIN who is **not** a GLOBAL member, open an agent detail, see the Promote button, submit promotion, see toast + the inbox row
- [ ] Manual smoke: open `/dashboard/reports`, switch between Skill/Agent tabs, verify pending counts update independently
- [ ] Manual smoke: open `/dashboard/stars`, switch between Skills/Agents tabs, verify both lists populate from the user's own stars

## 8. References

- ADR 0003 §1.1 — fork owns Agent management
- `memo/memo.md` 2026-04-28 — original P0 follow-up enumeration
- `memo/memo.md` 2026-04-28 § A9 Agent Promotion — context for item 1
- `memo/lessons.md` 2026-04-28 grep-before-create — guard for items 3 & 4
- `memo/lessons.md` 2026-04-27 mvn-pl-vs-root — test baseline policy
- `memo/lessons.md` 2026-04-26 backlog-estimate-audit — guard against blind "mirror" estimates
- `docs/plans/2026-04-27-fork-backlog.md` — A6 / A9 entries this spec corrects

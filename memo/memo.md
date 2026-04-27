# Project Session Memos

Update at session end with what shipped, what was deferred, and what to pick up next.

---

## Fork scope (read first)

This fork (`HE1780/Weave-Hub`) takes Agent management, independent visual UI,
and safe runtime as its own development lines. Governance / social / search
enhancements track upstream `iflytek/skillhub` instead. Authoritative scope
decisions live in [docs/adr/0003-fork-scope-and-upstream-boundary.md](../docs/adr/0003-fork-scope-and-upstream-boundary.md).
Every new plan should declare which clause of ADR 0003 (1.1 / 1.2 / 1.3) it
belongs to.

---

## 2026-04-26 — Landing page dual-channel redesign

**Plan:** `docs/plans/2026-04-26-landing-page-dual-channel.md`
**Branch:** `main` (worked directly)
**Range:** `f01dcccc` (plan commit) → `f5b58d7c` (final wire-up)

### What shipped (7 commits)

```
f5b58d7c feat(web): wire LandingChannelsSection into landing page
a50d2138 feat(web): add Popular Agents section to landing page
0264fdcb feat(web): add Agents count to landing stats row
e4ccf2ca feat(web): add Browse Agents CTA to landing hero
d6c92bbe feat(web): add LandingChannelsSection (dual-channel intro)
94a8102e feat(i18n): add landing.channels, popularAgents, hero.browseAgents, stats.agents keys
f01dcccc docs(plan): landing page dual-channel redesign (Skills + Agents)
```

### Decisions locked (in lieu of brainstorming Q&A)

User said "都听你的" so I picked:
- Layout strategy **C**: Hero stays unchanged (search + 3 CTAs + 4 stats), new Channels block goes between Stats and Features.
- Agents data: read from existing mock-backed `useAgents` (top 3).
- Quick Start: **no change** — `LandingQuickStartSection` already has the two-tab structure (`agent` / `human`) we wanted; reusing avoids churn.
- Unified search Tabs: **deferred** (no agent search API yet on backend).
- Color overhaul (Skills blue / Agents purple): **deferred** — this would touch global design tokens; out of scope for layout redesign.

### Test counts

- Web: 598 → **606 passing** (added 4 channels + 4 popular-agents tests)
- Backend: 432 (unchanged, untouched)
- Typecheck: clean for all my files; only pre-existing `registry-skill.tsx` errors remain (untracked)
- Lint: clean

### Known gaps (intentional)

1. **Stats numbers are still static**: 1000+/50+/50K+/200+. These were already static before this PR; agents number `50+` is consistent with the existing marketing-copy style.
2. **`PopularAgents` lazy-loads `AgentCard` and `useAgents` synchronously** — no skeleton flicker since `useAgents` resolves immediately from mock. When real backend lands, the existing `isLoading` branch handles the network case.
3. **Channels cards use brand-gradient for both icons** (not differentiated colors per Q4 decision). When the color overhaul plan lands, these cards are the natural canary for Skills-blue / Agents-purple.

### How to resume

If the user wants to do the **color overhaul** later:
1. Define design tokens (CSS variables) for `--channel-skill` (blue) and `--channel-agent` (purple).
2. Apply to `LandingChannelsSection` icons + `AgentCard` accent + `SkillCard` accent.
3. Update Hero CTA buttons: keep brand-gradient on the primary `Explore Skills`, switch `Browse Agents` from secondary to a purple variant.

If the user wants the **third Quick Start tab "Agent Architect"**:
- Wait until `/dashboard/publish/agent` exists (no agent publish form today).
- Then add the third tab to `LandingQuickStartSection` with copy from the design doc.

---

## 2026-04-26 — Skill version comments backend (Tasks 1–13 of plan)

**Plan:** `docs/plans/2026-04-26-skill-version-comments.md`
**Branch:** `feat/skill-version-comments` (in-place on `main` checkout)
**Base:** `d11d75cf` (`docs(adr): 0002 skill-version comments design`)
**Head:** `f0a08e14` (`feat(notification): dispatch COMMENT_POSTED to version author`)

### What shipped (12 commits)

```
f0a08e14 feat(notification): dispatch COMMENT_POSTED to version author
ba800c91 feat(api): add CommentController edit/delete/pin endpoints
503ffa62 feat(api): add SkillVersionCommentController list/create endpoints
c85ce120 feat(api): add comment request/response DTOs
ceefc69f feat(domain): add SkillVersionCommentService with permission predicates
49585630 feat(domain): add CommentPermissions record and posted event
d0eea5c4 feat(infra): add SkillVersionCommentRepository (JPA-backed)
87f33655 feat(domain): add SkillVersionComment entity with body validation
c8d79c4f feat(db): add skill_version_comment table (V40)
a6de64e8 feat(notification): add COMMENT category
e763ad3d feat(web): add createWrapper test helper for hook tests
f2f4baa7 chore(web): add @testing-library/react and jsdom for hook tests
```

Backend coverage end-to-end: schema → entity → repo → service → DTOs → 2 controllers → security policy → notification listener. Full backend test suite: **432 tests, 0 failures**.

### Spec divergences from the plan (intentional, all approved during execution)

1. **Task 1 — `jsdom` is a real direct devDep, not a transitive accident.**
   The implementer first added it as collateral; spec reviewer caught the bogus reasoning; code reviewer pointed out we genuinely need it for the per-file `// @vitest-environment jsdom` docblock to remain stable across pnpm regenerations. Final state: both `@testing-library/react` and `jsdom` are explicit devDeps with the rationale recorded in commit `f2f4baa7`'s body.

2. **Task 7 — added `@Transactional(readOnly=true)` to `listForVersion`.**
   The plan didn't specify it; code review flagged the LazyInitializationException risk for projects without open-session-in-view. Also restructured `edit` to load `Skill` once (matching `post`'s pattern) instead of re-loading after save. Both applied as an amend to commit `ceefc69f`.

3. **Task 9 → Task 10/11 — author hydration uses real `UserAccountRepository.findByIdIn`.**
   The plan defaulted to a `(authorId, authorId, null)` fallback because recon hadn't found a user lookup. Recon confirmed `UserAccountRepository.findById(String)` and `findByIdIn(List<String>)` exist and `UserAccount` has `getDisplayName()` + `getAvatarUrl()`. Both controllers hydrate properly; fallback only triggers on a missing user (deleted account).

4. **Task 10 — security policy added to `RouteSecurityPolicyRegistry`.**
   The plan didn't anticipate this; the existing policy's `anyRequest().authenticated()` fallback meant anonymous read on PUBLIC versions (ADR §5.5) was rejected with 401. Added 2 `permitAll(GET)` entries adjacent to the existing star/rating policy entries.

5. **Task 11 — security policy: 6 explicit `authenticated` entries for PATCH/DELETE/POST-pin.**
   Defensive-but-redundant POST-create entries were skipped (the fallback covers them and no other POST entries exist in the file). Decision and rationale documented in the commit body.

6. **Task 13 — DEFERRED.** No `@SpringBootTest` precedent in this codebase drives real repos through the controller layer; every existing controller test mocks the service. Writing one from scratch for this task would either invent a new pattern (scope creep) or be flaky (writing to H2 in-memory through real JPA). The plan explicitly permits skipping if no precedent exists. Trade-off: 40+ unit/controller-with-mock tests cover the contract; a true full-stack test would still add value but it's a separate spec.

### Known follow-ups (not bugs, but worth tracking)

1. **Bean validation isn't wired.** The codebase has `jakarta.validation-api` on the classpath but no `hibernate-validator` runtime impl. `@Valid` and `@NotBlank` annotations on DTOs are no-ops. **Worked around** for this feature: validation is enforced at the entity layer (`SkillVersionComment` constructor throws `DomainBadRequestException` on bad bodies) and at the DB layer (V40 CHECK constraints). Test paths assert the domain-level rejection, not bean validation. **Worth a separate spec** to add `spring-boot-starter-validation` and migrate existing controllers to assume bean validation works.

2. **Author hydration falls back to `userId` on lookup miss.** That's the right behavior for deleted/missing accounts but means UIs may render a userId where they expect a display name. Consider whether the API should explicitly mark "author missing" with a different shape, or whether the current contract (always returns `AuthorRef`) is correct.

3. **No real integration test for the comment lifecycle.** Task 13 deferred. If desired later, the cleanest precedent to establish would be a `@DataJpaTest`-style test that writes through the JPA repos and hits the service directly (skipping HTTP).

### What's next — Tasks 14–25 (web UI)

The plan covers the web side as Tasks 14–24 plus the final verification (25). Independent of the backend except for the API contract, which is now stable.

Outline for next session:
- Task 14: web types + `useVersionComments` query-key factory
- Tasks 15–17: 5 hook files (infinite query + 4 mutations) with hook tests using the `createWrapper` helper from Task 1
- Task 18: i18n keys (en + zh) — block of `comments.*` keys per ADR §8.7
- Task 19: `CommentMarkdownRenderer` (GFM **off**, separate from existing skill `MarkdownRenderer` which has GFM on) + XSS regression tests
- Tasks 20–22: `<CommentItem>`, `<CommentComposer>`, `<CommentList>`
- Task 23: `<VersionCommentsSection>` orchestration component
- Task 24: wire into `web/src/pages/skill-detail.tsx`
- Task 25: full test runs + visual smoke test against running backend

When picking this up:
1. `git checkout feat/skill-version-comments` (currently in place on `main` checkout — branch is local-only, hasn't been pushed).
2. Read this memo's "Spec divergences" section so you don't fight battles already settled.
3. The API the web hooks consume is exactly the one shipped in commits `503ffa62` and `ba800c91`. Author shape includes `displayName` and (sometimes) `avatarUrl` — see `SkillVersionCommentResponse.AuthorRef` in commit `c85ce120`. Body is raw markdown.
4. The `createWrapper()` helper at `web/src/shared/test/create-wrapper.tsx` is ready to use.

### How to resume

```bash
cd /Users/lydoc/projectscoding/skillhub
git status   # branch should be feat/skill-version-comments
./server/mvnw -pl skillhub-app test -Dtest=SkillVersionCommentControllerTest,CommentControllerTest,SkillVersionCommentNotificationListenerTest
# ↑ should be 5 + 9 + 3 = 17 tests passing
cd web && pnpm vitest run src/shared/test/create-wrapper.test.tsx
# ↑ should be 3/3 passing
```

---

## 2026-04-26 — Skill version comments web UI (Tasks 14–25 of plan)

**Plan:** `docs/plans/2026-04-26-skill-version-comments.md`
**Branch:** `feat/skill-version-comments` (continued)
**Base:** `f0a08e14` (last backend commit from prior session)
**Head:** `003343e1`

### What shipped (12 commits)

```
003343e1 fix(web): wrap post.mutateAsync in async lambda for typecheck
4c5dff23 feat(web): mount VersionCommentsSection on skill-detail page
a85ffc77 feat(web): add VersionCommentsSection orchestration component
f2f06e3e feat(web): add CommentList with load-more button
eb97afb0 feat(web): add CommentComposer with write/preview tabs and length cap
457e3696 feat(web): add CommentItem with permission-gated action menu
b1dba1b9 feat(web): add CommentMarkdownRenderer with XSS regression tests
86106b97 feat(i18n): add comments.* keys (en + zh)
47b15a83 feat(web): add edit/delete/pin comment mutations
bb87b5ba feat(web): add usePostComment mutation
72cbd579 feat(web): add useVersionComments infinite query hook
6651b9dc feat(web): add comment types, query-key factory, and commentsApi group
```

End-to-end UI: types → query keys → API client → 5 hooks → i18n keys → markdown renderer → 3 components → orchestration → page wiring. Test counts: 588 → 589 then expanding to **38 new tests across 11 files** in `web/src/features/comments/`. Full web suite: **589 passed, 0 failed**. Backend: **432 passed, 0 failed** (unchanged).

### Spec divergences from the plan (intentional, all approved during execution)

1. **API client uses `fetchJson` + a `commentsApi` group, not `openapi-fetch`'s `client.GET/POST/PATCH`.**
   The plan templated calls like `client.GET('/api/web/skill-versions/{versionId}/comments', …)` but this codebase doesn't use the openapi-fetch low-level client; every feature group (notificationApi, namespaceApi, …) wraps `fetchJson(url, init)` with manual `URLSearchParams` building and `getCsrfHeaders()`. Added `commentsApi` next to `notificationApi` in `web/src/api/client.ts` following the established pattern. All hooks now mock `commentsApi.{list,post,edit,delete,togglePin}` rather than a fictional `apiFetch`.

2. **Test mocks of `react-i18next` return the raw key, not English literal.**
   The plan asserted on `screen.getByText(/Pinned/i)` etc., which would couple tests to locale state. Switched to the project-standard mock pattern (already used in `dashboard.test.tsx`, `search.test.tsx`): `useTranslation: () => ({ t: (key) => key })`, then assertions check `'comments.badge.pinned'` etc.

3. **Manual `cleanup()` in `afterEach` for component tests.**
   `@testing-library/react`'s auto-cleanup requires `globals: true` or a setup file in the vitest config — neither is configured here. Without cleanup, multiple `render()` calls in one file stack DOM nodes and break role-based queries. Added `import { cleanup } from '@testing-library/react'; afterEach(() => cleanup())` to every component test.

4. **Replaced `expect(...).toBeInTheDocument()` with `.toBeTruthy()` / DOM property checks.**
   `@testing-library/jest-dom` is not installed and is out of scope for this feature. Used native vitest matchers throughout.

5. **Used the project's existing `@/shared/ui/dropdown-menu` wrapper instead of importing `@radix-ui/react-dropdown-menu` directly** in `CommentItem`. Matches existing UI conventions and inherits the project's styling.

6. **Test for `<CommentItem>` action-menu items intentionally does NOT click-through into the dropdown content.**
   Radix renders DropdownMenuContent into a portal, which is awkward to query in jsdom without forwarding open state. Coverage was redistributed: nine assertions on the trigger (gating, badges, avatar, content) instead of asserting on portal-rendered items. The wired callbacks are exercised end-to-end via `<VersionCommentsSection>` integration paths.

7. **Inserted `comments.section.title` i18n key** (not in the plan's i18n block) so the heading on the skill-detail page reads "Comments" / "评论" instead of an awkward derivation from the empty-state string. Noted in commit `86106b97`.

8. **Added `vi.mock('@/features/comments', ...)` to `skill-detail.test.tsx`.**
   The new section requires `useInfiniteQuery`, which the existing test doesn't provide a `QueryClientProvider` for. Stubbed the barrel export with a placeholder div, mirroring the existing `vi.mock('@/features/skill/markdown-renderer', ...)`.

### Known gaps (call out, do not fix in this branch)

1. **OpenAPI types regeneration is still deferred.** The hooks call paths the backend has implemented, but `web/src/api/generated/schema.ts` does not yet include the comment endpoints. This is fine because we use `fetchJson` directly instead of openapi-fetch's typed `client`. Pending: re-run `pnpm openapi-typescript` and add a typed wrapper if desired.

2. **Author avatar/displayName fall back to `userId`** (carried over from the backend session — not changed here).

3. **No real integration test for the comment lifecycle** (carried over from Task 13 deferral).

4. **Pre-existing typecheck and lint warnings in `web/src/pages/registry-skill.tsx`** (untracked, out of scope for this branch). My changes pass typecheck and lint cleanly when scoped to `src/features/comments/`, `src/api/client.ts`, and `src/pages/skill-detail.tsx`.

5. **`window.confirm()` for delete confirmation** in `CommentItemWithMutations` rather than a `<ConfirmDialog>`. Acceptable for v1 per the plan; can be upgraded later.

6. **No visual smoke test was performed.** The plan's Step 5 of Task 24 calls for a manual browser walkthrough against a running backend. Skipped in this session because there is no live backend running locally; verified via typecheck + 589/589 unit tests instead.

### How to resume

```bash
cd /Users/lydoc/projectscoding/skillhub
git status   # branch should be feat/skill-version-comments, 12 commits ahead of origin
cd web && pnpm vitest run src/features/comments/   # 38 tests, 11 files
cd web && pnpm typecheck   # only registry-skill.tsx errors remain (pre-existing untracked)
```

---

## 2026-04-26 — Agents Frontend MVP 实施完成 (merged later)

执行了 `docs/plans/2026-04-26-agents-frontend-mvp.md`，全部 12 个任务通过 subagent-driven-development 流程交付。

**12 个 commit**：
- i18n keys
- Agent types (`web/src/api/agent-types.ts`)
- mock 数据 (`web/src/features/agent/mock-agents.ts`)
- useAgents hook (TDD)
- useAgentDetail hook (TDD)
- **infra**: 加 `@testing-library/jest-dom` + `jsdom` + setupFiles
- AgentCard
- WorkflowSteps
- agents.tsx 列表页
- agent-detail.tsx 详情页
- 路由注册 `/agents` + `/agents/$name`
- nav 加 "Agents/智能体" 链接

**测试**: 181 files / 557 tests 全绿（合并 comments 之后变 188 / 589）
**浏览器烟雾测试**: 列表 + 三个详情 + 未知 name + en/zh 切换 全部通过

**未做的事**: agent 上传表单（UI 入口已加，但实际 publish 流程不在 MVP 范围）。

**下一步可做的跟进**（来自最终 code review，全部 NICE-TO-HAVE）：
1. AgentCard 的 `role="link"` + `tabIndex={0}` 没有键盘 (Enter/Space) 测试覆盖；改用 TanStack `<Link>` 是更彻底的修复。
2. agents.tsx 用 `navigate()` 而非 `<Link>`，cmd-click/middle-click/右键打开新标签都不工作；和现有 skill-card 同病。
3. workflow-steps.tsx 里 `WorkflowSteps` 和 `StepBody` 都各自调 `useTranslation()`，可以提到一起。
4. 三个 hook 测试都各自重建 QueryClient，可以抽出 `createWrapper()` helper（comments session 已经做了，agents 可以迁移过去）。
5. agent-detail.tsx 里 "not found" 和 "network error" 共用同一条 `agents.loadError`，等真后端接入后区分。

**关键架构决策**：mock-vs-API 切换面在 `useAgents.queryFn` 和 `useAgentDetail.queryFn` 两个函数体内，符合计划承诺的"换后端只动一处"。

---

## 2026-04-27 — Agent publish & review pipeline · Phase E (Tasks 25–30)

**Plan:** `docs/plans/2026-04-27-agent-publish-review-pipeline.md` (Phase E)
**Branch:** `main`
**Range:** `7036273b` (My Agents tab, last Phase D commit) → `c77dc275` (Hero dropdown)

### What shipped (6 commits)

```
c77dc275 feat(web): split Hero Publish CTA into Skill/Agent dropdown
aaa9cc16 feat(web): switch agent-detail route to /agents/$namespace/$slug
ee032449 feat(web): AgentReviewDetailPage with approve/reject controls
1a098375 feat(web): AgentReviewsPage (reviewer inbox)
2aca5015 feat(web): add agent review query and mutation hooks
(plus: Task 30 memo commit)
```

End-to-end Agent flow now closed: publish → review inbox → review detail (with soul.md + workflow.yaml visible) → approve/reject → public list. Hero offers dropdown CTA for both Publish Skill and Publish Agent.

### Test counts

- Web: 606 → **627 passing** (+21 new). 202 files, 0 failures.
- Backend: **broken baseline (see follow-up #1)** — did not run as part of this session.
- Typecheck: only pre-existing `registry-skill.tsx` errors.
- Lint: only pre-existing `registry-skill.tsx` warnings.

### Spec divergences from the plan (all intentional)

1. **Task 27 added a backend endpoint** — `GET /api/web/agents/reviews/{taskId}/detail` (also `/api/v1`). Phase C plan Task 16 created the `AgentReviewVersionDetailResponse` DTO but Task 17 never wired a controller to return it. The reviewer detail screen needs agent metadata + soul.md + workflow.yaml in one shot, so this commit closes that wiring. Service.getById was already in place; controller picks up `AgentRepository`/`AgentVersionRepository`/`NamespaceRepository` to assemble the joined response. Security policy gained two `*/detail` entries because the existing `/reviews/*` rule is single-segment and doesn't cross the next slash.

2. **Task 26 took a simpler shape than skills' reviews page** — agent review API requires `namespaceId`, so the inbox UI is built around a namespace selector (filtered to OWNER/ADMIN namespaces) instead of the global SKILL_ADMIN tab + namespace tab combo. Less code, fewer special cases. Dashboard sidebar grew an `Agent Reviews` card gated on the same role check.

3. **Task 28 made `/agents/$name` a redirect** — old route became `beforeLoad` redirect to `/agents/global/$name` so any cached/external links still resolve; canonical URL is now `/agents/$namespace/$slug`. `useAgentDetail` takes both segments. `AgentSummary.namespace` was added so cards can build the canonical URL without re-fetching. Distinguishes "no published version" (new i18n key) from generic load errors.

4. **mock-agents.ts deleted** — no remaining importers; backend is the source of truth. Tests still mock the hooks (`useAgents`, `useAgentDetail`) directly.

5. **Review-detail page renders soul/workflow as `<pre>` blocks, not markdown.** Workflow is YAML (not markdown), and the reviewer needs to see soul exactly as the runtime will read it. Skipping the markdown renderer also avoids the XSS concern entirely on this surface.

### Known gaps and follow-ups (in priority order)

1. **✅ RESOLVED 2026-04-27** — Backend baseline is **NOT** actually broken. Original report misdiagnosed root cause as JPA scanning/wiring issue. Real cause: `mvn -pl skillhub-app test` doesn't rebuild upstream modules, and m2 cache held a stale `skillhub-infra-0.1.0.jar` from before the agent JPA repositories were added (jar lacked `Agent*JpaRepository.class`). The fresh domain jar referenced `AgentRepository` but no impl was on the classpath → `No qualifying bean`. Fix: run `./mvnw test` from `server/` root (full reactor) instead of `-pl skillhub-app`. **Backend now: 455/455 passing**, no source code changes required. See `memo/lessons.md` 2026-04-27 multi-module maven entry for full diagnostic trail.

2. **No real integration test for the agent review approve/reject lifecycle.** Carried over from the comments-session pattern — the codebase has no `@SpringBootTest` precedent that drives real repos through the controller layer for these workflows. Worth a separate spec.

3. **Backend AgentControllerTest, AgentReviewControllerTest, AgentPublishControllerTest** — 7 + 4 + ~4 tests assuming the agent JPA wiring works; will pass once #1 is fixed.

4. **`/agents/$name` redirect uses hardcoded `'global'` namespace.** Fine for now (single-tenant deployments use `global`); when multi-namespace publishing surfaces, the legacy redirect needs a backend lookup or graceful 404 instead.

5. **AgentReviewsPage doesn't show agent name/version in the row** — only task ID and version ID. Backend list response is the bare task row; to enrich, either (a) the list endpoint joins agent metadata (mirrors `ReviewTaskResponse` carrying `skillSlug`), or (b) the page does N+1 fetches client-side. Both are scope creep for v1.

6. **No browser smoke test was performed.** The plan's Task 30 step calls for a manual walkthrough. Skipped because no live backend was running in this session and the backend baseline is broken (see #1). Verified via 627/627 unit tests + typecheck.

7. **`window.confirm` not used** — review actions go through `<ConfirmDialog>` matching the skill-review pattern.

### How to resume

```bash
cd /Users/lydoc/projectscoding/skillhub
git status   # branch: main, 6 commits ahead of origin/main since 7036273b
cd web && pnpm vitest run                       # 627/627 passing
cd web && pnpm typecheck                        # only registry-skill.tsx errors remain (pre-existing)
cd server && ./mvnw test                        # 455/455 passing — RUN FROM ROOT, not -pl skillhub-app
```

If continuing the agent rollout, the natural next stops are:
- Surface review-task list rows with agent slug + version (follow-up #5).
- Wire `Agents Frontend MVP` follow-ups #1–#4 (keyboard-nav `<Link>`s, shared `createWrapper`, etc).
- Optionally re-attempt Task 27's `AgentReviewControllerTest.getDetail_returns_full_projection` test that was held back due to the now-resolved baseline issue.

---

## 2026-04-27 — Phase E follow-up #1 resolved (backend baseline restored)

Diagnosed and fixed the "broken backend baseline" reported in the previous Phase E session. **No source code changes were needed.** The previous session's hypothesis (JPA scanning/wiring misconfiguration) was wrong; actual root cause was a stale m2 cache + the wrong maven invocation pattern.

**Diagnostic**: error stacktrace showed `agentPublishService defined in URL [jar:file:/Users/lydoc/.m2/...skillhub-domain-0.1.0.jar!/...]` — services were loading from m2 cache. Inspecting `~/.m2/.../skillhub-infra-0.1.0.jar` confirmed it was missing the `Agent*JpaRepository.class` files (jar predated the agent module additions). Fresh `./mvnw install` regenerated the jar; `./mvnw test` from server root passes all 455 tests.

**Verified state**:
- Backend: 455/455 (was: 241/456 broken)
- Web: 627/627 (unchanged)
- Typecheck: clean (only pre-existing registry-skill.tsx errors)

**Lesson recorded** at `memo/lessons.md` — multi-module maven, `-pl X test` doesn't rebuild upstream modules. Default to `./mvnw test` from root.

---

## 2026-04-27 — Phase E cleanup: follow-ups #5, #1-#4 (web), Task 27 test

Closed every remaining Phase E follow-up in one session, in C → A → B order.

### What shipped (6 commits)

```
c0a76f81 test(web): migrate agent hook tests to shared createWrapper helper
5eccf35e refactor(web): pass t down in workflow-steps instead of two useTranslation calls
27df14e3 refactor(web): use TanStack Link instead of navigate() in agent UI
c3ec0e75 feat(web): show agent name + version in review inbox row
f3363cb5 feat(api): hydrate agent review list rows with agent slug/version
9c114e21 test(api): cover AgentReviewController getDetail endpoint
```

ADR 0003 §1.1 (Agent management) for all six.

### What got resolved

- **Phase E Task 27 test (held back from prior session)** — added 4 cases to `AgentReviewControllerTest` (happy path, version-missing 404, agent-missing 404, anonymous 401) for `/api/web/agents/reviews/{taskId}/detail`.
- **Phase E follow-up #5** — review inbox rows now carry agent slug/displayName/namespace/version. Backend extends `AgentReviewTaskResponse` with four nullable fields populated by the list endpoint via the new `enriched()` factory. Single-task endpoints (getOne/approve/reject) keep the unhydrated `from()` factory. Added `AgentVersionRepository.findByIdIn` for the batch lookup. Frontend renders the new Agent column and replaces `#agentVersionId` with the version string, with `—` and `#id` fallbacks for orphaned/legacy data.
- **Agents Frontend MVP follow-ups #1+#2** — `AgentCard` now wraps in TanStack `<Link>`, dropping the role/tabIndex/onKeyDown plumbing; `agents.tsx` and `popular-agents.tsx` use `<Link>` + `buttonVariants()` for their CTAs. cmd-click / middle-click / right-click open in new tab now work.
- **Agents Frontend MVP follow-up #3** — `WorkflowSteps` lifts `useTranslation` to the parent and threads `t` down to `StepBody`.
- **Agents Frontend MVP follow-up #4** — 7 agent hook tests now use the shared `createWrapper()` helper. Added `createWrapperWithClient()` variant exposing the QueryClient for approve/reject tests that need to spy on `invalidateQueries`.

### Test counts

- Backend: 459 → **460/460** (added 1 list test for the orphaned-version fallback case; net is +5 vs baseline including Task 27 cases).
- Web: 627 → **631/631** (+4: 1 createWrapperWithClient test, 1 extra agent-card link assertion, 2 enriched/fallback row cases).
- Typecheck: clean (only pre-existing registry-skill.tsx errors).

### Spec divergences worth noting

1. **`AgentCard` lost the `onClick` prop.** It used to accept an optional click handler so callers could navigate. With the Link wrapper that's vestigial — every caller in the tree already wanted the same destination. Tests updated to assert on `href` instead of `vi.fn()` invocation. If a future caller needs click-side-effects, prefer `onClick` *additionally* on the `Card` (Link still owns navigation), not as a replacement.

2. **`createWrapperWithClient` is a new public helper, not a refactor of `createWrapper`.** Approve/reject tests need the QueryClient instance to install spies; rather than break the existing `wrapper: createWrapper()` callsite shape used by 5 comments tests + 5 agent tests, added a separate two-return helper. Rule of thumb: prefer `createWrapper()`; reach for `createWrapperWithClient` only when spying on cache ops.

3. **Backend `AgentReviewTaskResponse` now has both `from(task)` and `enriched(task, agent, version, slug)` factories.** Detail/approve/reject use `from()` (unhydrated, four `null` fields); list uses `enriched()`. The frontend treats the four enriched fields as `?: T | null` so missing data falls back gracefully — no breaking change for clients that don't read them.

### How to resume

Backend baseline + Phase E follow-up list are now empty. Natural next stops per ADR 0003:

- **Color overhaul plan** (ADR 0003 §1.2): Skills blue / Agents purple design tokens, Hero double-entry refresh, third Quick Start tab "Agent Architect".
- **Agent comments plan** (ADR 0003 §1.1): extend skill-version comments polymorphism to Agent, or fork. Requirements doc already at `docs/plans/2026-04-26-comments-feature-requirements.md`.
- **Agent search integration** (ADR 0003 §1.1): `/agents` keyword search wired into the unified search Tabs.

```bash
cd /Users/lydoc/projectscoding/skillhub
cd web && pnpm vitest run                       # 631/631 passing
cd web && pnpm tsc --noEmit                     # only registry-skill.tsx errors remain (pre-existing)
cd server && ./mvnw test                        # 460/460 passing — RUN FROM ROOT
```

---

## 2026-04-27 — P0-1a: WeaveHub design tokens migrated

**Plan:** [docs/plans/2026-04-27-weavehub-tokens.md](../docs/plans/2026-04-27-weavehub-tokens.md)
**Spec:** [docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md](../docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md) §2 + §4.1
**ADR clause:** [ADR 0003](../docs/adr/0003-fork-scope-and-upstream-boundary.md) §1.2
**Branch:** `main`
**Range:** `30c05d89` → `d4583420` (9 commits, all on top of plan commit `79483ce3`)
**Method:** subagent-driven (spec + code-quality two-stage review per task)

### What shipped (9 commits)

```
d4583420 feat(web): migrate AgentCard to glass-card visual
762b74fe feat(web): migrate SkillCard to glass-card visual
17872a11 feat(web): add opt-in glass prop to base Card
0362c05c feat(web): migrate Button default variant to btn-primary semantic class
5b069ed0 feat(web): expose brand-50..700 palette in tailwind theme
31cd3540 feat(web): add glass-card, btn-primary/secondary, nav-chip utility classes
2a796667 feat(web): drop Syne/IBM Plex Sans heading fallbacks (use Inter)
24b0c2de feat(web): switch brand color palette from indigo/violet to weavehub green
30c05d89 chore(web): add motion dependency for upcoming weavehub animations
```

End-to-end token layer migrated: brand palette indigo+violet → green-monochrome (`#34a853` / `#2c8e46` / `#23743a`),
heading font fallback `Syne / IBM Plex Sans` → `Inter`, 4 new utility classes (`.glass-card` / `.btn-primary` /
`.btn-secondary` / `.nav-chip` + modifiers), tailwind exposes `bg-brand-500` etc., Button default uses semantic
`btn-primary` class, base Card has opt-in `glass` prop with 4 new tests, SkillCard / AgentCard migrated to
`<Card glass>`. `motion` 12.38.0 installed for P0-1b.

### Tests

- Web: 631 → **635 passing** (+4 from new card.test.tsx)
- Backend: 460 (unchanged, untouched)
- Typecheck: only pre-existing `registry-skill.tsx` errors
- Lint: 3 warnings (2 in `registry-skill.tsx` pre-existing + 1 in `web/weavehub---知连/src/App.tsx` —
  this is the user-provided reference prototype, not project code; should be ignored via `.eslintrc.cjs`
  `ignorePatterns` in a separate small commit if/when we need lint to be clean again. **Not introduced by P0-1a.**)

### Visual delta

- All purple/indigo gradients → green via single token (`--brand-gradient`)
- 14 downstream `bg-brand-gradient` / `text-brand-gradient` references auto-recolored, no class renames
- SkillCard / AgentCard now glass-morphism (translucent white + backdrop-blur + rounded-3xl + hover lift)
- Default Button variant simpler: 1 semantic class instead of 4 compound utilities
- `--radius` bumped from `0.75rem` → `1rem` (rounded-lg etc. all scale up)

### Spec divergences (intentional)

1. **`web/index.html` font links not changed** — they were already correct (only Inter+JetBrains Mono loaded).
   Plan's Task 2 mentioned changing them; turned out unneeded.
2. **`web/index.html <title>` stays `SkillHub`** — title rename is in P0-1b, per spec §3.7.
3. **`web/src/app/layout.tsx` nav not yet migrated to `nav-chip` class** — current nav still uses
   `bg-brand-gradient pill` style which auto-recolors green. Class swap moved to P0-1b's nav-link reshuffle
   (cleaner to do in one shot).
4. **8 `brand-gradient` files mentioned in spec actually grep to 14 references in 7 files** — handled
   by single-token redefinition, no per-file edits needed.
5. **`motion` resolved to ^12.38.0**, not ^11.x as plan guessed. Motion 12 has no breaking changes for
   our future use.
6. **`card.test.tsx`** (.tsx not .ts as plan said) — JSX requires .tsx (per memo/lessons.md
   2026-04-26 entry).

### Known gaps for P0-1b

1. Site name still says "SkillHub" — P0-1b will change to "知连 WeaveHub"
2. Landing page nine-section structure unchanged — P0-1b rewrites to weavehub 4-section IA
3. Nav links / chip migration — P0-1b
4. `motion/react` installed but not used — P0-1b consumes it
5. The `Card glass` prop is only adopted by SkillCard + AgentCard so far. Other Card consumers
   (workflow-steps, landing-channels, landing-quick-start, skeleton-loader, landing.tsx) still default
   to plain solid `rounded-xl bg-card`. P0-1b will sweep landing-* components and decide per-component
   whether to opt in.

### How to resume (for P0-1b)

```bash
cd /Users/lydoc/projectscoding/skillhub
git status   # branch: main, P0-1a complete
cd web && pnpm vitest run   # 635/635 passing
cd web && pnpm tsc --noEmit # only registry-skill.tsx errors (pre-existing)
```

Before starting P0-1b, **start the dev server and visually confirm P0-1a output**:

```bash
cd web && pnpm dev
```

Walk: `/` (landing — 颜色变绿,卡片有 glass 感), `/search`, `/agents`, login button, /dashboard/publish.
If anything looks broken, the cause is almost certainly in commits `24b0c2de` (palette) or `31cd3540`
(utility classes). Roll back individual commits if needed; each task is its own atomic commit.

After visual confirmation, invoke `superpowers:writing-plans` against spec §4.2 to expand P0-1b into
an executable plan.

---

## 2026-04-27 — P3-2a + P2-2 + P2-4 backend triple-tap

**Backlog:** [docs/plans/2026-04-27-fork-backlog.md](../docs/plans/2026-04-27-fork-backlog.md)
**Branch:** `main`
**Range:** `5d62a75b` (P3-2a) → `67a64cb8` (P2-4)
**Method:** sequential (subagent-free, all backend-only, no frontend dependencies)

### What shipped

- **P3-2a** (`5d62a75b`) — Wired `BasicPrePublishValidator` into `AgentPublishService`. New
  `PrePublishValidator.validateEntries(entries, publisherId, namespaceId)` default-method overload lets
  the agent flow reuse the same scanner without an artificial `SkillMetadata` requirement. Failures and
  warnings throw `DomainBadRequestException` before any persistence.
- **P2-2** (`3d4d0e0d`, see snafu below) — Added Agent governance endpoints:
  `POST /api/{v1,web}/agents/{namespace}/{slug}/archive` + `/unarchive`. New `AgentLifecycleService`
  in domain (owner OR namespace ADMIN/OWNER required), new `AgentLifecycleController` in app, new
  `AgentLifecycleMutationResponse` DTO, four new entries in `RouteSecurityPolicyRegistry`, plus
  `Agent.unarchive()` to mirror the existing `archive()` method. Audit-log records under `ARCHIVE_AGENT`
  / `UNARCHIVE_AGENT` action / `AGENT` target type.
- **P2-4** (`67a64cb8`) — Added `spring-boot-starter-validation` to `skillhub-app/pom.xml`. The ~44
  pre-existing `@Valid`/`@NotBlank`/`@Email`/`@Size` annotations are now actually enforced. The
  pre-existing `GlobalExceptionHandler.handleValidation` was already wired to map
  `MethodArgumentNotValidException` to 400, so external contract is unchanged.

### Test counts

- Backend: 460 → **468/468** in skillhub-app (full reactor 1098 across 7 modules, 0 failures, 0 errors)
- New tests: AgentPublishServiceTest 6 → 8 (validator-warning + validator-failure cases),
  AgentLifecycleServiceTest 8 (owner/admin/ns-owner/stranger/null-roles/404/unarchive variants),
  AgentLifecycleControllerTest 7 (happy + at-prefix + 404×2 + 403 + 401 + unarchive),
  GlobalExceptionHandlerTest 2 → 3 (handleValidation case)
- Test fallout from validation activation: 4 cases in `LocalAuthControllerTest` previously asserted
  `verify(service).register/...(badEmail)`; updated to `verify(service, never()).<method>(any(), ...)`
  reflecting the new short-circuit at the validation layer. 400 contract assertions unchanged.

### Spec divergences worth noting

1. **P3-2a — entries-only overload, not full context refactor.** The plan said "inject validator into
   `AgentPublishService`" but `PrePublishValidator.SkillPackageContext` requires a `SkillMetadata`,
   which the agent flow doesn't have (it has `AgentMetadata`). Considered three options: (a) pass null
   metadata, (b) refactor context to be polymorphic, (c) add a new entry-only method. Picked (c) —
   minimal diff, keeps existing skill-side callers untouched, both existing impls (`Basic`, `NoOp`)
   work because neither reads the metadata field. The default method delegates to the existing
   `validate(SkillPackageContext)` with null metadata.

2. **P2-2 — `Agent.unarchive()` was missing; added it.** The backlog said "domain ready, app missing"
   but only `archive()` existed. `AgentStatus.ARCHIVED` and `ACTIVE` enum values exist; just needed
   the inverse method. No version-level archive (matches v1 scope per backlog).

3. **P2-4 — bigger blast radius than expected: validation activation broke 4 LocalAuthControllerTest
   cases.** The tests were written assuming bean validation didn't fire, so they stubbed the service
   to throw on bad email and asserted the service was called. With validation enabled, the service
   is *never* called for bad emails — the validation layer 400s first. Updated assertions to match
   reality. This is exactly the kind of latent issue the backlog called out as "潜伏炸弹".

### Snafu — P2-2 commit message is wrong (worth knowing)

Commit `3d4d0e0d` is captioned `feat(web): register /my-weave route (auth-gated)` but its actual
content is the seven P2-2 backend files (AgentLifecycleController/Service/Test, dto, security policy,
Agent.java, AgentLifecycleServiceTest) PLUS one tiny `web/src/app/router.tsx` line from the parallel
P0-1b session. A concurrent claude instance running P0-1b's `/my-weave` work executed `git add` over
the whole tree at the moment my P2-2 files were staged, then committed everything under their own
message before I could commit mine. The code is correct in main, just the commit-log archeology is
misleading. Did not amend (history is shared and this is non-destructive enough to leave). If anyone
later searches for "AgentLifecycle" in `git log` and finds nothing, look at `3d4d0e0d`.

Lesson recorded in `memo/lessons.md` 2026-04-27 parallel-claude entry.

### Known gaps (not bugs)

1. **No frontend wiring for archive/unarchive yet.** Backend endpoints are live and security-gated.
   Plan says "前端:My Agents 页 + agent-detail 加 archive/unarchive 按钮(权限可见)" — that's a
   separate frontend task. Listed as next backlog candidate.
2. **No real integration test for the lifecycle flow.** Carried over from skill comments / agent
   review pattern — codebase has no `@SpringBootTest` precedent that drives real repos through the
   controller for these mutations. Mocked controller tests + domain-service tests cover the
   contract.
3. **P3-2b (extending validator chain) is still pending.** Independent brainstorm per backlog.

### How to resume

```bash
cd /Users/lydoc/projectscoding/skillhub
git status   # branch: main, my 3 commits live (P3-2a / P2-2 conflated / P2-4)
cd server && ./mvnw test          # 1098 / 0F / 0E across 7 modules
```

Backlog updates after this session — these can be marked ✅:

- P3-2a: ✅ done (commit `5d62a75b`)
- P2-2: ✅ done (commit `3d4d0e0d` — see snafu note above)
- P2-4: ✅ done (commit `67a64cb8`)

Natural next stops per ADR 0003:

- P0-2 (Agent list search backend + frontend) — backlog says ~1 day full-stack.
- P2-1 (Agent star + rating, mirrors skill_star/skill_rating) — backlog says ~1 day full-stack.
- Wire archive/unarchive UI buttons into My Agents + agent-detail pages.
- P3-2b (extend validator rule chain — needs brainstorm per backlog).

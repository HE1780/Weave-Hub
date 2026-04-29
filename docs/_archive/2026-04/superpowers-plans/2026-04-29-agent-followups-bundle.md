# Agent Followups Bundle Implementation Plan

> ✅ **SHIPPED — 已完成 2026-04-29。** 4 任务全部落地(commits `2c17ae30` → `38b42297`),backend 578/578 + web 706/706 passing。详见 [docs/plans/2026-04-29-spec-status-ledger.md](../../plans/2026-04-29-spec-status-ledger.md)。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the four small follow-ups identified by the 2026-04-28 P0 audit (Agent promotion default-GLOBAL, Agent card rating chip, agent reports moderation tab, My Stars Agents tab) as a single plan with four independent commits.

**Architecture:** Each task lands as one self-contained backend+frontend commit. Tasks 1, 2, 3, 4 have no inter-dependencies — they could ship in any order — but the chosen order (smallest first) keeps blast radius predictable.

**Tech Stack:** Spring Boot 3 + Maven multi-module + JPA · React 18 + Vite + TanStack Query + Tailwind · pnpm 10 + Vitest · Java 21 + Hibernate 6.4

**Spec:** [docs/superpowers/specs/2026-04-29-agent-followups-bundle-design.md](../specs/2026-04-29-agent-followups-bundle-design.md)

---

## Pre-flight (run once before Task 1)

- [ ] **Step 0.1: Verify clean baseline**

```bash
cd /Users/lydoc/projectscoding/skillhub
git status -sb
```

Expected: branch `main`, no staged changes, only `deploy/.env.example` and `deploy/compose.solo.yml` as untracked (pre-existing).

- [ ] **Step 0.2: Verify backend baseline green**

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw test 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`, no failures or errors. Per `memo/lessons.md` 2026-04-27 stale-m2: if any "No qualifying bean of type X" errors surface, run `./mvnw -DskipTests install` first then retry.

- [ ] **Step 0.3: Verify frontend baseline green**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -3
```

Expected: all tests pass.

- [ ] **Step 0.4: Verify TS baseline clean**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm tsc --noEmit 2>&1 | tail -5
```

Expected: no errors.

If any of 0.2 / 0.3 / 0.4 fail, STOP. Do not proceed — baseline must be green to attribute regressions correctly.

---

## Task 1: Agent promotion defaults to GLOBAL when targetNamespaceId omitted

**Why:** Frontend currently must resolve GLOBAL namespace id via `useMyNamespaces`, which returns nothing for users who aren't GLOBAL members. Mirroring Skill's design (where GLOBAL is resolved server-side or via a dedicated detail call) lets any admin promote without being a GLOBAL member.

**Files:**
- Modify: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/PromotionController.java:42-78`
- Modify: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/portal/PromotionPortalControllerTest.java` (add 1 test)
- Modify: `web/src/features/agent/promotion/promote-agent-button.tsx`
- Modify: `web/src/features/agent/promotion/promote-agent-button.test.tsx`
- Modify: `web/src/pages/agent-detail.tsx:115-130, 716-725`
- Modify: `web/src/features/promotion/use-promotion-list.ts` (where `useSubmitAgentPromotion` lives — verify path in step 1.1)

- [ ] **Step 1.1: Locate `useSubmitAgentPromotion` and `submitAgentPromotion` API call**

```bash
cd /Users/lydoc/projectscoding/skillhub
grep -rn "submitAgentPromotion\|useSubmitAgentPromotion" web/src --include="*.ts" --include="*.tsx" | grep -v test | head -10
```

Record the exact file paths. Update the "Files" list above mentally if different.

- [ ] **Step 1.2: Read PromotionController.submitPromotion (lines 42-78) for current branch logic**

```bash
sed -n '40,80p' server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/PromotionController.java
```

Confirm: AGENT branch reads `request.targetNamespaceId()` and passes it to `governanceWorkflowAppService.submitAgentPromotion(...)`. We change only the AGENT branch, not the SKILL branch.

- [ ] **Step 1.3: Add failing controller test**

Append to `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/portal/PromotionPortalControllerTest.java` inside the existing `@Nested` agent promotion class (or near other agent promotion tests):

```java
@Test
@WithMockUser(roles = "SKILL_ADMIN")
void submitAgentPromotion_omitsTargetNamespaceId_resolvesToGlobal() throws Exception {
    Namespace globalNs = mock(Namespace.class);
    when(globalNs.getId()).thenReturn(99L);
    when(namespaceRepository.findBySlug("global")).thenReturn(Optional.of(globalNs));

    String body = """
            {
              "sourceType": "AGENT",
              "sourceAgentId": 5,
              "sourceAgentVersionId": 7
            }
            """;

    mockMvc.perform(post("/api/web/promotions")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isOk());

    verify(governanceWorkflowAppService).submitAgentPromotion(eq(5L), eq(7L), eq(99L), anyString());
}
```

You may need imports: `org.springframework.security.test.context.support.WithMockUser`, `static org.mockito.Mockito.*`, `static org.mockito.ArgumentMatchers.*`, `com.iflytek.skillhub.domain.namespace.Namespace`. Match existing test class's import style.

- [ ] **Step 1.4: Run the test to verify it fails**

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw -pl skillhub-app test -Dtest=PromotionPortalControllerTest#submitAgentPromotion_omitsTargetNamespaceId_resolvesToGlobal 2>&1 | tail -10
```

Expected: test FAILS — most likely with NPE on `targetNamespaceId` or 400 response.

If the test errors with "No qualifying bean..." instead of asserting failure, run `./mvnw -DskipTests install` from `server/` then retry (per lessons.md 2026-04-27).

- [ ] **Step 1.5: Implement: resolve GLOBAL when targetNamespaceId is null in AGENT branch**

Edit `PromotionController.java`. The current AGENT branch (around line 48-65) reads:

```java
if (request.sourceType() == SourceType.AGENT) {
    if (request.sourceAgentId() == null || request.sourceAgentVersionId() == null) {
        throw new DomainBadRequestException(
                "AGENT promotion requires sourceAgentId and sourceAgentVersionId");
    }
    // ... call submitAgentPromotion with request.targetNamespaceId()
}
```

Change the call site to resolve null targetNamespaceId to GLOBAL's id. The minimal diff is:

```java
Long targetNamespaceId = request.targetNamespaceId();
if (targetNamespaceId == null) {
    targetNamespaceId = namespaceRepository.findBySlug("global")
            .map(ns -> ns.getId())
            .orElseThrow(() -> new DomainBadRequestException("error.namespace.global.notFound"));
}
return ok("response.success.created",
        governanceWorkflowAppService.submitAgentPromotion(
                request.sourceAgentId(),
                request.sourceAgentVersionId(),
                targetNamespaceId,
                principal.userId()));
```

Inject `NamespaceRepository` into `PromotionController` if not already present (check current constructor). If injection is needed, add field + constructor arg + matching test setUp wiring.

- [ ] **Step 1.6: Run the test to verify it passes**

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw -pl skillhub-app test -Dtest=PromotionPortalControllerTest 2>&1 | tail -10
```

Expected: ALL `PromotionPortalControllerTest` tests pass — including the new one and pre-existing ones. If pre-existing tests fail, you broke the SKILL branch — review your edits.

- [ ] **Step 1.7: Run full backend reactor (per lessons.md mvn-pl-vs-root rule)**

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw clean compile 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`. (We use `clean compile` per lessons.md 2026-04-28 — incremental compile is unreliable for verifying overwrites.)

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw test 2>&1 | tail -5
```

Expected: all tests pass, no failures or errors.

- [ ] **Step 1.8: Update frontend `PromoteAgentButton` component**

Edit `web/src/features/agent/promotion/promote-agent-button.tsx`:

1. Remove the `globalNamespaceId: number | null | undefined` prop from the interface.
2. Remove the destructuring of `globalNamespaceId` from props.
3. Remove the gate `if (!versionId || !globalNamespaceId) return null` and replace with `if (!versionId) return null`.
4. Update the `handleConfirm` body: remove `targetNamespaceId: globalNamespaceId` from the mutation payload.

Resulting `handleConfirm` should look like:

```tsx
const handleConfirm = async () => {
  await submit.mutateAsync({
    sourceAgentId: agentId,
    sourceAgentVersionId: versionId,
  })
}
```

- [ ] **Step 1.9: Update `useSubmitAgentPromotion` mutation type**

Open the file you found in Step 1.1. Find `useSubmitAgentPromotion`'s mutation function. Make `targetNamespaceId` optional in the input type (or remove it entirely if Agent flow never needs to pass an explicit target). Recommended:

```ts
useMutation({
  mutationFn: (params: { sourceAgentId: number; sourceAgentVersionId: number; targetNamespaceId?: number }) => {
    return promotionApi.submitAgent(params)
  },
  // ... existing onSuccess
})
```

Update `promotionApi.submitAgent` (in `web/src/api/client.ts`) to omit `targetNamespaceId` from the body when undefined — the JSON body should not include the key at all in that case (use `JSON.stringify` with conditional spread).

- [ ] **Step 1.10: Update `agent-detail.tsx` to drop the GLOBAL id plumbing**

Edit `web/src/pages/agent-detail.tsx`:

1. Remove the import of `useMyNamespaces` (line ~19) IF no other code on this page uses it. Verify with `grep useMyNamespaces web/src/pages/agent-detail.tsx`.
2. Remove the `myNamespaces` and `globalNamespaceId` derivations (lines ~125-126).
3. Remove the `globalNamespaceId={globalNamespaceId}` prop from the `<PromoteAgentButton ... />` JSX (around line ~717-724). Keep `agentId`, `versionId`, `versionStatus`, `isInGlobalNamespace` props.
4. Remove the obsolete comment at lines 121-124 about the GLOBAL fallback limitation.

- [ ] **Step 1.11: Update component test**

Edit `web/src/features/agent/promotion/promote-agent-button.test.tsx`:
1. Remove any test setup that passes `globalNamespaceId` prop.
2. Remove (or rewrite) the test case asserting "hides when globalNamespaceId is null" — that gate no longer exists.
3. Add a new test: "renders for admin without requiring globalNamespaceId" — mount with the minimal required props (agentId, versionId, versionStatus='PUBLISHED', isInGlobalNamespace=false), mock useAuth's `hasRole` to return true for SKILL_ADMIN, assert the button is visible.

If `agent-detail.test.tsx` mocked `useMyNamespaces`, remove that mock setup as well.

- [ ] **Step 1.12: Run scoped frontend tests**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/features/agent/promotion src/pages/agent-detail.test.tsx 2>&1 | tail -10
```

Expected: all pass.

- [ ] **Step 1.13: Run full frontend suite**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -5
```

Expected: all pass.

- [ ] **Step 1.14: Run TS check**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm tsc --noEmit 2>&1 | tail -10
```

Expected: clean.

- [ ] **Step 1.15: Stage + commit (single Bash call per lessons.md parallel-agent rule)**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/PromotionController.java server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/portal/PromotionPortalControllerTest.java web/src/features/agent/promotion/promote-agent-button.tsx web/src/features/agent/promotion/promote-agent-button.test.tsx web/src/pages/agent-detail.tsx web/src/pages/agent-detail.test.tsx web/src/api/client.ts web/src/features/promotion/use-promotion-list.ts && git commit -m "$(cat <<'EOF'
feat(api,web): default agent promotion target to GLOBAL when omitted

Agent promotion now mirrors Skill's contract: targetNamespaceId is
optional in the request body. PromotionController resolves the GLOBAL
namespace server-side when omitted. PromoteAgentButton drops its
globalNamespaceId prop and useMyNamespaces dependency, so admins who
aren't GLOBAL members can still see and use the button.

Spec: docs/superpowers/specs/2026-04-29-agent-followups-bundle-design.md §3 Item 1

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

If any file paths in the `git add` differ from what you actually touched (Step 1.1 may have located `useSubmitAgentPromotion` elsewhere), adjust the file list before running.

---

## Task 2: Agent list cards display ratingAvg + ratingCount

**Why:** Backend `AgentResponse` already returns these fields; frontend just doesn't render them. Mirror Skill card chip design (`SkillCard:80-87`).

**Files:**
- Modify: `web/src/api/agent-types.ts:9-17` (AgentSummary interface)
- Modify: `web/src/features/agent/use-agents.ts:30-50` (mapper)
- Modify: `web/src/features/agent/use-agents.test.tsx`
- Modify: `web/src/features/agent/agent-card.tsx`
- Modify: `web/src/features/agent/agent-card.test.tsx`

- [ ] **Step 2.1: Read SkillCard's rating chip implementation as reference**

```bash
sed -n '78,90p' web/src/features/skill/skill-card.tsx
```

Confirm chip shape:

```tsx
{skill.ratingAvg !== undefined && skill.ratingCount > 0 && (
  <span className="flex items-center gap-1">
    <svg className="w-3.5 h-3.5 text-primary" fill="currentColor" viewBox="0 0 20 20">
      <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
    </svg>
    {skill.ratingAvg.toFixed(1)}
  </span>
)}
```

- [ ] **Step 2.2: Add failing card test**

Edit `web/src/features/agent/agent-card.test.tsx`. Add two cases (snippet — adapt to the file's existing render helpers and imports):

```tsx
it('renders rating chip when ratingCount > 0', () => {
  const agent = makeAgent({ ratingAvg: 4.3, ratingCount: 12 })
  render(<AgentCard agent={agent} />)
  expect(screen.getByText('4.3')).toBeInTheDocument()
})

it('hides rating chip when ratingCount === 0', () => {
  const agent = makeAgent({ ratingAvg: 0, ratingCount: 0 })
  render(<AgentCard agent={agent} />)
  expect(screen.queryByText('0.0')).not.toBeInTheDocument()
})
```

If the file lacks a `makeAgent` helper, copy the existing test setup pattern (likely an inline literal). If `screen.getByText('4.3')` is too loose because '4.3' could appear elsewhere, scope with a `within()` over the rating chip's container — read the existing tests to see how they scope assertions.

- [ ] **Step 2.3: Run test, verify it fails**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/features/agent/agent-card.test.tsx 2>&1 | tail -10
```

Expected: new tests FAIL ("Unable to find element with text 4.3").

- [ ] **Step 2.4: Add fields to `AgentSummary` interface**

Edit `web/src/api/agent-types.ts`. Replace the `AgentSummary` interface (lines 9-17) with:

```ts
export interface AgentSummary {
  name: string
  displayName?: string
  description: string
  version?: string
  namespace?: string
  starCount?: number
  downloadCount?: number
  ratingAvg?: number
  ratingCount?: number
}
```

- [ ] **Step 2.5: Update mapper in `useAgents`**

Open `web/src/features/agent/use-agents.ts`. Find the dto → AgentSummary mapper around line 39 (where `starCount` is mapped). Add right after `starCount`:

```ts
ratingAvg: dto.ratingAvg !== undefined && dto.ratingAvg !== null ? Number(dto.ratingAvg) : undefined,
ratingCount: dto.ratingCount ?? 0,
```

Note: backend serializes BigDecimal as a JSON number (verified by Skill flow already doing `Number(...)`-style conversion). If TypeScript complains that `dto.ratingAvg` doesn't exist, also add the fields to the dto type (look for the type definition near the top of `use-agents.ts` or in `web/src/api/types.ts`).

- [ ] **Step 2.6: Add chip to `AgentCard`**

Edit `web/src/features/agent/agent-card.tsx`. Locate the bottom row that renders `starCount` (around line 60). Add the rating chip span immediately after the star span, identical to SkillCard's structure:

```tsx
{agent.ratingAvg !== undefined && (agent.ratingCount ?? 0) > 0 && (
  <span className="flex items-center gap-1">
    <svg className="w-3.5 h-3.5 text-primary" fill="currentColor" viewBox="0 0 20 20">
      <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
    </svg>
    {agent.ratingAvg.toFixed(1)}
  </span>
)}
```

The conditional uses `(agent.ratingCount ?? 0) > 0` (not `agent.ratingCount > 0`) because `AgentSummary.ratingCount` is optional.

- [ ] **Step 2.7: Update mapper test**

Edit `web/src/features/agent/use-agents.test.tsx`. Add a case that asserts the mapper passes through `ratingAvg` and `ratingCount`:

```tsx
it('maps ratingAvg and ratingCount from dto', () => {
  // ... mock fetch / queryFn to return [{ ratingAvg: 4.3, ratingCount: 12, ...rest }]
  // ... assert hook result includes ratingAvg: 4.3, ratingCount: 12
})
```

Adapt to the existing test's mock pattern (likely uses `vi.mock('@/api/client', ...)` or similar — read the file's existing setup before adding).

- [ ] **Step 2.8: Run scoped tests, verify pass**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/features/agent 2>&1 | tail -10
```

Expected: all pass.

- [ ] **Step 2.9: Run full frontend suite + TS check**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -3 && pnpm tsc --noEmit 2>&1 | tail -3
```

Expected: all pass, TS clean.

- [ ] **Step 2.10: Stage + commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/api/agent-types.ts web/src/features/agent/use-agents.ts web/src/features/agent/use-agents.test.tsx web/src/features/agent/agent-card.tsx web/src/features/agent/agent-card.test.tsx && git commit -m "$(cat <<'EOF'
feat(web): show ratingAvg/ratingCount on agent list cards

Mirrors SkillCard's rating chip design. Backend AgentResponse already
returned these fields; AgentSummary frontend type and useAgents mapper
just hadn't picked them up.

Spec: docs/superpowers/specs/2026-04-29-agent-followups-bundle-design.md §3 Item 2

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 3: Admin moderation dashboard handles agent reports

**Why:** Backend `AgentReportController` only has user-submit; admin moderation queue + resolve/dismiss endpoints don't exist. Frontend `reports.tsx` is skill-only. We mirror Skill's `AdminSkillReportController` / `AdminSkillReportAppService` / `JpaAdminSkillReportQueryRepository` exactly, plus add a Skill/Agent outer Tabs to the dashboard page.

**Pre-flight verification (run first):**

- [ ] **Step 3.0: Verify mirror targets exist (per lessons.md grep-before-create)**

```bash
cd /Users/lydoc/projectscoding/skillhub
find server -name "AdminAgentReportController.java" 2>/dev/null
find server -name "AdminAgentReportAppService.java" 2>/dev/null
find server -name "AdminAgentReportSummaryResponse.java" 2>/dev/null
find server -name "AdminAgentReportActionRequest.java" 2>/dev/null
find server -name "AdminAgentReportQueryRepository.java" 2>/dev/null
find server -name "AgentReportDisposition.java" 2>/dev/null
find server -name "AgentReportResolvedEvent.java" -o -name "AgentReportDismissedEvent.java" 2>/dev/null
```

Expected: ALL empty (none exist yet — we create them all).

```bash
ls server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/report/
```

Expected: `AgentReport.java`, `AgentReportRepository.java`, `AgentReportService.java`, `AgentReportStatus.java` (no event subdirectory yet).

```bash
ls server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/lifecycle/ 2>/dev/null
ls server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/service/AgentLifecycleService.java
```

Confirm: `AgentLifecycleService.archive(...)` is on disk; **no `hideAgent` method exists**. → We will NOT include `RESOLVE_AND_HIDE` in `AgentReportDisposition` for v1 (spec §3 Item 3).

```bash
grep -n "findByStatus" server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/report/AgentReportRepository.java
```

Expected: `Page<AgentReport> findByStatus(AgentReportStatus status, Pageable pageable);` already declared.

**Files:**

Backend (create):
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/report/AgentReportDisposition.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/report/event/AgentReportResolvedEvent.java`
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/report/event/AgentReportDismissedEvent.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/AdminAgentReportSummaryResponse.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/AdminAgentReportActionRequest.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/repository/AdminAgentReportQueryRepository.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/repository/JpaAdminAgentReportQueryRepository.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/AdminAgentReportAppService.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/admin/AdminAgentReportController.java`
- `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/agent/report/AgentReportServiceTest.java` (extend existing if present, else create)
- `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/admin/AdminAgentReportControllerTest.java`

Backend (modify):
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/report/AgentReportService.java` (add resolve/dismiss/listByStatus)

Frontend (create):
- `web/src/features/report/use-agent-reports.ts`

Frontend (modify):
- `web/src/api/client.ts` (add `agentReportApi`)
- `web/src/pages/dashboard/reports.tsx` (extract panels, add outer Tabs)
- `web/src/pages/dashboard/reports.test.tsx` (verify exists; if not, create)
- `web/src/i18n/locales/en.json` and `web/src/i18n/locales/zh.json` (3 new keys)

### Backend portion

- [ ] **Step 3.1: Create `AgentReportDisposition` enum**

Create `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/report/AgentReportDisposition.java`:

```java
package com.iflytek.skillhub.domain.agent.report;

/**
 * Disposition options for resolving an agent report. Mirrors
 * {@link com.iflytek.skillhub.domain.report.SkillReportDisposition}, minus
 * RESOLVE_AND_HIDE — Agent has no hide path in v1 (AgentLifecycleService
 * lacks a hideAgent method). Add HIDE here only after that lifecycle path
 * lands. See plan 2026-04-29-agent-followups-bundle.md §Task 3.
 */
public enum AgentReportDisposition {
    RESOLVE_ONLY,
    RESOLVE_AND_ARCHIVE
}
```

- [ ] **Step 3.2: Create `AgentReportResolvedEvent` and `AgentReportDismissedEvent`**

Create the directory and both events:

```bash
mkdir -p server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/report/event
```

`AgentReportResolvedEvent.java`:

```java
package com.iflytek.skillhub.domain.agent.report.event;

public record AgentReportResolvedEvent(
        Long reportId,
        Long agentId,
        String adminUserId,
        String reporterId,
        String dispositionLabel
) {}
```

`AgentReportDismissedEvent.java`:

```java
package com.iflytek.skillhub.domain.agent.report.event;

public record AgentReportDismissedEvent(
        Long reportId,
        Long agentId,
        String adminUserId,
        String reporterId
) {}
```

- [ ] **Step 3.3: Read SkillReportService.resolveReport for mirror reference**

```bash
sed -n '85,150p' server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/report/SkillReportService.java
```

Note the patterns: two `resolveReport` overloads (default + with disposition), `requirePendingReport` helper, `auditLogService.record(...)`, `eventPublisher.publishEvent(...)`, `governanceNotificationService.notifyUser(...)`.

- [ ] **Step 3.4: Add failing service tests**

Locate or create `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/agent/report/AgentReportServiceTest.java`. Add the following test methods (adapt to the existing file's setUp / mock declarations — look at `SkillReportServiceTest` if you need a close reference for mock wiring):

```java
@Test
void listByStatus_returnsRepositoryPage() {
    Page<AgentReport> mockPage = new PageImpl<>(List.of());
    when(agentReportRepository.findByStatus(eq(AgentReportStatus.PENDING), any(Pageable.class)))
            .thenReturn(mockPage);

    Page<AgentReport> result = agentReportService.listByStatus(AgentReportStatus.PENDING, PageRequest.of(0, 20));

    assertThat(result).isSameAs(mockPage);
}

@Test
void resolveReport_setsStatusAndAuditFields() {
    AgentReport pending = mock(AgentReport.class);
    when(pending.getStatus()).thenReturn(AgentReportStatus.PENDING);
    when(pending.getAgentId()).thenReturn(7L);
    when(pending.getReporterId()).thenReturn("alice");
    when(agentReportRepository.findById(99L)).thenReturn(Optional.of(pending));
    when(agentReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    AgentReport result = agentReportService.resolveReport(99L, "admin1", "looks legit", "127.0.0.1", "ua");

    verify(pending).setStatus(AgentReportStatus.RESOLVED);
    verify(pending).setHandledBy("admin1");
    verify(pending).setHandleComment("looks legit");
    verify(pending).setHandledAt(any());
    verify(eventPublisher).publishEvent(any(AgentReportResolvedEvent.class));
    verify(auditLogService).record(eq("admin1"), eq("RESOLVE_AGENT_REPORT"), eq("AGENT_REPORT"), eq(99L), any(), any(), any(), any());
}

@Test
void resolveReport_withArchiveDispositon_callsLifecycleArchive() {
    AgentReport pending = mock(AgentReport.class);
    when(pending.getStatus()).thenReturn(AgentReportStatus.PENDING);
    when(pending.getAgentId()).thenReturn(7L);
    when(pending.getReporterId()).thenReturn("alice");
    when(agentReportRepository.findById(99L)).thenReturn(Optional.of(pending));
    when(agentReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    agentReportService.resolveReport(99L, "admin1", AgentReportDisposition.RESOLVE_AND_ARCHIVE, "abuse", "127.0.0.1", "ua");

    verify(agentLifecycleService).archive(eq(7L), eq("admin1"), any(), any());
}

@Test
void resolveReport_failsIfNotPending() {
    AgentReport resolved = mock(AgentReport.class);
    when(resolved.getStatus()).thenReturn(AgentReportStatus.RESOLVED);
    when(agentReportRepository.findById(99L)).thenReturn(Optional.of(resolved));

    assertThatThrownBy(() ->
            agentReportService.resolveReport(99L, "admin1", "x", "127.0.0.1", "ua"))
            .isInstanceOf(DomainBadRequestException.class);
}

@Test
void dismissReport_setsStatusAndAuditFields() {
    AgentReport pending = mock(AgentReport.class);
    when(pending.getStatus()).thenReturn(AgentReportStatus.PENDING);
    when(pending.getAgentId()).thenReturn(7L);
    when(pending.getReporterId()).thenReturn("alice");
    when(agentReportRepository.findById(99L)).thenReturn(Optional.of(pending));
    when(agentReportRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    agentReportService.dismissReport(99L, "admin1", "noise", "127.0.0.1", "ua");

    verify(pending).setStatus(AgentReportStatus.DISMISSED);
    verify(eventPublisher).publishEvent(any(AgentReportDismissedEvent.class));
}

@Test
void dismissReport_failsIfNotPending() {
    AgentReport dismissed = mock(AgentReport.class);
    when(dismissed.getStatus()).thenReturn(AgentReportStatus.DISMISSED);
    when(agentReportRepository.findById(99L)).thenReturn(Optional.of(dismissed));

    assertThatThrownBy(() ->
            agentReportService.dismissReport(99L, "admin1", "x", "127.0.0.1", "ua"))
            .isInstanceOf(DomainBadRequestException.class);
}
```

You may need to add fields to the test class for `agentLifecycleService`, `eventPublisher`, `auditLogService`, etc. — mirror the existing `AgentReportServiceTest` setUp + add what's missing. If `AgentReportServiceTest` doesn't exist yet, create it modeled after `SkillReportServiceTest`.

- [ ] **Step 3.5: Run service tests, verify they fail**

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw -DskipTests install 2>&1 | tail -3 && ./mvnw -pl skillhub-domain test -Dtest=AgentReportServiceTest 2>&1 | tail -15
```

Expected: tests FAIL with "method listByStatus does not exist" / "resolveReport(...) does not exist" or NPE.

- [ ] **Step 3.6: Implement service additions**

Edit `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/report/AgentReportService.java`. Add these dependencies via constructor (in addition to existing ones):
- `AgentLifecycleService agentLifecycleService`
- `ApplicationEventPublisher eventPublisher` (likely already present)
- `AuditLogService auditLogService` (verify name — check skill-side equivalent)
- `GovernanceNotificationService governanceNotificationService` (optional — may not be wired for agents yet; if wiring fails, omit notification call and add follow-up)
- `Clock clock` if not already present

Add these methods (adapt parameter types to match existing service style):

```java
@Transactional(readOnly = true)
public Page<AgentReport> listByStatus(AgentReportStatus status, Pageable pageable) {
    return agentReportRepository.findByStatus(status, pageable);
}

@Transactional
public AgentReport resolveReport(Long reportId, String actorUserId, String comment, String clientIp, String userAgent) {
    return resolveReport(reportId, actorUserId, AgentReportDisposition.RESOLVE_ONLY, comment, clientIp, userAgent);
}

@Transactional
public AgentReport resolveReport(Long reportId,
                                 String actorUserId,
                                 AgentReportDisposition disposition,
                                 String comment,
                                 String clientIp,
                                 String userAgent) {
    AgentReport report = requirePendingReport(reportId);
    if (disposition == AgentReportDisposition.RESOLVE_AND_ARCHIVE) {
        agentLifecycleService.archive(report.getAgentId(), actorUserId, /* userNsRoles= */ Map.of(), /* platformRoles= */ Set.of("SUPER_ADMIN"));
    }
    report.setStatus(AgentReportStatus.RESOLVED);
    report.setHandledBy(actorUserId);
    report.setHandleComment(normalize(comment));
    report.setHandledAt(Instant.now(clock));
    AgentReport saved = agentReportRepository.save(report);
    auditLogService.record(actorUserId, "RESOLVE_AGENT_REPORT", "AGENT_REPORT", reportId, null, clientIp, userAgent, null);
    eventPublisher.publishEvent(new AgentReportResolvedEvent(
            saved.getId(), saved.getAgentId(), actorUserId, saved.getReporterId(), disposition.name()));
    return saved;
}

@Transactional
public AgentReport dismissReport(Long reportId, String actorUserId, String comment, String clientIp, String userAgent) {
    AgentReport report = requirePendingReport(reportId);
    report.setStatus(AgentReportStatus.DISMISSED);
    report.setHandledBy(actorUserId);
    report.setHandleComment(normalize(comment));
    report.setHandledAt(Instant.now(clock));
    AgentReport saved = agentReportRepository.save(report);
    auditLogService.record(actorUserId, "DISMISS_AGENT_REPORT", "AGENT_REPORT", reportId, null, clientIp, userAgent, null);
    eventPublisher.publishEvent(new AgentReportDismissedEvent(
            saved.getId(), saved.getAgentId(), actorUserId, saved.getReporterId()));
    return saved;
}

private AgentReport requirePendingReport(Long reportId) {
    AgentReport report = agentReportRepository.findById(reportId)
            .orElseThrow(() -> new DomainBadRequestException("error.agent.report.notFound"));
    if (report.getStatus() != AgentReportStatus.PENDING) {
        throw new DomainBadRequestException("error.agent.report.notPending");
    }
    return report;
}

private String normalize(String comment) {
    return comment == null || comment.isBlank() ? null : comment.trim();
}
```

**Important:** `AgentLifecycleService.archive`'s exact signature must match the call. Verify with:

```bash
grep -A 5 "public Agent archive" server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/service/AgentLifecycleService.java
```

If the signature is `archive(Long agentId, String userId, Map<Long, NamespaceRole> userNsRoles, Set<String> platformRoles)`, the call above is correct. If different, adapt the args. The intent: admin archives — the lifecycle service's permission gate must accept SUPER_ADMIN platform role.

- [ ] **Step 3.7: Run service tests, verify they pass**

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw -DskipTests install 2>&1 | tail -3 && ./mvnw -pl skillhub-domain test -Dtest=AgentReportServiceTest 2>&1 | tail -10
```

Expected: all pass.

- [ ] **Step 3.8: Create DTOs**

`server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/AdminAgentReportSummaryResponse.java`:

```java
package com.iflytek.skillhub.dto;

import java.time.Instant;

public record AdminAgentReportSummaryResponse(
        Long id,
        Long agentId,
        String namespace,
        String agentSlug,
        String agentDisplayName,
        String reporterId,
        String reason,
        String details,
        String status,
        String handledBy,
        String handleComment,
        Instant createdAt,
        Instant handledAt
) {}
```

`server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/AdminAgentReportActionRequest.java`:

```java
package com.iflytek.skillhub.dto;

public record AdminAgentReportActionRequest(
        String comment,
        String disposition
) {}
```

- [ ] **Step 3.9: Create QueryRepository (mirror `JpaAdminSkillReportQueryRepository`)**

`server/skillhub-app/src/main/java/com/iflytek/skillhub/repository/AdminAgentReportQueryRepository.java`:

```java
package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.agent.report.AgentReport;
import com.iflytek.skillhub.dto.AdminAgentReportSummaryResponse;
import java.util.List;

public interface AdminAgentReportQueryRepository {
    List<AdminAgentReportSummaryResponse> getAgentReportSummaries(List<AgentReport> reports);
}
```

`server/skillhub-app/src/main/java/com/iflytek/skillhub/repository/JpaAdminAgentReportQueryRepository.java`:

```java
package com.iflytek.skillhub.repository;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.report.AgentReport;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.dto.AdminAgentReportSummaryResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

@Repository
public class JpaAdminAgentReportQueryRepository implements AdminAgentReportQueryRepository {

    private final AgentRepository agentRepository;
    private final NamespaceRepository namespaceRepository;

    public JpaAdminAgentReportQueryRepository(AgentRepository agentRepository,
                                              NamespaceRepository namespaceRepository) {
        this.agentRepository = agentRepository;
        this.namespaceRepository = namespaceRepository;
    }

    @Override
    public List<AdminAgentReportSummaryResponse> getAgentReportSummaries(List<AgentReport> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }
        List<Long> agentIds = reports.stream().map(AgentReport::getAgentId).distinct().toList();
        Map<Long, Agent> agentsById = agentIds.isEmpty()
                ? Map.of()
                : agentRepository.findByIdIn(agentIds).stream()
                        .collect(Collectors.toMap(Agent::getId, Function.identity()));

        List<Long> namespaceIds = agentsById.values().stream()
                .map(Agent::getNamespaceId).distinct().toList();
        Map<Long, String> namespaceSlugs = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                        .collect(Collectors.toMap(Namespace::getId, Namespace::getSlug));

        return reports.stream()
                .map(report -> toResponse(report, agentsById.get(report.getAgentId()), namespaceSlugs))
                .toList();
    }

    private AdminAgentReportSummaryResponse toResponse(AgentReport report,
                                                       Agent agent,
                                                       Map<Long, String> namespaceSlugs) {
        return new AdminAgentReportSummaryResponse(
                report.getId(),
                report.getAgentId(),
                agent != null ? namespaceSlugs.get(agent.getNamespaceId()) : null,
                agent != null ? agent.getSlug() : null,
                agent != null ? agent.getDisplayName() : null,
                report.getReporterId(),
                report.getReason(),
                report.getDetails(),
                report.getStatus().name(),
                report.getHandledBy(),
                report.getHandleComment(),
                report.getCreatedAt(),
                report.getHandledAt()
        );
    }
}
```

If `AgentRepository.findByIdIn` doesn't exist, verify with:

```bash
grep -n "findByIdIn" server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentRepository.java
```

Per pre-flight, it does exist (line 17). If absent at runtime, add it as a port method + JPA derived query (parallel to Skill's).

- [ ] **Step 3.10: Create AppService**

`server/skillhub-app/src/main/java/com/iflytek/skillhub/service/AdminAgentReportAppService.java`:

```java
package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.agent.report.AgentReportRepository;
import com.iflytek.skillhub.domain.agent.report.AgentReportStatus;
import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import com.iflytek.skillhub.dto.AdminAgentReportSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.repository.AdminAgentReportQueryRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Mirrors {@link AdminSkillReportAppService}: enriches raw agent report
 * records with agent + namespace context for admin moderation UIs.
 */
@Service
public class AdminAgentReportAppService {

    private final AgentReportRepository agentReportRepository;
    private final AdminAgentReportQueryRepository adminAgentReportQueryRepository;

    public AdminAgentReportAppService(AgentReportRepository agentReportRepository,
                                      AdminAgentReportQueryRepository adminAgentReportQueryRepository) {
        this.agentReportRepository = agentReportRepository;
        this.adminAgentReportQueryRepository = adminAgentReportQueryRepository;
    }

    public PageResponse<AdminAgentReportSummaryResponse> listReports(String status, int page, int size) {
        AgentReportStatus resolvedStatus = parseStatus(status);
        var reportPage = agentReportRepository.findByStatus(resolvedStatus, PageRequest.of(page, size));
        List<AdminAgentReportSummaryResponse> items =
                adminAgentReportQueryRepository.getAgentReportSummaries(reportPage.getContent());
        return new PageResponse<>(items, reportPage.getTotalElements(), reportPage.getNumber(), reportPage.getSize());
    }

    private AgentReportStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return AgentReportStatus.PENDING;
        }
        try {
            return AgentReportStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new DomainBadRequestException("error.agent.report.status.invalid", status);
        }
    }
}
```

- [ ] **Step 3.11: Create AdminAgentReportController**

`server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/admin/AdminAgentReportController.java`:

```java
package com.iflytek.skillhub.controller.admin;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.agent.report.AgentReportDisposition;
import com.iflytek.skillhub.domain.agent.report.AgentReportService;
import com.iflytek.skillhub.dto.AdminAgentReportActionRequest;
import com.iflytek.skillhub.dto.AdminAgentReportSummaryResponse;
import com.iflytek.skillhub.dto.AgentReportMutationResponse;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.PageResponse;
import com.iflytek.skillhub.service.AdminAgentReportAppService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/agent-reports")
public class AdminAgentReportController extends BaseApiController {

    private final AdminAgentReportAppService adminAgentReportAppService;
    private final AgentReportService agentReportService;

    public AdminAgentReportController(AdminAgentReportAppService adminAgentReportAppService,
                                      AgentReportService agentReportService,
                                      ApiResponseFactory responseFactory) {
        super(responseFactory);
        this.adminAgentReportAppService = adminAgentReportAppService;
        this.agentReportService = agentReportService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<PageResponse<AdminAgentReportSummaryResponse>> listReports(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ok("response.success", adminAgentReportAppService.listReports(status, page, size));
    }

    @PostMapping("/{reportId}/resolve")
    @PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AgentReportMutationResponse> resolveReport(@PathVariable Long reportId,
                                                                  @RequestBody(required = false) AdminAgentReportActionRequest request,
                                                                  @AuthenticationPrincipal PlatformPrincipal principal,
                                                                  HttpServletRequest httpRequest) {
        AgentReportDisposition disposition = request != null && request.disposition() != null
                ? AgentReportDisposition.valueOf(request.disposition().trim().toUpperCase())
                : AgentReportDisposition.RESOLVE_ONLY;
        var report = agentReportService.resolveReport(
                reportId,
                principal.userId(),
                disposition,
                request != null ? request.comment() : null,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ok("response.success.updated", new AgentReportMutationResponse(report.getId(), report.getStatus().name()));
    }

    @PostMapping("/{reportId}/dismiss")
    @PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")
    public ApiResponse<AgentReportMutationResponse> dismissReport(@PathVariable Long reportId,
                                                                  @RequestBody(required = false) AdminAgentReportActionRequest request,
                                                                  @AuthenticationPrincipal PlatformPrincipal principal,
                                                                  HttpServletRequest httpRequest) {
        var report = agentReportService.dismissReport(
                reportId,
                principal.userId(),
                request != null ? request.comment() : null,
                httpRequest.getRemoteAddr(),
                httpRequest.getHeader("User-Agent")
        );
        return ok("response.success.updated", new AgentReportMutationResponse(report.getId(), report.getStatus().name()));
    }
}
```

Note: only `/api/v1/admin/...` route — Skill admin uses single-route too (no `/api/web/admin/...`). Verify by checking AdminSkillReportController's `@RequestMapping`. **The frontend calls `/api/v1/admin/...` for skill admin — confirm by reading `web/src/api/client.ts` for the skill report admin call before assuming**.

- [ ] **Step 3.12: Add controller tests**

Create `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/admin/AdminAgentReportControllerTest.java` modeled after `AdminSkillReportControllerTest`. Required cases:

```java
// listReports
@Test @WithMockUser(roles = "SKILL_ADMIN")
void listReports_returnsPage() // happy

@Test @WithMockUser(roles = "USER")
void listReports_forbiddenForNonAdmin() // 403

@Test
void listReports_unauthorizedAnonymous() // 401

// resolveReport
@Test @WithMockUser(roles = "SKILL_ADMIN")
void resolveReport_happy()

@Test @WithMockUser(roles = "SKILL_ADMIN")
void resolveReport_notFoundReturns400() // service throws DomainBadRequestException

@Test @WithMockUser(roles = "USER")
void resolveReport_forbiddenForNonAdmin()

// dismissReport
@Test @WithMockUser(roles = "SKILL_ADMIN")
void dismissReport_happy()

@Test @WithMockUser(roles = "USER")
void dismissReport_forbiddenForNonAdmin()
```

Read `AdminSkillReportControllerTest` first to copy the exact test setUp boilerplate (MockMvc / mocked beans). The skill test file is at `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/admin/AdminSkillReportControllerTest.java`.

- [ ] **Step 3.13: Run controller tests, verify pass**

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw -DskipTests install 2>&1 | tail -3 && ./mvnw -pl skillhub-app test -Dtest=AdminAgentReportControllerTest 2>&1 | tail -10
```

Expected: all pass.

- [ ] **Step 3.14: Run full backend reactor**

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw clean compile 2>&1 | tail -5 && ./mvnw test 2>&1 | tail -5
```

Expected: BUILD SUCCESS, all tests pass.

### Frontend portion

- [ ] **Step 3.15: Add `agentReportApi` to client.ts**

Edit `web/src/api/client.ts`. Locate where `skillReportApi` (or admin skill report functions) is defined for reference. Add `agentReportApi` near it:

```ts
export const agentReportApi = {
  async listAdminReports(params: { status?: string; page?: number; size?: number } = {}): Promise<{ items: AdminAgentReportSummary[]; total: number; page: number; size: number }> {
    const sp = new URLSearchParams()
    if (params.status) sp.set('status', params.status)
    sp.set('page', String(params.page ?? 0))
    sp.set('size', String(params.size ?? 20))
    return fetchJson(`/api/v1/admin/agent-reports?${sp.toString()}`)
  },

  async resolve(reportId: number, body: { comment?: string; disposition?: 'RESOLVE_ONLY' | 'RESOLVE_AND_ARCHIVE' }) {
    return fetchJson(`/api/v1/admin/agent-reports/${reportId}/resolve`, {
      method: 'POST',
      body: JSON.stringify(body),
    })
  },

  async dismiss(reportId: number, body: { comment?: string }) {
    return fetchJson(`/api/v1/admin/agent-reports/${reportId}/dismiss`, {
      method: 'POST',
      body: JSON.stringify(body),
    })
  },
}

export interface AdminAgentReportSummary {
  id: number
  agentId: number
  namespace: string | null
  agentSlug: string | null
  agentDisplayName: string | null
  reporterId: string
  reason: string
  details: string | null
  status: 'PENDING' | 'RESOLVED' | 'DISMISSED'
  handledBy: string | null
  handleComment: string | null
  createdAt: string
  handledAt: string | null
}
```

(If `AdminAgentReportSummary` should live in `@/api/types`, move accordingly per existing convention.)

- [ ] **Step 3.16: Create `use-agent-reports.ts` hooks**

`web/src/features/report/use-agent-reports.ts`:

```ts
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { agentReportApi, AdminAgentReportSummary } from '@/api/client'

export function useAgentReports(status: 'PENDING' | 'RESOLVED' | 'DISMISSED') {
  return useQuery({
    queryKey: ['agentReports', status],
    queryFn: async () => {
      const page = await agentReportApi.listAdminReports({ status, page: 0, size: 100 })
      return page.items as AdminAgentReportSummary[]
    },
  })
}

export function useResolveAgentReport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, disposition, comment }: { id: number; disposition?: 'RESOLVE_ONLY' | 'RESOLVE_AND_ARCHIVE'; comment?: string }) =>
      agentReportApi.resolve(id, { disposition, comment }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['agentReports'] }),
  })
}

export function useDismissAgentReport() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, comment }: { id: number; comment?: string }) =>
      agentReportApi.dismiss(id, { comment }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['agentReports'] }),
  })
}
```

(Compare to `web/src/features/report/use-skill-reports.ts` for shape — adapt naming if hooks there differ).

- [ ] **Step 3.17: Add i18n keys**

Edit `web/src/i18n/locales/en.json`. Find the `reports` block. Add:

```json
"tabSkillReports": "Skill reports",
"tabAgentReports": "Agent reports",
"openAgent": "Open agent"
```

Mirror in `web/src/i18n/locales/zh.json`:

```json
"tabSkillReports": "技能举报",
"tabAgentReports": "智能体举报",
"openAgent": "打开智能体"
```

- [ ] **Step 3.18: Refactor `reports.tsx` — extract Skill panel, then add outer Tabs**

Edit `web/src/pages/dashboard/reports.tsx`. Two-step refactor:

**Sub-step 18a: Extract `<SkillReportsPanel />` from current body**

Move the current page body (everything below `<DashboardPageHeader />` — the inner Tabs PENDING/RESOLVED/DISMISSED + the lists + the confirm dialog) into a new local component `SkillReportsPanel`. The page-level component should now look like:

```tsx
export function ReportsPage() {
  const { t } = useTranslation()
  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader title={t('reports.title')} subtitle={t('reports.subtitle')} />
      <SkillReportsPanel />
    </div>
  )
}

function SkillReportsPanel() {
  // ... all the existing body code
}
```

Run `pnpm vitest run src/pages/dashboard/reports.test.tsx` here to verify the refactor didn't break existing tests.

**Sub-step 18b: Add outer Tabs and `<AgentReportsPanel />`**

```tsx
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'

export function ReportsPage() {
  const { t } = useTranslation()
  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader title={t('reports.title')} subtitle={t('reports.subtitle')} />
      <Tabs defaultValue="SKILL">
        <TabsList>
          <TabsTrigger value="SKILL">{t('reports.tabSkillReports')}</TabsTrigger>
          <TabsTrigger value="AGENT">{t('reports.tabAgentReports')}</TabsTrigger>
        </TabsList>
        <TabsContent value="SKILL" className="mt-6">
          <SkillReportsPanel />
        </TabsContent>
        <TabsContent value="AGENT" className="mt-6">
          <AgentReportsPanel />
        </TabsContent>
      </Tabs>
    </div>
  )
}
```

Create `AgentReportsPanel` as a near-copy of `SkillReportsPanel`:
- Replace `useSkillReports / useResolveSkillReport / useDismissSkillReport` with `useAgentReports / useResolveAgentReport / useDismissAgentReport`.
- Replace `report.skillSlug` / `report.namespace` with `report.agentSlug` / `report.namespace` (same field name, just different DTO type).
- Replace navigate target: `/space/{ns}/{slug}` → `/agents/{ns}/{slug}` (verified agent detail route).
- Replace `t('reports.openSkill')` link label → `t('reports.openAgent')`.

Same `Tabs` for status (PENDING / RESOLVED / DISMISSED), same confirm dialog, same resolve/dismiss button structure.

To avoid 200+ lines of duplication, you may extract a small shared `<ReportRow />` if helpful — judgment call; if it adds more abstraction than it saves, keep them parallel and accept the duplication for now.

- [ ] **Step 3.19: Update reports test**

Edit `web/src/pages/dashboard/reports.test.tsx`. Add cases:

```tsx
it('defaults to Skill reports tab', () => {
  // ... render
  expect(screen.getByText(/Skill reports/i)).toBeInTheDocument()
  // assert skill content visible (e.g., "Open skill" link or skill-specific text)
})

it('switches to Agent reports tab and lists pending agent reports', async () => {
  // ... render with mocked useAgentReports returning a stub report
  await user.click(screen.getByRole('tab', { name: /Agent reports/i }))
  // assert agent-specific content (e.g., "Open agent" link)
})
```

Mock `useAgentReports / useResolveAgentReport / useDismissAgentReport` parallel to existing skill mocks.

- [ ] **Step 3.20: Run scoped frontend tests**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/pages/dashboard/reports.test.tsx src/features/report 2>&1 | tail -10
```

Expected: all pass.

- [ ] **Step 3.21: Run full frontend suite + TS check**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -5 && pnpm tsc --noEmit 2>&1 | tail -5
```

Expected: all pass, TS clean.

- [ ] **Step 3.22: Stage + commit (single Bash call)**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add server/skillhub-domain server/skillhub-app web/src/api/client.ts web/src/features/report/use-agent-reports.ts web/src/pages/dashboard/reports.tsx web/src/pages/dashboard/reports.test.tsx web/src/i18n/locales/en.json web/src/i18n/locales/zh.json && git commit -m "$(cat <<'EOF'
feat(api,web): admin moderation dashboard handles agent reports

Backend mirrors AdminSkillReportController/AppService/QueryRepository
exactly — new admin endpoints at /api/v1/admin/agent-reports for list,
resolve, dismiss. AgentReportService gains listByStatus, resolveReport
(two overloads) and dismissReport with audit + event publish, mirroring
SkillReportService. AgentReportDisposition omits RESOLVE_AND_HIDE for v1
because AgentLifecycleService has no hide path yet (follow-up).

Frontend wraps the existing reports page body in an outer Skill / Agent
Tabs. Skill panel logic extracted unchanged; Agent panel mirrors it
against new use-agent-reports hooks.

Spec: docs/superpowers/specs/2026-04-29-agent-followups-bundle-design.md §3 Item 3

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 4: My Stars page Agents tab

**Why:** `MyStarsPage` is skill-only. Backend `AgentStarRepository.findByUserId` exists already (verified in pre-flight); we add the app service + controller + DTO + frontend tab.

**Pre-flight verification:**

- [ ] **Step 4.0: Verify mirror targets exist**

```bash
cd /Users/lydoc/projectscoding/skillhub
grep -n "findByUserId" server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/social/AgentStarRepository.java
```

Expected: line 15 — `Page<AgentStar> findByUserId(String userId, Pageable pageable);` ✅ (verified)

```bash
find server -name "MyAgentAppService.java" 2>/dev/null
find server -name "AgentSummaryResponse.java" 2>/dev/null
```

Expected: BOTH empty.

```bash
grep -n "findByIdIn" server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentRepository.java
```

Expected: present (line 17).

**Files:**

Backend (create):
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/AgentSummaryResponse.java`
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/MyAgentAppService.java`
- `server/skillhub-app/src/test/java/com/iflytek/skillhub/service/MyAgentAppServiceTest.java`

Backend (modify):
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/MeController.java` (add new endpoint)
- `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/portal/MeControllerTest.java` (add test)

Frontend (modify):
- `web/src/api/client.ts` (add `meApi.getAgentStarsPage`)
- `web/src/api/agent-types.ts` (extend `AgentSummary` if needed — Task 2 already added rating fields; ensure `id` and `slug` are present too)
- `web/src/shared/hooks/use-user-queries.ts` (add `useMyAgentStarsPage`)
- `web/src/pages/dashboard/stars.tsx` (extract Skills panel, add Tabs + Agents panel)
- `web/src/pages/dashboard/stars.test.ts` (add cases)
- `web/src/i18n/locales/{en,zh}.json` (3 new keys)

### Backend portion

- [ ] **Step 4.1: Read `MySkillAppService.listMyStars` for mirror reference**

```bash
sed -n '69,91p' server/skillhub-app/src/main/java/com/iflytek/skillhub/service/MySkillAppService.java
```

Mirror this exactly for agents.

- [ ] **Step 4.2: Read `SkillSummaryResponse` for mirror reference**

```bash
cat server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillSummaryResponse.java
```

Note the fields. We'll create a parallel `AgentSummaryResponse` with the agent equivalents — drop `headlineVersion`/`publishedVersion`/`ownerPreviewVersion`/`resolutionMode`/`canSubmitPromotion` for v1 (these are skill-lifecycle-specific concepts; agent versions exist but the summary list view doesn't need them per current `AgentResponse` shape and the spec's §3 Item 4 "v1 mirrors mySkillAppService.listMyStars" scoping).

- [ ] **Step 4.3: Create `AgentSummaryResponse`**

`server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/AgentSummaryResponse.java`:

```java
package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.agent.Agent;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Slimmed-down agent payload for list views (My Stars, future search). Mirrors
 * {@link SkillSummaryResponse} in spirit — drops detail-only fields like
 * headlineVersion / publishedVersion. Use {@link AgentResponse} for detail
 * paths where lifecycle context is needed.
 */
public record AgentSummaryResponse(
        Long id,
        String slug,
        String displayName,
        String description,
        String visibility,
        String status,
        Integer downloadCount,
        Integer starCount,
        BigDecimal ratingAvg,
        Integer ratingCount,
        String namespace,
        Instant updatedAt
) {
    public static AgentSummaryResponse from(Agent agent, String namespace) {
        return new AgentSummaryResponse(
                agent.getId(),
                agent.getSlug(),
                agent.getDisplayName(),
                agent.getDescription(),
                agent.getVisibility().name(),
                agent.getStatus().name(),
                agent.getDownloadCount(),
                agent.getStarCount(),
                agent.getRatingAvg(),
                agent.getRatingCount(),
                namespace,
                agent.getUpdatedAt()
        );
    }
}
```

- [ ] **Step 4.4: Create failing service test**

`server/skillhub-app/src/test/java/com/iflytek/skillhub/service/MyAgentAppServiceTest.java`. Use `MySkillAppServiceTest` as template (same module, similar shape):

```java
package com.iflytek.skillhub.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.social.AgentStar;
import com.iflytek.skillhub.domain.agent.social.AgentStarRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.dto.AgentSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

class MyAgentAppServiceTest {
    private final AgentStarRepository agentStarRepository = mock(AgentStarRepository.class);
    private final AgentRepository agentRepository = mock(AgentRepository.class);
    private final NamespaceRepository namespaceRepository = mock(NamespaceRepository.class);
    private final MyAgentAppService service = new MyAgentAppService(agentStarRepository, agentRepository, namespaceRepository);

    @Test
    void listMyAgentStars_paginatesAndOrdersByStarCreatedAt() {
        AgentStar s1 = mock(AgentStar.class);
        when(s1.getAgentId()).thenReturn(11L);
        AgentStar s2 = mock(AgentStar.class);
        when(s2.getAgentId()).thenReturn(22L);

        Page<AgentStar> starPage = new PageImpl<>(List.of(s1, s2), PageRequest.of(0, 10), 2);
        when(agentStarRepository.findByUserId(eq("user1"), any(Pageable.class))).thenReturn(starPage);

        Agent a1 = mock(Agent.class);
        when(a1.getId()).thenReturn(11L);
        when(a1.getSlug()).thenReturn("a1");
        when(a1.getDisplayName()).thenReturn("Agent One");
        when(a1.getNamespaceId()).thenReturn(100L);
        // mock getters used by AgentSummaryResponse.from — visibility, status, downloadCount, starCount, ratingAvg, ratingCount, updatedAt, description
        // ... (set sensible defaults on each)
        Agent a2 = mock(Agent.class);
        when(a2.getId()).thenReturn(22L);
        when(a2.getNamespaceId()).thenReturn(100L);
        // ... defaults

        when(agentRepository.findByIdIn(List.of(11L, 22L))).thenReturn(List.of(a1, a2));

        Namespace ns = mock(Namespace.class);
        when(ns.getId()).thenReturn(100L);
        when(ns.getSlug()).thenReturn("global");
        when(namespaceRepository.findByIdIn(List.of(100L))).thenReturn(List.of(ns));

        PageResponse<AgentSummaryResponse> result = service.listMyAgentStars("user1", 0, 10);

        assertThat(result.items()).hasSize(2);
        assertThat(result.items().get(0).id()).isEqualTo(11L); // ordering by star
        assertThat(result.items().get(1).id()).isEqualTo(22L);
        assertThat(result.total()).isEqualTo(2);
    }

    @Test
    void listMyAgentStars_handlesEmptyPage() {
        when(agentStarRepository.findByUserId(eq("user1"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        PageResponse<AgentSummaryResponse> result = service.listMyAgentStars("user1", 0, 10);

        assertThat(result.items()).isEmpty();
        assertThat(result.total()).isZero();
    }

    @Test
    void listMyAgentStars_filtersOutDeletedAgents() {
        AgentStar s1 = mock(AgentStar.class);
        when(s1.getAgentId()).thenReturn(99L);
        Page<AgentStar> starPage = new PageImpl<>(List.of(s1), PageRequest.of(0, 10), 1);
        when(agentStarRepository.findByUserId(eq("user1"), any(Pageable.class))).thenReturn(starPage);
        when(agentRepository.findByIdIn(List.of(99L))).thenReturn(List.of()); // agent gone
        when(namespaceRepository.findByIdIn(any())).thenReturn(List.of());

        PageResponse<AgentSummaryResponse> result = service.listMyAgentStars("user1", 0, 10);

        assertThat(result.items()).isEmpty();
    }
}
```

- [ ] **Step 4.5: Run test, verify it fails**

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw -DskipTests install 2>&1 | tail -3 && ./mvnw -pl skillhub-app test -Dtest=MyAgentAppServiceTest 2>&1 | tail -10
```

Expected: compile fails — `MyAgentAppService` doesn't exist.

- [ ] **Step 4.6: Implement `MyAgentAppService`**

`server/skillhub-app/src/main/java/com/iflytek/skillhub/service/MyAgentAppService.java`:

```java
package com.iflytek.skillhub.service;

import com.iflytek.skillhub.domain.agent.Agent;
import com.iflytek.skillhub.domain.agent.AgentRepository;
import com.iflytek.skillhub.domain.agent.social.AgentStar;
import com.iflytek.skillhub.domain.agent.social.AgentStarRepository;
import com.iflytek.skillhub.domain.namespace.Namespace;
import com.iflytek.skillhub.domain.namespace.NamespaceRepository;
import com.iflytek.skillhub.dto.AgentSummaryResponse;
import com.iflytek.skillhub.dto.PageResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Mirrors {@link MySkillAppService}'s listMyStars for agents.
 */
@Service
public class MyAgentAppService {

    private final AgentStarRepository agentStarRepository;
    private final AgentRepository agentRepository;
    private final NamespaceRepository namespaceRepository;

    public MyAgentAppService(AgentStarRepository agentStarRepository,
                             AgentRepository agentRepository,
                             NamespaceRepository namespaceRepository) {
        this.agentStarRepository = agentStarRepository;
        this.agentRepository = agentRepository;
        this.namespaceRepository = namespaceRepository;
    }

    public PageResponse<AgentSummaryResponse> listMyAgentStars(String userId, int page, int size) {
        Page<AgentStar> starPage = agentStarRepository.findByUserId(userId, PageRequest.of(page, size));
        List<AgentStar> stars = starPage.getContent();

        List<Long> agentIds = stars.stream().map(AgentStar::getAgentId).distinct().toList();
        Map<Long, Agent> agentsById = agentIds.isEmpty()
                ? Map.of()
                : agentRepository.findByIdIn(agentIds).stream()
                        .collect(Collectors.toMap(Agent::getId, Function.identity()));

        List<Long> namespaceIds = agentsById.values().stream()
                .map(Agent::getNamespaceId).distinct().toList();
        Map<Long, String> namespaceSlugs = namespaceIds.isEmpty()
                ? Map.of()
                : namespaceRepository.findByIdIn(namespaceIds).stream()
                        .collect(Collectors.toMap(Namespace::getId, Namespace::getSlug));

        List<AgentSummaryResponse> items = stars.stream()
                .map(star -> agentsById.get(star.getAgentId()))
                .filter(Objects::nonNull)
                .map(agent -> AgentSummaryResponse.from(agent, namespaceSlugs.get(agent.getNamespaceId())))
                .toList();

        return new PageResponse<>(items, starPage.getTotalElements(), starPage.getNumber(), starPage.getSize());
    }
}
```

If `NamespaceRepository.findByIdIn` doesn't exist, verify:

```bash
grep -n "findByIdIn" server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/NamespaceRepository.java
```

Skill side already uses it (line 41 of `JpaAdminSkillReportQueryRepository`), so it exists.

- [ ] **Step 4.7: Run service tests, verify pass**

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw -DskipTests install 2>&1 | tail -3 && ./mvnw -pl skillhub-app test -Dtest=MyAgentAppServiceTest 2>&1 | tail -10
```

Expected: all 3 pass.

- [ ] **Step 4.8: Add MeController endpoint**

Edit `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/MeController.java`. Add field + constructor wiring for `MyAgentAppService` (mirror existing `MySkillAppService` injection). Add endpoint:

```java
@GetMapping("/agent-stars")
public ApiResponse<PageResponse<AgentSummaryResponse>> listMyAgentStars(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "12") int size,
        @AuthenticationPrincipal PlatformPrincipal principal) {
    if (principal == null) {
        throw new UnauthorizedException("error.auth.required");
    }
    return ok("response.success.read", myAgentAppService.listMyAgentStars(principal.userId(), page, size));
}
```

- [ ] **Step 4.9: Add MeController test**

Edit (or create) `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/portal/MeControllerTest.java`. Add:

```java
@Test
@WithMockUser(username = "user1")
void listMyAgentStars_paginates() throws Exception {
    when(myAgentAppService.listMyAgentStars(eq("user1"), eq(0), eq(12)))
            .thenReturn(new PageResponse<>(List.of(), 0, 0, 12));

    mockMvc.perform(get("/api/web/me/agent-stars?page=0&size=12"))
            .andExpect(status().isOk());
}

@Test
void listMyAgentStars_unauthorized() throws Exception {
    mockMvc.perform(get("/api/web/me/agent-stars"))
            .andExpect(status().isUnauthorized());
}
```

If MeControllerTest doesn't exist, scaffold it from any other portal controller test (e.g., AgentControllerTest's setUp).

- [ ] **Step 4.10: Run full backend reactor**

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw clean compile 2>&1 | tail -5 && ./mvnw test 2>&1 | tail -5
```

Expected: BUILD SUCCESS, all tests pass.

### Frontend portion

- [ ] **Step 4.11: Add `meApi.getAgentStarsPage` to client.ts**

Edit `web/src/api/client.ts`. Inside `meApi` object, add (right after `getStarsPage` for symmetry):

```ts
async getAgentStarsPage(params?: { page?: number; size?: number }): Promise<{ items: AgentSummary[]; total: number; page: number; size: number }> {
  const sp = new URLSearchParams()
  sp.set('page', String(params?.page ?? 0))
  sp.set('size', String(params?.size ?? 12))
  return fetchJson(`${WEB_API_PREFIX}/me/agent-stars?${sp.toString()}`)
},
```

You may need to import `AgentSummary` from `@/api/agent-types`. Match the file's existing import style.

**Note:** the response wraps items in a server-side DTO with extra fields (`description`, `slug`, `id`, `namespace`, `ratingAvg`, etc.) that may not be on the current frontend `AgentSummary`. Verify by reading `AgentSummary` (Task 2 added `ratingAvg`/`ratingCount` already). If `id`, `slug`, `description` are missing, add them now to `AgentSummary` so this endpoint's payload types resolve cleanly. Frontend list rendering for stars uses `id` and `slug` (for navigation), so they must be on the type.

- [ ] **Step 4.12: Add `useMyAgentStarsPage` hook**

Edit `web/src/shared/hooks/use-user-queries.ts`. Add (mirror `useMyStarsPage`):

```ts
async function getMyAgentStarsPage(params: { page?: number; size?: number } = {}): Promise<PagedResponse<AgentSummary>> {
  return meApi.getAgentStarsPage(params)
}

export function useMyAgentStarsPage(params: { page?: number; size?: number } = {}, enabled = true) {
  return useQuery({
    queryKey: ['agents', 'stars', 'page', params],
    queryFn: () => getMyAgentStarsPage(params),
    enabled,
  })
}
```

Import `AgentSummary` from `@/api/agent-types`.

- [ ] **Step 4.13: Add i18n keys**

Edit `web/src/i18n/locales/en.json`, find `stars` block, add:

```json
"tabSkills": "Starred skills",
"tabAgents": "Starred agents",
"emptyAgents": "You haven't starred any agents yet."
```

Mirror in `zh.json`:

```json
"tabSkills": "收藏的技能",
"tabAgents": "收藏的智能体",
"emptyAgents": "你还没有收藏任何智能体。"
```

- [ ] **Step 4.14: Refactor stars.tsx — extract Skills panel + add Tabs**

Edit `web/src/pages/dashboard/stars.tsx`. Two-step refactor like Task 3.

**Sub-step 14a: Extract `<SkillsStarsPanel />`**

Move the entire current page body (everything below `<DashboardPageHeader />`) into a new local `SkillsStarsPanel` component. Page becomes:

```tsx
export function MyStarsPage() {
  const { t } = useTranslation()
  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader title={t('stars.title')} subtitle={t('stars.subtitle')} />
      <SkillsStarsPanel />
    </div>
  )
}

function SkillsStarsPanel() {
  // ... existing body verbatim
}
```

Run `pnpm vitest run src/pages/dashboard/stars.test.ts` to confirm refactor preserves behavior.

**Sub-step 14b: Add outer Tabs + AgentsStarsPanel**

```tsx
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/shared/ui/tabs'
import { AgentCard } from '@/features/agent/agent-card'
import { useMyAgentStarsPage } from '@/shared/hooks/use-user-queries'

export function MyStarsPage() {
  const { t } = useTranslation()
  return (
    <div className="space-y-8 animate-fade-up">
      <DashboardPageHeader title={t('stars.title')} subtitle={t('stars.subtitle')} />
      <Tabs defaultValue="SKILLS">
        <TabsList>
          <TabsTrigger value="SKILLS">{t('stars.tabSkills')}</TabsTrigger>
          <TabsTrigger value="AGENTS">{t('stars.tabAgents')}</TabsTrigger>
        </TabsList>
        <TabsContent value="SKILLS" className="mt-6">
          <SkillsStarsPanel />
        </TabsContent>
        <TabsContent value="AGENTS" className="mt-6">
          <AgentsStarsPanel />
        </TabsContent>
      </Tabs>
    </div>
  )
}

function AgentsStarsPanel() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const { data, isLoading } = useMyAgentStarsPage({ page, size: PAGE_SIZE })
  const agents = data?.items ?? []
  const totalPages = data ? Math.max(Math.ceil(data.total / data.size), 1) : 1

  if (isLoading) {
    return (
      <div className="space-y-4 animate-fade-up">
        {Array.from({ length: 3 }).map((_, i) => (
          <div key={i} className="h-32 animate-shimmer rounded-xl" />
        ))}
      </div>
    )
  }

  if (!agents || agents.length === 0) {
    return <Card className="p-12 text-center text-muted-foreground">{t('stars.emptyAgents')}</Card>
  }

  return (
    <>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
        {agents.map((agent) => (
          <AgentCard
            key={agent.id ?? agent.name}
            agent={agent}
            onClick={() => navigate({ to: `/agents/${agent.namespace}/${encodeURIComponent(agent.slug ?? agent.name)}` })}
          />
        ))}
      </div>
      {data && data.total > PAGE_SIZE ? (
        <Pagination page={page} totalPages={totalPages} onPageChange={setPage} />
      ) : null}
    </>
  )
}
```

**Important:** `AgentCard`'s `onClick` prop must accept the same agent shape `useMyAgentStarsPage` returns. Verify `AgentCard`'s props interface accepts `AgentSummary` — it should already after Task 2 ran.

- [ ] **Step 4.15: Update stars test**

Edit `web/src/pages/dashboard/stars.test.ts`. Add cases:

```tsx
it('defaults to Skills tab', () => {
  // ... assert "Starred skills" tab is active or its content is visible
})

it('switches to Agents tab and renders agent stars', async () => {
  // ... mock useMyAgentStarsPage returning [{ id: 1, name: 'a', slug: 'a', namespace: 'global', ... }]
  await user.click(screen.getByRole('tab', { name: /Starred agents/i }))
  // assert agent card for 'a' visible
})

it('shows agent-specific empty state on Agents tab', async () => {
  // ... mock useMyAgentStarsPage returning empty
  await user.click(screen.getByRole('tab', { name: /Starred agents/i }))
  expect(screen.getByText(/You haven't starred any agents yet/)).toBeInTheDocument()
})
```

Mock both `useMyStarsPage` (existing) and `useMyAgentStarsPage` (new) at the test setUp level.

- [ ] **Step 4.16: Run scoped tests**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/pages/dashboard/stars.test.ts src/shared/hooks 2>&1 | tail -10
```

Expected: all pass.

- [ ] **Step 4.17: Run full frontend suite + TS check**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -5 && pnpm tsc --noEmit 2>&1 | tail -5
```

Expected: all pass, TS clean.

- [ ] **Step 4.18: Stage + commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/AgentSummaryResponse.java server/skillhub-app/src/main/java/com/iflytek/skillhub/service/MyAgentAppService.java server/skillhub-app/src/test/java/com/iflytek/skillhub/service/MyAgentAppServiceTest.java server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/MeController.java server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/portal/MeControllerTest.java web/src/api/client.ts web/src/api/agent-types.ts web/src/shared/hooks/use-user-queries.ts web/src/pages/dashboard/stars.tsx web/src/pages/dashboard/stars.test.ts web/src/i18n/locales/en.json web/src/i18n/locales/zh.json && git commit -m "$(cat <<'EOF'
feat(api,web): My Stars page lists starred agents

Backend mirrors MySkillAppService.listMyStars exactly: new
MyAgentAppService + AgentSummaryResponse DTO, MeController endpoint
GET /api/web/me/agent-stars. Frontend wraps stars page body in outer
Skills / Agents Tabs; Agents panel uses new useMyAgentStarsPage hook
and renders AgentCard.

Spec: docs/superpowers/specs/2026-04-29-agent-followups-bundle-design.md §3 Item 4

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Final verification (run after all 4 tasks)

- [ ] **Step 5.1: Run full backend reactor cleanly**

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw clean test 2>&1 | tail -10
```

Expected: BUILD SUCCESS, no failures, no errors.

- [ ] **Step 5.2: Run full frontend suite + TS check**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -5 && pnpm tsc --noEmit 2>&1 | tail -5
```

Expected: all green.

- [ ] **Step 5.3: Verify 4 commits live**

```bash
cd /Users/lydoc/projectscoding/skillhub && git log --oneline main -6
```

Expected: 4 new commits on top of `04d1c910` (the spec commit).

- [ ] **Step 5.4: Update memo and lessons (optional but recommended)**

Append a session entry to `memo/memo.md` describing what shipped and the 4 commit SHAs. If anything diverged from the plan, add a note.

If any sub-agent / mirror audit hallucinated a path that didn't exist, add a `memo/lessons.md` entry to the existing 2026-04-28 sub-agent audit lesson.

- [ ] **Step 5.5: Push to origin/main**

Per CLAUDE.md §git策略 and §分支操作约束 #5 — `feat/*` branches merge directly to main on completion. We worked directly on main this session per `memo/memo.md` 2026-04-27 P3-2a/P2-2/P2-4 precedent (backend triple-tap shipped on main).

```bash
cd /Users/lydoc/projectscoding/skillhub && git push origin main
```

Expected: push succeeds, 4 task commits + spec commit land on `origin/main`.

---

## Self-review notes (for the implementer)

- **Per lessons.md `mvn -q compile` pitfall**: every backend task uses `./mvnw clean compile` (or `clean test`) to verify, not `mvn -q`. Stale `.class` files have eaten plans before.
- **Per lessons.md grep-before-create**: every "Create file at X" step has a Step 4.0 / 3.0-style preflight that confirms X doesn't already exist. Items not preflighted (e.g., events directory) are zero-impact creations — no `git revert` risk.
- **Per lessons.md parallel-agent index theft**: every commit uses `git add ... && git commit ... ` in a single Bash call. The window between stage and commit cannot be hijacked.
- **Per lessons.md sub-agent audit hallucination**: if you delegate any task to a sub-agent, instruct them to cite file paths + line numbers and have them run `ls`/`grep` to verify their own claims before reporting "done."

If executing via subagent-driven-development: each task above is one subagent dispatch.

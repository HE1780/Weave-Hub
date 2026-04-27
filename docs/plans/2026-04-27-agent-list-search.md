# P0-2: Agent 列表搜索 + 筛选 Implementation Plan

**ADR 0003 §1.1** (Agent management) · 估时 ~1 天 · 全栈
**Backlog entry:** [docs/plans/2026-04-27-fork-backlog.md](2026-04-27-fork-backlog.md) §P0-2

## Goal

`GET /api/web/agents` 接受 `q`/`namespace`/`visibility` 查询参数，前端 `agents.tsx` 加搜索框 + 命名空间下拉 + visibility 单选。**不**建独立的 search document 表（那是 P3-3，Agent 数据量大才有必要）。

## Brainstorming 决策（已锁定）

- **Q1=B** `q` ILIKE 字段：`display_name` + `description`（不含 slug）
- **Q2=C** 未登录传 `visibility=PRIVATE/NAMESPACE_ONLY` 时返空列表（不报错，不泄露存在性）
- **Q3=A** `namespace` 参数 = slug，slug 不存在返 404（与 `getOne` 一致）
- **Q4=B** **"我能看到的全部"语义**：默认返 PUBLIC + 调用者有权限的 PRIVATE/NAMESPACE_ONLY；权限闸门复用 `AgentVisibilityChecker.canAccess`

## Architecture

**后端策略：DB 层做 keyword + namespace 预过滤，应用层做 visibility 过滤**

DB 层不 enforce visibility 规则的原因：visibility 规则依赖调用者身份（owner / namespace role / SUPER_ADMIN），写成 SQL 会导致每个调用要传 4 个参数 + 复杂 OR 子句。`AgentVisibilityChecker` 是现成的纯函数 predicate，应用层 `.filter()` 一行解决，逻辑集中。

代价是分页：DB 返回 N 条，filter 后 ≤ N 条。**对 P0 ILIKE 阶段（数据量小）可接受**；P3-3 上 search_document + GIN 时再重做。

**前端策略：搜索 input 用 300ms debounce，参数变化触发 React Query 重新 fetch（query key 包含全部参数）**

## File structure after this plan

**Modified (backend):**
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentRepository.java` — add `searchPublic(keyword, namespaceId, pageable)` port method
- `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/AgentJpaRepository.java` — add `@Query` JPA implementation with ILIKE
- `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/service/AgentService.java` — add `searchPublic(...)` method with visibility filter (Q4=B semantics)
- `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/AgentController.java` — `listPublic` accepts `q`/`namespace`/`visibility` query params
- `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/AgentControllerTest.java` — add ≥6 test cases

**Modified (frontend):**
- `web/src/api/client.ts` — `agentsApi.list` accepts `q`/`namespace`/`visibility`
- `web/src/features/agent/use-agents.ts` — accepts params; query key includes them
- `web/src/features/agent/use-agents.test.tsx` — params propagation case
- `web/src/pages/agents.tsx` — search input + namespace `<Select>` + visibility radio
- `web/src/i18n/locales/en.json` + `zh.json` — `agents.search.*` keys

**New (frontend):**
- `web/src/features/agent/use-debounced-value.ts` (or check if exists; if so reuse)
- `web/src/pages/agents.test.tsx` — page-level test for search input → hook param propagation

## Pre-flight

- [ ] **Step 0.1: Verify branch and baseline**

```bash
cd /Users/lydoc/projectscoding/skillhub
git status   # working tree should be clean
cd server && ./mvnw test 2>&1 | tail -3   # 468/468 expected (after P3-2a + P2-2 + P2-4)
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -3   # 632/632 expected
```

If baseline is different, **stop and reconcile** with `memo/memo.md`.

- [ ] **Step 0.2: Debounce helper status** — confirmed `web/src/shared/hooks/use-debounce.ts` already exports `useDebounce<T>(value, delay=300)`. **Task 7 is skipped**; import this existing helper.

---

## Task 1: Domain port — `AgentRepository.searchPublic`

**Files:**
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentRepository.java`

Add a single new method:

```java
Page<Agent> searchPublic(String keyword, Long namespaceId, Pageable pageable);
```

**Semantics**:
- Returns `ACTIVE` agents only (not ARCHIVED — those are filtered earlier).
- `keyword` null/empty → no keyword filter.
- `namespaceId` null → no namespace filter.
- **Visibility is NOT filtered here.** Returns all `ACTIVE` agents matching keyword+namespace; service layer does the visibility filter (because it needs caller identity).

The existing `findByVisibilityAndStatus` stays as-is (used elsewhere or by tests).

- [ ] **Step 1.1: Add port method**

- [ ] **Step 1.2: Verify `AgentService` doesn't reference any new method yet** (compile will fail until Task 3; that's fine for this checkpoint)

---

## Task 2: JPA implementation

**Files:**
- Modify: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/AgentJpaRepository.java`

Add `@Query` method:

```java
@Override
@Query("""
    SELECT a FROM Agent a
    WHERE a.status = com.iflytek.skillhub.domain.agent.AgentStatus.ACTIVE
      AND (:keyword IS NULL OR :keyword = ''
           OR LOWER(a.displayName) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(a.description) LIKE LOWER(CONCAT('%', :keyword, '%')))
      AND (:namespaceId IS NULL OR a.namespaceId = :namespaceId)
    """)
Page<Agent> searchPublic(@Param("keyword") String keyword,
                         @Param("namespaceId") Long namespaceId,
                         Pageable pageable);
```

**Note on `LOWER(...) LIKE LOWER(...)` vs PostgreSQL `ILIKE`**: JPQL has no `ILIKE`. Using `LOWER()` on both sides is portable and gets the same result. If the column has a btree expression index later, PostgreSQL will use it; for P0 small data this is fine.

- [ ] **Step 2.1: Add `@Query` method matching the port signature**

- [ ] **Step 2.2: Compile sanity**

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw -pl skillhub-domain,skillhub-infra install -DskipTests -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS.

---

## Task 3: Service — `AgentService.searchPublic` with permission filter

**Files:**
- Modify: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/service/AgentService.java`

Add new method (keeps `listPublic(Pageable)` for backward compat — controller will stop calling it but no other caller exists; **leave it for now**, Task 11 will optionally remove if unused).

```java
public Page<Agent> searchPublic(String keyword,
                                Long namespaceId,
                                AgentVisibility visibilityFilter,
                                String currentUserId,
                                Map<Long, NamespaceRole> userNamespaceRoles,
                                Set<String> platformRoles) {
    Page<Agent> raw = agentRepository.searchPublic(keyword, namespaceId, pageable);
    List<Agent> filtered = raw.getContent().stream()
            .filter(a -> visibilityChecker.canAccess(a, currentUserId, userNamespaceRoles, platformRoles))
            .filter(a -> visibilityFilter == null || a.getVisibility() == visibilityFilter)
            .toList();
    // Note: pagination metadata (totalElements) is the raw page total — NOT post-filter.
    // This is acceptable for P0 because filter rejection rate is low and consistent across pages
    // (visibility only excludes a fixed set per caller). Documented as known limitation.
    return new PageImpl<>(filtered, pageable, raw.getTotalElements());
}
```

**Wait — `pageable` is missing from signature above.** Fix:

```java
public Page<Agent> searchPublic(String keyword,
                                Long namespaceId,
                                AgentVisibility visibilityFilter,
                                String currentUserId,
                                Map<Long, NamespaceRole> userNamespaceRoles,
                                Set<String> platformRoles,
                                Pageable pageable) { ... }
```

**Decision recorded inline above**: total = raw repo total (over-counts by exactly the filtered-out items per page). For P0 ILIKE this is acceptable; documented in service Javadoc and in the plan's "Known limitations" section.

- [ ] **Step 3.1: Add `searchPublic(...)` method**

- [ ] **Step 3.2: Add Javadoc explaining the post-filter pagination caveat**

- [ ] **Step 3.3: Update existing `listPublic(Pageable)` Javadoc** to mention `searchPublic` is preferred for new callers (don't delete — controller might still use as default if no params)

---

## Task 4: Controller — query params + slug-to-id resolution

**Files:**
- Modify: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/AgentController.java`

`listPublic` becomes:

```java
@GetMapping
public ApiResponse<PageResponse<AgentResponse>> listPublic(
        @RequestParam(value = "q", required = false) String q,
        @RequestParam(value = "namespace", required = false) String namespace,
        @RequestParam(value = "visibility", required = false) String visibility,
        @RequestParam(value = "page", defaultValue = "0") int page,
        @RequestParam(value = "size", defaultValue = "20") int size,
        @AuthenticationPrincipal PlatformPrincipal principal,
        @RequestAttribute(value = "userNsRoles", required = false) Map<Long, NamespaceRole> userNsRoles) {

    int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
    Long namespaceId = null;
    if (namespace != null && !namespace.isBlank()) {
        namespaceId = namespaceRepository.findBySlug(namespace)
                .orElseThrow(() -> new DomainNotFoundException("Namespace not found: " + namespace))
                .getId();
    }
    AgentVisibility visibilityEnum = parseVisibility(visibility);  // null if param absent

    Page<Agent> result = agentService.searchPublic(
            q,
            namespaceId,
            visibilityEnum,
            principal == null ? null : principal.userId(),
            rolesOrEmpty(userNsRoles),
            principal == null ? Set.of() : principal.platformRoles(),
            PageRequest.of(Math.max(page, 0), safeSize));
    // ... existing namespace-slug hydration + AgentResponse mapping unchanged
}

private AgentVisibility parseVisibility(String raw) {
    if (raw == null || raw.isBlank()) return null;
    try {
        return AgentVisibility.valueOf(raw.toUpperCase());
    } catch (IllegalArgumentException e) {
        throw new DomainBadRequestException("Invalid visibility: " + raw);
    }
}
```

**Add imports** as needed: `AgentVisibility`, `PlatformPrincipal`, `NamespaceRole`, `DomainBadRequestException`.

- [ ] **Step 4.1: Update `listPublic` signature + body**

- [ ] **Step 4.2: Add `parseVisibility` private helper** (mirrors `parseStatus` in `AgentReviewController`)

- [ ] **Step 4.3: Verify imports compile**

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw -pl skillhub-app compile 2>&1 | tail -10
```

---

## Task 5: Controller tests — ≥6 cases

**Files:**
- Modify: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/AgentControllerTest.java`

Add `@MockBean` for `NamespaceRepository` and `AgentService.searchPublic` mocking pattern. Existing `agent(...)` / `ns(...)` fixtures already in the test class.

Cases (all `@Test`, names verbatim):

1. **`list_with_no_params_returns_public_agents`** — calls `searchPublic(null, null, null, ...)`; existing happy path adapted.
2. **`list_with_q_filters_by_keyword`** — calls `searchPublic("hello", null, null, ...)`, asserts the keyword arg propagates.
3. **`list_with_namespace_slug_resolves_to_id`** — passes `?namespace=team-x`, mocks `namespaceRepository.findBySlug("team-x")`; asserts service receives `namespaceId=42L`.
4. **`list_with_unknown_namespace_returns_404`** — passes `?namespace=ghost`, `namespaceRepository.findBySlug("ghost")` returns empty.
5. **`list_with_visibility_PUBLIC_propagates_to_service`** — passes `?visibility=PUBLIC`.
6. **`list_with_invalid_visibility_returns_400`** — passes `?visibility=BANANA`.
7. **`list_anonymous_with_visibility_PRIVATE_returns_empty_list`** — passes `?visibility=PRIVATE` unauthenticated; mock `searchPublic` returns one agent but visibilityChecker filter (in service) excludes it. Since service is mocked at controller test level, this test really verifies that **`visibility=PRIVATE` is forwarded to service**; the actual filter happens in service test (Task 6).

Cases 7's note implies we also need a service-level test. Add Task 6 below.

- [ ] **Step 5.1: Add 6 cases to `AgentControllerTest`**

- [ ] **Step 5.2: Run**

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw -pl skillhub-app test -Dtest=AgentControllerTest 2>&1 | tail -10
```

Expected: 5 (existing) + 6 (new) = **11 tests passing**.

---

## Task 6: Service-level test — visibility filter logic

**Files:**
- Find existing: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/agent/service/AgentServiceTest.java` (or create if missing — check first)
- If missing, mirror `AgentPublishServiceTest` test infra

```bash
ls /Users/lydoc/projectscoding/skillhub/server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/agent/service/
```

If `AgentServiceTest.java` exists, add 4 cases:

1. **`searchPublic_returns_only_PUBLIC_for_anonymous_caller`** — agent visibility mix; anonymous → only PUBLIC items returned.
2. **`searchPublic_returns_PRIVATE_owned_by_caller`** — owner sees own PRIVATE.
3. **`searchPublic_returns_NAMESPACE_ONLY_for_namespace_member`** — caller has role in namespace.
4. **`searchPublic_visibility_filter_PRIVATE_only_returns_PRIVATE_subset`** — caller passes `visibilityFilter=PRIVATE`, gets only PRIVATE items they have access to.

Mock `AgentRepository.searchPublic` to return a fixed list with mixed visibilities; assert filter result.

- [ ] **Step 6.1: Add 4 service-level test cases**

- [ ] **Step 6.2: Run service tests**

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw -pl skillhub-domain test -Dtest=AgentServiceTest 2>&1 | tail -10
```

---

## Task 7: Frontend — `useDebouncedValue` helper (or reuse)

**Files:**
- New: `web/src/shared/hooks/use-debounced-value.ts` (if no existing)

```ts
import { useEffect, useState } from 'react'

/** Returns `value` after `delayMs` of stability. Use for search inputs. */
export function useDebouncedValue<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(id)
  }, [value, delayMs])
  return debounced
}
```

- [ ] **Step 7.1: grep for existing helper** (Pre-flight 0.2 already did this; if found, skip)

- [ ] **Step 7.2: Create file if needed**

- [ ] **Step 7.3: Add `web/src/shared/hooks/use-debounced-value.test.ts`**

```ts
// @vitest-environment jsdom
import { describe, it, expect, vi } from 'vitest'
import { renderHook, act } from '@testing-library/react'
import { useDebouncedValue } from './use-debounced-value'

describe('useDebouncedValue', () => {
  it('returns initial value immediately', () => { ... })
  it('updates to new value after delay', async () => { vi.useFakeTimers(); ... })
  it('cancels pending update on rapid changes', async () => { ... })
})
```

3 tests, vi.useFakeTimers for deterministic timing.

---

## Task 8: Frontend — `agentsApi.list` extends params

**Files:**
- Modify: `web/src/api/client.ts`

```ts
async list(params: {
  page?: number
  size?: number
  q?: string
  namespace?: string
  visibility?: 'PUBLIC' | 'PRIVATE' | 'NAMESPACE_ONLY'
} = {}) {
  const search = new URLSearchParams()
  if (params.page !== undefined) search.set('page', String(params.page))
  if (params.size !== undefined) search.set('size', String(params.size))
  if (params.q) search.set('q', params.q)
  if (params.namespace) search.set('namespace', params.namespace)
  if (params.visibility) search.set('visibility', params.visibility)
  const qs = search.toString()
  const suffix = qs ? `?${qs}` : ''
  return fetchJson<PagedResponse<AgentDto>>(`${WEB_API_PREFIX}/agents${suffix}`)
}
```

- [ ] **Step 8.1: Update params type and body**

---

## Task 9: Frontend — `useAgents` accepts params

**Files:**
- Modify: `web/src/features/agent/use-agents.ts`
- Modify: `web/src/features/agent/use-agents.test.tsx`

```ts
export interface UseAgentsParams {
  q?: string
  namespace?: string
  visibility?: 'PUBLIC' | 'PRIVATE' | 'NAMESPACE_ONLY'
}

export function useAgents(params: UseAgentsParams = {}): UseQueryResult<AgentSummary[]> {
  return useQuery({
    queryKey: ['agents', params],   // params object hash forms cache key
    queryFn: async () => {
      const page = await agentsApi.list({ page: 0, size: 50, ...params })
      return page.items.map(toSummary)
    },
  })
}
```

Test additions (mirror existing structure):

1. **`forwards q/namespace/visibility to agentsApi.list`** — call with all three; assert `listMock` received them.
2. **`uses different cache keys for different params`** — render hook twice with different params; assert two distinct `queryFn` calls (use the existing `createWrapper` helper).

- [ ] **Step 9.1: Update hook signature**

- [ ] **Step 9.2: Add 2 test cases**

- [ ] **Step 9.3: Verify existing hook tests still pass**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/features/agent/use-agents.test.tsx 2>&1 | tail -5
```

---

## Task 10: Frontend — `agents.tsx` UI

**Files:**
- Modify: `web/src/pages/agents.tsx`
- New: `web/src/pages/agents.test.tsx`
- Modify: `web/src/i18n/locales/en.json`
- Modify: `web/src/i18n/locales/zh.json`

UI structure inserted between hero and list grid:

```tsx
<div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 mb-6 flex flex-col gap-3 md:flex-row md:items-center">
  <Input
    placeholder={t('agents.search.placeholder')}
    value={rawQ}
    onChange={(e) => setRawQ(e.target.value)}
    className="md:flex-1"
  />
  <Select value={namespace ?? '__all__'} onValueChange={(v) => setNamespace(v === '__all__' ? undefined : v)}>
    <SelectTrigger className="md:w-48"><SelectValue placeholder={t('agents.search.allNamespaces')} /></SelectTrigger>
    <SelectContent>
      <SelectItem value="__all__">{t('agents.search.allNamespaces')}</SelectItem>
      {myNamespaces.map(ns => <SelectItem key={ns.id} value={ns.slug}>{ns.displayName}</SelectItem>)}
    </SelectContent>
  </Select>
  {user && (
    <Select value={visibility ?? '__all__'} onValueChange={(v) => setVisibility(v === '__all__' ? undefined : v as VisibilityFilter)}>
      <SelectTrigger className="md:w-40"><SelectValue placeholder={t('agents.search.allVisibility')} /></SelectTrigger>
      <SelectContent>
        <SelectItem value="__all__">{t('agents.search.allVisibility')}</SelectItem>
        <SelectItem value="PUBLIC">{t('agents.search.visibilityPublic')}</SelectItem>
        <SelectItem value="NAMESPACE_ONLY">{t('agents.search.visibilityNamespace')}</SelectItem>
        <SelectItem value="PRIVATE">{t('agents.search.visibilityPrivate')}</SelectItem>
      </SelectContent>
    </Select>
  )}
</div>
```

State:
```tsx
const [rawQ, setRawQ] = useState('')
const debouncedQ = useDebouncedValue(rawQ, 300)
const [namespace, setNamespace] = useState<string | undefined>(undefined)
const [visibility, setVisibility] = useState<VisibilityFilter | undefined>(undefined)
const { data: agents, ... } = useAgents({ q: debouncedQ || undefined, namespace, visibility })
```

i18n keys (en + zh):
- `agents.search.placeholder` / "Search agents…" / "搜索智能体…"
- `agents.search.allNamespaces` / "All namespaces" / "全部命名空间"
- `agents.search.allVisibility` / "All visibility" / "全部可见性"
- `agents.search.visibilityPublic` / "Public" / "公开"
- `agents.search.visibilityNamespace` / "Namespace only" / "仅命名空间"
- `agents.search.visibilityPrivate` / "Private" / "私有"

Page test (`agents.test.tsx`) — mock `useAgents`, verify:
1. typing in search input updates the hook param after debounce delay (use `vi.useFakeTimers`)
2. selecting a namespace updates the hook param immediately
3. visibility selector only renders when `useAuth().user` is non-null

- [ ] **Step 10.1: Create `agents.test.tsx`** (3 cases)

- [ ] **Step 10.2: Add i18n keys (en + zh)**

- [ ] **Step 10.3: Update `agents.tsx`** (add state + UI elements)

- [ ] **Step 10.4: Verify**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/pages/agents.test.tsx src/features/agent/use-agents.test.tsx 2>&1 | tail -8
```

---

## Task 11: Final verification + memo + backlog

- [ ] **Step 11.1: Backend full suite**

```bash
cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw test 2>&1 | grep -E "Tests run: [0-9]+, Failures: 0, Errors: 0, Skipped" | tail -1
```

Expected: `Tests run: 470, ...` (460 + ~10 new). Zero failures.

- [ ] **Step 11.2: Web full suite**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -3
```

Expected: `Tests  643 passed (643)` (635 + ~8 new). Zero failures.

- [ ] **Step 11.3: Typecheck**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm tsc --noEmit 2>&1 | grep -v "registry-skill.tsx" | tail -5
```

Expected: no errors except pre-existing `registry-skill.tsx`.

- [ ] **Step 11.4: Update `memo/memo.md`** with session summary (commits, test counts, divergences from this plan).

- [ ] **Step 11.5: Update `docs/plans/2026-04-27-fork-backlog.md`**:
- Mark P0-2 as ✅ 已完成 with commit range and test delta.

- [ ] **Step 11.6: Commits split**

Suggested split (keep PRs reviewable):
1. `feat(api): AgentRepository.searchPublic + AgentService.searchPublic` (Tasks 1–3, +6 service tests)
2. `feat(api): wire q/namespace/visibility query params on /api/web/agents` (Task 4–5, +6 controller tests)
3. `feat(web): useDebouncedValue helper` (Task 7)
4. `feat(web): agentsApi.list + useAgents accept search params` (Task 8–9)
5. `feat(web): agent list page search input + filters` (Task 10)
6. `docs(memo+backlog): close P0-2`

---

## Known limitations (documented up front)

1. **Pagination total over-counts**: service post-filters by visibility, so `Page.totalElements` reflects raw repo total, not filtered total. Frontend pagination UI may show e.g. "Page 1 of 3 / 50 results" while only 35 are visible. Acceptable for P0; revisit when data volume justifies search_document table (P3-3).
2. **`namespace` parameter requires existing namespace slug**: invalid slug → 404. Could be silently empty list instead, but 404 matches existing `getOne` and helps users debug typos.
3. **Visibility filter is OR with default visibility set**: if caller passes `visibility=PRIVATE`, results include only PRIVATE items the caller can see (per `AgentVisibilityChecker`). If caller passes nothing, results include PUBLIC + caller-accessible PRIVATE + caller-accessible NAMESPACE_ONLY (Q4=B "everything I can see" semantics).
4. **No ranking / no highlight**: ILIKE returns un-ranked results; default order is `created_at DESC`. P3-3 (search_document with `ts_rank`) will improve.
5. **No autocomplete**: search box is plain `<Input>`; debounce is 300ms.

## Rollback

All backend changes are additive (new service method, new controller params with defaults). Frontend changes are additive UI elements. Reverting the 6 commits in this plan removes the feature without leaving DB migrations or schema cruft.

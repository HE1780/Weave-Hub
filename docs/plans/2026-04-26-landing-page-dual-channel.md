# Landing Page — Dual-Channel Redesign (Skills + Agents)

> ✅ **SHIPPED — 已完成 2026-04-26。** 7 commits `f01dcccc` → `f5b58d7c` 全部 in `main`。**注意:** 本方案被 P0-1b WeaveHub Landing IA 重写部分取代(LandingChannelsSection / PopularAgents 已删除)。详见 [docs/plans/2026-04-29-spec-status-ledger.md](2026-04-29-spec-status-ledger.md)。

**Plan date:** 2026-04-26
**Status:** Ready for execution
**Source design:** `docs/landing-page-redesign.md`
**Branch:** Work directly on `main` (small UI change, fully tested per task).

## Decisions locked

- **Layout strategy:** C — Hero stays simple (search + CTAs + stats); a new "Channels" block sits between Stats and Features as the dual-channel introduction.
- **Agents data:** mock-backed `useAgents` (already shipped; 6 agents). Showing top 3 on the home page.
- **Quick Start:** No change — existing `LandingQuickStartSection` already has the two-tab structure (`agent` / `human`) we want.
- **Unified search Tabs:** Out of scope (deferred until backend has agent search).
- **Color overhaul:** Out of scope. Differentiate Skills vs Agents via icons + labels only; keep current brand gradient.

## Scope (5 tasks)

| # | Change | Touch |
|---|---|---|
| 1 | Add `landing.channels.*` and `landing.stats.agents` i18n keys (en + zh) | `web/src/i18n/locales/{en,zh}.json` |
| 2 | New `<ChannelsSection>` component (dual-channel intro card) | `web/src/shared/components/landing-channels.tsx` (+ test) |
| 3 | Hero CTAs: add "Browse Agents" button between "Explore Skills" and "Publish" | `web/src/pages/landing.tsx` |
| 4 | Stats: 3 → 4 items (Skills / Agents / Downloads / Teams) | `web/src/pages/landing.tsx` |
| 5 | Add a `<PopularAgents>` section after `Popular Skills`; both render side-by-side on lg+, stacked on mobile | `web/src/pages/landing.tsx` (+ small new component) |

Out of scope:
- Splitting the Features card into 3 thematic groups (deferred — current 6 features still apply to both channels; rewriting them is bigger than copy).
- Replacing brand-gradient buttons with channel-specific colors.

## Pre-flight

- All work happens on `main` (currently `0831cf5b`). No new branch.
- Each task is an independent commit; if any one breaks, revert just that commit.
- After every task: `pnpm vitest run` (web) must stay green.

---

## Task 1: i18n keys

**Files:**
- Modify: `web/src/i18n/locales/en.json`
- Modify: `web/src/i18n/locales/zh.json`

**Step 1 — Add `agents` to `landing.stats`:**

`en.json` → `landing.stats`:
```json
"agents": "Agents"
```

`zh.json` → `landing.stats`:
```json
"agents": "智能体"
```

**Step 2 — Add `landing.channels` block:**

`en.json`:
```json
"channels": {
  "title": "Two ways to compose AI capabilities",
  "subtitle": "Skills are the building blocks. Agents are the assembled solutions. Use them together or alone.",
  "skill": {
    "label": "Skills",
    "tagline": "Capability units",
    "description": "Single-purpose, reusable skill packages — prompts, data tools, code helpers, and more.",
    "cta": "Browse Skills"
  },
  "agent": {
    "label": "Agents",
    "tagline": "Smart configurations",
    "description": "Pre-assembled agents that combine multiple skills into ready-to-use workflows.",
    "cta": "Browse Agents"
  }
}
```

`zh.json`:
```json
"channels": {
  "title": "两种组合 AI 能力的方式",
  "subtitle": "Skills 是基础积木，Agents 是组装好的方案。可独立使用，也可协同使用。",
  "skill": {
    "label": "Skills",
    "tagline": "能力单元",
    "description": "单一功能、可复用的技能包 —— 提示词、数据工具、代码助手等。",
    "cta": "浏览技能包"
  },
  "agent": {
    "label": "Agents",
    "tagline": "智能配置",
    "description": "组合多个技能的预制智能体，开箱即用。",
    "cta": "浏览智能体"
  }
}
```

**Step 3 — Add `landing.popularAgents.*`:**

`en.json`:
```json
"popularAgents": {
  "title": "Popular Agents",
  "description": "Pre-configured agents combining multiple skills.",
  "viewAll": "View all agents"
}
```

`zh.json`:
```json
"popularAgents": {
  "title": "热门智能体",
  "description": "组合多个技能的预制智能体。",
  "viewAll": "查看全部"
}
```

**Step 4 — Hero browse-agents CTA:**

`en.json` → `landing.hero`:
```json
"browseAgents": "Browse Agents"
```

`zh.json` → `landing.hero`:
```json
"browseAgents": "浏览智能体"
```

**Step 5 — Verify:**
```bash
cd web && node -e "JSON.parse(require('fs').readFileSync('src/i18n/locales/en.json'));JSON.parse(require('fs').readFileSync('src/i18n/locales/zh.json'));console.log('OK')"
pnpm vitest run src/i18n/
```

**Commit:**
```
feat(i18n): add landing.channels, popularAgents, hero.browseAgents, stats.agents keys
```

---

## Task 2: `<ChannelsSection>` component

**Files:**
- Create: `web/src/shared/components/landing-channels.tsx`
- Create: `web/src/shared/components/landing-channels.test.tsx`

**Component contract:**
- Two side-by-side cards (lg+) / stacked (mobile).
- Left card: Skills (icon `PackageOpen`, links to `/skills`).
- Right card: Agents (icon `Bot`, links to `/agents`).
- Each card: tagline, description, CTA button.
- Header above: title + subtitle.
- No animation logic (let parent's `scroll-fade-up` wrapper handle it).

**Test (4 cases):**
1. Renders both channel labels (`landing.channels.skill.label`, `landing.channels.agent.label`).
2. Skills card has `to="/skills"` link.
3. Agents card has `to="/agents"` link.
4. Renders the section title and subtitle keys.

**Use the project's existing test patterns:**
- `vi.mock('react-i18next', ...)` returning key as-is.
- `vi.mock('@tanstack/react-router', () => ({ Link: ({ children, to }) => <a href={to}>{children}</a> }))`.
- `afterEach(cleanup)`.

**Verify:**
```bash
cd web && pnpm vitest run src/shared/components/landing-channels.test.tsx
```

**Commit:**
```
feat(web): add LandingChannelsSection (dual-channel intro)
```

---

## Task 3: Hero CTA — add "Browse Agents"

**Files:**
- Modify: `web/src/pages/landing.tsx`

**Step 1:** Insert a new `<Link>` between the existing `exploreSkills` and `publishSkill` links inside the `flex flex-wrap justify-center gap-4 mb-14` div. Use the same brand-gradient style as `exploreSkills` but with `bg-secondary` background to differentiate (matches existing `publishSkill` button style — re-use, not invent).

```tsx
<Link
  to="/agents"
  className="px-8 py-3.5 rounded-xl text-base font-medium border transition-colors"
  style={{
    background: 'hsl(var(--secondary))',
    borderColor: 'hsl(var(--muted-foreground))',
    color: 'hsl(var(--muted-foreground))',
  }}
>
  {t('landing.hero.browseAgents')}
</Link>
```

**Step 2:** Update `landing.test.tsx` if needed (it currently mocks `Link` to render only children — so the new link is invisible to the test; no change required, but re-run to confirm).

**Verify:**
```bash
cd web && pnpm vitest run src/pages/landing.test.tsx
pnpm typecheck
```

**Commit:**
```
feat(web): add Browse Agents CTA to landing hero
```

---

## Task 4: Stats — 3 → 4 items (add Agents count)

**Files:**
- Modify: `web/src/pages/landing.tsx`

**Step 1:** Update the `stats` array in `LandingPage`:

```tsx
const stats = [
  { value: '1000+', label: t('landing.stats.skills') },
  { value: '50+', label: t('landing.stats.agents') },
  { value: '50K+', label: t('landing.stats.downloads') },
  { value: '200+', label: t('landing.stats.teams') },
]
```

(Choose `50+` for the agents number — round, plausible for a private platform; matches the marketing-copy style of the other figures, which are also static.)

**Step 2:** Adjust the row's `gap-16 md:gap-24` → `gap-10 md:gap-16` so 4 columns still fit at 768px without overflow.

**Verify:**
```bash
cd web && pnpm vitest run src/pages/landing.test.tsx
```

**Commit:**
```
feat(web): add Agents count to landing stats row
```

---

## Task 5: Popular Agents section

**Files:**
- Create: `web/src/shared/components/popular-agents.tsx`
- Create: `web/src/shared/components/popular-agents.test.tsx`
- Modify: `web/src/pages/landing.tsx`

**Step 1 — Component contract** (`popular-agents.tsx`):
- Reads `useAgents()` (already exists, mock-backed).
- Renders top 3 agents using `AgentCard` (already exists).
- Section header: title + description + "View all" button → `/agents`.
- Same scroll-fade-up wrapper pattern as `Popular Skills`.
- Loading: render 3 skeleton items via `SkeletonList`.
- Empty: render nothing (the `useAgents` mock guarantees data).

**Step 2 — Test (3 cases):**
1. Renders `landing.popularAgents.title`.
2. With 6 agents in mock data, renders exactly 3 cards.
3. Renders the "View all" button with `landing.popularAgents.viewAll` key.

Mock `@/features/agent/use-agents` to return a fixed 6-agent array in test.

**Step 3 — Wire into landing.tsx:**
- Import `<PopularAgents>` at top.
- Place it directly after the existing `Popular Downloads Section` and before the `Latest Releases Section`.

**Verify:**
```bash
cd web && pnpm vitest run src/shared/components/popular-agents.test.tsx
pnpm vitest run src/pages/landing.test.tsx
pnpm vitest run    # full suite — must remain green
pnpm typecheck     # only registry-skill.tsx errors should remain (pre-existing)
```

**Commit:**
```
feat(web): add Popular Agents section to landing page
```

---

## Task 6: ChannelsSection wired into landing.tsx

**Files:**
- Modify: `web/src/pages/landing.tsx`

**Step 1:** Add `import { LandingChannelsSection } from '@/shared/components/landing-channels'`.

**Step 2:** Place it directly after the closing `</main>` of the Hero section, before the existing `Features Section`. Wrap in the same `useInView` + `scroll-fade-up` pattern.

```tsx
const channelsView = useInView()
// ...
<div ref={channelsView.ref} className={`scroll-fade-up${channelsView.inView ? ' in-view' : ''}`}>
  <LandingChannelsSection />
</div>
```

**Verify:**
```bash
cd web && pnpm vitest run src/pages/landing.test.tsx
pnpm vitest run    # full suite
pnpm typecheck
```

**Commit:**
```
feat(web): wire LandingChannelsSection into landing page
```

---

## Task 7: Final verification + visual check

**Step 1:** Web full test run — must stay 598/598 (or 598+N where N = new tests).
**Step 2:** Backend full test run — must stay 432/432.
**Step 3:** Browser smoke test on http://localhost:3000 (already running):
- Hero shows 3 CTAs: Explore Skills, Browse Agents, Publish
- Stats row shows 4 items including Agents count
- Channels section appears below Hero, two cards side-by-side
- Popular Agents section appears between Popular Skills and Latest Releases
- Both Skills and Agents cards link to their respective pages
- Switch en ↔ zh; new strings render correctly
**Step 4:** Update `memo/memo.md` with what shipped.
**Step 5:** Push to `origin/main`.

---

## Plan complete

Total: 6 implementation tasks + 1 verification = 7 commits expected.

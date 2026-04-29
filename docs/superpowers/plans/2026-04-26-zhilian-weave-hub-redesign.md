# 知联 Weave Hub Redesign Implementation Plan

> ✅ **SHIPPED — 已完成 2026-04-27。** 拆为 P0-1a (tokens) + P0-1b (landing IA) 两份子计划落地;`agents.tsx` 全量页面 + LandingHotSection 混排已替代原 spec 描述的 "Agent column stub"。详见 [docs/plans/2026-04-29-spec-status-ledger.md](../../plans/2026-04-29-spec-status-ledger.md)。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebrand UI to 知联 / Weave Hub, redesign home page with parallel Skill+Agent channels (green/orange), fix the broken `/agents` route, and replace the Agent stub with a real channel list page — all additively, no backend changes.

**Architecture:** Frontend-only refactor. Two new CSS tokens for channel colors, three new shared components (`AgentCard`, `ChannelBadge`, `DualChannelRow`), and a `type?: 'SKILL'|'AGENT'` extension point added to `useSearchSkills`. Server may ignore the new param; Agent column degrades gracefully to an empty state.

**Tech Stack:** React 19, TypeScript, Vite, Tailwind CSS, TanStack Router/Query, Vitest, react-i18next, lucide-react.

**Spec:** [docs/superpowers/specs/2026-04-26-zhilian-weave-hub-redesign-design.md](../specs/2026-04-26-zhilian-weave-hub-redesign-design.md)

**Working directory:** All paths relative to repo root `/Users/lydoc/projectscoding/skillhub`. All `pnpm` commands run from `web/`.

---

## Conventions Used Throughout

This codebase follows these patterns (verified in existing code) — match them, do not impose external patterns:

- **Tests are minimal**: pure-function tests use vitest; component tests usually only assert the named export exists with all dependencies mocked. See [web/src/app/layout.test.ts](../../../web/src/app/layout.test.ts) for the canonical example. Don't write full DOM-rendering tests for new components — match the existing style.
- **No TDD ceremony**: write the file, then a small companion test, then commit. Skip the red→green dance for new presentational components.
- **i18n keys**: dotted hierarchy in `en.json` / `zh.json`. Both must be updated together.
- **Inline styles for theme tokens**: the codebase uses `style={{ color: 'hsl(var(--text-secondary))' }}` extensively — match it; do not introduce a styling system.
- **Commit messages**: Conventional commits (`feat:`, `fix:`, `style:`, `chore:`). Look at `git log --oneline -20` for tone.

---

## Task 1: Add channel CSS tokens and flip brand gradient

Establishes the green/orange palette and replaces the purple→violet brand gradient with green→orange. All later visual work consumes these tokens.

**Files:**
- Modify: [web/src/index.css](../../../web/src/index.css)

- [ ] **Step 1: Add Skill/Agent tokens to `:root` and `.dark`**

In [web/src/index.css](../../../web/src/index.css), inside the `:root` block (after the `--border-card` line, around line 50), add:

```css
    /* ─── Channel palette (知联 dual channels) ─── */
    /* Skill channel — green, tools/growth */
    --skill-accent: 152 60% 36%;
    --skill-accent-soft: 152 60% 95%;
    --skill-accent-fg: 152 80% 20%;
    /* Agent channel — orange, intelligence/vitality */
    --agent-accent: 24 90% 50%;
    --agent-accent-soft: 24 95% 96%;
    --agent-accent-fg: 24 85% 32%;
```

In the `.dark` block (after the `--glow-accent` line, around line 76), add the same tokens with darker-mode-friendly lightness values:

```css
    --skill-accent: 152 55% 55%;
    --skill-accent-soft: 152 30% 18%;
    --skill-accent-fg: 152 60% 80%;
    --agent-accent: 24 85% 60%;
    --agent-accent-soft: 24 40% 18%;
    --agent-accent-fg: 24 80% 80%;
```

- [ ] **Step 2: Flip the brand gradient from purple to green→orange**

In `:root`, replace the existing brand variables (currently lines 40-42):

```css
    --brand-start: #6A6DFF;
    --brand-end: #B85EFF;
    --brand-gradient: linear-gradient(135deg, #6A6DFF 0%, #B85EFF 100%);
```

With:

```css
    --brand-start: #1F9D5C;   /* hsl(152 60% 36%) — Skill green */
    --brand-end: #F08A1F;     /* hsl(24 90% 50%) — Agent orange */
    --brand-gradient: linear-gradient(135deg, #1F9D5C 0%, #F08A1F 100%);
```

- [ ] **Step 3: Update the dark-mode `.text-gradient-hero` override**

Around line 256, the `.dark .text-gradient-hero` rule hardcodes the old purple. Replace:

```css
.dark .text-gradient-hero {
  background: linear-gradient(135deg, #8183FF, #C77EFF);
```

With:

```css
.dark .text-gradient-hero {
  background: linear-gradient(135deg, #2BB871, #F89E3D);
```

- [ ] **Step 4: Update upload-zone hover tint and feature-icon shadow that hardcoded purple**

Around line 461, replace `background: rgba(106, 109, 255, 0.04);` with `background: rgba(31, 157, 92, 0.05);`.

Around line 476, replace `box-shadow: 0 14px 30px rgba(106, 109, 255, 0.45);` with `box-shadow: 0 14px 30px rgba(31, 157, 92, 0.35);`.

- [ ] **Step 5: Verify the build still compiles**

Run from `web/`:
```bash
pnpm typecheck
```
Expected: no errors (this step only changed CSS, but typecheck catches any side-effect import issues).

- [ ] **Step 6: Commit**

```bash
git add web/src/index.css
git commit -m "style: introduce knonglian (Weave Hub) green/orange brand palette"
```

---

## Task 2: Add `type` parameter to `SearchParams` and the search URL builder

The Agent column will reuse `useSearchSkills` with `type: 'AGENT'`. The backend may ignore the param today; this task only sets up the contract on the frontend so consumer code in later tasks compiles.

**Files:**
- Modify: [web/src/api/types.ts](../../../web/src/api/types.ts) (around line 271)
- Modify: [web/src/shared/hooks/skill-query-helpers.ts](../../../web/src/shared/hooks/skill-query-helpers.ts)
- Modify: [web/src/shared/hooks/skill-query-helpers.test.ts](../../../web/src/shared/hooks/skill-query-helpers.test.ts) (extend existing tests)

- [ ] **Step 1: Extend `SearchParams` type**

In [web/src/api/types.ts](../../../web/src/api/types.ts), modify the `SearchParams` interface (currently lines 271-279):

```ts
export interface SearchParams {
  q?: string
  namespace?: string
  label?: string
  sort?: string
  page?: number
  size?: number
  starredOnly?: boolean
  type?: 'SKILL' | 'AGENT'
}
```

Also add an optional `type` field to `SkillSummary` (currently lines 143-161), inserted after `resolutionMode`:

```ts
  resolutionMode?: string
  type?: 'SKILL' | 'AGENT'
}
```

- [ ] **Step 2: Forward `type` in the URL builder**

In [web/src/shared/hooks/skill-query-helpers.ts](../../../web/src/shared/hooks/skill-query-helpers.ts), inside `buildSkillSearchUrl`, add a `type` clause after the existing `if (params.size !== undefined)` block (around line 33):

```ts
  if (params.type) {
    queryParams.append('type', params.type)
  }
```

Final function should look like:

```ts
export function buildSkillSearchUrl(params: SearchParams) {
  const queryParams = new URLSearchParams()
  const normalizedQuery = normalizeSearchQuery(params.q ?? '')

  if (params.q !== undefined) {
    queryParams.append('q', normalizedQuery)
  }
  if (params.namespace) {
    const cleanNamespace = params.namespace.startsWith('@') ? params.namespace.slice(1) : params.namespace
    queryParams.append('namespace', cleanNamespace)
  }
  if (params.label) {
    queryParams.append('label', params.label)
  }
  if (params.sort) {
    queryParams.append('sort', params.sort)
  }
  if (params.page !== undefined) {
    queryParams.append('page', String(params.page))
  }
  if (params.size !== undefined) {
    queryParams.append('size', String(params.size))
  }
  if (params.type) {
    queryParams.append('type', params.type)
  }

  const queryString = queryParams.toString()
  return queryString ? `${WEB_API_PREFIX}/skills?${queryString}` : `${WEB_API_PREFIX}/skills`
}
```

- [ ] **Step 3: Add a test case for the new `type` param**

Open [web/src/shared/hooks/skill-query-helpers.test.ts](../../../web/src/shared/hooks/skill-query-helpers.test.ts). Inside the existing `describe('buildSkillSearchUrl', () => { ... })` block, add a new `it(...)` case (just before the closing `})` of the describe):

```ts
  it('includes type when provided', () => {
    expect(buildSkillSearchUrl({ type: 'AGENT' })).toBe('/api/web/skills?q=&type=AGENT')
  })

  it('omits type when not provided', () => {
    expect(buildSkillSearchUrl({ sort: 'newest' })).toBe('/api/web/skills?q=&sort=newest')
  })
```

> Note: the existing tests show `q=` always appears because `params.q !== undefined` is checked, but `normalizeSearchQuery('')` returns `''`. Match that behavior — read the existing test file for the exact format other tests assert.

- [ ] **Step 4: Run the test**

```bash
cd web && pnpm test -- skill-query-helpers
```
Expected: all tests pass, including the two new cases.

- [ ] **Step 5: Commit**

```bash
git add web/src/api/types.ts web/src/shared/hooks/skill-query-helpers.ts web/src/shared/hooks/skill-query-helpers.test.ts
git commit -m "feat(search): add optional type param to skill search query"
```

---

## Task 3: Fix `/agents` route registration

`agentsRoute` is declared but never added to `routeTree.addChildren(...)`. Today, the nav link 404s. This must be fixed before any Agent UI work matters.

**Files:**
- Modify: [web/src/app/router.tsx](../../../web/src/app/router.tsx) (around line 422-458)

- [ ] **Step 1: Add `agentsRoute` to the `addChildren` call**

In [web/src/app/router.tsx](../../../web/src/app/router.tsx), locate `const routeTree = rootRoute.addChildren([` (around line 422). The array currently lists `landingRoute`, `skillsRoute`, ... `registrySkillRoute`. Insert `agentsRoute` between `skillDetailRoute` and `dashboardRoute` (so the order matches navigation order):

```ts
const routeTree = rootRoute.addChildren([
  landingRoute,
  skillsRoute,
  loginRoute,
  registerRoute,
  resetPasswordRoute,
  privacyRoute,
  searchRoute,
  termsRoute,
  namespaceRoute,
  skillDetailRoute,
  agentsRoute,        // ← ADD THIS LINE
  dashboardRoute,
  // ... rest unchanged
```

> The existing line 433 already has `agentsRoute,` in some local checkouts due to in-progress work. **Verify it's actually missing first** by checking if `agentsRoute` appears between `skillDetailRoute` and `dashboardRoute` in the list. If it's already there, skip to Step 3.

- [ ] **Step 2: Typecheck**

```bash
cd web && pnpm typecheck
```
Expected: no errors. (`agentsRoute` was already declared, so this is a one-line addition.)

- [ ] **Step 3: Smoke test in dev server**

```bash
cd web && pnpm dev
```

Open `http://localhost:5173/agents` (or whatever port Vite reports). Expected: the existing stub `AgentsPage` renders (the 🚧 "under construction" page). Stop the dev server with Ctrl-C.

- [ ] **Step 4: Commit**

```bash
git add web/src/app/router.tsx
git commit -m "fix(router): register agentsRoute in route tree so /agents resolves"
```

---

## Task 4: Create `ChannelBadge` shared component

Tiny pill component reused in cards and channel headers. Green for Skill, orange for Agent.

**Files:**
- Create: [web/src/shared/components/channel-badge.tsx](../../../web/src/shared/components/channel-badge.tsx)
- Create: [web/src/shared/components/channel-badge.test.ts](../../../web/src/shared/components/channel-badge.test.ts)

- [ ] **Step 1: Write the component**

Create [web/src/shared/components/channel-badge.tsx](../../../web/src/shared/components/channel-badge.tsx):

```tsx
import { Wrench, Bot } from 'lucide-react'

export type Channel = 'SKILL' | 'AGENT'

interface ChannelBadgeProps {
  channel: Channel
  /** Hide icon and only render the label text. Defaults to false. */
  iconOnly?: boolean
  /** Optional label override (e.g. translated string). */
  label?: string
}

const CHANNEL_STYLES: Record<Channel, { bg: string; fg: string; defaultLabel: string }> = {
  SKILL: {
    bg: 'hsl(var(--skill-accent-soft))',
    fg: 'hsl(var(--skill-accent-fg))',
    defaultLabel: 'Skill',
  },
  AGENT: {
    bg: 'hsl(var(--agent-accent-soft))',
    fg: 'hsl(var(--agent-accent-fg))',
    defaultLabel: 'Agent',
  },
}

export function ChannelBadge({ channel, iconOnly = false, label }: ChannelBadgeProps) {
  const styles = CHANNEL_STYLES[channel]
  const Icon = channel === 'SKILL' ? Wrench : Bot
  const text = label ?? styles.defaultLabel

  return (
    <span
      className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-medium"
      style={{ background: styles.bg, color: styles.fg }}
    >
      <Icon className="w-3 h-3" />
      {!iconOnly && <span>{text}</span>}
    </span>
  )
}
```

- [ ] **Step 2: Write the test**

Create [web/src/shared/components/channel-badge.test.ts](../../../web/src/shared/components/channel-badge.test.ts):

```ts
import { describe, expect, it } from 'vitest'
import { ChannelBadge } from './channel-badge'

describe('ChannelBadge', () => {
  it('exports a named ChannelBadge component function', () => {
    expect(typeof ChannelBadge).toBe('function')
    expect(ChannelBadge.name).toBe('ChannelBadge')
  })
})
```

- [ ] **Step 3: Run the test**

```bash
cd web && pnpm test -- channel-badge
```
Expected: 1 test, passes.

- [ ] **Step 4: Commit**

```bash
git add web/src/shared/components/channel-badge.tsx web/src/shared/components/channel-badge.test.ts
git commit -m "feat(ui): add ChannelBadge component for Skill/Agent identification"
```

---

## Task 5: Create `AgentCard` (parallel to `SkillCard`, orange accent)

`AgentCard` accepts the same `SkillSummary` shape (since Agent reuses the Skill table per spec) and displays an orange accent bar plus the channel badge.

**Files:**
- Create: [web/src/features/agent/agent-card.tsx](../../../web/src/features/agent/agent-card.tsx)
- Create: [web/src/features/agent/agent-card.test.ts](../../../web/src/features/agent/agent-card.test.ts)

- [ ] **Step 1: Write the component**

Create [web/src/features/agent/agent-card.tsx](../../../web/src/features/agent/agent-card.tsx). Match the structure of [web/src/features/skill/skill-card.tsx](../../../web/src/features/skill/skill-card.tsx) with these differences: top accent stripe in agent orange, ChannelBadge with `channel="AGENT"`, otherwise identical (downloads / stars / version):

```tsx
import type { SkillSummary } from '@/api/types'
import { useAuth } from '@/features/auth/use-auth'
import { useStar } from '@/features/social/use-star'
import { Card } from '@/shared/ui/card'
import { NamespaceBadge } from '@/shared/components/namespace-badge'
import { ChannelBadge } from '@/shared/components/channel-badge'
import { getHeadlineVersion } from '@/shared/lib/skill-lifecycle'
import { formatCompactCount } from '@/shared/lib/number-format'
import { Bookmark } from 'lucide-react'

interface AgentCardProps {
  agent: SkillSummary
  onClick?: () => void
  highlightStarred?: boolean
}

/**
 * Card for displaying one Agent (a Skill row with type=AGENT).
 *
 * Visually parallel to SkillCard but with orange accent and Agent channel badge.
 */
export function AgentCard({ agent, onClick, highlightStarred = true }: AgentCardProps) {
  const { isAuthenticated } = useAuth()
  const { data: starStatus } = useStar(agent.id, highlightStarred && isAuthenticated)
  const showStarredHighlight = highlightStarred && isAuthenticated && starStatus?.starred
  const headlineVersion = getHeadlineVersion(agent)
  const isInteractive = typeof onClick === 'function'

  return (
    <Card
      className="h-full p-5 cursor-pointer group relative overflow-hidden bg-white border shadow-sm transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-offset-2"
      style={{
        borderColor: 'hsl(var(--border-card))',
      }}
      onClick={onClick}
      onKeyDown={(event) => {
        if (!isInteractive) return
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onClick()
        }
      }}
      role={isInteractive ? 'link' : undefined}
      tabIndex={isInteractive ? 0 : undefined}
    >
      {/* Orange accent stripe */}
      <div
        className="absolute top-0 left-0 right-0 h-1"
        style={{ background: 'hsl(var(--agent-accent))' }}
      />

      <div className="flex h-full flex-col">
        <div className="flex items-start justify-between mb-3">
          <div className="space-y-2">
            <div className="flex items-center gap-2">
              <ChannelBadge channel="AGENT" />
            </div>
            <h3
              className="font-semibold text-lg transition-colors"
              style={{ color: 'hsl(var(--foreground))' }}
            >
              {agent.displayName}
            </h3>
          </div>
          <div className="flex items-center gap-2">
            <NamespaceBadge type="TEAM" name={`@${agent.namespace}`} />
          </div>
        </div>

        {agent.summary && (
          <p className="text-sm text-muted-foreground mb-4 line-clamp-2 leading-relaxed">
            {agent.summary}
          </p>
        )}

        <div className="mt-auto flex items-center gap-4 text-xs text-muted-foreground">
          {headlineVersion && (
            <span className="px-2.5 py-1 rounded-full bg-secondary/60 font-mono">
              v{headlineVersion.version}
            </span>
          )}
          <span className="flex items-center gap-1">
            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M7 16a4 4 0 01-.88-7.903A5 5 0 1115.9 6L16 6a5 5 0 011 9.9M9 19l3 3m0 0l3-3m-3 3V10" />
            </svg>
            {formatCompactCount(agent.downloadCount)}
          </span>
          <span
            className={`flex items-center gap-1 ${showStarredHighlight ? 'font-semibold' : ''}`}
            style={showStarredHighlight ? { color: 'hsl(var(--agent-accent))' } : undefined}
          >
            <Bookmark className={`w-3.5 h-3.5 ${showStarredHighlight ? 'fill-current' : ''}`} />
            {agent.starCount}
          </span>
          {agent.ratingAvg !== undefined && agent.ratingCount > 0 && (
            <span className="flex items-center gap-1">
              <svg className="w-3.5 h-3.5" fill="currentColor" viewBox="0 0 20 20" style={{ color: 'hsl(var(--agent-accent))' }}>
                <path d="M9.049 2.927c.3-.921 1.603-.921 1.902 0l1.07 3.292a1 1 0 00.95.69h3.462c.969 0 1.371 1.24.588 1.81l-2.8 2.034a1 1 0 00-.364 1.118l1.07 3.292c.3.921-.755 1.688-1.54 1.118l-2.8-2.034a1 1 0 00-1.175 0l-2.8 2.034c-.784.57-1.838-.197-1.539-1.118l1.07-3.292a1 1 0 00-.364-1.118L2.98 8.72c-.783-.57-.38-1.81.588-1.81h3.461a1 1 0 00.951-.69l1.07-3.292z" />
              </svg>
              {agent.ratingAvg.toFixed(1)}
            </span>
          )}
        </div>
      </div>
    </Card>
  )
}
```

- [ ] **Step 2: Write the test**

Create [web/src/features/agent/agent-card.test.ts](../../../web/src/features/agent/agent-card.test.ts):

```ts
import { describe, expect, it, vi } from 'vitest'

vi.mock('@/features/auth/use-auth', () => ({
  useAuth: () => ({ isAuthenticated: false }),
}))
vi.mock('@/features/social/use-star', () => ({
  useStar: () => ({ data: undefined }),
}))
vi.mock('@/shared/lib/skill-lifecycle', () => ({
  getHeadlineVersion: () => undefined,
}))
vi.mock('@/shared/lib/number-format', () => ({
  formatCompactCount: (n: number) => String(n),
}))
vi.mock('@/shared/ui/card', () => ({
  Card: ({ children }: { children: unknown }) => children,
}))
vi.mock('@/shared/components/namespace-badge', () => ({
  NamespaceBadge: () => null,
}))
vi.mock('@/shared/components/channel-badge', () => ({
  ChannelBadge: () => null,
}))

import { AgentCard } from './agent-card'

describe('AgentCard', () => {
  it('exports a named AgentCard component function', () => {
    expect(typeof AgentCard).toBe('function')
    expect(AgentCard.name).toBe('AgentCard')
  })
})
```

- [ ] **Step 3: Run the test**

```bash
cd web && pnpm test -- agent-card
```
Expected: 1 test, passes.

- [ ] **Step 4: Commit**

```bash
git add web/src/features/agent/agent-card.tsx web/src/features/agent/agent-card.test.ts
git commit -m "feat(agent): add AgentCard component parallel to SkillCard"
```

---

## Task 6: Create `DualChannelRow` shared layout component

Generic two-column "Skill | Agent" section. Used twice on the home page (Popular and Latest).

**Files:**
- Create: [web/src/shared/components/dual-channel-row.tsx](../../../web/src/shared/components/dual-channel-row.tsx)
- Create: [web/src/shared/components/dual-channel-row.test.ts](../../../web/src/shared/components/dual-channel-row.test.ts)

- [ ] **Step 1: Write the component**

Create [web/src/shared/components/dual-channel-row.tsx](../../../web/src/shared/components/dual-channel-row.tsx):

```tsx
import type { ReactNode } from 'react'

interface DualChannelRowProps {
  /** Heading shown above both columns. */
  title: string
  /** Optional subtitle. */
  description?: string
  /** Skill column content (cards or skeletons). */
  skillColumn: ReactNode
  /** Agent column content (cards, skeletons, or empty state). */
  agentColumn: ReactNode
  /** Skill column header text (e.g. "Skills"). */
  skillLabel: string
  /** Agent column header text (e.g. "Agents"). */
  agentLabel: string
  /** Action shown next to Skill column header (usually a "View all" link). */
  skillAction?: ReactNode
  /** Action shown next to Agent column header. */
  agentAction?: ReactNode
}

/**
 * Section that places Skill and Agent channels side-by-side at md+ widths,
 * stacking vertically on smaller screens. Each column gets its own header
 * with channel-tinted accent so the boundary is visually obvious.
 */
export function DualChannelRow({
  title,
  description,
  skillColumn,
  agentColumn,
  skillLabel,
  agentLabel,
  skillAction,
  agentAction,
}: DualChannelRowProps) {
  return (
    <section className="space-y-6 animate-fade-up">
      <div>
        <h2 className="text-3xl font-bold tracking-tight mb-2" style={{ color: 'hsl(var(--foreground))' }}>
          {title}
        </h2>
        {description && (
          <p style={{ color: 'hsl(var(--text-secondary))' }}>{description}</p>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        {/* Skill column */}
        <div className="space-y-4">
          <div
            className="flex items-center justify-between border-l-4 pl-3"
            style={{ borderColor: 'hsl(var(--skill-accent))' }}
          >
            <h3 className="text-lg font-semibold" style={{ color: 'hsl(var(--skill-accent-fg))' }}>
              {skillLabel}
            </h3>
            {skillAction}
          </div>
          {skillColumn}
        </div>

        {/* Agent column */}
        <div className="space-y-4">
          <div
            className="flex items-center justify-between border-l-4 pl-3"
            style={{ borderColor: 'hsl(var(--agent-accent))' }}
          >
            <h3 className="text-lg font-semibold" style={{ color: 'hsl(var(--agent-accent-fg))' }}>
              {agentLabel}
            </h3>
            {agentAction}
          </div>
          {agentColumn}
        </div>
      </div>
    </section>
  )
}
```

- [ ] **Step 2: Write the test**

Create [web/src/shared/components/dual-channel-row.test.ts](../../../web/src/shared/components/dual-channel-row.test.ts):

```ts
import { describe, expect, it } from 'vitest'
import { DualChannelRow } from './dual-channel-row'

describe('DualChannelRow', () => {
  it('exports a named DualChannelRow component function', () => {
    expect(typeof DualChannelRow).toBe('function')
    expect(DualChannelRow.name).toBe('DualChannelRow')
  })
})
```

- [ ] **Step 3: Run the test**

```bash
cd web && pnpm test -- dual-channel-row
```
Expected: 1 test, passes.

- [ ] **Step 4: Commit**

```bash
git add web/src/shared/components/dual-channel-row.tsx web/src/shared/components/dual-channel-row.test.ts
git commit -m "feat(ui): add DualChannelRow layout for Skill+Agent home sections"
```

---

## Task 7: Add i18n keys for the redesign

All new copy goes through i18n. Both `en.json` and `zh.json` must be updated together to keep keys in sync.

**Files:**
- Modify: [web/src/i18n/locales/en.json](../../../web/src/i18n/locales/en.json)
- Modify: [web/src/i18n/locales/zh.json](../../../web/src/i18n/locales/zh.json)

- [ ] **Step 1: Add `brand.*` keys at the top of `en.json`**

Open [web/src/i18n/locales/en.json](../../../web/src/i18n/locales/en.json). Insert a new top-level `brand` block at the very start of the JSON object (right after the opening `{`, before `"nav"`):

```json
  "brand": {
    "name": "Weave Hub",
    "nameZh": "知联",
    "tagline": "Where Skills Weave into Agents"
  },
```

- [ ] **Step 2: Add channel labels to `nav` and a new `channels` block in `en.json`**

Inside the existing `"nav"` block, add `"skills"` after `"home"`:

```json
  "nav": {
    "landing": "Home",
    "home": "Skill Center",
    "skills": "Skills",
    "search": "Search",
    "agents": "Agents",
    ...
```

(The `nav.home` key currently maps to "Skill Center" and is wired to `/skills`. We're keeping that unchanged for backwards compatibility but adding `nav.skills` for use in the new layout. The new nav will use `nav.skills` for the `/skills` link.)

After the `nav` block closes, add a new top-level `channels` block:

```json
  "channels": {
    "skill": {
      "label": "Skills",
      "tagline": "Reusable capability packages"
    },
    "agent": {
      "label": "Agents",
      "tagline": "Compositions of skills, ready to run"
    }
  },
```

- [ ] **Step 3: Extend `home.*` with dual-channel section copy in `en.json`**

In the existing `"home"` block (around line 136), add new keys (keep existing keys; just append):

```json
    "popularAgentsTitle": "Popular Agents",
    "latestAgentsTitle": "Latest Agents",
    "agentColumnEmpty": "No agents published yet",
    "agentColumnEmptyHint": "Be the first to publish an agent — or browse skills to see the building blocks.",
    "browseAgents": "Browse Agents"
```

- [ ] **Step 4: Add an `agents.*` block in `en.json` for the channel page**

After the `home` block closes, add:

```json
  "agents": {
    "title": "Agents",
    "subtitle": "Compositions of skills, ready to run",
    "searchPlaceholder": "Search agents...",
    "emptyTitle": "No agents yet",
    "emptyDescription": "The Agent channel is being prepared. While you wait, browse the Skills that future Agents will be built from.",
    "exploreSkillsCta": "Browse Skills"
  },
```

- [ ] **Step 5: Mirror everything in `zh.json`**

Apply the parallel changes to [web/src/i18n/locales/zh.json](../../../web/src/i18n/locales/zh.json):

```json
  "brand": {
    "name": "Weave Hub",
    "nameZh": "知联",
    "tagline": "知道你能做什么，连接你想做的事"
  },
```

In the `nav` block, add `"skills": "技能"` between `"home"` and `"search"`.

Add the `channels` block:

```json
  "channels": {
    "skill": {
      "label": "技能",
      "tagline": "可复用的能力组件"
    },
    "agent": {
      "label": "智能体",
      "tagline": "由技能组合而成、开箱即用"
    }
  },
```

In the `home` block, add:

```json
    "popularAgentsTitle": "热门智能体",
    "latestAgentsTitle": "最新智能体",
    "agentColumnEmpty": "暂无已发布的智能体",
    "agentColumnEmptyHint": "成为第一位发布智能体的人——或浏览技能，看看构成智能体的基础组件。",
    "browseAgents": "浏览智能体"
```

After the `home` block, add:

```json
  "agents": {
    "title": "智能体",
    "subtitle": "由技能组合而成、开箱即用",
    "searchPlaceholder": "搜索智能体...",
    "emptyTitle": "暂无智能体",
    "emptyDescription": "智能体频道筹备中。在此期间，可以先浏览技能——未来的智能体正是由它们组合而成。",
    "exploreSkillsCta": "浏览技能"
  },
```

- [ ] **Step 6: Validate JSON syntax for both files**

```bash
cd web && node -e "JSON.parse(require('fs').readFileSync('src/i18n/locales/en.json'))" && node -e "JSON.parse(require('fs').readFileSync('src/i18n/locales/zh.json'))" && echo OK
```
Expected: prints `OK`. If a `SyntaxError` appears, fix the misplaced comma or brace it points to.

- [ ] **Step 7: Typecheck**

```bash
cd web && pnpm typecheck
```
Expected: no errors.

- [ ] **Step 8: Commit**

```bash
git add web/src/i18n/locales/en.json web/src/i18n/locales/zh.json
git commit -m "i18n: add brand, channel, and dual-section keys for Weave Hub redesign"
```

---

## Task 8: Refactor home page to dual-channel layout

Replace the existing single-column Popular and Latest sections with two `DualChannelRow`s, each backed by two `useSearchSkills` calls (one per channel type).

**Files:**
- Modify: [web/src/pages/home.tsx](../../../web/src/pages/home.tsx)

- [ ] **Step 1: Replace the entire `home.tsx` content**

Overwrite [web/src/pages/home.tsx](../../../web/src/pages/home.tsx) with:

```tsx
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { SearchBar } from '@/features/search/search-bar'
import { SkillCard } from '@/features/skill/skill-card'
import { AgentCard } from '@/features/agent/agent-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { QuickStartSection } from '@/shared/components/quick-start'
import { DualChannelRow } from '@/shared/components/dual-channel-row'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { normalizeSearchQuery } from '@/shared/lib/search-query'
import { Button } from '@/shared/ui/button'

export function HomePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const { data: popularSkills, isLoading: isLoadingPopularSkills } = useSearchSkills({
    sort: 'downloads',
    size: 3,
    type: 'SKILL',
  })
  const { data: popularAgents, isLoading: isLoadingPopularAgents } = useSearchSkills({
    sort: 'downloads',
    size: 3,
    type: 'AGENT',
  })
  const { data: latestSkills, isLoading: isLoadingLatestSkills } = useSearchSkills({
    sort: 'newest',
    size: 3,
    type: 'SKILL',
  })
  const { data: latestAgents, isLoading: isLoadingLatestAgents } = useSearchSkills({
    sort: 'newest',
    size: 3,
    type: 'AGENT',
  })

  const handleSearch = (query: string) => {
    navigate({
      to: '/search',
      search: { q: normalizeSearchQuery(query), sort: 'relevance', page: 0, starredOnly: false },
    })
  }

  const handleSkillClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${encodeURIComponent(slug)}` })
  }

  const renderSkillGrid = (
    skills: typeof popularSkills,
    isLoading: boolean,
  ) => {
    if (isLoading) return <SkeletonList count={3} />
    return (
      <div className="grid grid-cols-1 gap-4">
        {skills?.items.map((skill) => (
          <SkillCard
            key={skill.id}
            skill={skill}
            onClick={() => handleSkillClick(skill.namespace, skill.slug)}
          />
        ))}
      </div>
    )
  }

  const renderAgentGrid = (
    agents: typeof popularAgents,
    isLoading: boolean,
  ) => {
    if (isLoading) return <SkeletonList count={3} />
    if (!agents?.items.length) {
      return (
        <div
          className="rounded-xl border border-dashed p-8 text-center"
          style={{ borderColor: 'hsl(var(--agent-accent))', background: 'hsl(var(--agent-accent-soft))' }}
        >
          <p className="text-sm font-medium mb-2" style={{ color: 'hsl(var(--agent-accent-fg))' }}>
            {t('home.agentColumnEmpty')}
          </p>
          <p className="text-xs" style={{ color: 'hsl(var(--text-secondary))' }}>
            {t('home.agentColumnEmptyHint')}
          </p>
        </div>
      )
    }
    return (
      <div className="grid grid-cols-1 gap-4">
        {agents.items.map((agent) => (
          <AgentCard
            key={agent.id}
            agent={agent}
            onClick={() => handleSkillClick(agent.namespace, agent.slug)}
          />
        ))}
      </div>
    )
  }

  const viewAllSkillsAction = (sort: 'downloads' | 'newest') => (
    <Button
      variant="ghost"
      size="sm"
      onClick={() => navigate({ to: '/search', search: { q: '', sort, page: 0, starredOnly: false } })}
    >
      {t('home.viewAll')}
    </Button>
  )

  const viewAllAgentsAction = () => (
    <Button variant="ghost" size="sm" onClick={() => navigate({ to: '/agents' })}>
      {t('home.viewAll')}
    </Button>
  )

  return (
    <div className="space-y-20">
      {/* Hero Section */}
      <div className="text-center space-y-8 py-16 animate-fade-up">
        <div className="space-y-4">
          <h1 className="text-6xl md:text-7xl lg:text-8xl font-bold text-brand-gradient leading-tight">
            {t('brand.nameZh')}
          </h1>
          <p className="text-2xl md:text-3xl font-medium" style={{ color: 'hsl(var(--text-secondary))' }}>
            {t('brand.name')}
          </p>
          <p className="text-lg md:text-xl max-w-2xl mx-auto" style={{ color: 'hsl(var(--text-secondary))' }}>
            {t('brand.tagline')}
          </p>
        </div>

        <div className="max-w-2xl mx-auto animate-fade-up delay-1">
          <SearchBar onSearch={handleSearch} />
        </div>

        <div className="flex flex-wrap items-center justify-center gap-4 animate-fade-up delay-2">
          <button
            className="px-6 py-3 rounded-xl text-base font-medium text-white shadow-sm hover:opacity-95 transition-opacity"
            style={{ background: 'hsl(var(--skill-accent))' }}
            onClick={() => navigate({ to: '/search', search: { q: '', sort: 'relevance', page: 0, starredOnly: false } })}
          >
            {t('home.browseSkills')}
          </button>
          <button
            className="px-6 py-3 rounded-xl text-base font-medium text-white shadow-sm hover:opacity-95 transition-opacity"
            style={{ background: 'hsl(var(--agent-accent))' }}
            onClick={() => navigate({ to: '/agents' })}
          >
            {t('home.browseAgents')}
          </button>
          <button
            className="px-6 py-3 rounded-xl text-base font-medium border transition-colors"
            style={{ borderColor: 'hsl(var(--muted-foreground))', color: 'hsl(var(--muted-foreground))' }}
            onClick={() => navigate({ to: '/dashboard/publish' })}
          >
            {t('home.publishSkill')}
          </button>
        </div>
      </div>

      {/* Popular dual-channel row */}
      <DualChannelRow
        title={t('home.popularTitle')}
        description={t('home.popularDescription')}
        skillLabel={t('channels.skill.label')}
        agentLabel={t('channels.agent.label')}
        skillAction={viewAllSkillsAction('downloads')}
        agentAction={viewAllAgentsAction()}
        skillColumn={renderSkillGrid(popularSkills, isLoadingPopularSkills)}
        agentColumn={renderAgentGrid(popularAgents, isLoadingPopularAgents)}
      />

      {/* Latest dual-channel row */}
      <DualChannelRow
        title={t('home.latestTitle')}
        description={t('home.latestDescription')}
        skillLabel={t('channels.skill.label')}
        agentLabel={t('channels.agent.label')}
        skillAction={viewAllSkillsAction('newest')}
        agentAction={viewAllAgentsAction()}
        skillColumn={renderSkillGrid(latestSkills, isLoadingLatestSkills)}
        agentColumn={renderAgentGrid(latestAgents, isLoadingLatestAgents)}
      />

      {/* Quick Start Section */}
      <QuickStartSection ns="home" />
    </div>
  )
}
```

- [ ] **Step 2: Run home page tests and typecheck**

```bash
cd web && pnpm test -- home && pnpm typecheck
```
Expected: existing `home.test.tsx` still passes (it likely just asserts the export). Typecheck clean.

> If `home.test.tsx` fails because it deeply renders and asserts old copy, update only the brittle assertions to match new keys (e.g. asserting `t('home.popularTitle')` instead of literal "Popular Downloads"). Do not weaken assertions that test routing or behavior.

- [ ] **Step 3: Smoke test in dev**

```bash
cd web && pnpm dev
```

Open `http://localhost:5173/skills`. Expected:
- Hero shows "知联" giant title in green→orange gradient, "Weave Hub" subtitle, "Where Skills Weave into Agents" tagline
- Three CTA buttons: green "Browse Skills", orange "Browse Agents", outlined "Publish Skill"
- Two sections below — Popular and Latest — each with a Skill column (3 cards) and Agent column (probably empty-state because backend doesn't filter `type=AGENT` yet)

Stop dev with Ctrl-C.

- [ ] **Step 4: Commit**

```bash
git add web/src/pages/home.tsx
git commit -m "feat(home): dual-channel Skill+Agent layout with Weave Hub branding"
```

---

## Task 9: Replace Agent stub with a real channel list page

The page mirrors what `/skills` (HomePage) does but constrained to `type='AGENT'` and with an orange channel hero. Empty state is the most likely render until backend lands.

**Files:**
- Modify: [web/src/pages/agents.tsx](../../../web/src/pages/agents.tsx)

- [ ] **Step 1: Overwrite `agents.tsx`**

Replace the entire content of [web/src/pages/agents.tsx](../../../web/src/pages/agents.tsx) with:

```tsx
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Bot } from 'lucide-react'
import { AgentCard } from '@/features/agent/agent-card'
import { SkeletonList } from '@/shared/components/skeleton-loader'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'

/**
 * Agents channel page — parallel to /skills (HomePage), restricted to type=AGENT.
 *
 * Until the backend honors the `type` filter, this page will most likely
 * render the empty state, which is the intended graceful degradation.
 */
export function AgentsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const { data: agents, isLoading } = useSearchSkills({
    sort: 'newest',
    size: 24,
    type: 'AGENT',
  })

  const handleAgentClick = (namespace: string, slug: string) => {
    navigate({ to: `/space/${namespace}/${encodeURIComponent(slug)}` })
  }

  return (
    <div className="space-y-12">
      {/* Channel hero */}
      <section
        className="rounded-2xl px-8 py-12 md:py-16 text-center animate-fade-up"
        style={{
          background: 'linear-gradient(135deg, hsl(var(--agent-accent-soft)) 0%, hsl(var(--background)) 100%)',
        }}
      >
        <div
          className="inline-flex items-center justify-center w-16 h-16 rounded-2xl mb-4 shadow-sm"
          style={{ background: 'hsl(var(--agent-accent))' }}
        >
          <Bot className="w-8 h-8 text-white" />
        </div>
        <h1 className="text-4xl md:text-5xl font-bold mb-3" style={{ color: 'hsl(var(--foreground))' }}>
          {t('agents.title')}
        </h1>
        <p className="text-lg max-w-2xl mx-auto" style={{ color: 'hsl(var(--text-secondary))' }}>
          {t('agents.subtitle')}
        </p>
      </section>

      {/* Content */}
      {isLoading ? (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          <SkeletonList count={6} />
        </div>
      ) : !agents?.items.length ? (
        <section
          className="rounded-2xl border border-dashed p-12 text-center max-w-xl mx-auto"
          style={{ borderColor: 'hsl(var(--agent-accent))', background: 'hsl(var(--agent-accent-soft))' }}
        >
          <h2 className="text-xl font-semibold mb-2" style={{ color: 'hsl(var(--agent-accent-fg))' }}>
            {t('agents.emptyTitle')}
          </h2>
          <p className="text-sm mb-6" style={{ color: 'hsl(var(--text-secondary))' }}>
            {t('agents.emptyDescription')}
          </p>
          <button
            className="px-6 py-3 rounded-xl text-base font-medium text-white shadow-sm hover:opacity-95 transition-opacity"
            style={{ background: 'hsl(var(--skill-accent))' }}
            onClick={() => navigate({ to: '/search', search: { q: '', sort: 'relevance', page: 0, starredOnly: false } })}
          >
            {t('agents.exploreSkillsCta')}
          </button>
        </section>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {agents.items.map((agent, idx) => (
            <div key={agent.id} className={`animate-fade-up delay-${Math.min(idx + 1, 6)}`}>
              <AgentCard
                agent={agent}
                onClick={() => handleAgentClick(agent.namespace, agent.slug)}
              />
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
```

- [ ] **Step 2: Typecheck**

```bash
cd web && pnpm typecheck
```
Expected: clean.

- [ ] **Step 3: Smoke test**

```bash
cd web && pnpm dev
```

Open `http://localhost:5173/agents`. Expected:
- Orange channel hero with `Bot` icon, "Agents" / "智能体" title, tagline
- Empty state card (assuming backend ignores `type=AGENT` and returns 0 with the filter, OR returns all skills which still renders cards — either is acceptable)
- Empty-state CTA "Browse Skills" navigates to `/search`

Stop dev with Ctrl-C.

- [ ] **Step 4: Commit**

```bash
git add web/src/pages/agents.tsx
git commit -m "feat(agents): replace stub with real channel list page"
```

---

## Task 10: Reorder navigation and refresh the layout shell

Final task: rebrand the header logo, reorder nav items, soften the decorative orb, and add a mobile hamburger.

**Files:**
- Modify: [web/src/app/layout.tsx](../../../web/src/app/layout.tsx)

- [ ] **Step 1: Update the navItems list and use new i18n keys**

In [web/src/app/layout.tsx](../../../web/src/app/layout.tsx), replace the `navItems` array (currently lines 43-55) with:

```ts
  const navItems: Array<{
    label: string
    to: string
    exact?: boolean
    auth?: boolean
  }> = [
    { label: t('nav.landing'), to: '/', exact: true },
    { label: t('nav.skills'), to: '/skills' },
    { label: t('nav.agents'), to: '/agents' },
    { label: t('nav.search'), to: '/search' },
    { label: t('nav.publish'), to: '/dashboard/publish', auth: true },
    { label: t('nav.dashboard'), to: '/dashboard', auth: true },
  ]
```

(Drops `nav.mySkills` from the top bar — it remains accessible via Dashboard. Reorders so channels come before search.)

- [ ] **Step 2: Soften the decorative orb**

Around line 67-72, the orb gradient currently uses purple. Replace its `style.background` with a warmer, lower-opacity blend:

```tsx
      <div
        className="absolute top-0 right-0 w-[600px] h-[500px] rounded-full opacity-90 pointer-events-none z-0"
        style={{
          background: 'radial-gradient(ellipse at 70% 20%, hsl(var(--agent-accent) / 0.10) 0%, hsl(var(--skill-accent) / 0.08) 40%, transparent 70%)',
          filter: 'blur(60px)',
        }}
      />
```

- [ ] **Step 3: Update the SkillHub logo to "知联 Weave Hub"**

Around line 76-78, the header brand link currently reads:

```tsx
        <Link to="/" className="text-xl font-semibold tracking-tight text-brand-gradient">
          SkillHub
        </Link>
```

Replace with:

```tsx
        <Link to="/" className="flex items-baseline gap-2">
          <span className="text-xl font-semibold tracking-tight text-brand-gradient">
            {t('brand.nameZh')}
          </span>
          <span className="hidden sm:inline text-xs font-medium" style={{ color: 'hsl(var(--text-secondary))' }}>
            {t('brand.name')}
          </span>
        </Link>
```

- [ ] **Step 4: Update the footer logo brand text**

Around line 141-145, the footer brand block reads:

```tsx
                <div className="w-9 h-9 rounded-lg flex items-center justify-center text-white text-sm font-bold shadow-sm bg-brand-gradient">
                  S
                </div>
                <span className="text-lg font-bold text-brand-gradient">SkillHub</span>
```

Replace with:

```tsx
                <div className="w-9 h-9 rounded-lg flex items-center justify-center text-white text-sm font-bold shadow-sm bg-brand-gradient">
                  W
                </div>
                <span className="text-lg font-bold text-brand-gradient">{t('brand.nameZh')} · {t('brand.name')}</span>
```

- [ ] **Step 5: Add mobile hamburger menu**

The current header uses `hidden md:flex` for the nav, leaving mobile users with no navigation. Add a state-driven hamburger.

At the top of the `Layout` function, add a state hook (immediately after the existing `useState(false)` for `isHeaderElevated`, around line 26):

```tsx
  const [isMobileNavOpen, setIsMobileNavOpen] = useState(false)
```

You will also need to import `Menu` and `X` from `lucide-react`. Add them to the existing imports at the top:

```tsx
import { Menu, X } from 'lucide-react'
```

Inside the `<header>` (right after the closing `</nav>` tag for the desktop nav, around line 99), add a mobile toggle button visible only below `md`:

```tsx
        <button
          type="button"
          aria-label="Toggle navigation"
          className="md:hidden p-2 rounded-lg hover:bg-muted/50 transition-colors"
          onClick={() => setIsMobileNavOpen((open) => !open)}
        >
          {isMobileNavOpen ? <Menu className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
        </button>
```

Wait — we need both icons. Replace the children of the button with a conditional:

```tsx
        <button
          type="button"
          aria-label="Toggle navigation"
          className="md:hidden p-2 rounded-lg hover:bg-muted/50 transition-colors"
          onClick={() => setIsMobileNavOpen((open) => !open)}
        >
          {isMobileNavOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
        </button>
```

Then, immediately after the closing `</header>` tag, before `<main>`, add the mobile drawer:

```tsx
      {isMobileNavOpen && (
        <div
          className="md:hidden border-b px-6 py-4 space-y-3 relative z-10"
          style={{ borderColor: 'hsl(var(--border))', background: 'hsl(var(--background))' }}
        >
          {navItems.map((item) => {
            if (item.auth && !user) return null
            const active = isActive(item.to, item.exact)
            return (
              <Link
                key={item.to}
                to={item.to}
                onClick={() => setIsMobileNavOpen(false)}
                className={`block px-3 py-2 rounded-lg text-[15px] ${
                  active
                    ? 'bg-brand-gradient text-white shadow-sm'
                    : 'hover:bg-muted/50 transition-colors'
                }`}
                style={!active ? { color: 'hsl(var(--text-secondary))' } : undefined}
              >
                {item.label}
              </Link>
            )
          })}
        </div>
      )}
```

- [ ] **Step 6: Run all tests**

```bash
cd web && pnpm test
```
Expected: all tests pass. The existing [web/src/app/layout.test.ts](../../../web/src/app/layout.test.ts) only verifies the named export and mocks dependencies, so adding state hooks won't break it.

- [ ] **Step 7: Run typecheck**

```bash
cd web && pnpm typecheck
```
Expected: clean.

- [ ] **Step 8: Manual end-to-end smoke**

```bash
cd web && pnpm dev
```

Verify in the browser at `http://localhost:5173`:

1. **Landing page** (`/`): unchanged behavior, but header reads "知联 Weave Hub" with green→orange gradient
2. **`/skills`**: dual-channel home page renders (Skill column with cards, Agent column likely empty state)
3. **`/agents`**: orange channel hero, empty state CTA links back to `/search`
4. **Nav order**: Home, Skills, Agents, Search, (Publish, Dashboard if logged in)
5. **Resize browser to <768px**: hamburger button appears, click → drawer opens with same items, click an item → drawer closes and route changes
6. **Switch language EN ↔ ZH** (existing `LanguageSwitcher`): all new copy translates correctly; brand name shows "知联" in both modes (it's a single key)
7. **No purple anywhere** except possibly in a low-opacity decorative orb; everything that identifies a channel is green or orange

Stop dev with Ctrl-C.

- [ ] **Step 9: Commit**

```bash
git add web/src/app/layout.tsx
git commit -m "feat(layout): rebrand header to Weave Hub, reorder nav, add mobile drawer"
```

---

## Task 11: Final verification pass

A clean run through everything to make sure the integration is healthy.

- [ ] **Step 1: Run the full test suite**

```bash
cd web && pnpm test
```
Expected: all tests pass.

- [ ] **Step 2: Run typecheck**

```bash
cd web && pnpm typecheck
```
Expected: no errors.

- [ ] **Step 3: Run lint**

```bash
cd web && pnpm lint
```
Expected: no errors. (Lint is configured with `--max-warnings 0`.)

- [ ] **Step 4: Build for production**

```bash
cd web && pnpm build
```
Expected: build succeeds. Check the output for any warnings about unused imports introduced during the refactor.

- [ ] **Step 5: Spot-check the routes one final time**

```bash
cd web && pnpm dev
```

Walk through these URLs and confirm each renders without errors (open browser devtools and watch the Console):

- `/` — landing
- `/skills` — new dual-channel home
- `/agents` — Agent channel page
- `/search` — search results
- `/space/<any-namespace>/<any-slug>` — pick one from a Skill card on `/skills`
- `/dashboard` (logged in) — unchanged
- `/dashboard/publish` (logged in) — unchanged

Stop dev.

- [ ] **Step 6: Update `docs/todo.md` to reflect progress**

The existing [docs/todo.md](../../../docs/todo.md) tracks the multi-phase Agent rollout. Mark these items complete:

```
- [x] 修改`web/src/app/layout.tsx`，添加Agents导航项
- [x] 修改`web/src/app/router.tsx`，添加Agents相关路由
- [x] 创建`web/src/pages/agents.tsx`基础页面
- [x] 添加Agents相关的翻译键值
- [x] 创建`web/src/shared/components/agent-card.tsx`
```

Note: `agent-card.tsx` actually lives at `web/src/features/agent/agent-card.tsx` per the actual file structure. Update the path in `todo.md` to match.

- [ ] **Step 7: Final commit**

```bash
git add docs/todo.md
git commit -m "chore(docs): mark Agent channel skeleton tasks complete"
```

---

## Summary of Deliverables

After all tasks complete:

| Outcome | How to verify |
|---|---|
| Brand name shown is 知联 / Weave Hub | Header and footer display new name; old "SkillHub" text gone from UI |
| Brand gradient is green→orange | `--brand-gradient` in [web/src/index.css](../../../web/src/index.css) updated; visible on title and active nav pill |
| `/agents` resolves (no longer 404) | `agentsRoute` in `routeTree.addChildren(...)` |
| Home page shows two dual-channel rows | [web/src/pages/home.tsx](../../../web/src/pages/home.tsx) uses `DualChannelRow` twice |
| Agent column degrades to empty state | Empty-state card with orange dashed border and CTA back to skills |
| `/agents` page mirrors `/skills` structure | Orange channel hero + grid + empty state |
| Mobile users have working navigation | Hamburger menu opens drawer with same nav items |
| `useSearchSkills({type: 'AGENT'})` is wired | URL query string includes `type=AGENT` (verify in browser Network tab) |
| All tests, lint, typecheck pass | `pnpm test && pnpm typecheck && pnpm lint && pnpm build` clean |
| No backend changes | `git diff main -- server/` is empty |

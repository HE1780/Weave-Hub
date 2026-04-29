# Agents Frontend MVP Implementation Plan

> ✅ **SHIPPED — 已完成 2026-04-26。** 12 个 task commits `87c361db` → `ba343843` 全部 in `main`。详见 [docs/plans/2026-04-29-spec-status-ledger.md](2026-04-29-spec-status-ledger.md)。本文件保留作为实施过程参考,**不要据此再起新 plan**。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire the existing `agents.tsx` placeholder into the router and nav, then render an Agent list and detail page driven by mock data shaped per the locked Agent Package Format v1.

**Architecture:** Frontend-only. Mock data lives in a single TypeScript fixture file shaped to match the future API response. Components mirror existing skill-card / skill-list / skill-detail patterns so swapping mocks for a real API is a one-file change later. No backend, no real network calls. Data is exposed to pages through a `useAgents` / `useAgentDetail` hook layer that *today* returns mock data synchronously via TanStack Query's `queryFn` (so the eventual API swap touches only the queryFn body).

**Tech Stack:** React 18, TanStack Router, TanStack Query, Vitest, react-i18next, Tailwind, shared/ui primitives (Card, Button, Tabs).

**Reference spec:** [docs/adr/0001-agent-package-format.md](../adr/0001-agent-package-format.md) — defines the `Agent` shape this plan renders.

---

## File Structure

### New files (created in this plan)
- `web/src/api/agent-types.ts` — TypeScript types: `AgentSummary`, `AgentDetail`, `AgentWorkflow`, `AgentWorkflowStep`. Mirrors the spec's frontend interface.
- `web/src/features/agent/mock-agents.ts` — fixture array of `AgentDetail` objects. The single source of mock truth.
- `web/src/features/agent/use-agents.ts` — `useAgents()` hook returning `{ data, isLoading, error }`.
- `web/src/features/agent/use-agents.test.ts` — vitest unit test for the hook.
- `web/src/features/agent/use-agent-detail.ts` — `useAgentDetail(name)` hook.
- `web/src/features/agent/use-agent-detail.test.ts` — vitest unit test.
- `web/src/features/agent/agent-card.tsx` — list-card component (mirrors `skill-card.tsx`).
- `web/src/features/agent/agent-card.test.tsx` — vitest render test.
- `web/src/features/agent/workflow-steps.tsx` — read-only renderer for `workflow.steps[]`.
- `web/src/features/agent/workflow-steps.test.tsx` — vitest render test.
- `web/src/pages/agent-detail.tsx` — detail page component.
- `web/src/pages/agent-detail.test.tsx` — vitest render test.

### Modified files
- `web/src/i18n/locales/en.json` — add `nav.agents`, `agents.*` keys.
- `web/src/i18n/locales/zh.json` — add same keys, Chinese translations.
- `web/src/app/layout.tsx` — add `Agents` to the `navItems` array.
- `web/src/app/router.tsx` — add `AgentsPage` lazy import (already exists), add `AgentDetailPage` lazy import (new), add `agentsRoute` and `agentDetailRoute`, add both to `routeTree`.
- `web/src/pages/agents.tsx` — replace placeholder with a list driven by `useAgents()`.

### File responsibilities (one job each)
- **agent-types.ts** — Shape contract only. No logic.
- **mock-agents.ts** — Fixture data only. No logic.
- **use-agents.ts / use-agent-detail.ts** — Adapt mock data into TanStack Query results so pages don't know data is mocked.
- **agent-card.tsx** — Visual list-item only. Gets `AgentSummary` prop, renders, fires `onClick`.
- **workflow-steps.tsx** — Visual renderer of `workflow.steps[]` only. Knows nothing about routing or fetching.
- **agents.tsx (page)** — Layout + list. Uses `useAgents()`, maps to `<AgentCard>`.
- **agent-detail.tsx (page)** — Layout + detail. Uses `useAgentDetail()`, renders metadata + soul + workflow.

---

## Pre-flight Verification

- [ ] **Step 0.1: Confirm working directory and starting state**

Run from repo root:
```bash
git status --short web/ docs/
node -v && npm -v
```
Expected: working tree may have unrelated dirty files; that's fine. Node >= 18, npm available.

- [ ] **Step 0.2: Confirm dev server starts and existing pages still work**

Run:
```bash
cd web && npm install && npm run dev
```
Expected: dev server starts (Vite), existing `/`, `/search`, `/dashboard` routes load without console errors. Stop the server (`Ctrl+C`) before continuing.

- [ ] **Step 0.3: Confirm test runner works**

Run:
```bash
cd web && npm test -- --run --reporter=basic --bail=1 src/features/skill/skill-card.test.ts
```
Expected: 1 file passed. (This proves vitest config is healthy before we add new tests.)

---

## Task 1: i18n Keys

**Files:**
- Modify: `web/src/i18n/locales/en.json`
- Modify: `web/src/i18n/locales/zh.json`

- [ ] **Step 1.1: Read current en.json structure**

Open `web/src/i18n/locales/en.json` and find the `nav` object. Note its current keys (`landing`, `publish`, `search`, `dashboard`, `mySkills`, etc.).

- [ ] **Step 1.2: Add `nav.agents` and `agents.*` keys to en.json**

Add `"agents": "Agents"` to the `nav` object (preserve existing keys, add as last entry to minimize diff).

Add a new top-level `"agents"` object with these exact keys:
```json
"agents": {
  "title": "Agents",
  "subtitle": "Multi-skill orchestration with personality",
  "emptyTitle": "No agents yet",
  "emptyDescription": "Once agents are published, they will appear here.",
  "loading": "Loading agents…",
  "loadError": "Failed to load agents.",
  "detail": {
    "soulHeading": "Soul",
    "workflowHeading": "Workflow",
    "skillsHeading": "Skills used",
    "noSoul": "(no soul provided)",
    "noWorkflow": "(no workflow defined)",
    "stepLabel": "Step",
    "typeLabel": "Type",
    "skillLabel": "Skill",
    "promptLabel": "Prompt",
    "inputsLabel": "Inputs"
  }
}
```

- [ ] **Step 1.3: Add the same keys to zh.json with Chinese translations**

In `web/src/i18n/locales/zh.json`, mirror the same structure:
```json
"nav": { ..., "agents": "智能体" }

"agents": {
  "title": "智能体",
  "subtitle": "带人格的多技能编排",
  "emptyTitle": "暂无智能体",
  "emptyDescription": "智能体发布后将显示在这里。",
  "loading": "正在加载智能体…",
  "loadError": "加载智能体失败。",
  "detail": {
    "soulHeading": "灵魂",
    "workflowHeading": "工作流",
    "skillsHeading": "依赖的技能",
    "noSoul": "（未提供灵魂）",
    "noWorkflow": "（未定义工作流）",
    "stepLabel": "步骤",
    "typeLabel": "类型",
    "skillLabel": "技能",
    "promptLabel": "提示词",
    "inputsLabel": "输入"
  }
}
```

- [ ] **Step 1.4: Verify both files are valid JSON**

Run:
```bash
node -e "JSON.parse(require('fs').readFileSync('web/src/i18n/locales/en.json','utf8'))" \
 && node -e "JSON.parse(require('fs').readFileSync('web/src/i18n/locales/zh.json','utf8'))" \
 && echo OK
```
Expected: prints `OK`.

- [ ] **Step 1.5: Commit**

```bash
git add web/src/i18n/locales/en.json web/src/i18n/locales/zh.json
git commit -m "feat(agents): add i18n keys for agents nav and pages"
```

---

## Task 2: Agent Type Definitions

**Files:**
- Create: `web/src/api/agent-types.ts`

- [ ] **Step 2.1: Create the types file**

Create `web/src/api/agent-types.ts` with the following exact contents:

```typescript
/**
 * Agent package shape — frontend mirror of docs/adr/0001-agent-package-format.md.
 *
 * The backend is not yet implemented; these types are the contract the eventual
 * API will return. Mock data uses the same shape so the UI doesn't change when
 * the real fetch is wired up.
 */

export interface AgentSummary {
  name: string
  description: string
  version?: string
}

export interface AgentDetail extends AgentSummary {
  body?: string
  soul?: string
  workflow?: AgentWorkflow
  frontmatter?: Record<string, unknown>
}

export interface AgentWorkflow {
  steps: AgentWorkflowStep[]
  output?: string
}

export interface AgentWorkflowStep {
  id: string
  type?: string
  skill?: string
  prompt?: string
  inputs?: Record<string, string>
}
```

- [ ] **Step 2.2: Type-check**

Run:
```bash
cd web && npx tsc --noEmit
```
Expected: no errors. (No code yet imports these types, so this just confirms the file itself is valid.)

- [ ] **Step 2.3: Commit**

```bash
git add web/src/api/agent-types.ts
git commit -m "feat(agents): define Agent and AgentWorkflow types"
```

---

## Task 3: Mock Fixture

**Files:**
- Create: `web/src/features/agent/mock-agents.ts`

- [ ] **Step 3.1: Create the mock fixture**

Create `web/src/features/agent/mock-agents.ts` with three example agents covering the shape variations (with workflow + soul, with `think`-type steps, minimal):

```typescript
import type { AgentDetail } from '@/api/agent-types'

/**
 * Static fixture used by useAgents/useAgentDetail until the backend lands.
 * Shape mirrors docs/adr/0001-agent-package-format.md.
 */
export const MOCK_AGENTS: AgentDetail[] = [
  {
    name: 'customer-support-agent',
    description: 'Triages incoming support tickets and drafts responses.',
    version: '1.0.0',
    body: 'Routes a ticket through classification, reflection, and a knowledge-base lookup before drafting a response.',
    soul: 'You are a calm, empathetic support specialist. Read every ticket twice before responding.',
    workflow: {
      steps: [
        {
          id: 'classify',
          type: 'skill',
          skill: 'ticket-classifier',
          inputs: { ticket: '$.input.ticket' },
        },
        {
          id: 'reflect',
          type: 'think',
          prompt: 'Given the classification, decide if escalation is needed.',
          inputs: { classification: '$.steps.classify.output' },
        },
        {
          id: 'search',
          type: 'skill',
          skill: 'knowledge-base-search',
          inputs: { category: '$.steps.classify.output.category' },
        },
      ],
      output: '$.steps.search.output',
    },
    frontmatter: {
      skills: [{ name: 'ticket-classifier' }, { name: 'knowledge-base-search' }],
    },
  },
  {
    name: 'release-notes-writer',
    description: 'Drafts release notes from a list of merged pull requests.',
    version: '0.2.0',
    soul: 'You write crisp, factual release notes. No marketing language.',
    workflow: {
      steps: [
        {
          id: 'summarize',
          type: 'think',
          prompt: 'Summarize each PR in one sentence focused on user impact.',
        },
      ],
    },
  },
  {
    name: 'minimal-agent',
    description: 'A stub agent with no workflow yet.',
  },
]

export function findMockAgent(name: string): AgentDetail | undefined {
  return MOCK_AGENTS.find((agent) => agent.name === name)
}
```

- [ ] **Step 3.2: Type-check**

Run:
```bash
cd web && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 3.3: Commit**

```bash
git add web/src/features/agent/mock-agents.ts
git commit -m "feat(agents): add mock agent fixtures"
```

---

## Task 4: useAgents Hook (TDD)

**Files:**
- Create: `web/src/features/agent/use-agents.test.ts`
- Create: `web/src/features/agent/use-agents.ts`

- [ ] **Step 4.1: Write the failing test**

Create `web/src/features/agent/use-agents.test.ts`:

```typescript
import { describe, it, expect } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAgents } from './use-agents'
import { MOCK_AGENTS } from './mock-agents'

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

describe('useAgents', () => {
  it('returns the mock agent list as AgentSummary records', async () => {
    const { result } = renderHook(() => useAgents(), { wrapper })

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.data).toHaveLength(MOCK_AGENTS.length)
    expect(result.current.data?.[0]).toEqual(
      expect.objectContaining({
        name: MOCK_AGENTS[0].name,
        description: MOCK_AGENTS[0].description,
      }),
    )
  })
})
```

- [ ] **Step 4.2: Run the test and verify it fails**

```bash
cd web && npm test -- --run src/features/agent/use-agents.test.ts
```
Expected: fails with "Cannot find module './use-agents'".

- [ ] **Step 4.3: Implement the hook**

Create `web/src/features/agent/use-agents.ts`:

```typescript
import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import type { AgentSummary } from '@/api/agent-types'
import { MOCK_AGENTS } from './mock-agents'

/**
 * Returns the list of agents.
 *
 * The backend is not yet implemented; this hook resolves to a static fixture.
 * When the API lands, replace the queryFn body with a fetchJson call.
 */
export function useAgents(): UseQueryResult<AgentSummary[]> {
  return useQuery({
    queryKey: ['agents'],
    queryFn: async () => {
      return MOCK_AGENTS.map(({ name, description, version }) => ({
        name,
        description,
        version,
      }))
    },
  })
}
```

- [ ] **Step 4.4: Run the test and verify it passes**

```bash
cd web && npm test -- --run src/features/agent/use-agents.test.ts
```
Expected: 1 test passed.

- [ ] **Step 4.5: Commit**

```bash
git add web/src/features/agent/use-agents.ts web/src/features/agent/use-agents.test.ts
git commit -m "feat(agents): add useAgents hook backed by mock fixture"
```

---

## Task 5: useAgentDetail Hook (TDD)

**Files:**
- Create: `web/src/features/agent/use-agent-detail.test.ts`
- Create: `web/src/features/agent/use-agent-detail.ts`

- [ ] **Step 5.1: Write the failing tests**

Create `web/src/features/agent/use-agent-detail.test.ts`:

```typescript
import { describe, it, expect } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useAgentDetail } from './use-agent-detail'

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

describe('useAgentDetail', () => {
  it('returns the matching agent for a known name', async () => {
    const { result } = renderHook(() => useAgentDetail('customer-support-agent'), { wrapper })

    await waitFor(() => expect(result.current.isLoading).toBe(false))

    expect(result.current.data?.name).toBe('customer-support-agent')
    expect(result.current.data?.workflow?.steps.length).toBeGreaterThan(0)
  })

  it('errors for an unknown name', async () => {
    const { result } = renderHook(() => useAgentDetail('does-not-exist'), { wrapper })

    await waitFor(() => expect(result.current.isError).toBe(true))
    expect(result.current.error).toBeInstanceOf(Error)
  })
})
```

- [ ] **Step 5.2: Run the test and verify it fails**

```bash
cd web && npm test -- --run src/features/agent/use-agent-detail.test.ts
```
Expected: fails with "Cannot find module './use-agent-detail'".

- [ ] **Step 5.3: Implement the hook**

Create `web/src/features/agent/use-agent-detail.ts`:

```typescript
import { useQuery, type UseQueryResult } from '@tanstack/react-query'
import type { AgentDetail } from '@/api/agent-types'
import { findMockAgent } from './mock-agents'

/**
 * Returns one agent's full detail by name.
 * Errors if the name is unknown so callers can render a not-found state.
 */
export function useAgentDetail(name: string): UseQueryResult<AgentDetail> {
  return useQuery({
    queryKey: ['agents', name],
    queryFn: async () => {
      const agent = findMockAgent(name)
      if (!agent) {
        throw new Error(`Agent not found: ${name}`)
      }
      return agent
    },
    enabled: name.length > 0,
    retry: false,
  })
}
```

- [ ] **Step 5.4: Run the tests and verify they pass**

```bash
cd web && npm test -- --run src/features/agent/use-agent-detail.test.ts
```
Expected: 2 tests passed.

- [ ] **Step 5.5: Commit**

```bash
git add web/src/features/agent/use-agent-detail.ts web/src/features/agent/use-agent-detail.test.ts
git commit -m "feat(agents): add useAgentDetail hook with not-found handling"
```

---

## Task 6: AgentCard Component (TDD)

**Files:**
- Create: `web/src/features/agent/agent-card.test.tsx`
- Create: `web/src/features/agent/agent-card.tsx`

- [ ] **Step 6.1: Write the failing test**

Create `web/src/features/agent/agent-card.test.tsx`:

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { AgentCard } from './agent-card'

describe('AgentCard', () => {
  const agent = {
    name: 'demo-agent',
    description: 'A demo agent for tests.',
    version: '1.2.3',
  }

  it('renders the agent name, description, and version', () => {
    render(<AgentCard agent={agent} />)

    expect(screen.getByText('demo-agent')).toBeInTheDocument()
    expect(screen.getByText('A demo agent for tests.')).toBeInTheDocument()
    expect(screen.getByText(/1\.2\.3/)).toBeInTheDocument()
  })

  it('fires onClick when activated', () => {
    const onClick = vi.fn()
    render(<AgentCard agent={agent} onClick={onClick} />)

    fireEvent.click(screen.getByRole('link'))
    expect(onClick).toHaveBeenCalledOnce()
  })
})
```

- [ ] **Step 6.2: Run the test and verify it fails**

```bash
cd web && npm test -- --run src/features/agent/agent-card.test.tsx
```
Expected: fails with "Cannot find module './agent-card'".

- [ ] **Step 6.3: Implement the component**

Create `web/src/features/agent/agent-card.tsx`:

```tsx
import type { AgentSummary } from '@/api/agent-types'
import { Card } from '@/shared/ui/card'

interface AgentCardProps {
  agent: AgentSummary
  onClick?: () => void
}

/**
 * Visual list-card for one agent. Mirrors the role of skill-card.tsx
 * but with the simpler AgentSummary fields available today.
 */
export function AgentCard({ agent, onClick }: AgentCardProps) {
  const isInteractive = typeof onClick === 'function'

  return (
    <Card
      className="h-full p-5 cursor-pointer group relative overflow-hidden bg-white border shadow-sm transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70 focus-visible:ring-offset-2"
      style={{ borderColor: 'hsl(var(--border-card))' }}
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
      <div className="flex h-full flex-col">
        <div className="flex items-start justify-between mb-3">
          <h3 className="font-semibold text-lg group-hover:text-primary transition-colors" style={{ color: 'hsl(var(--foreground))' }}>
            {agent.name}
          </h3>
          {agent.version && (
            <span className="px-2.5 py-1 rounded-full bg-secondary/60 font-mono text-xs">
              v{agent.version}
            </span>
          )}
        </div>
        <p className="text-sm text-muted-foreground line-clamp-2 leading-relaxed">
          {agent.description}
        </p>
      </div>
    </Card>
  )
}
```

- [ ] **Step 6.4: Run the test and verify it passes**

```bash
cd web && npm test -- --run src/features/agent/agent-card.test.tsx
```
Expected: 2 tests passed.

- [ ] **Step 6.5: Commit**

```bash
git add web/src/features/agent/agent-card.tsx web/src/features/agent/agent-card.test.tsx
git commit -m "feat(agents): add AgentCard list component"
```

---

## Task 7: WorkflowSteps Component (TDD)

**Files:**
- Create: `web/src/features/agent/workflow-steps.test.tsx`
- Create: `web/src/features/agent/workflow-steps.tsx`

- [ ] **Step 7.1: Write the failing test**

Create `web/src/features/agent/workflow-steps.test.tsx`:

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import { WorkflowSteps } from './workflow-steps'
import type { AgentWorkflow } from '@/api/agent-types'

i18n.use(initReactI18next).init({
  lng: 'en',
  resources: {
    en: {
      translation: {
        agents: {
          detail: {
            stepLabel: 'Step',
            typeLabel: 'Type',
            skillLabel: 'Skill',
            promptLabel: 'Prompt',
            inputsLabel: 'Inputs',
            noWorkflow: '(no workflow defined)',
          },
        },
      },
    },
  },
  interpolation: { escapeValue: false },
})

function renderWith(workflow: AgentWorkflow | undefined) {
  return render(
    <I18nextProvider i18n={i18n}>
      <WorkflowSteps workflow={workflow} />
    </I18nextProvider>,
  )
}

describe('WorkflowSteps', () => {
  it('renders skill and think steps with their type-specific fields', () => {
    renderWith({
      steps: [
        { id: 'a', type: 'skill', skill: 'foo' },
        { id: 'b', type: 'think', prompt: 'reflect on a' },
      ],
    })

    expect(screen.getByText('a')).toBeInTheDocument()
    expect(screen.getByText('foo')).toBeInTheDocument()
    expect(screen.getByText('b')).toBeInTheDocument()
    expect(screen.getByText('reflect on a')).toBeInTheDocument()
  })

  it('renders an empty-state message when workflow is missing', () => {
    renderWith(undefined)
    expect(screen.getByText('(no workflow defined)')).toBeInTheDocument()
  })
})
```

- [ ] **Step 7.2: Run the test and verify it fails**

```bash
cd web && npm test -- --run src/features/agent/workflow-steps.test.tsx
```
Expected: fails with "Cannot find module './workflow-steps'".

- [ ] **Step 7.3: Implement the component**

Create `web/src/features/agent/workflow-steps.tsx`:

```tsx
import { useTranslation } from 'react-i18next'
import type { AgentWorkflow, AgentWorkflowStep } from '@/api/agent-types'

interface WorkflowStepsProps {
  workflow: AgentWorkflow | undefined
}

/**
 * Read-only renderer for a workflow's step list.
 * Knows nothing about routing or fetching — pure presentation.
 */
export function WorkflowSteps({ workflow }: WorkflowStepsProps) {
  const { t } = useTranslation()

  if (!workflow || workflow.steps.length === 0) {
    return (
      <p className="text-sm text-muted-foreground italic">
        {t('agents.detail.noWorkflow')}
      </p>
    )
  }

  return (
    <ol className="space-y-3">
      {workflow.steps.map((step, index) => (
        <li key={step.id} className="rounded-lg border p-4" style={{ borderColor: 'hsl(var(--border-card))' }}>
          <div className="flex items-center gap-3 mb-2">
            <span className="px-2 py-0.5 rounded-full bg-secondary/60 text-xs font-mono">
              {t('agents.detail.stepLabel')} {index + 1}
            </span>
            <span className="font-mono text-sm">{step.id}</span>
            {step.type && (
              <span className="text-xs text-muted-foreground">
                {t('agents.detail.typeLabel')}: {step.type}
              </span>
            )}
          </div>
          <StepBody step={step} />
        </li>
      ))}
    </ol>
  )
}

function StepBody({ step }: { step: AgentWorkflowStep }) {
  const { t } = useTranslation()
  return (
    <dl className="text-sm space-y-1">
      {step.skill && (
        <div className="flex gap-2">
          <dt className="text-muted-foreground">{t('agents.detail.skillLabel')}:</dt>
          <dd className="font-mono">{step.skill}</dd>
        </div>
      )}
      {step.prompt && (
        <div className="flex gap-2">
          <dt className="text-muted-foreground">{t('agents.detail.promptLabel')}:</dt>
          <dd>{step.prompt}</dd>
        </div>
      )}
      {step.inputs && Object.keys(step.inputs).length > 0 && (
        <div>
          <dt className="text-muted-foreground">{t('agents.detail.inputsLabel')}:</dt>
          <dd>
            <pre className="text-xs bg-muted/50 rounded p-2 mt-1 overflow-x-auto">
              {JSON.stringify(step.inputs, null, 2)}
            </pre>
          </dd>
        </div>
      )}
    </dl>
  )
}
```

- [ ] **Step 7.4: Run the test and verify it passes**

```bash
cd web && npm test -- --run src/features/agent/workflow-steps.test.tsx
```
Expected: 2 tests passed.

- [ ] **Step 7.5: Commit**

```bash
git add web/src/features/agent/workflow-steps.tsx web/src/features/agent/workflow-steps.test.tsx
git commit -m "feat(agents): add WorkflowSteps renderer"
```

---

## Task 8: Replace agents.tsx Placeholder with List

**Files:**
- Modify: `web/src/pages/agents.tsx`

- [ ] **Step 8.1: Replace the file's contents**

Overwrite `web/src/pages/agents.tsx` with:

```tsx
import { useTranslation } from 'react-i18next'
import { useNavigate } from '@tanstack/react-router'
import { useAgents } from '@/features/agent/use-agents'
import { AgentCard } from '@/features/agent/agent-card'
import { EmptyState } from '@/shared/components/empty-state'

/**
 * Agents list page. Reads from useAgents() — backed by mocks today, real API later.
 */
export function AgentsPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: agents, isLoading, isError } = useAgents()

  return (
    <div className="min-h-screen bg-gradient-to-br from-background to-muted/20">
      <div className="relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-r from-purple-500/10 to-blue-500/10" />
        <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-16 md:py-24">
          <div className="text-center">
            <h1 className="text-4xl md:text-5xl font-bold tracking-tight mb-4">
              <span className="bg-gradient-to-r from-purple-600 to-blue-600 bg-clip-text text-transparent">
                {t('agents.title')}
              </span>
            </h1>
            <p className="text-xl text-muted-foreground max-w-2xl mx-auto">
              {t('agents.subtitle')}
            </p>
          </div>
        </div>
      </div>

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        {isLoading && (
          <p className="text-center text-muted-foreground">{t('agents.loading')}</p>
        )}
        {isError && (
          <p className="text-center text-destructive">{t('agents.loadError')}</p>
        )}
        {!isLoading && !isError && agents && agents.length === 0 && (
          <EmptyState
            title={t('agents.emptyTitle')}
            description={t('agents.emptyDescription')}
          />
        )}
        {!isLoading && !isError && agents && agents.length > 0 && (
          <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
            {agents.map((agent) => (
              <AgentCard
                key={agent.name}
                agent={agent}
                onClick={() => navigate({ to: '/agents/$name', params: { name: agent.name } })}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  )
}
```

- [ ] **Step 8.2: Verify EmptyState API matches**

Run:
```bash
grep -nE "interface.*EmptyState|EmptyStateProps|export function EmptyState" web/src/shared/components/empty-state.tsx
```
Expected: confirm the component takes `title` and `description` string props. If the actual API differs, adjust the call site to match the existing component's prop names rather than changing the component.

- [ ] **Step 8.3: Type-check**

```bash
cd web && npx tsc --noEmit
```
Expected: no errors. (If `navigate({ to: '/agents/$name' })` type-errors, that's because router.tsx hasn't registered the route yet — expected; Task 10 fixes it. Skip this step if so and re-run after Task 10.)

- [ ] **Step 8.4: Commit**

```bash
git add web/src/pages/agents.tsx
git commit -m "feat(agents): wire agents page to useAgents and AgentCard"
```

---

## Task 9: agent-detail.tsx Page (TDD)

**Files:**
- Create: `web/src/pages/agent-detail.test.tsx`
- Create: `web/src/pages/agent-detail.tsx`

- [ ] **Step 9.1: Write the failing test**

Create `web/src/pages/agent-detail.test.tsx`:

```tsx
import { describe, it, expect } from 'vitest'
import { render, screen, waitFor } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { I18nextProvider } from 'react-i18next'
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import type { ReactNode } from 'react'
import { AgentDetailPage } from './agent-detail'

i18n.use(initReactI18next).init({
  lng: 'en',
  resources: {
    en: {
      translation: {
        agents: {
          loading: 'Loading agents…',
          loadError: 'Failed to load agents.',
          detail: {
            soulHeading: 'Soul',
            workflowHeading: 'Workflow',
            skillsHeading: 'Skills used',
            noSoul: '(no soul provided)',
            noWorkflow: '(no workflow defined)',
            stepLabel: 'Step',
            typeLabel: 'Type',
            skillLabel: 'Skill',
            promptLabel: 'Prompt',
            inputsLabel: 'Inputs',
          },
        },
      },
    },
  },
  interpolation: { escapeValue: false },
})

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return (
    <I18nextProvider i18n={i18n}>
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    </I18nextProvider>
  )
}

describe('AgentDetailPage', () => {
  it('renders the agent name, description, soul, and workflow when found', async () => {
    render(<AgentDetailPage name="customer-support-agent" />, { wrapper })

    await waitFor(() =>
      expect(screen.getByText('customer-support-agent')).toBeInTheDocument(),
    )
    expect(screen.getByText('Soul')).toBeInTheDocument()
    expect(screen.getByText('Workflow')).toBeInTheDocument()
  })

  it('renders the load-error message when the agent is unknown', async () => {
    render(<AgentDetailPage name="does-not-exist" />, { wrapper })

    await waitFor(() => expect(screen.getByText('Failed to load agents.')).toBeInTheDocument())
  })
})
```

- [ ] **Step 9.2: Run the test and verify it fails**

```bash
cd web && npm test -- --run src/pages/agent-detail.test.tsx
```
Expected: fails with "Cannot find module './agent-detail'".

- [ ] **Step 9.3: Implement the page**

Create `web/src/pages/agent-detail.tsx`:

```tsx
import { useTranslation } from 'react-i18next'
import { useAgentDetail } from '@/features/agent/use-agent-detail'
import { WorkflowSteps } from '@/features/agent/workflow-steps'

interface AgentDetailPageProps {
  /**
   * Optional override for tests. In normal use this is read from route params via the
   * route component wrapper in router.tsx.
   */
  name?: string
}

/**
 * Detail page for a single agent. Renders metadata + soul + workflow.
 */
export function AgentDetailPage({ name }: AgentDetailPageProps) {
  const { t } = useTranslation()
  const resolvedName = name ?? ''
  const { data: agent, isLoading, isError } = useAgentDetail(resolvedName)

  if (isLoading) {
    return (
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <p className="text-muted-foreground">{t('agents.loading')}</p>
      </div>
    )
  }

  if (isError || !agent) {
    return (
      <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <p className="text-destructive">{t('agents.loadError')}</p>
      </div>
    )
  }

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12 space-y-8">
      <header>
        <h1 className="text-3xl font-bold tracking-tight">{agent.name}</h1>
        {agent.version && (
          <p className="text-sm font-mono text-muted-foreground mt-1">v{agent.version}</p>
        )}
        <p className="text-lg text-muted-foreground mt-3">{agent.description}</p>
      </header>

      <section>
        <h2 className="text-xl font-semibold mb-3">{t('agents.detail.soulHeading')}</h2>
        {agent.soul ? (
          <pre className="whitespace-pre-wrap text-sm bg-muted/40 rounded-lg p-4 leading-relaxed">
            {agent.soul}
          </pre>
        ) : (
          <p className="text-sm text-muted-foreground italic">{t('agents.detail.noSoul')}</p>
        )}
      </section>

      <section>
        <h2 className="text-xl font-semibold mb-3">{t('agents.detail.workflowHeading')}</h2>
        <WorkflowSteps workflow={agent.workflow} />
      </section>

      {agent.body && (
        <section>
          <pre className="whitespace-pre-wrap text-sm leading-relaxed">{agent.body}</pre>
        </section>
      )}
    </div>
  )
}
```

- [ ] **Step 9.4: Run the test and verify it passes**

```bash
cd web && npm test -- --run src/pages/agent-detail.test.tsx
```
Expected: 2 tests passed.

- [ ] **Step 9.5: Commit**

```bash
git add web/src/pages/agent-detail.tsx web/src/pages/agent-detail.test.tsx
git commit -m "feat(agents): add agent detail page"
```

---

## Task 10: Wire Router

**Files:**
- Modify: `web/src/app/router.tsx`

- [ ] **Step 10.1: Add lazy imports**

In `web/src/app/router.tsx`, locate the section of `createLazyRouteComponent(...)` declarations (around lines 64-136 in the current file). Add these two lines next to the other page declarations:

```typescript
const AgentsPage = createLazyRouteComponent(() => import('@/pages/agents'), 'AgentsPage')
const AgentDetailPage = createLazyRouteComponent(() => import('@/pages/agent-detail'), 'AgentDetailPage')
```

- [ ] **Step 10.2: Add route declarations**

In the same file, after the `searchRoute` declaration (around line 209), add:

```typescript
const agentsRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'agents',
  component: AgentsPage,
})

const agentDetailRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'agents/$name',
  component: function AgentDetailRouteComponent() {
    const { name } = agentDetailRoute.useParams()
    return <AgentDetailPage name={name} />
  },
})
```

(Casting `useParams()` is not needed; TanStack Router infers the param shape from the path.)

- [ ] **Step 10.3: Add both routes to the routeTree**

In the `rootRoute.addChildren([...])` call (around lines 408-442), add `agentsRoute` and `agentDetailRoute` to the array. Place them next to `searchRoute` for readability:

```typescript
const routeTree = rootRoute.addChildren([
  landingRoute,
  skillsRoute,
  loginRoute,
  registerRoute,
  resetPasswordRoute,
  privacyRoute,
  searchRoute,
  agentsRoute,           // NEW
  agentDetailRoute,      // NEW
  termsRoute,
  // ... rest unchanged
])
```

- [ ] **Step 10.4: Type-check**

```bash
cd web && npx tsc --noEmit
```
Expected: no errors. The earlier `navigate({ to: '/agents/$name' })` in `agents.tsx` should now type-check because the route is registered.

- [ ] **Step 10.5: Commit**

```bash
git add web/src/app/router.tsx
git commit -m "feat(agents): register /agents and /agents/:name routes"
```

---

## Task 11: Wire Layout Nav

**Files:**
- Modify: `web/src/app/layout.tsx`

- [ ] **Step 11.1: Add the Agents nav item**

In `web/src/app/layout.tsx`, find the `navItems` array (around lines 43-54). Add an entry for Agents between `search` and `dashboard`:

```typescript
const navItems: Array<{
  label: string
  to: string
  exact?: boolean
  auth?: boolean
}> = [
  { label: t('nav.landing'), to: '/', exact: true },
  { label: t('nav.publish'), to: '/dashboard/publish', auth: true },
  { label: t('nav.search'), to: '/search' },
  { label: t('nav.agents'), to: '/agents' },        // NEW
  { label: t('nav.dashboard'), to: '/dashboard', auth: true },
  { label: t('nav.mySkills'), to: '/dashboard/skills', auth: true },
]
```

- [ ] **Step 11.2: Type-check**

```bash
cd web && npx tsc --noEmit
```
Expected: no errors.

- [ ] **Step 11.3: Commit**

```bash
git add web/src/app/layout.tsx
git commit -m "feat(agents): add Agents nav item to layout"
```

---

## Task 12: End-to-End Smoke Test in Browser

**Files:** none — manual verification.

- [ ] **Step 12.1: Run the test suite once for everything**

```bash
cd web && npm test -- --run
```
Expected: all tests pass, including the existing skill-related tests (regression check). If anything fails outside of Agent files, stop and fix the regression — do not proceed.

- [ ] **Step 12.2: Start the dev server**

```bash
cd web && npm run dev
```
Expected: starts on the configured port (likely 5173 or 3001).

- [ ] **Step 12.3: Verify navigation**

In a browser:
1. Open the dev server URL.
2. Confirm an "Agents" / "智能体" link appears in the top nav.
3. Click it — should navigate to `/agents` and render the three mock agent cards.
4. Click `customer-support-agent` — should navigate to `/agents/customer-support-agent` and render the soul + workflow with three steps (`classify`, `reflect`, `search`).
5. Click `release-notes-writer` — should render its single `think` step.
6. Click `minimal-agent` — should render with `(no soul provided)` and `(no workflow defined)`.
7. Manually navigate to `/agents/no-such-name` — should render the load-error message.
8. Confirm `/`, `/search`, `/dashboard` (logged in) still work — no regression.

- [ ] **Step 12.4: Verify language switching**

Toggle the language switcher between English and Chinese; confirm the Agent nav label and page text both translate.

- [ ] **Step 12.5: Stop the dev server.**

`Ctrl+C` in the terminal running `npm run dev`.

- [ ] **Step 12.6: No commit — verification only.**

If everything passed, the feature is complete.

---

## Final Verification Checklist

Before declaring done, confirm:

- [ ] All 11 implementation commits are present (`git log --oneline | head -15`).
- [ ] `npm test -- --run` is green from a clean run.
- [ ] `npx tsc --noEmit` is green.
- [ ] Browser smoke test from Task 12 passed all sub-steps.
- [ ] No files outside `web/src/{api,features/agent,pages,app,i18n}/...` were modified.
- [ ] `docs/todo.md` Phase 1 / Week 1 frontend rows can be checked off (mention to user; do not edit todo.md silently).

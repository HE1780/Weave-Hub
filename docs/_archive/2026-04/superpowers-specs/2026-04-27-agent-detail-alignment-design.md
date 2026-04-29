# 2026-04-27 Agent Detail Alignment Design

> ✅ **SHIPPED — 实施完成 2026-04-27。** Agent 详情页已对齐 skill-detail UX(flex lg:flex-row + Overview/Workflow/Versions Tabs + 侧栏)。详见 [docs/plans/2026-04-29-spec-status-ledger.md](../../plans/2026-04-29-spec-status-ledger.md)。

## 1. Context And Goal

Current `agent-detail` is functional but visually and structurally weaker than `skill-detail`.

User-selected scope is **B**:
- align visual hierarchy and interaction rhythm with `skill-detail`
- keep agent-specific content model (`soul`, `workflow`, `body`)
- treat `agent-detail` as skill-detail-style parity page, with the only intentional content difference in main docs display

Target page: `/agents/$namespace/$slug`

## 2. Scope

### In Scope

- Convert `agent-detail` from single-column flow to `skill-detail`-like two-column layout (`main + sidebar`)
- Add skill-detail-like tabbed main-content structure:
  - Overview
  - Files
  - Versions
- Preserve and restyle existing lifecycle actions (`archive`, `unarchive`)
- Preserve social interactions (`AgentStarButton`, `AgentRatingInput`) and move into sidebar rhythm
- Wire agent detail capabilities only when backed by real backend APIs (no fake placeholders)
- Update existing tests to cover new structure

### Out Of Scope

- Mocked, fake, or UI-only placeholder features
- Any temporary "coming soon" behavior inside core action flows

## 3. UX And IA Design

### 3.1 Page Skeleton

- Outer container: `max-w-6xl mx-auto` with `lg:flex-row` split
- Main content left:
  - back button
  - status/visibility badges
  - title + description
  - tabs with content cards
- Sidebar right:
  - metadata card (version/rating/star/namespace)
  - interaction card (star + rating controls)
  - lifecycle card (archive/unarchive)

### 3.2 Main Tabs

- `Overview`:
  - Main doc viewer uses tag switch between `agent.md` and `soul.md`
  - Default tab is `agent.md` (user choice)
  - `soul.md` is the second tab
  - Markdown rendering style aligns with skill overview readability
- `Workflow`:
  - Workflow card with `WorkflowSteps`
  - fallback message for missing workflow
- `Versions`:
  - follow skill-detail interaction pattern where backend support exists
  - no fabricated list/actions when API is unavailable

### 3.3 Content Mapping Rules

- `agent.md` is the default primary document in Overview
- `soul.md` is the secondary document via tag switch
- `agent.workflow` remains in dedicated tab for readability
- keep typography and spacing compatible with existing design tokens

## 4. Behavior And Data Flow

- Query source remains agent-side detail endpoints (starting from `useAgentDetail(namespace, slug)`) and extends as backend parity lands
- Loading/error/not-found handling remains, but visual containers align with new page shell
- Lifecycle operations:
  - keep existing mutation hooks and confirm dialogs
  - card placement changes only; business logic unchanged
- Login-required social action behavior remains unchanged

## 5. Error Handling

- Keep current error branches:
  - loading state
  - generic load error
  - no-published-version specific message
- For absent optional docs:
  - no `agent.md` -> render doc-unavailable fallback
  - no `soul.md` -> render doc-unavailable fallback
  - no workflow -> existing `WorkflowSteps` fallback semantics

## 6. Testing Plan

- Update `agent-detail.test.tsx` with structure-oriented assertions:
  - renders skill-style tab shell
  - renders overview doc tag switch (`agent.md` default + `soul.md`)
  - lifecycle card visibility still gated by `canManageLifecycle`
- Keep behavior tests for archive/unarchive visibility unchanged where valid
- Avoid fragile snapshot-style class assertions; focus on user-visible structure

## 7. Rollout Strategy

1. Refactor layout and tab shell in `agent-detail.tsx` to mirror skill detail rhythm
2. Implement overview doc tag switch (`agent.md` default / `soul.md`)
3. Reposition sidebar cards (social + lifecycle)
4. Wire only real backend-supported features (no placeholders)
5. Update tests
6. Run targeted test suite and diagnostics

## 8. Risks And Mitigations

- Risk: layout churn breaks smaller-screen readability
  - Mitigation: preserve responsive classes from `skill-detail` pattern (`flex-col` on small screens)
- Risk: tabs hide content users previously saw immediately
  - Mitigation: default tab to `Overview`, ensure key summary appears before tabs
- Risk: backend parity timing mismatch
  - Mitigation: merge only API-backed features; defer unsupported items without UI stubs

## 9. Acceptance Criteria

- `agent-detail` visually reads as same design family as `skill-detail`
- overview doc area supports two-tag switch with default `agent.md`
- no fake features are introduced; all surfaced actions are API-backed
- lifecycle operations still work as before
- tests pass for updated page structure and permission-gated lifecycle controls

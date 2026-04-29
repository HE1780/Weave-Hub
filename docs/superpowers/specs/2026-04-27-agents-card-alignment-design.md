# Agents Card Alignment Design (Skills parity)

> ✅ **SHIPPED — 实施完成 2026-04-27。** AgentCard 已对齐 SkillCard 信息层(namespace badge + version chip + download + star + ratingAvg/Count)。详见 [docs/plans/2026-04-29-spec-status-ledger.md](../../plans/2026-04-29-spec-status-ledger.md)。

## Context

User requested that `/agents` page cards align with `/skills` cards, including:
- version badge
- `@global` / namespace marker
- download and star info row
- card height consistency across the grid

Constraint confirmed by user:
- Download field has no backend metric yet, so display `—` as placeholder.

## Scope

### In Scope
- Align `AgentCard` information architecture with `SkillCard`:
  - title + namespace badge in header
  - summary body with clamp
  - footer meta row with version / downloads / stars
- Add minimal data fields needed by card rendering:
  - `displayName`
  - `version`
  - `starCount`
- Keep existing click/navigation behavior unchanged.
- Normalize card height visual rhythm to match skills page effect.

### Out of Scope
- Fake download count generation.
- New backend endpoints or query params.
- Sorting/filtering behavior changes.

## Design

### Data Mapping
- Extend `AgentSummary` with optional `displayName`, `version`, `starCount`.
- In `useAgents.toSummary()`, map from `AgentDto`:
  - `displayName <- dto.displayName`
  - `version <- latest published version if currently available from dto payload; otherwise undefined`
  - `starCount <- dto.starCount ?? 0`

### Card Structure
- `AgentCard` updated to match `SkillCard` rhythm:
  - Header:
    - left: `displayName ?? name`
    - right: namespace badge (`@${namespace}` style; use `global` fallback)
  - Body:
    - 2-line clamped description
  - Footer:
    - version chip: `v{version}` or `—`
    - downloads: icon + `—`
    - stars: bookmark icon + `starCount`

### Height Consistency
- Keep `h-full` and footer pushed by `mt-auto`.
- Add a minimum content height class on card container to stabilize visual rhythm across rows (`min-h-*`), matching skills-page density.

## Testing

- Update `agent-card.test.tsx`:
  - renders namespace badge
  - renders version chip fallback `—`
  - renders download placeholder `—`
  - renders star count
  - renders height class for consistency
- Keep `agents.test.tsx` regression green.

## Success Criteria

- `/agents` cards visually align with `/skills` cards in header/footer rhythm.
- Namespace, version, downloads placeholder, and stars are visible on each card.
- Card height appears consistent within list layout.
- Existing navigation and page tests remain passing.

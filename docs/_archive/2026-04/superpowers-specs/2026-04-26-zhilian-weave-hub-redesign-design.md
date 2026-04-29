# 知联 Weave Hub — Home & Agent Channel Redesign Spec

> ✅ **SHIPPED — 实施完成 2026-04-27 (拆为 P0-1a + P0-1b)。** 原 spec 中 "Agent column will render empty state until backend lands" 的 stub 描述**已不适用** — agents.tsx 已是全量列表页 + LandingHotSection 已混排 skill/agent。详见 [docs/plans/2026-04-29-spec-status-ledger.md](../../plans/2026-04-29-spec-status-ledger.md)。

> Source brainstorm plan: `~/.claude/plans/abundant-discovering-wreath.md`

## 1. Context

SkillHub already has a complete Skill management surface (entities, versioning, publishing, search, governance). The user wants to:

1. Optimize the **home page** — layout, color, navigation
2. Establish an **Agent channel** managed identically to Skills (publish/version/search) but visually and informationally distinct
3. Stay **fully additive** — no break to existing Skill features
4. Embody **content over form** — focus on operating high-quality Skill+Agent resources, not flashy visuals

Current state (verified in code, 2026-04-26):

- `/agents` route declared at [web/src/app/router.tsx:416-420](web/src/app/router.tsx#L416-L420) but **omitted from `routeTree.addChildren(...)` at line 422-458 — it 404s today**
- [web/src/pages/agents.tsx](web/src/pages/agents.tsx) is a stub "under construction" page
- [web/src/pages/home.tsx](web/src/pages/home.tsx) only renders Skills (Hero → popular → latest → quick start)
- [web/src/index.css](web/src/index.css) defines a purple/indigo brand gradient (`--brand-start: #6A6DFF`, `--brand-end: #B85EFF`)
- `/` mounts `LandingPage`; `/skills` mounts `HomePage`. So "the home page" = `web/src/pages/home.tsx` reachable at `/skills`
- i18n keys live at [web/src/i18n/locales/{en,zh}.json](web/src/i18n/locales/en.json); `nav.agents` already exists as "Agents" / "智能体"
- `useSearchSkills` is at [web/src/shared/hooks/use-skill-queries.ts:55](web/src/shared/hooks/use-skill-queries.ts#L55), URL built by [web/src/shared/hooks/skill-query-helpers.ts:5](web/src/shared/hooks/skill-query-helpers.ts#L5) — does **not** currently emit `type=...` query param

## 2. Final Decisions

| Aspect | Decision |
|---|---|
| Product UI name | **知联**（Weave Hub）— 中英并列，仅 UI 层改名，仓库/API/DB 仍叫 `skillhub` |
| Slogan | 知道你能做什么，连接你想做的事 / Where Skills Weave into Agents |
| Channel colors | Skill = green (`152 60% 36%`), Agent = orange (`24 90% 50%`), brand gradient changes from purple→violet to **green→orange** |
| Home IA | Two-column dual-channel: Skill column \| Agent column for both Popular and Latest sections |
| Navigation | `Home · Skills · Agents · Search · Publish · Dashboard` (single Publish entry; Agent publish disabled with tooltip) |
| Stats section | Not added (avoid placeholder data) |
| Backend | Only ship the **frontend contract**: `useSearchSkills` accepts optional `type: 'SKILL' \| 'AGENT'` and forwards it as a query param. Server may ignore it for now — Agent column will render an empty state until backend lands. **No DB migrations, no controller changes in this plan.** |

## 3. Architecture Overview

**No new architecture layers.** This is a UI/IA refactor with one extension point added to the existing search hook.

- New CSS tokens for Skill green / Agent orange added to `:root` in [web/src/index.css](web/src/index.css)
- New presentational component `AgentCard` mirrors `SkillCard` with orange accent
- New shared `ChannelBadge` for "Skill" / "Agent" pills (used in cards, channel headers, and future search-result rows)
- `home.tsx` renders two `<DualChannelRow>` sections (one Popular, one Latest), each composed of two parallel column subtrees driven by `useSearchSkills({type: 'SKILL'|'AGENT'})`
- `agents.tsx` becomes a real channel list page mirroring the existing `/skills` (HomePage) layout, restricted to `type='AGENT'`
- `router.tsx` is fixed to actually mount `agentsRoute`
- `layout.tsx` nav reordered, mobile hamburger added

## 4. Files

### Modify

| File | Purpose |
|---|---|
| [web/src/index.css](web/src/index.css) | Add channel CSS tokens; flip `--brand-gradient` to green→orange |
| [web/src/app/layout.tsx](web/src/app/layout.tsx) | Reorder nav, add mobile hamburger, soften decorative orb |
| [web/src/app/router.tsx](web/src/app/router.tsx) | Add `agentsRoute` to `routeTree.addChildren(...)` |
| [web/src/pages/home.tsx](web/src/pages/home.tsx) | Replace single-column lists with dual-channel rows; update copy |
| [web/src/pages/agents.tsx](web/src/pages/agents.tsx) | Replace stub with real list page (orange channel hero + grid) |
| [web/src/shared/hooks/use-skill-queries.ts](web/src/shared/hooks/use-skill-queries.ts) | Pass `type` through to `searchSkills` |
| [web/src/shared/hooks/skill-query-helpers.ts](web/src/shared/hooks/skill-query-helpers.ts) | Emit `type` query param when present |
| [web/src/api/types.ts](web/src/api/types.ts) | Add `type?: 'SKILL' \| 'AGENT'` to `SearchParams`; add same to `SkillSummary` (optional) |
| [web/src/i18n/locales/en.json](web/src/i18n/locales/en.json) | Add `brand.*`, channel labels, dual-section copy |
| [web/src/i18n/locales/zh.json](web/src/i18n/locales/zh.json) | Same in Chinese |

### Create

| File | Purpose |
|---|---|
| [web/src/features/agent/agent-card.tsx](web/src/features/agent/agent-card.tsx) | Card visually parallel to SkillCard, orange accent |
| [web/src/features/agent/agent-card.test.ts](web/src/features/agent/agent-card.test.ts) | Named-export sanity test (matches existing convention) |
| [web/src/shared/components/channel-badge.tsx](web/src/shared/components/channel-badge.tsx) | "Skill" / "Agent" pill (green / orange soft) |
| [web/src/shared/components/channel-badge.test.ts](web/src/shared/components/channel-badge.test.ts) | Pure-function test for badge variants |
| [web/src/shared/components/dual-channel-row.tsx](web/src/shared/components/dual-channel-row.tsx) | Generic two-column "Skill | Agent" section used by Home |
| [web/src/shared/components/dual-channel-row.test.ts](web/src/shared/components/dual-channel-row.test.ts) | Named-export sanity test |

## 5. Out of Scope (explicit)

- Backend `Skill.type` column / migration / controller filter (frontend sends the param; server may 200 without filtering)
- AGENT.md parser, `AgentConfig` entity, Agent publish service / UI
- Agent execution engine, workflow designer, template marketplace
- Stats counters, A/B tests, performance dashboard

## 6. Verification

After implementation:

1. **No regressions**: `pnpm test` and `pnpm typecheck` pass; `/skills` (HomePage), `/search`, `/space/:ns/:slug`, `/dashboard/*` all render and behave as before
2. **`/agents` resolves** (no longer 404) and shows orange channel hero + (likely empty) grid + empty-state CTA
3. **Home page** shows two dual-channel rows (Popular / Latest), each with a Skill column (green accent) and Agent column (orange accent or empty state)
4. **Navigation order**: Home, Skills, Agents, Search, Publish, Dashboard — visible at `md+`, collapsible hamburger on smaller widths
5. **Color**: brand gradient is green→orange in title, footer logo, active nav pill; no leftover purple identifying any non-decorative element. Decorative orb in layout becomes a low-opacity warm wash.
6. **Manual smoke**: switch language EN↔ZH, all new strings appear translated; auth menu unchanged

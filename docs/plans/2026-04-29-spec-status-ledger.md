# Spec / Plan Status Ledger

**Date:** 2026-04-29
**Purpose:** Single source of truth for the status of every spec / plan / design doc in the repo. Replaces ad-hoc "is this done?" archaeology across `git log` + `memo/memo.md`.

**Scope:** `docs/plans/*.md`, `docs/superpowers/plans/*.md`, `docs/superpowers/specs/*.md`. Excludes `docs/diagnosis/` (already snapshot-style) and `docs/adr/` (decisions, not work items).

**Convention:**
- ✅ **Shipped** — feature is in `main`, verified via commits + tests
- 🔁 **Living** — intentionally open (e.g., the fork backlog)
- 📝 **Reference** — meta-doc, not a work item
- ⏸ **Deferred** — explicitly out of scope per the doc itself; tracked below
- 🔧 **Open** — not yet started

**Cleanup 2026-04-29:** 已完成的 4 月 spec/plan **顶部已加 SHIPPED 横幅**指向本 ledger;两份易引起混淆的文件(`2026-04-26-comments-feature-requirements.md` SUPERSEDED、`2026-04-26-agents-frontend-mvp-START-PROMPT.md` Reference 模板)已**移到 `docs/archive/2026-04/`**。`docs/plans/2026-04-27-fork-backlog.md` 顶部 "当前快照" 段已重写,移除矛盾的 2026-04-27 历史 audit 备注(测试 baseline 460/460 / 视觉路线"重置" / P0-2 P2-2 audit 修正等)。

---

## ✅ Shipped (21)

All shipped items have memo entries under `memo/memo.md` and verified commit ranges. Listed for inventory only — no action needed.

### `docs/superpowers/specs/`

| # | File | Memo entry | Evidence |
|---|---|---|---|
| 1 | `2026-03-12-phase2-namespace-skill-core-design.md` | Phase 2 baseline | `V2__phase2_skill_tables.sql` + SkillService |
| 2 | `2026-03-12-phase3-review-cli-social-design.md` | Phase 3 baseline | `V3__phase3_review_social_tables.sql` + PromotionService |
| 3 | `2026-03-12-phase4-ops-polish-design.md` | Phase 4 baseline | `V5__phase4_auth_governance.sql` + Prometheus wiring |
| 4 | `2026-03-16-namespace-governance-design.md` | Phase 4 follow-on | `NamespaceGovernanceService` |
| 5 | `2026-03-19-notification-system-design.md` | Phase 4 follow-on | `V37__notification_system.sql` |
| 6 | `2026-03-20-skill-detail-hard-delete-design.md` | Phase 4 follow-on | `SkillDeleteController` + skill-detail.tsx confirmation |
| 7 | `2026-04-27-agent-detail-alignment-design.md` | 2026-04-27 entries | agent-detail.tsx aligned with skill-detail UX |
| 8 | `2026-04-27-agents-card-alignment-design.md` | 2026-04-27 entries | AgentCard mirrors SkillCard |
| 9 | `2026-04-27-weavehub-visual-overhaul-design.md` | 2026-04-27 P0-1a/P0-1b | Inter font + green brand tokens shipped |
| 10 | `2026-04-28-agent-promotion-design.md` | 2026-04-28 A9 | A9 19-task plan complete (`b3cba0c5` → `ddb82067`) |
| 11 | `2026-04-28-p0-followups-and-backlog-sync-design.md` | 2026-04-28 entry | 6 commits `2266fd4e` → `f0a209e6` |
| 12 | `2026-04-29-agent-followups-bundle-design.md` | 2026-04-29 entry | 4 tasks complete `2c17ae30` → `38b42297`, web 706/706 |

### `docs/superpowers/plans/`

| # | File | Memo entry | Evidence |
|---|---|---|---|
| 13 | `2026-03-11-phase1-foundation-auth.md` | Phase 1 baseline | Auth + Maven structure live |
| 14 | `2026-03-12-phase2-namespace-skill-core.md` | Phase 2 baseline | (see spec #1) |
| 15 | `2026-03-12-phase3-review-cli-social.md` | Phase 3 baseline | (see spec #2) |
| 16 | `2026-03-12-phase4-ops-polish.md` | Phase 4 baseline | (see spec #3) |
| 17 | `2026-03-14-dev-workflow-optimization.md` | March infra | Makefile + local-env shipped |
| 18 | `2026-03-16-namespace-governance.md` | (see spec #4) | (see spec #4) |
| 19 | `2026-04-28-agent-promotion.md` | 2026-04-28 A9 | (see spec #10) |
| 20 | `2026-04-28-p0-followups-and-backlog-sync.md` | (see spec #11) | (see spec #11) |
| 21 | `2026-04-29-agent-followups-bundle.md` | (see spec #12) | (see spec #12) |

### `docs/plans/`

| # | File | Memo entry | Evidence |
|---|---|---|---|
| 22 | `2026-04-26-agents-frontend-mvp.md` | 2026-04-26 Agents Frontend MVP | 12 tasks `87c361db` → `ba343843` |
| 23 | `2026-04-26-landing-page-dual-channel.md` | 2026-04-26 Landing dual-channel | 7 commits `f01dcccc` → `f5b58d7c` |
| 24 | `2026-04-26-skill-version-comments.md` | 2026-04-26 Skill comments | V40 + SkillVersionCommentController shipped |
| 25 | `2026-04-27-agent-archive-and-rating.md` | 2026-04-27 + 2026-04-28 audit | A0/A2/A3 全栈完成 |
| 26 | `2026-04-27-agent-list-search.md` | P0-2 close-out | `93bfff15` (api) + `da7e4c68` (web) + `d3993cbc` (memo) |
| 27 | `2026-04-27-agent-publish-review-pipeline.md` | 2026-04-27 Phase E | 30-task plan complete; AgentReviewController + agent-publish UI live |
| 28 | `2026-04-27-agent-skill-parity-cluster.md` | 2026-04-27 parity cluster | A4/A7/A8/A10 done in cluster; A9 split off (now ✅) |
| 29 | `2026-04-27-weavehub-landing-ia.md` | 2026-04-27 P0-1b | `9402e3aa` → `60da6145`; landing.tsx rewrite + /my-weave route |
| 30 | `2026-04-27-weavehub-tokens.md` | 2026-04-27 P0-1a | green tokens migrated; web 635/635 |
| 31 | `2026-04-28-open-registration-and-team-creation.md` | 2026-04-28 entry | `cc4073f5` |

### `docs/superpowers/specs/` 设计文档但实际是 P0-1a/P0-1b 的上游 brainstorm

| # | File | Status | Notes |
|---|---|---|---|
| 32 | `2026-04-26-zhilian-weave-hub-redesign-design.md` | ✅ Shipped (via P0-1a + P0-1b) | Spec is the design contract, P0-1a 完成 tokens，P0-1b 完成 IA。原 spec 中提到的 Agent column "stub" 已被 LandingHotSection 混排 + agents.tsx 全量页面替代。 |
| 33 | ~~`docs/plans/2026-04-26-agents-frontend-mvp-START-PROMPT.md`~~ → `docs/archive/2026-04/` | 📝 Reference (已归档) | Subagent 启动模板，不是实施计划 |
| 34 | ~~`docs/plans/2026-04-26-comments-feature-requirements.md`~~ → `docs/archive/2026-04/` | ✅ Superseded (已归档) | Requirements memo；后续被 ADR 0002 + skill-version-comments plan + `cc11f651` (agent comments) 实施。原文 "Open questions" 已在实施中回答（独立表方案）。归档文件顶部加了 SUPERSEDED 横幅。 |
| 35 | `2026-04-26-zhilian-weave-hub-redesign.md` (plans/) | ✅ Shipped (via P0-1a + P0-1b) | 同 #32 |

### 🔁 Living

| # | File | Status |
|---|---|---|
| 36 | `docs/plans/2026-04-27-fork-backlog.md` | 🔁 Living tracker — 持续维护，不封口 |

---

## ⏸ Deferred (explicit out-of-scope items)

这些是在已完成 spec 内**明确写明延后**的工作，不是"还没做"，是"故意先不做"。如果决定做，需要先 brainstorm + 新 plan。

来源主要是 **A9 Agent Promotion Spec §3 / §15** 和 **ADR 0004**，外加 P0 follow-up 收尾遗留。

### A9 — Agent Promotion 延后项

| ID | 项 | 来源 | 备注 |
|---|---|---|---|
| D-1 | **`LandingHotSection` 接 promoted agents** | A9 spec §3 / §15 | landing 页热门区当前是 skill+agent 混排但**没有按 promotion 状态加权**；spec 显式延后到 promotion 落地后再做 |
| D-2 | **Agent search index 同步 promotion 状态** | A9 spec §3 / §15 → P3-3 | 已挂在 P3-3 名下；agent 升级到 GLOBAL 后搜索索引 visibility 不会自动重算 |
| D-3 | **Source-link 可追溯字段** (`sourceAgentId` 在 promoted Agent 实体上) | A9 spec §3 | 当前 promoted agent 是物化拷贝，没回链到原 agent |
| D-4 | **Review queue filter tabs** | A9 spec §3 | YAGNI；当前混排即可 |
| D-5 | **Per-source-type 指标 / 可观测性** | A9 spec §3 / §12 | spec 注：~10 LOC 的 `@EventListener` 可后补 |
| D-6 | **Source type 进通知 body JSON** | A9 spec §15 | 通知系统暂不区分 skill / agent promotion |
| D-7 | **`LabelDefinition` 加 namespace scope 后过滤拷贝标签** | A9 spec §3 | 条件触发；当前 LabelDefinition 没有 namespace 概念 |
| D-8 | **公开 `/namespaces/global` 端点** | ADR 0004 | 当前 backend 默认 GLOBAL（commit `2c17ae30`）已绕过；属于"代码优雅性"而非功能缺失 |

### 其他显式延后

| ID | 项 | 来源 | 备注 |
|---|---|---|---|
| D-9 | **A6 admin moderation 接 agent reports** | fork-backlog 启动建议 #1 | fork-backlog 标 0.5–1 天；2026-04-29 followups bundle Task 3 实际**已完成**（AdminAgentReportController + reports.tsx Tabs） — **可移出延后**，下次迭代时清理 |
| D-10 | **List 卡片平均评分展示** | fork-backlog 启动建议 #3 | 2026-04-29 followups bundle Task 2 实际**已完成**（AgentSummary +ratingAvg/Count，AgentCard 渲染） — **可移出延后** |
| D-11 | **My Stars 页 Agent 段** | fork-backlog 启动建议 #4 | 2026-04-29 followups bundle Task 4 实际**已完成** — **可移出延后** |
| D-12 | **P2-4 bean validation 接通** | fork-backlog 启动建议 #5 | 后端潜伏炸弹清理；未启动 |
| D-13 | **P3-2b validator chain 扩展** | fork-backlog 启动建议 #6 | `PrePublishValidator` 改 `List<...>` / chain + 加规则；P3-2a 接入已完成（`5d62a75b`），P3-2b 未启动 |

---

## 🔧 Open (待启动)

- **D-12** P2-4 bean validation 接通
- **D-13** P3-2b validator chain 扩展

A9 §3/§15 那 7 项（D-1 ~ D-7）+ D-8 都属于"显式延后"，需要先决定要不要做、何时做，再起 plan。

---

## 维护规则

1. **新 spec/plan 完成后**：在表格里补一行，给 commit 区间或证据；不动原 spec 文件。
2. **新延后项产生时**（spec 写到 "out of scope" / "deferred"）：补到 ⏸ 段，注明来源章节。
3. **延后项决定启动时**：从 ⏸ 移到 🔧 Open，并起独立 plan。
4. **此文件本身**：不是 living 文档，但每次集中盘点（season-end / 大版本前）刷新一次。

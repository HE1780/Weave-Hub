# Archived planning documents

Documents in this directory describe **previous** plans that have either
shipped, been superseded, or no longer reflect the current direction of the
fork. They are kept (rather than deleted) so future readers can trace why
the project arrived at its present shape.

**For the current state of work:**

- Status ledger (single source of truth): [docs/plans/2026-04-29-spec-status-ledger.md](../plans/2026-04-29-spec-status-ledger.md)
- Living roadmap / backlog: [docs/plans/2026-04-27-fork-backlog.md](../plans/2026-04-27-fork-backlog.md)
- Latest progress + gap report: [docs/diagnosis/](../diagnosis/)
- Project scope ADR: [docs/adr/0003-fork-scope-and-upstream-boundary.md](../adr/0003-fork-scope-and-upstream-boundary.md)

> 历史 specs/plans 在 2026-04-29 集中归档,原 `docs/superpowers/{plans,specs}/` + `docs/plans/` 中已完成的文件全部移到本目录的 `2026-03/` / `2026-04/` 子目录,**不要据此再起新 plan**。完成证据见 git log + `memo/memo.md`。

## Index

### `2026-04-26-pre-fork-todo.md`
Original "Phase 1 / Week 1" Agent feature todo list, written before the fork
adopted ADR 0003 and the WeaveHub aesthetic. By 2026-04-27 every Phase 1
item had shipped (Agent publish / review / list / detail / search /
archive / star / rating UI all live), and Phase 2/3 sections were either
delivered, deprecated (`Workflow Executor` is explicitly out of scope —
see ADR 0003 §1.3 amendment), or replaced by the Agent–Skill alignment
cluster A0–A10 in the current backlog. **Do not use this file as a planning
source.**

### `2026-03/`
March phase 1-4 baseline plans + design specs (foundation auth / namespace+skill core / review-CLI-social / ops-polish + namespace-governance / notification / hard-delete / dev-workflow). 全部在 4 月初前已 shipped。

### `2026-04/`
4 月 agent / weavehub / promotion 全部已完成的 specs + plans:
- `plans/`: agents-frontend-mvp / landing-page-dual-channel / skill-version-comments / agent-archive-and-rating / agent-list-search / agent-publish-review-pipeline / agent-skill-parity-cluster / weavehub-tokens (P0-1a) / weavehub-landing-ia (P0-1b) / open-registration-and-team-creation
- `superpowers-plans/`: zhilian-weave-hub-redesign / agent-promotion (A9) / p0-followups-and-backlog-sync / agent-followups-bundle
- `superpowers-specs/`: 对应 6 份 design specs(visual-overhaul / agent-detail-alignment / agents-card-alignment / zhilian-weave-hub-redesign-design / A9 design / P0 followups design / agent-followups-bundle design)
- 顶层: comments-feature-requirements (SUPERSEDED by ADR 0002 + V40)、agents-frontend-mvp-START-PROMPT (subagent 启动模板)

每份文件顶部已加 SHIPPED / SUPERSEDED 横幅。状态总账见根 ledger。

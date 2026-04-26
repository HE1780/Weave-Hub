# Session 记录

此文件记录当前 session 的工作进度，可跨 session 覆写或追加。

---

## 2026-04-26 — Agents Frontend MVP 实施完成

执行了 `docs/plans/2026-04-26-agents-frontend-mvp.md`，全部 12 个任务通过 subagent-driven-development 流程交付。

**12 个 commit (e54766d4..HEAD)**：
- 87c361db i18n keys
- 032d291a Agent types (`web/src/api/agent-types.ts`)
- 6f67aa00 mock 数据 (`web/src/features/agent/mock-agents.ts`)
- b1910fbd useAgents hook (TDD)
- 703dc30e useAgentDetail hook (TDD)
- 181a9cfa **infra**: 加 `@testing-library/jest-dom` + `jsdom` + setupFiles
- e8b98ad5 AgentCard
- 4992e888 WorkflowSteps
- 5ce1e3c4 agents.tsx 列表页
- c73eb828 agent-detail.tsx 详情页
- 1d44913d 路由注册 `/agents` + `/agents/$name`
- ba343843 nav 加 "Agents/智能体" 链接

**测试**: 181 files / 557 tests 全绿
**tsc**: 仅 4 个错误，全部在预先就脏的 `web/src/pages/registry-skill.tsx`（不在本计划范围）
**浏览器烟雾测试**: 列表 + 三个详情 + 未知 name + en/zh 切换 全部通过

**未做的事**（按计划要求）：未推送、未开 PR、未合并。等待用户决定。

**下一步可做的跟进**（来自最终 code review，全部 NICE-TO-HAVE）：
1. AgentCard 的 `role="link"` + `tabIndex={0}` 没有键盘 (Enter/Space) 测试覆盖；改用 TanStack `<Link>` 是更彻底的修复。
2. agents.tsx 用 `navigate()` 而非 `<Link>`，cmd-click/middle-click/右键打开新标签都不工作；和现有 skill-card 同病。
3. workflow-steps.tsx 里 `WorkflowSteps` 和 `StepBody` 都各自调 `useTranslation()`，可以提到一起。
4. 三个 hook 测试都各自重建 QueryClient，可以抽出 `createWrapper()` helper。
5. agent-detail.tsx 里 "not found" 和 "network error" 共用同一条 `agents.loadError`，等真后端接入后区分。

**关键架构决策**：mock-vs-API 切换面在 `useAgents.queryFn` 和 `useAgentDetail.queryFn` 两个函数体内，符合计划承诺的"换后端只动一处"。

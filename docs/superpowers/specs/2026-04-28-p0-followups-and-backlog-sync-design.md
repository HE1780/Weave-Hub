# Spec — P0 Follow-ups + Fork Backlog Sync

**Date:** 2026-04-28
**Author:** session
**ADR:** 0003 §1.1 (fork 自有路线收尾) + 0004 (agent promotion follow-ups)
**Branch:** `feat/p0-followups-and-backlog-sync`

## Goal

闭合 `memo/memo.md` 2026-04-28 两条 entry 列出的 P0 follow-ups，并把 `docs/plans/2026-04-27-fork-backlog.md` 的 A0/A2/A3/A5/A6 状态校正为代码现状，把 sub-agent audit 期间发现的"baseline regression 误判"沉淀到 lessons.md。

## Audit 事实（已与代码核对）

- `NamespaceBatchMemberControllerTest.batchAddMembers_emptyArray_returnsError` —— 测试断言期望 500，实际返回 400。**这不是 upstream regression**，是测试预期写错（Spring Boot 在 record `@RequestBody` + `@Valid` 路径上对空数组校验本来就抛 `MethodArgumentNotValidException` → 400）。memo 04-28 把它定性为 baseline regression 是误判。
- A0 (archive/unarchive) backend ✅ ([AgentLifecycleController.java](../../../server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/AgentLifecycleController.java))，frontend ✅ ([use-archive-agent.ts](../../../web/src/features/agent/use-archive-agent.ts) 已挂 my-agents + agent-detail)。backlog 描述"app 层缺 + 前端缺"过期。
- A2 (star) backend + frontend ✅ ([AgentStarController.java](../../../server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/AgentStarController.java) + [agent-star-button.tsx](../../../web/src/features/agent/social/agent-star-button.tsx) 已挂 [agent-detail.tsx:702](../../../web/src/pages/agent-detail.tsx#L702))。backlog 标 ❌ 过期。
- A3 (rating) backend + frontend ✅ ([AgentRatingController.java](../../../server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/AgentRatingController.java) + [agent-rating-input.tsx](../../../web/src/features/agent/social/agent-rating-input.tsx) 已挂 [agent-detail.tsx:708](../../../web/src/pages/agent-detail.tsx#L708))。backlog 标 ❌ 过期。
- A5 (tag) backend ✅ (V46 + AgentTagController GET/PUT/DELETE)，但**对齐目标 SkillTag 也没有前端 UI**——它是 admin/CLI 给 version 起 alias 名 (类似 docker `latest` tag) 的功能。所以 "前端缺失" 是误读，对齐已经完成。
- A6 (report) backend ✅，提交端前端 ✅（[use-report-agent.ts](../../../web/src/features/agent/use-report-agent.ts) + agent-detail 举报 dialog），但 admin moderation dashboard ([reports.tsx](../../../web/src/pages/dashboard/reports.tsx)) 只接 skill reports，agent reports 列表/审核未接通。**真实缺口**，但属中等改动，不在本次 P0 范围。
- PromotionController 的 agent dispatch 分支没有专属单元测试（memo 04-28 follow-up #1）。
- `web/src/pages/promotions.tsx` 的 source-type badge 用 inline Tailwind 颜色 `bg-blue-*` / `bg-purple-*`，应 tokenize（memo 04-28 follow-up #5）。

## Scope（本次做）

### Track A — 测试/文档校正

1. **改 `NamespaceBatchMemberControllerTest.batchAddMembers_emptyArray_returnsError`** 期望 400 + 校正注释，解释 Spring Boot record-body validation 实际语义。
2. **更新 `docs/plans/2026-04-27-fork-backlog.md`**:
   - A0 / P2-2 → ✅ 已完成 (2026-04-27)，写出实际承载文件
   - A2 → ✅ 已完成 (2026-04-28 之前)
   - A3 → ✅ 已完成 (2026-04-28 之前)
   - A5 → ✅ 已对齐 (2026-04-28)，备注"named version tag = admin/CLI surface, Skill 侧亦无前端 UI"
   - A6 → 🟡 部分完成：提交端 ✅，moderation dashboard ❌（独立 follow-up）
3. **更新 `memo/lessons.md`**：
   - "sub-agent audit 报告必须人工交叉校验" 模式
   - "把 baseline 测试失败定性为 regression 之前先读测试本身" 模式
4. **修正 `memo/memo.md` 04-28 'open registration' entry 中的 🔥 follow-up 描述**：把 "upstream regression" 改为 "test assertion was wrong"，并指向 Track A 的修复 commit。

### Track B — P0 follow-up 真代码

5. **PromotionController agent 路径单元测试**：在 `PromotionPortalControllerTest`（或现有 controller 测试类）加 2-3 个 case，覆盖 `dto.sourceType()=AGENT` 的 dispatch 分支：成功提交、缺字段返 400、agent 不存在返 404。
6. **promotions.tsx source-type badge tokenize**：把 inline `bg-blue-*` / `bg-purple-*` 提取到 `web/src/shared/lib/source-type-color.ts`（或就在文件顶部 `const sourceTypeStyles`），同步更新 i18n key 不变。

### Out of Scope（明确延后）

- A6 admin moderation dashboard 接通 agent reports（独立 brainstorm，需改 reports.tsx 双 list + 类型过滤 + agent dispose 端点对接）
- Public `/namespaces/global` 端点（独立 plan，涉及 RouteSecurityPolicy）
- LandingHotSection 接 promoted agents
- Agent search index 同步

## Verification

每个改动单独跑相关测试：

| 改动 | 验证命令 |
|---|---|
| Track A.1 | `cd server && mvn test -pl skillhub-app -Dtest=NamespaceBatchMemberControllerTest` 全部通过 |
| Track A.2-4 | 视觉 review，无代码影响 |
| Track B.5 | `cd server && mvn test -pl skillhub-app -Dtest=PromotionPortalControllerTest`（或新建 test class） |
| Track B.6 | `cd web && npm test -- promotions` 仍通过；视觉抽查 promotions 页 badge 颜色未变 |
| 回归 | 全 backend `mvn test` + 全 web `npm test` 不退化（baseline 560/561 → 561/561 因为 A.1 修复了那条；web 684/684 不变） |

## Risk

- **Track A.1 风险**：改测试断言可能掩盖真实问题 → 已亲自跑过 baseline 上同一测试，输出确实是 400 + body 是合法 ApiResponse 错误格式，确认 400 才是对的语义。注释中写清楚为何不是 500。
- **Track B.5 风险**：如果 PromotionPortalControllerTest 需要 mock agent repository 而现有 setup 不支持 → 退而新建独立 test class。
- **Track B.6 风险**：tokenize 时可能漏掉 hover/dark 变体 → grep 全 `promotions.tsx` 内所有 `bg-(blue|purple)` 出现确保替换完整。

## How to resume

完成后 push，把 04-28 memo 那行 🔥 follow-up 标记为已闭合，`memo/memo.md` 加 04-28 第三条 session entry。下一波建议从 backlog 校正后**真实**ortega的 ❌ 项启动：A6 moderation dashboard、A1 (Agent 评论) 已完成？需再 audit、P3-2 (Agent 安全扫描)。

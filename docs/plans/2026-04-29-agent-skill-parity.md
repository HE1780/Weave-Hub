# Agent ↔ Skill 流程对齐改造（A2 完整对齐版）

> ✅ **SHIPPED 2026-05-01**(merge `b250461e`)。后端 1180 测试 + 前端 708 测试全过。后续 polish 见 [memo](../../memo/memo.md#2026-05-01)。

- **分支**:`feat/agent-skill-parity`(已合 main 并删除)
- **创建日期**：2026-04-29（修订 2026-04-30）
- **关联 ADR**：ADR 0003（fork 主动做：Agent 自有能力补齐）
- **数据策略**：当前为测试数据，**直接 drop 表重建**，无迁移

## 决策结果

| 决策 | 结果 |
|---|---|
| 状态机粒度 | 完整对齐 skill：增加 `UPLOADED / SCANNING / SCAN_FAILED / YANKED` 四个新状态 |
| 安全扫描 | 引入 `AgentSecurityScanService` 占位实现（同步立刻 PASS），状态机经过 SCANNING → UPLOADED |
| SUPER_ADMIN 跳审 | 支持，行为对齐 `SkillPublishService` 中的 `forceAutoPublish` |
| 历史数据 | drop & recreate，不写迁移脚本 |
| 举报处置 | `AgentReportDisposition` 增加 `RESOLVE_AND_HIDE` |
| 搜索接入 | 前端 `pages/search.tsx` 增加 agent tab |

## 改动总图

### 1. Domain 层（`server/skillhub-domain`）

- **`AgentVersionStatus.java`**：新增 `UPLOADED, SCANNING, SCAN_FAILED, YANKED`
- **`AgentVersion.java`**：
  - 默认状态从 `DRAFT` 改为 `SCANNING`
  - 新增 `setStatus(...)`（包级 / 受保护，仅给 ScanService 用）/ `markUploaded()` / `markScanFailed()` / `yank(String, String)` 等方法
  - 增加 `yankedAt / yankedBy / yankReason / requestedVisibility / latestVersion` 等字段（参考 `SkillVersion`）
- **`Agent.java`**：增加 `hidden` 字段（boolean）、`hiddenAt / hiddenBy / hideReason`、`latestVersionId`
- **`AgentSecurityScanService.java`**（新建）：占位实现，`triggerScan` 直接把状态从 `SCANNING` 翻到 `UPLOADED`
- **`AgentReviewSubmitService.java`**（新建）：仿 `SkillReviewSubmitService`
  - `submitForReview(agentId, versionId, targetVisibility, actor, roles)` —— UPLOADED → PENDING_REVIEW，新建 `AgentReviewTask`
  - `confirmPublish(agentId, versionId, actor, roles)` —— UPLOADED → PUBLISHED（仅 PRIVATE）
- **`AgentPublishService.java`**：重写发布流
  - 不再调 `submitForReview()` 或 `autoPublish()`
  - 改为 `version.setStatus(SCANNING)` → 调 `AgentSecurityScanService.triggerScan(...)` 进入 UPLOADED
  - 新增 `forceAutoPublish` 入参；SUPER_ADMIN 时直接 PUBLISHED 跳过整个流程
  - PRIVATE 也走 SCANNING → UPLOADED；UPLOADED 状态下 PRIVATE 由用户手动 `confirmPublish`
- **`AgentReviewService.approve`**：保持 PENDING_REVIEW → PUBLISHED 行为不变
- **`AgentLifecycleService.java`**：
  - `rereleaseVersion` 入口的 source-status 校验加上 UPLOADED
  - `yankVersion(versionId, reason, actor)` 新增 — PUBLISHED → YANKED
- **`AgentDownloadService.java`**：UPLOADED/PENDING_REVIEW/SCANNING/SCAN_FAILED 仅作者可下载（对齐 skill 规则）
- **`AgentService.java`**（query）：UPLOADED/SCANNING/SCAN_FAILED 不进公开列表，仅作者 + 命名空间管理员可见
- **`AgentGovernanceService.java`**（新建）：
  - `hideAgent(agentId, reason, actor)` / `unhideAgent(agentId, actor)` / `yankVersion(versionId, reason, actor)`
- **`AgentReportDisposition.java`**：增加 `RESOLVE_AND_HIDE` 枚举值
- **`AgentReportService.java`**：处置分支增加 `RESOLVE_AND_HIDE` 分支，调 `AgentGovernanceService.hideAgent`

### 2. App 层（`server/skillhub-app`）

- **`AgentLifecycleAppService.java`**（新建，对齐 `SkillLifecycleAppService`）：
  - `submitForReview` / `confirmPublish` 两个对外方法
  - 复用 `assertCanManageLifecycle` 模式
  - `auditLogService.record(SUBMIT_REVIEW / CONFIRM_PUBLISH, AGENT_VERSION, ...)`
- **`AdminAgentAppService.java`**（新建）：
  - `hideAgent / unhideAgent / yankVersion`，权限检查 + 审计日志
- **`AdminAgentReportAppService.java`**：处置入口增加 `RESOLVE_AND_HIDE` 分支
- **`controller/portal/AgentLifecycleController.java`**：
  - 新增 `POST /{ns}/{slug}/submit-review`（body `targetVisibility`）
  - 新增 `POST /{ns}/{slug}/versions/{version}/confirm-publish`（注意路径含 `/versions/{version}` 与 skill 保持一致 —— 见下文路径细节）
- **`controller/admin/AdminAgentController.java`**（新建）：
  - `POST /api/v1/admin/agents/{agentId}/hide`
  - `POST /api/v1/admin/agents/{agentId}/unhide`
  - `POST /api/v1/admin/agents/versions/{versionId}/yank`
- **`controller/admin/AdminAgentReportController.java`**：解除 `RESOLVE_AND_HIDE` 屏蔽，与 skill 一致只校验 SUPER_ADMIN

### 3. 测试

- 复制 `SkillReviewSubmitServiceTest` → `AgentReviewSubmitServiceTest`
- 复制 `SkillLifecycleControllerTest` 涉及 submit-review/confirm-publish 部分 → `AgentLifecycleControllerTest`
- 新增 `AgentSecurityScanServiceTest`（占位实现的最小验证）
- 新增 `AdminAgentControllerTest`
- 现有 `AgentPublishServiceTest` 全部用例需要更新（旧期望是 `submitForReview()` 自动调用，新期望是进入 SCANNING）

### 4. 前端（`web/src`）

- **`api/client.ts`**：
  - `agentLifecycleApi` 新增 `submitForReview(namespace, slug, version, targetVisibility)` / `confirmPublish(namespace, slug, version)`
  - `adminApi` 新增 `hideAgent(agentId, reason)` / `unhideAgent(agentId)` / `yankAgentVersion(versionId, reason)`
  - `reportApi` `AgentReportDisposition` 类型加 `RESOLVE_AND_HIDE`
- **`features/agent/use-submit-agent-review.ts`**（新建）+ **`use-confirm-agent-publish.ts`**（新建）：mutation hooks
- **`features/agent/version-status-badge.tsx`**（新建，复刻 `features/skill/version-status-badge.tsx`）：UPLOADED/SCANNING/SCAN_FAILED/YANKED 状态徽标
- **`pages/agent-detail.tsx`**：
  - UPLOADED 状态展示 "提交审核 / 确认发布" CTA（PRIVATE 显示后者，PUBLIC/NS 显示前者）
  - SCANNING 展示扫描中 banner
- **`pages/search.tsx`**：
  - 顶部 tab `[Skills | Agents]`
  - Agent tab 调 `agentsApi.list({ q, page, size })`，渲染 `<AgentCard>`
  - i18n key 加 `search.tabSkills/tabAgents/emptyAgents`
- **`pages/dashboard/publish-agent.tsx`**：上传成功后跳转到 agent 详情页 UPLOADED 视图（不再是"等审核中"）

### 5. 数据库 drop & recreate

测试数据 drop 表脚本：
```sql
DROP TABLE IF EXISTS agent_review_task;
DROP TABLE IF EXISTS agent_version;
DROP TABLE IF EXISTS agent;
```
然后让 JPA `ddl-auto=update` 在重启时按新实体重建。提交一个 `server/scripts/drop-agent-tables.sh` 便于 dev 手动跑。

## 路径形态决定

跟 skill 端点对齐：
- `POST /api/v1/agents/{ns}/{slug}/submit-review` — body `{ targetVisibility, version }`（**注意**：skill 现版的 `/submit-review` 不带 version 参数，是因为它依赖 skill latestVersionId；agent 也按这个 pattern，但需要先校验 skill 的实际 body 形态)
- `POST /api/v1/agents/{ns}/{slug}/confirm-publish` — body `{ version }`

待实现时再核对 skill 端点的 body 字段，保持完全一致。

## 执行顺序

1. **Step 1（domain 状态机）**：扩 enum、改 AgentVersion / Agent 实体、写 AgentSecurityScanService 占位、改 AgentVersion state methods —— 一个 commit
2. **Step 2（domain 服务）**：写 AgentReviewSubmitService、改 AgentPublishService、改 AgentLifecycleService、改 AgentDownloadService、写 AgentGovernanceService、扩 AgentReportDisposition —— 一个 commit
3. **Step 3（app + controller）**：写 AgentLifecycleAppService、AdminAgentAppService、AgentLifecycleController 加端点、新建 AdminAgentController、改 AdminAgentReportController —— 一个 commit
4. **Step 4（测试）**：补全/修正所有相关单元测试 —— 一个 commit
5. **Step 5（前端）**：API 客户端 + hooks + UI —— 一个 commit
6. **Step 6（最终验证 + 合并）**：drop 表脚本、`mvn test` / `pnpm test` / `pnpm build`，全过则合 main —— 一个 commit

## Definition of Done

- [ ] 后端 `mvn -pl skillhub-app -am test` 全过
- [ ] 前端 `pnpm test` 全过 + `pnpm build` 通过
- [ ] 手工冒烟：上传 PUBLIC agent → SCANNING → UPLOADED → 点提交审核 → PENDING_REVIEW → 审核通过 → PUBLISHED
- [ ] 手工冒烟：上传 PRIVATE agent → SCANNING → UPLOADED → 点确认发布 → PUBLISHED
- [ ] 手工冒烟：admin hide/unhide/yank 三个动作生效
- [ ] 手工冒烟：搜索页 agent tab 能命中
- [ ] 合并到 main 并 push origin

## 不在范围

- 真扫描器对接（占位实现，未来真要扫描时再开 spec）
- 评论 / promotion 子模块的目录结构对称重构
- skill star ID→slug 路径改造

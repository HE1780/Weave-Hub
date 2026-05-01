# Handover：Agent ↔ Skill 流程对齐改造

> ✅ **SHIPPED 2026-05-01**(merge `b250461e`)。本 handover 已无需新会话接管。延后项见 [memo](../../memo/memo.md#2026-05-01)。

> 新会话从此文档开始即可无缝接管。配合 [docs/plans/2026-04-29-agent-skill-parity.md](2026-04-29-agent-skill-parity.md) 一起读。

---

## 1. 任务一句话

让 Agent 模块的发布生命周期 **完全对齐** Skill：从 `DRAFT → SCANNING → UPLOADED → PENDING_REVIEW → PUBLISHED` 一路打通，包括 SUPER_ADMIN 跳审 / 管理员 hide / yank / 举报 RESOLVE_AND_HIDE / 搜索页 agent tab 等所有 skill 已有的能力。

## 2. 当前状态（截至 handover 时刻）

- **分支**：`feat/agent-skill-parity`（已从 `main` 切出）
- **commit 历史**：
  - `3fdd2deb docs(plan): agent-skill parity A2 spec — full state machine alignment`
  - 工作区有 1 个未提交修改：`AgentVersionStatus.java`（已扩枚举，但未提交）
- **stash 暂存**：`stash@{0}: wip-doc-edits-pre-parity-spec` —— 与本任务无关，是上一任务遗留的 docs 编辑（IDE 改动），完成本任务合并 main 之后再决定是否恢复。
- **远程**：`origin/main` 已是最新（`084cd9cc`），分支尚未 push。

## 3. 关键决策记录（用户已拍板，不要再问）

| 决策项 | 结果 | 理由 |
|---|---|---|
| 对齐方案 | A2 完整对齐 | 用户明确选择，保留 SCANNING/SCAN_FAILED 中间态 |
| 安全扫描 | 占位实现（同步立即 PASS） | agent 没真扫描器，但状态机要齐 |
| 历史数据迁移 | drop 重建 | 用户原话："目前全部为测试数据可直接删除 随便改" |
| SUPER_ADMIN 跳审 | 支持 | 对齐 skill 的 forceAutoPublish 行为 |
| 分支策略 | 单分支一气呵成 | 用户原话："一个分支，不要搞那么多phase" |
| 路径形态 | 与 skill 保持一致 | submit-review 在 agent 路径，confirm-publish 同理 |

**伪需求警示**：原本想"加两个端点就能对齐"是错的——agent 现状是上传后 `AgentPublishService.publish` 直接调 `submitForReview()` 自动进 PENDING_REVIEW，PRIVATE 直接 PUBLISHED，没有 UPLOADED 中间态。要做完整对齐就必须重做发布状态机本身。这是用户在确认 A2 时已经知情接受的范围。

## 4. 已完成

1. ✅ 暂存无关的 docs 改动（`git stash`）
2. ✅ 核对 AgentVersionStatus vs SkillVersionStatus 差异
3. ✅ 核对 SkillReviewSubmitService 模式（这是 agent 实现的参考模板）
4. ✅ 写完落地 spec：`docs/plans/2026-04-29-agent-skill-parity.md`
5. ✅ 扩展 `AgentVersionStatus` 枚举：增加 `UPLOADED, SCANNING, SCAN_FAILED, YANKED` 四个新状态（**已修改未提交**）

## 5. 待办（按执行顺序）

> 每步完成后都跑一次 `mvn -pl skillhub-domain -am compile` 防止编译破裂。

### Step 1：domain 状态机 (1 commit)
- [ ] **`AgentVersion.java`**：
  - 默认状态从 `DRAFT` 改为 `SCANNING`
  - 加字段：`requestedVisibility`（@Enumerated AgentVisibility，nullable）、`yankedAt`、`yankedBy`、`yankReason`、`bundleReady`、`downloadReady`
  - 加 state-machine 方法：`markScanPassed()`（SCANNING → UPLOADED）、`markScanFailed()`（SCANNING → SCAN_FAILED）、`retryScan()`（SCAN_FAILED → SCANNING）、`yank(String reason, String by)`（PUBLISHED → YANKED）
  - 改造现有方法：`submitForReview()` 的前置条件从 `DRAFT` 改成 `UPLOADED`，并接受 `requestedVisibility` 参数；`autoPublish()` 的前置条件从 `DRAFT` 改成 `UPLOADED`；`withdrawReview()` 终态从 `DRAFT` 改成 `UPLOADED`
- [ ] **`Agent.java`**：加 `hidden` boolean、`hiddenAt`、`hiddenBy`、`hideReason`、`latestVersionId`
- [ ] **`AgentSecurityScanService.java`**（新建）：占位实现
  - `triggerScan(versionId, entries, publisherId)`：直接把版本从 SCANNING 翻 UPLOADED
  - `isEnabled()` 返回 true（占位：同步通过）
  - 文件位置：`server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/scan/AgentSecurityScanService.java`
- [ ] commit message 建议：`feat(agent-domain): extend version status machine with UPLOADED/SCANNING/YANKED`

### Step 2：domain 服务层 (1 commit)
- [ ] **`AgentReviewSubmitService.java`**（新建，仿 `SkillReviewSubmitService`）
  - `submitForReview(agentId, versionId, targetVisibility, actor, namespaceRoles)`
  - `confirmPublish(agentId, versionId, actor, namespaceRoles)`
  - 注入 `AgentRepository`、`AgentVersionRepository`、`AgentReviewTaskRepository`、`Clock`、`ApplicationEventPublisher`
- [ ] **`AgentPublishService.java`** 重写：
  - 移除 `newVersion.autoPublish()` 和 `newVersion.submitForReview()` 直接调用
  - 改为：`newVersion.setStatus(SCANNING)` → `agentSecurityScanService.triggerScan(...)` 进入 UPLOADED
  - 加 `forceAutoPublish` 入参（SUPER_ADMIN 时 UPLOADED → PUBLISHED 跳审）
  - 不再在 publish 时创建 `AgentReviewTask`（task 在 `submitForReview` 时创建）
- [ ] **`AgentLifecycleService.java`**：`rereleaseVersion` 的 source-status 校验加上 `UPLOADED`；新增 `yankVersion(versionId, reason, actor)`
- [ ] **`AgentDownloadService.java`**：UPLOADED/SCANNING/SCAN_FAILED/PENDING_REVIEW 仅作者可下载
- [ ] **`AgentService.java`**（query）：UPLOADED/SCANNING/SCAN_FAILED 不进公开列表，仅作者 + 命名空间管理员可见
- [ ] **`AgentGovernanceService.java`**（新建）：`hideAgent` / `unhideAgent` / `yankVersion`
- [ ] **`AgentReportDisposition.java`**：增加 `RESOLVE_AND_HIDE`
- [ ] **`AgentReportService.java`**：处置分支增加 RESOLVE_AND_HIDE，调 `AgentGovernanceService.hideAgent`
- [ ] commit message 建议：`feat(agent-domain): add review-submit/governance services and rewrite publish flow`

### Step 3：app 层 + controller (1 commit)
- [ ] **`AgentLifecycleAppService.java`**（新建）：`submitForReview` / `confirmPublish` 两个对外方法 + 审计日志
- [ ] **`AdminAgentAppService.java`**（新建）：`hideAgent` / `unhideAgent` / `yankVersion`
- [ ] **`AdminAgentReportAppService.java`**：处置入口增加 RESOLVE_AND_HIDE 分支
- [ ] **`controller/portal/AgentLifecycleController.java`**：
  - 新增 `POST /{ns}/{slug}/submit-review`（body 形态对齐 `SkillLifecycleController` 的 submitForReview，**实现前先核对 skill 真实 body 字段**）
  - 新增 `POST /{ns}/{slug}/versions/{version}/confirm-publish`
- [ ] **`controller/admin/AdminAgentController.java`**（新建）：
  - `POST /api/v1/admin/agents/{agentId}/hide`
  - `POST /api/v1/admin/agents/{agentId}/unhide`
  - `POST /api/v1/admin/agents/versions/{versionId}/yank`
- [ ] **`controller/admin/AdminAgentReportController.java`**：解除 RESOLVE_AND_HIDE 屏蔽
- [ ] commit message：`feat(agent-app): add submit-review/confirm-publish endpoints + AdminAgentController`

### Step 4：测试 (1 commit)
- [ ] **`AgentPublishServiceTest`**：所有期望"publish 后直接 PENDING_REVIEW/PUBLISHED"的用例必须改为期望 SCANNING → UPLOADED
- [ ] **`AgentReviewSubmitServiceTest`**（新建）：复制 `SkillReviewSubmitServiceTest` 适配
- [ ] **`AgentLifecycleControllerTest`**：补 submit-review / confirm-publish 端点测试
- [ ] **`AdminAgentControllerTest`**（新建）
- [ ] **`AgentSecurityScanServiceTest`**（新建，最小验证占位行为）
- [ ] **预期需要更新的现有测试**：所有调用 `AgentVersion(...)` 默认期望状态是 DRAFT 的、所有 mock `AgentPublishService.publish` 返回值的测试。可以先 `mvn -pl skillhub-app -am test` 跑一遍，按报错列表修。

### Step 5：前端 (1 commit)
- [ ] **`web/src/api/client.ts`**：
  - `agentLifecycleApi` 加 `submitForReview` / `confirmPublish`
  - `adminApi` 加 `hideAgent` / `unhideAgent` / `yankAgentVersion`
  - `AgentReportDisposition` 类型加 `RESOLVE_AND_HIDE`
- [ ] **`features/agent/use-submit-agent-review.ts`** + **`use-confirm-agent-publish.ts`**（新建 mutation hooks，参考 `features/skill/use-skill-detail` 模式）
- [ ] **`features/agent/version-status-badge.tsx`**（新建）：UPLOADED/SCANNING/SCAN_FAILED/YANKED 状态徽标
- [ ] **`pages/agent-detail.tsx`**：UPLOADED 时展示"提交审核 / 确认发布"按钮（按 visibility 分支）；SCANNING 展示扫描中 banner
- [ ] **`pages/search.tsx`**：顶部 Tabs 增加 `[Skills | Agents]`，agent tab 调 `agentsApi.list({ q })`，渲染 `AgentCard`
- [ ] i18n key：`search.tabSkills` / `search.tabAgents` / `search.emptyAgents` / `agent.status.uploaded` / `agent.status.scanning` 等

### Step 6：drop 表 + 全量验证 + 合并 (1 commit)
- [ ] 写 `server/scripts/drop-agent-tables.sh`：`DROP TABLE IF EXISTS agent_review_task; DROP TABLE IF EXISTS agent_version; DROP TABLE IF EXISTS agent;`
- [ ] 跑 `mvn test`（全量后端）
- [ ] 跑 `pnpm test`（前端）
- [ ] 跑 `pnpm build`（前端）
- [ ] 启本地服务，手工冒烟（见 spec 的 DoD 清单）
- [ ] `git checkout main && git merge --no-ff feat/agent-skill-parity && git push origin main`

## 6. 用户协作约定（重要）

- 用户**全局 CLAUDE.md** 要求：始终用中文回复、提供选项 + 推荐 + 理由、思考再写代码、最小代码、外科级修改
- 用户**项目 CLAUDE.md** 要求：仅在 `feat/*` 分支改代码（已遵守）；feat 分支最小可用即合 main 不走 PR；提交前跑 `lint`/`test`/`build`；小步提交单一目的
- 用户的 `.claude/CLAUDE.md` 要求项目级 artifacts 必须在项目内（已遵守，spec 写在 `docs/plans/`）
- **memo 协议**（用户全局 + 项目 CLAUDE.md 双重要求）：
  - session 开始读 `memo/memo.md` + `memo/lessons.md`
  - 修复任何 bug 前查 `memo/lessons.md` 类似问题
  - 用户更正后更新 `memo/lessons.md`
  - session 结束更新 `memo/memo.md`
- 用户对**节奏的明示**："你定"、"keep agent 和skill 一致的流程"、"不存在与现在兼容问题，a2"——这意味着可以放手干，不要每步都问

## 7. 重要参考文件路径速查

**Skill 端的标准实现（agent 要照抄）：**
- 状态枚举：`server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVersionStatus.java`
- 实体（带 yank 字段）：`server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVersion.java`
- 提审/确认发布：`server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillReviewSubmitService.java`
- 治理（hide/yank）：`server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillGovernanceService.java`
- 发布流（带 forceAutoPublish）：`server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java`
- 安全扫描参考：`server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/security/SecurityScanService.java`
- App 层：`server/skillhub-app/src/main/java/com/iflytek/skillhub/service/SkillLifecycleAppService.java`
- Controller：`server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillLifecycleController.java`
- Admin Controller：`server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/admin/AdminSkillController.java`

**Agent 端待改文件：**
- 状态枚举（已改）：`server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentVersionStatus.java`
- 实体：`server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentVersion.java` / `Agent.java`
- 现有发布服务：`server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/service/AgentPublishService.java`
- 现有生命周期服务：`server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/service/AgentLifecycleService.java`
- 现有审核服务：`server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/review/AgentReviewService.java`
- App 层：`server/skillhub-app/src/main/java/com/iflytek/skillhub/service/`（待加 AgentLifecycleAppService、AdminAgentAppService）
- Controller：`server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/AgentLifecycleController.java`（待加端点）+ `controller/admin/`（待加 AdminAgentController）

## 8. 新会话第一句话推荐

```
请读 docs/plans/2026-05-01-agent-skill-parity-HANDOVER.md，
然后 git status 确认在 feat/agent-skill-parity 分支。
继续从 Step 1 开始：先 commit 已修改的 AgentVersionStatus，
然后改造 AgentVersion.java 实体加状态字段和 state-machine 方法。
```

## 9. 风险提示

1. **AgentReviewTask 创建时机变更**：原本 publish 时直接建，新流程下 publish 不建，submitForReview 时建。所有现有 `AgentReviewServiceTest` / `AgentPublishServiceTest` 都会受影响。
2. **AgentVersion 默认 status**：从 DRAFT 改为 SCANNING。所有直接 `new AgentVersion(...)` 然后断言 `getStatus() == DRAFT` 的测试都要改。
3. **路径冲突**：`POST /{ns}/{slug}/submit-review` 这条路径可能与现有路由冲突，需先核对 `AgentLifecycleController` 现有 `@RequestMapping`。
4. **bundleReady/downloadReady 字段**：skill 有，agent 现在没有；如果加了，所有 AgentVersion 构造调用都要给默认值（false）。
5. **SCAN 占位的同步性**：`AgentSecurityScanService.triggerScan` 同步把状态翻成 UPLOADED 时，要注意 transactional 边界，避免在同一事务内 setStatus(SCANNING) 又 setStatus(UPLOADED) 引发 JPA 误判。skill 的实现是异步消息队列驱动，agent 占位实现要明确写"DEV ONLY 同步通过"。

## 10. 备忘 stash

- `stash@{0}` 内含 docs/0*-*.md 的 IDE 改动（约 20 个文件），与本任务无关。
- 完成本任务并合 main 后，再 `git stash pop` 处理那批改动。

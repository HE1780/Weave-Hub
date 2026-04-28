# Project Session Memos

Update at session end with what shipped, what was deferred, and what to pick up next.

---

## 2026-04-29 — Agent followups bundle (4 tasks 全部完成)

**Spec:** [docs/superpowers/specs/2026-04-29-agent-followups-bundle-design.md](../docs/superpowers/specs/2026-04-29-agent-followups-bundle-design.md)
**Plan:** [docs/superpowers/plans/2026-04-29-agent-followups-bundle.md](../docs/superpowers/plans/2026-04-29-agent-followups-bundle.md)
**Branch:** main 直接推（参考 2026-04-27 P3-2a triple-tap 先例）；session 结束时 ahead origin/main 8 commits

### What shipped

| 项 | Commit | 内容 |
|---|---|---|
| Task 1 | `2c17ae30` | Agent promotion targetNamespaceId 缺省时 backend resolve `findBySlug("global")`；前端 `PromoteAgentButton` 删 globalNamespaceId prop + agent-detail.tsx 删 useMyNamespaces 依赖；admin-render 测试 |
| Task 2 | `0ee3b75a` | AgentSummary 加 ratingAvg/ratingCount；`AgentCard` mirror SkillCard 渲染评分 chip；frontend 702 |
| Task 3 main | `f7329d09` | AdminAgentReportController 全套 backend (controller/appservice/queryrepo/dto×2/disposition enum/2 events) + AgentReportService.{listByStatus, resolveReport×2, dismissReport} + AgentLifecycleService.archiveAsAdmin（plan 假设的 archive(...,Set<String>) 不存在）；前端 reports.tsx 加外层 Skill/Agent Tabs，Agent panel mirror skill panel 用 useAgentReports 系列 hook |
| Task 3 fix1 | `4cfd8ba8` | revert i18n title/subtitle drift（spec 显式只新增 3 keys）+ 补 4 个 controller 缺失测试 + 补 listByStatus service test |
| Task 3 fix2 | `dbacc01f` | 补 reporter notification on resolve/dismiss（mirror skill `governanceNotificationService.notifyUser`）+ 5 个 archiveAsAdmin 直接单元测试 |
| Task 4 main | `38b42297` | MyAgentAppService + AgentSummaryResponse + MeController endpoint `/me/agent-stars`；前端 stars.tsx 加外层 Skills/Agents Tabs + useMyAgentStarsPage hook + slug→name boundary adapter |
| Task 4 fix | `edc2035d` | `getAgentStarsPage` adapter 加 `Number(ratingAvg)` coercion（mirror useAgents.toSummary 防 BigDecimal-as-string 回归） |

### 测试结果

- Backend: `./mvnw clean test` 578/578 BUILD SUCCESS（baseline 568 → +10：Task 3 共 +6 service/controller，Task 3 fix2 +5 lifecycle/notification verify，Task 4 +5 service/controller，删除老 baseline 数会有些重复——以最终 578 为准）
- Frontend: `pnpm vitest run` 706/706（baseline 702 → Task 2 +0 module-shape unchanged → Task 3 +2 reports tab cases → Task 4 +2 net stars cases）
- TS: `pnpm tsc --noEmit` exit 0

### 计划偏离与教训

1. **Plan 给 archiveAsAdmin 写错签名**——plan §3.6 写 `archive(reportId, actor, Map.of(), Set.of("SUPER_ADMIN"))`，实际 `AgentLifecycleService.archive` 只有 3 个参数且只检 namespace role。Implementer 新加 `archiveAsAdmin` 方法 mirror `SkillGovernanceService.archiveSkillAsAdmin`，构造器加 `AuditLogService`。**Plan 错了，实现是对的**。
2. **Task 3 i18n drift**——initial implementer 把 `reports.title` 从 "Skill Reports" 改成 "Reports"，被 spec reviewer 抓出（spec 明确说只加 3 个 key）。Fix commit `4cfd8ba8` revert。Lesson 已被吸收到 Task 4：Task 4 implementer 主动保留 `stars.title/subtitle/empty` 不动。
3. **Task 3 reporter notification 漏掉**——code reviewer (I-2) 抓出 AgentReportService 没 mirror skill side 的 `governanceNotificationService.notifyUser`。Fix commit `dbacc01f` 补上。属于 spec §3 line 97 已经预言"if Agent reporter notifications are wired"——确实是 wired 的（`GovernanceNotificationService` 是 namespace-agnostic）。
4. **AgentCard 没 onClick prop**——plan §4.14b 草图用 `<AgentCard agent onClick={...}/>`，实际 AgentCard 内部包了 TanStack `<Link>`。Implementer 直接 drop onClick 用法。改进而非偏离。
5. **MeControllerTest 路径**——plan 写 `controller/portal/MeControllerTest.java`，实际在 `controller/MeControllerTest.java`（少了一层）。Implementer 修改实际文件。
6. **Sub-agent 报告偶有数字误差**：Task 4 implementer 报 backend 574→578 (+4)，code reviewer 数到 +5 个新 @Test（3 service + 2 controller）。代码没问题，是报告 fence-post 错。再次印证 lessons.md 2026-04-28 sub-agent audit 必须人工 cross-check（这次发现的偏差都是无害的）。

### Known follow-ups

1. **Task 3 frontend "agent panel resolve fires mutation" 测试**——spec reviewer 漏报但 code reviewer 没强制，留作 P2 增量。
2. **`stars.subtitle` / `stars.empty` 文案**——目前还停留在"View your starred skills"（en）/ "查看你标记过的技能"（zh），现在页面跨 Skills+Agents 两个 panel 后描述偏窄。Code reviewer 标 minor，仅文案微调。
3. **`AgentReport` 软隐藏路径**——`AgentReportDisposition` v1 不带 RESOLVE_AND_HIDE，因为 `Agent` aggregate 没 hidden 字段。等 Agent 加 hidden flag 后再补 enum + button。
4. **`AgentReportResolvedEvent` / `AgentReportDismissedEvent` 没消费者**——目前 0 listener；spec 没要求 listener，留待治理面板需要 counters 时再补。
5. **`AgentSummary.name` required vs id/slug optional 不对称**——可考虑后续把前端类型 `name` 整体改名 `slug`（option (b) 取代当前 boundary adapter），但 blast radius 较大，留作 housekeeping refactor。

### How to resume

bundle 已经全部落地。下一会话候选方向：
- fork-backlog 上其他未做的 P2 项（agent search index 同步、Bean validation 实际生效等）
- 上面 follow-up #2 文案 + #5 类型清理（小修）
- 等 Agent hidden flag 落地后回头补 `RESOLVE_AND_HIDE`

### 推送状态

Step 5.5 push origin/main 等用户明确确认才执行——本 session 结束时 main 仍在 origin/main + 8 commits，未推。

---

## 2026-04-28 — P0 follow-ups + fork-backlog 状态校正

**Spec:** [docs/superpowers/specs/2026-04-28-p0-followups-and-backlog-sync-design.md](../docs/superpowers/specs/2026-04-28-p0-followups-and-backlog-sync-design.md)
**Plan:** [docs/superpowers/plans/2026-04-28-p0-followups-and-backlog-sync.md](../docs/superpowers/plans/2026-04-28-p0-followups-and-backlog-sync.md)
**Branch:** `feat/p0-followups-and-backlog-sync` (合回 main)

### What shipped

| 项 | Commit | 内容 |
|---|---|---|
| Task 1 | `2266fd4e` | `NamespaceBatchMemberControllerTest.batchAddMembers_emptyArray_returnsError` 期望改为 400 + 注释纠错 |
| Task 2 | `1f9e9488` | PromotionController agent dispatch 3 个 unit test + 修 ResponseStatusException → 500 的潜在 bug（改用 IllegalArgumentException） |
| Task 3 | `e62badeb` | promotions.tsx SourceTypeBadge `bg-{blue,purple}` tokenize 成 `sourceTypeBadgeStyles` map |
| Task 4 | `bb88ddfe` | fork-backlog A0/A2/A3/A5/A6 + P2-1/P2-2 + 启动建议段全部校正；新加 spec/plan 文件 |
| Task 5 | `f0a209e6` | `memo/lessons.md` 加 2 条：测试断言先读再断、sub-agent audit 必须交叉校验 |
| Task 6 | (本 commit) | memo 校正 + 本 entry |

### Audit 校正一览（backlog 之前与实际不一致）

| backlog 标 | 实际 | 备注 |
|---|---|---|
| A0 / P2-2 archive: domain ready, app missing, 前端缺 | ✅ 全栈完成 | AgentLifecycleController + AgentLifecycleService + use-archive-agent + agent-detail/my-agents 双入口 |
| A2 Star: ❌ | ✅ 全栈完成 | V43 + AgentStarController + agent-star-button 已挂 |
| A3 Rating: ❌ | ✅ 全栈完成 | V43 + AgentRatingController + agent-rating-input 已挂 |
| A5 Tag (用户自定义 tag): ❌ | ✅ 已对齐 | "用户自定义 tag" 是误读，实际是 named version tag（admin/CLI），Skill 侧也无前端 UI |
| A6 Report: ❌ | 🟡 提交端 ✅ / moderation dashboard ❌ | submit 端齐全；admin reports.tsx 仅接 skill，agent moderation 是独立 follow-up |
| A1 评论 (P1-2): "前置 brainstorm" | ✅ 全栈完成 | AgentVersionCommentController + agent-version-comments-section 已挂 |

### 测试结果

- backend: `mvn -pl skillhub-app test -Dtest=NamespaceBatchMemberControllerTest` 6/6 ✅，`-Dtest=PromotionPortalControllerTest` 7/7 ✅
- web: dashboard 整 dir `npx vitest run src/pages/dashboard` 59/59 ✅
- 全套回归在 Task 7 跑

### Known follow-ups（真实剩余）

1. **A6 admin moderation dashboard** 接 agent reports —— reports.tsx 加 type 过滤 + 列表 + 处置按钮（端点齐）
2. **Public `/namespaces/global` 端点** —— A9 后续，让 PromoteAgentButton 不依赖 useMyNamespaces
3. **Agent 列表卡片平均评分** —— 扩 AgentSummary 字段
4. **My Stars 页 Agent 段** —— dashboard 重设计
5. **P2-4 bean validation 接通** —— 后端潜伏炸弹
6. **P3-2 安全扫描接 Agent 发布** —— BasicPrePublishValidator 接入 AgentPublishService

### How to resume

backlog 已经反映现状，下一会话直接挑 #1（A6 moderation） 或 #2（global namespace 端点）开始即可。再做之前先扫一眼 backlog 启动建议段。

---

## 2026-04-28 — 开放注册：Team Namespace 创建权限下放（plan 落地，1 行核心改动）

**Plan:** [docs/plans/2026-04-28-open-registration-and-team-creation.md](../docs/plans/2026-04-28-open-registration-and-team-creation.md)
**Commit:** `cc4073f5` (single commit, pushed to origin/main)
**ADR 0003 §1.1**（fork 自有路线：团队治理产品策略）

### What shipped

`NamespacePortalCommandAppService.createNamespace` 从"平台 SKILL_ADMIN/SUPER_ADMIN 才能创建" 改为"任何已登录用户都可创建 TEAM namespace"。删 9 行（`canCreateNamespace` 私有方法 + 调用点 + 不再用的 `ForbiddenException` import），加 21 行测试断言新行为。

| 文件 | 改动 |
|---|---|
| `NamespacePortalCommandAppService.java` | 删 `canCreateNamespace()` 方法 + 删调用点 + 删 unused import |
| `NamespacePortalCommandAppServiceTest.java` | `createNamespace_requiresPlatformAdminRole` → `createNamespace_allowsAnyAuthenticatedUser` + 新增 `createNamespace_rejectsAnonymous` |
| `NamespacePortalControllerTest.java` | `createNamespace_requiresPlatformAdminRole`(403) → `createNamespace_allowsAuthenticatedUser`(200)，保留 `createNamespace_allowsSkillAdmin` |

测试结果：`NamespacePortalCommandAppServiceTest` 7/7、`NamespacePortalControllerTest` 11/11、整套 backend 560/561。

### 关于唯一未通过的测试

`NamespaceBatchMemberControllerTest.batchAddMembers_emptyArray_returnsError` 期望 500 实际 400 —— **预存在 regression**，与本次改动无关。已用 `git stash` 在 baseline 上验证：同一测试在 baseline 上甚至更糟（`Failed to load ApplicationContext`），是 upstream 同步 `084d4b5b` 引入的 batch-import 端点的副作用。本次按 lessons.md 2026-04-27 "baseline 已坏不在范围内时分流"原则处理：(a) 我的代码改动正常做、(b) 不在那个坏掉的 controller test 里加新断言、(c) 在此处高优先级标注。

> **修正 (2026-04-28 P0 follow-ups session)**：上面的诊断**两条都是误判**。
> 1) 测试断言期望 500 是**断言写错了**——`@NotEmpty` 在 record-typed `@RequestBody` 上抛 `MethodArgumentNotValidException` → 400 是 Spring Boot 标准行为，400 才是对的。注释里"Spring Boot 3.2+ raises HandlerMethodValidationException (500)"是写测试的人推断错了框架。
> 2) baseline 上 `Failed to load ApplicationContext` 的现象不是 upstream 端点副作用，是 `~/.m2` 里的 `skillhub-domain` jar 是旧版（缺 `domain.review.SourceType`），`mvn -DskipTests install` 同步后整套测试 6/6 通过。
> 已在 commit `2266fd4e` 把断言改成 400，加注释解释为何 400 而非 500。教训沉淀到 `memo/lessons.md` "测试失败先读断言再定性 regression" 一条。

### 不变量验证（方案 §4.2 列的 4 条）

均已由现存代码 + 测试覆盖，无需重复加测：

1. **GLOBAL 不被滥用**：`NamespaceType.GLOBAL` 是系统内置，不通过 `createNamespace` 创建（`NamespaceService.createNamespace` 只创建 TEAM 类型）
2. **GLOBAL 发布审批不动**：`ReviewPermissionChecker.canReviewNamespace`（domain 层）守住 SKILL_ADMIN/SUPER_ADMIN 才能审批 GLOBAL，已被 `ReviewPermissionCheckerTest` 多 case 覆盖
3. **Promotion 审批不动**：`ReviewPermissionChecker.canReviewPromotion` 同上
4. **平台角色赋权链路不动**：`/api/v1/admin/users/{userId}/role` 端点未触及

### Known follow-ups（plan §6 列的 6 项后续迭代，刻意延后）

1. 邮件邀请 / 分享链接加入团队
2. 申请加入 Team（需审批）
3. 批量导入成员（已有端点，但本次未测试）
4. 创建 Namespace 时同时添加成员
5. Namespace 创建速率限制
6. Namespace 数量上限（per-user quota）

🔥 **优先 follow-up**：`NamespaceBatchMemberControllerTest.batchAddMembers_emptyArray_returnsError` 修复 —— upstream 同步引入的 regression，影响 batch member import 端点的空数组返回码，独立 plan 处理。

### How to resume

下一波最快回报：从 fork-backlog A2 (Agent Star) 或 A3 (Agent Rating) 起，模式现成、半天到一天可完成。

---

## 2026-04-28 — A9 Agent Promotion 落地（19 任务 plan 全部完成）

**Spec:** [docs/superpowers/specs/2026-04-28-agent-promotion-design.md](../docs/superpowers/specs/2026-04-28-agent-promotion-design.md)
**Plan:** [docs/superpowers/plans/2026-04-28-agent-promotion.md](../docs/superpowers/plans/2026-04-28-agent-promotion.md)
**ADR:** [docs/adr/0004-agent-promotion.md](../docs/adr/0004-agent-promotion.md)

### What shipped

后端：从 `b3cba0c5`（SourceType enum）到 `ddb82067`（promotions.tsx badge），约 16 个 commits 横跨 backend + web。Discriminator-column schema (V49) + strategy-pattern materializer。

| 层 | 关键改动 |
|---|---|
| DB | V49 加 `source_type/source_agent_*/target_agent_id` + CHECK 约束 + 两个 partial unique index 拆分 |
| Domain | `SourceType` enum、`PromotionMaterializer` 接口、`SkillPromotionMaterializer`（提取）+ `AgentPromotionMaterializer`（新）、`PromotionRequest` 新加工厂 + helper、`PromotionService.submitAgentPromotion` + `approvePromotion` 改 dispatch、`ReviewPermissionChecker.canSubmitPromotion(Agent, ...)` 重载 |
| Repository | `findBySourceAgentIdAndStatus` + `findByStatusAndSourceType`；`AgentVersionStatsRepository.save()` 在 domain port 上暴露（JPA impl 已经有） |
| API | DTO 扩字段（向后兼容）；Controller submit 端点按 `dto.sourceType()` 分支；list 端点新增可选 `sourceType` query；mapper 在 `JpaGovernanceQueryRepository` 按 sourceType 双向 dereference |
| Web | `promotionApi.submitAgent` + types 扩展、`useSubmitAgentPromotion` hook、`PromoteAgentButton` 组件 + i18n、`agent-detail.tsx` 挂载、`promotions.tsx` SourceTypeBadge + 分支字段 |

最终：**backend 554/554**（new domain tests +13），**web 684/684**（+2 module-shape tests）。

### Spec/Plan 偏离与教训（已沉淀到 `memo/lessons.md` + ADR 0004）

1. **Task 2 SKIPPED** — `AgentPublishedEvent` 已存在且被 6 处生产代码用，初次 sub-agent 误覆盖了文件，编译挂掉 3 处 caller。Reverted (`git revert 2efd73c8`)。Lesson：`mvn -q compile` 因为增量编译会用 stale class 文件不可信，验证破坏性改动必须 `mvn clean compile`。
2. **Task 8 SKIPPED** — 调研步骤改成把事实直接写进 plan，不需要单独 task。
3. **LabelDefinition 没有 namespace 概念** — spec 原说"按 namespace scope 过滤 label"，发现这是误解，改成全量 copy。
4. **`AgentLabel` 构造器第 2 参数是 `labelId` 不是 `labelDefinitionId`** — spec 拼错了字段名。
5. **`PromotionSubmittedEvent` 没有重命名** — plan 想改字段名但有 2 处 caller，决定 agent ids 复用 `skillId/versionId` 字段位置（listener 只读 promotionId）。
6. **`findByTargetNamespaceIdAndStatusAndSourceType` 改为 `findByStatusAndSourceType`** — 现有 list 端点不按 namespace 过滤。

整个 session 在执行中触发了**两次** "stop and re-plan" — 第一次在 Task 2，第二次在 Task 11 之前的批量 audit。审计后修补再继续是节省时间的正确决策。

### Known follow-ups

1. **PromotionController 的 agent 路径单元测试** — 控制器的 dispatch 分支没有专门的 unit test（依赖现有 PromotionPortalControllerTest 的 skill 路径覆盖 + 手工 e2e）。可补但不阻塞。
2. **Public `/namespaces/global` 端点** — 现在前端 `PromoteAgentButton` 通过 `useMyNamespaces` 找 GLOBAL ns id；admin 不是 global member 时按钮就不显示。dashboard 入口能补救，但加个公开端点更优雅。
3. **LandingHotSection 接 promoted agents** — spec §15 显式延后；A9 落地后可做。
4. **Agent search index 同步** — 同样 spec §3 显式延后（P3-3）。
5. **Source-type 设计 token** — promotions.tsx badge 用 inline Tailwind colors，`landing` 重设计已经标了 Skills-blue / Agents-purple 应该 tokenize。

### How to resume

backlog 上 A9 这条可以划掉了。下一波建议方向是上面 #1 + #2（短小改动），或者去做更高优先级的 fork roadmap 项。

---

## 2026-04-27 — Agent–Skill 能力对齐集群（A4/A7/A8/A10 完成，A9 延后）

**Plan:** [docs/plans/2026-04-27-agent-skill-parity-cluster.md](../docs/plans/2026-04-27-agent-skill-parity-cluster.md)
**Range:** `fb592b03` → `8fab7f20` （4 commits）

### What shipped

| 项 | Commit | 测试增量 |
|---|---|---|
| A4 Label | `fb592b03` | backend +17, web +2 |
| A7 Download | `ef9be96f` | backend +13, web ±0 |
| A8 CLI type=agent | `1715dec7` | backend +6, web ±0 |
| A10 Stats | `8fab7f20` | backend ±0, web +1 |

最终：**backend 554/554 passing，web 682/682 passing**。

### A9 Promotion — 延后未实施

**原因**：plan 估时 1 天且 backlog 标"mirror PromotionService"，实际 audit 后发现：

- `PromotionService` 360 行，硬编码 `sourceSkillId/sourceVersionId/targetSkillId`
- approve 路径会**物化**一份资源到目标 namespace（拷 SkillVersion + 文件 metadata + 重置 latestVersionId）
- 同等的 agent 路径要拷 AgentVersion + `package_object_key`（涉及对象存储）+ AgentVersionStats（A10 刚加） + AgentTag 等等
- 控制层 `PromotionController` 用的 `PromotionRequestDto` 和 `GovernanceWorkflowAppService` 的 method 签名也是 skill-only，加 agent 路径要么扩大签名要么抽 polymorphism
- 需要独立 brainstorm 决定：拆表 (`agent_promotion_request`) vs 加 polymorphism column (`target_type` 在 `promotion_request` 表) vs 全新 service (`AgentPromotionService` 平行)

**建议**：A9 重新走一次 brainstorm + ADR，独立 plan。当前 backlog 的 1 天估时是错的，实际更接近 2-3 天（含数据迁移 + UI + 测试）。

### Spec 偏离

**A7 frontend** — plan 列了"加下载按钮"，但读 `agent-detail.tsx` 发现 `handleDownloadPackage` 已经在 tree 里、调用 `/agents/{ns}/{slug}/versions/{v}/download` 这个尚未存在的端点。所以 A7 后端落地后前端零改动就工作了，commit message 已记录此节。

**A4 frontend** — `agent-detail.test.tsx` 9 个 test 全部因为引入 `hasRole` 依赖而需要 patch（mockReturnValue 现在要带 `hasRole: () => false`）。属于 useAuth 共享 mock 的合理扩散，记录在 commit body。

**A10** — backend 542 → 554 看上去没变是因为 surefire summary 是 per-module（被 skillhub-app 的 548 数字遮蔽），实际跨模块测试增加 5+8+2=15 个。

### Known follow-ups

1. **A9 重新 brainstorm**（最高优先级）：plan/backlog 里的"mirror PromotionService"误导，需要先决定 polymorphism vs 独立表/service 再拆任务
2. **agent_search_document 与 label 同步**：A4 没接 search index 因为 fork 暂无 agent_search_document 表（P3-3 留待）
3. **CLI publish/star for agents**：A8 只覆盖 read-and-pull，CLI 端推送场景仍只支持 skill
4. **LandingHotSection 接 promotion 数据源**：当 A9 落地后才能做
5. **Bean validation 仍未生效**（P2-4 既有项，与本次工作无关但所有 `@Valid` 仍 silent；当前所有 A 项依赖 domain 层守门）

### How to resume

A9 入口：

1. brainstorm（建议 superpowers:brainstorming 半小时）：决定 polymorphism 形态 + 数据迁移策略
2. 写 ADR 0004-agent-promotion 锁定决策
3. 起独立 plan `docs/plans/YYYY-MM-DD-agent-promotion.md`
4. 实施

不建议直接照 backlog 的 1 天估时跑——那个估时基于错误的"mirror"假设。

---

## Fork scope (read first)

This fork (`HE1780/Weave-Hub`) takes Agent management, independent visual UI,
and safe runtime as its own development lines. Governance / social / search
enhancements track upstream `iflytek/skillhub` instead. Authoritative scope
decisions live in [docs/adr/0003-fork-scope-and-upstream-boundary.md](../docs/adr/0003-fork-scope-and-upstream-boundary.md).
Every new plan should declare which clause of ADR 0003 (1.1 / 1.2 / 1.3) it
belongs to.

---

## 2026-04-26 — Landing page dual-channel redesign

**Plan:** `docs/plans/2026-04-26-landing-page-dual-channel.md`
**Branch:** `main` (worked directly)
**Range:** `f01dcccc` (plan commit) → `f5b58d7c` (final wire-up)

### What shipped (7 commits)

```
f5b58d7c feat(web): wire LandingChannelsSection into landing page
a50d2138 feat(web): add Popular Agents section to landing page
0264fdcb feat(web): add Agents count to landing stats row
e4ccf2ca feat(web): add Browse Agents CTA to landing hero
d6c92bbe feat(web): add LandingChannelsSection (dual-channel intro)
94a8102e feat(i18n): add landing.channels, popularAgents, hero.browseAgents, stats.agents keys
f01dcccc docs(plan): landing page dual-channel redesign (Skills + Agents)
```

### Decisions locked (in lieu of brainstorming Q&A)

User said "都听你的" so I picked:
- Layout strategy **C**: Hero stays unchanged (search + 3 CTAs + 4 stats), new Channels block goes between Stats and Features.
- Agents data: read from existing mock-backed `useAgents` (top 3).
- Quick Start: **no change** — `LandingQuickStartSection` already has the two-tab structure (`agent` / `human`) we wanted; reusing avoids churn.
- Unified search Tabs: **deferred** (no agent search API yet on backend).
- Color overhaul (Skills blue / Agents purple): **deferred** — this would touch global design tokens; out of scope for layout redesign.

### Test counts

- Web: 598 → **606 passing** (added 4 channels + 4 popular-agents tests)
- Backend: 432 (unchanged, untouched)
- Typecheck: clean for all my files; only pre-existing `registry-skill.tsx` errors remain (untracked)
- Lint: clean

### Known gaps (intentional)

1. **Stats numbers are still static**: 1000+/50+/50K+/200+. These were already static before this PR; agents number `50+` is consistent with the existing marketing-copy style.
2. **`PopularAgents` lazy-loads `AgentCard` and `useAgents` synchronously** — no skeleton flicker since `useAgents` resolves immediately from mock. When real backend lands, the existing `isLoading` branch handles the network case.
3. **Channels cards use brand-gradient for both icons** (not differentiated colors per Q4 decision). When the color overhaul plan lands, these cards are the natural canary for Skills-blue / Agents-purple.

### How to resume

If the user wants to do the **color overhaul** later:
1. Define design tokens (CSS variables) for `--channel-skill` (blue) and `--channel-agent` (purple).
2. Apply to `LandingChannelsSection` icons + `AgentCard` accent + `SkillCard` accent.
3. Update Hero CTA buttons: keep brand-gradient on the primary `Explore Skills`, switch `Browse Agents` from secondary to a purple variant.

If the user wants the **third Quick Start tab "Agent Architect"**:
- Wait until `/dashboard/publish/agent` exists (no agent publish form today).
- Then add the third tab to `LandingQuickStartSection` with copy from the design doc.

---

## 2026-04-26 — Skill version comments backend (Tasks 1–13 of plan)

**Plan:** `docs/plans/2026-04-26-skill-version-comments.md`
**Branch:** `feat/skill-version-comments` (in-place on `main` checkout)
**Base:** `d11d75cf` (`docs(adr): 0002 skill-version comments design`)
**Head:** `f0a08e14` (`feat(notification): dispatch COMMENT_POSTED to version author`)

### What shipped (12 commits)

```
f0a08e14 feat(notification): dispatch COMMENT_POSTED to version author
ba800c91 feat(api): add CommentController edit/delete/pin endpoints
503ffa62 feat(api): add SkillVersionCommentController list/create endpoints
c85ce120 feat(api): add comment request/response DTOs
ceefc69f feat(domain): add SkillVersionCommentService with permission predicates
49585630 feat(domain): add CommentPermissions record and posted event
d0eea5c4 feat(infra): add SkillVersionCommentRepository (JPA-backed)
87f33655 feat(domain): add SkillVersionComment entity with body validation
c8d79c4f feat(db): add skill_version_comment table (V40)
a6de64e8 feat(notification): add COMMENT category
e763ad3d feat(web): add createWrapper test helper for hook tests
f2f4baa7 chore(web): add @testing-library/react and jsdom for hook tests
```

Backend coverage end-to-end: schema → entity → repo → service → DTOs → 2 controllers → security policy → notification listener. Full backend test suite: **432 tests, 0 failures**.

### Spec divergences from the plan (intentional, all approved during execution)

1. **Task 1 — `jsdom` is a real direct devDep, not a transitive accident.**
   The implementer first added it as collateral; spec reviewer caught the bogus reasoning; code reviewer pointed out we genuinely need it for the per-file `// @vitest-environment jsdom` docblock to remain stable across pnpm regenerations. Final state: both `@testing-library/react` and `jsdom` are explicit devDeps with the rationale recorded in commit `f2f4baa7`'s body.

2. **Task 7 — added `@Transactional(readOnly=true)` to `listForVersion`.**
   The plan didn't specify it; code review flagged the LazyInitializationException risk for projects without open-session-in-view. Also restructured `edit` to load `Skill` once (matching `post`'s pattern) instead of re-loading after save. Both applied as an amend to commit `ceefc69f`.

3. **Task 9 → Task 10/11 — author hydration uses real `UserAccountRepository.findByIdIn`.**
   The plan defaulted to a `(authorId, authorId, null)` fallback because recon hadn't found a user lookup. Recon confirmed `UserAccountRepository.findById(String)` and `findByIdIn(List<String>)` exist and `UserAccount` has `getDisplayName()` + `getAvatarUrl()`. Both controllers hydrate properly; fallback only triggers on a missing user (deleted account).

4. **Task 10 — security policy added to `RouteSecurityPolicyRegistry`.**
   The plan didn't anticipate this; the existing policy's `anyRequest().authenticated()` fallback meant anonymous read on PUBLIC versions (ADR §5.5) was rejected with 401. Added 2 `permitAll(GET)` entries adjacent to the existing star/rating policy entries.

5. **Task 11 — security policy: 6 explicit `authenticated` entries for PATCH/DELETE/POST-pin.**
   Defensive-but-redundant POST-create entries were skipped (the fallback covers them and no other POST entries exist in the file). Decision and rationale documented in the commit body.

6. **Task 13 — DEFERRED.** No `@SpringBootTest` precedent in this codebase drives real repos through the controller layer; every existing controller test mocks the service. Writing one from scratch for this task would either invent a new pattern (scope creep) or be flaky (writing to H2 in-memory through real JPA). The plan explicitly permits skipping if no precedent exists. Trade-off: 40+ unit/controller-with-mock tests cover the contract; a true full-stack test would still add value but it's a separate spec.

### Known follow-ups (not bugs, but worth tracking)

1. **Bean validation isn't wired.** The codebase has `jakarta.validation-api` on the classpath but no `hibernate-validator` runtime impl. `@Valid` and `@NotBlank` annotations on DTOs are no-ops. **Worked around** for this feature: validation is enforced at the entity layer (`SkillVersionComment` constructor throws `DomainBadRequestException` on bad bodies) and at the DB layer (V40 CHECK constraints). Test paths assert the domain-level rejection, not bean validation. **Worth a separate spec** to add `spring-boot-starter-validation` and migrate existing controllers to assume bean validation works.

2. **Author hydration falls back to `userId` on lookup miss.** That's the right behavior for deleted/missing accounts but means UIs may render a userId where they expect a display name. Consider whether the API should explicitly mark "author missing" with a different shape, or whether the current contract (always returns `AuthorRef`) is correct.

3. **No real integration test for the comment lifecycle.** Task 13 deferred. If desired later, the cleanest precedent to establish would be a `@DataJpaTest`-style test that writes through the JPA repos and hits the service directly (skipping HTTP).

### What's next — Tasks 14–25 (web UI)

The plan covers the web side as Tasks 14–24 plus the final verification (25). Independent of the backend except for the API contract, which is now stable.

Outline for next session:
- Task 14: web types + `useVersionComments` query-key factory
- Tasks 15–17: 5 hook files (infinite query + 4 mutations) with hook tests using the `createWrapper` helper from Task 1
- Task 18: i18n keys (en + zh) — block of `comments.*` keys per ADR §8.7
- Task 19: `CommentMarkdownRenderer` (GFM **off**, separate from existing skill `MarkdownRenderer` which has GFM on) + XSS regression tests
- Tasks 20–22: `<CommentItem>`, `<CommentComposer>`, `<CommentList>`
- Task 23: `<VersionCommentsSection>` orchestration component
- Task 24: wire into `web/src/pages/skill-detail.tsx`
- Task 25: full test runs + visual smoke test against running backend

When picking this up:
1. `git checkout feat/skill-version-comments` (currently in place on `main` checkout — branch is local-only, hasn't been pushed).
2. Read this memo's "Spec divergences" section so you don't fight battles already settled.
3. The API the web hooks consume is exactly the one shipped in commits `503ffa62` and `ba800c91`. Author shape includes `displayName` and (sometimes) `avatarUrl` — see `SkillVersionCommentResponse.AuthorRef` in commit `c85ce120`. Body is raw markdown.
4. The `createWrapper()` helper at `web/src/shared/test/create-wrapper.tsx` is ready to use.

### How to resume

```bash
cd /Users/lydoc/projectscoding/skillhub
git status   # branch should be feat/skill-version-comments
./server/mvnw -pl skillhub-app test -Dtest=SkillVersionCommentControllerTest,CommentControllerTest,SkillVersionCommentNotificationListenerTest
# ↑ should be 5 + 9 + 3 = 17 tests passing
cd web && pnpm vitest run src/shared/test/create-wrapper.test.tsx
# ↑ should be 3/3 passing
```

---

## 2026-04-26 — Skill version comments web UI (Tasks 14–25 of plan)

**Plan:** `docs/plans/2026-04-26-skill-version-comments.md`
**Branch:** `feat/skill-version-comments` (continued)
**Base:** `f0a08e14` (last backend commit from prior session)
**Head:** `003343e1`

### What shipped (12 commits)

```
003343e1 fix(web): wrap post.mutateAsync in async lambda for typecheck
4c5dff23 feat(web): mount VersionCommentsSection on skill-detail page
a85ffc77 feat(web): add VersionCommentsSection orchestration component
f2f06e3e feat(web): add CommentList with load-more button
eb97afb0 feat(web): add CommentComposer with write/preview tabs and length cap
457e3696 feat(web): add CommentItem with permission-gated action menu
b1dba1b9 feat(web): add CommentMarkdownRenderer with XSS regression tests
86106b97 feat(i18n): add comments.* keys (en + zh)
47b15a83 feat(web): add edit/delete/pin comment mutations
bb87b5ba feat(web): add usePostComment mutation
72cbd579 feat(web): add useVersionComments infinite query hook
6651b9dc feat(web): add comment types, query-key factory, and commentsApi group
```

End-to-end UI: types → query keys → API client → 5 hooks → i18n keys → markdown renderer → 3 components → orchestration → page wiring. Test counts: 588 → 589 then expanding to **38 new tests across 11 files** in `web/src/features/comments/`. Full web suite: **589 passed, 0 failed**. Backend: **432 passed, 0 failed** (unchanged).

### Spec divergences from the plan (intentional, all approved during execution)

1. **API client uses `fetchJson` + a `commentsApi` group, not `openapi-fetch`'s `client.GET/POST/PATCH`.**
   The plan templated calls like `client.GET('/api/web/skill-versions/{versionId}/comments', …)` but this codebase doesn't use the openapi-fetch low-level client; every feature group (notificationApi, namespaceApi, …) wraps `fetchJson(url, init)` with manual `URLSearchParams` building and `getCsrfHeaders()`. Added `commentsApi` next to `notificationApi` in `web/src/api/client.ts` following the established pattern. All hooks now mock `commentsApi.{list,post,edit,delete,togglePin}` rather than a fictional `apiFetch`.

2. **Test mocks of `react-i18next` return the raw key, not English literal.**
   The plan asserted on `screen.getByText(/Pinned/i)` etc., which would couple tests to locale state. Switched to the project-standard mock pattern (already used in `dashboard.test.tsx`, `search.test.tsx`): `useTranslation: () => ({ t: (key) => key })`, then assertions check `'comments.badge.pinned'` etc.

3. **Manual `cleanup()` in `afterEach` for component tests.**
   `@testing-library/react`'s auto-cleanup requires `globals: true` or a setup file in the vitest config — neither is configured here. Without cleanup, multiple `render()` calls in one file stack DOM nodes and break role-based queries. Added `import { cleanup } from '@testing-library/react'; afterEach(() => cleanup())` to every component test.

4. **Replaced `expect(...).toBeInTheDocument()` with `.toBeTruthy()` / DOM property checks.**
   `@testing-library/jest-dom` is not installed and is out of scope for this feature. Used native vitest matchers throughout.

5. **Used the project's existing `@/shared/ui/dropdown-menu` wrapper instead of importing `@radix-ui/react-dropdown-menu` directly** in `CommentItem`. Matches existing UI conventions and inherits the project's styling.

6. **Test for `<CommentItem>` action-menu items intentionally does NOT click-through into the dropdown content.**
   Radix renders DropdownMenuContent into a portal, which is awkward to query in jsdom without forwarding open state. Coverage was redistributed: nine assertions on the trigger (gating, badges, avatar, content) instead of asserting on portal-rendered items. The wired callbacks are exercised end-to-end via `<VersionCommentsSection>` integration paths.

7. **Inserted `comments.section.title` i18n key** (not in the plan's i18n block) so the heading on the skill-detail page reads "Comments" / "评论" instead of an awkward derivation from the empty-state string. Noted in commit `86106b97`.

8. **Added `vi.mock('@/features/comments', ...)` to `skill-detail.test.tsx`.**
   The new section requires `useInfiniteQuery`, which the existing test doesn't provide a `QueryClientProvider` for. Stubbed the barrel export with a placeholder div, mirroring the existing `vi.mock('@/features/skill/markdown-renderer', ...)`.

### Known gaps (call out, do not fix in this branch)

1. **OpenAPI types regeneration is still deferred.** The hooks call paths the backend has implemented, but `web/src/api/generated/schema.ts` does not yet include the comment endpoints. This is fine because we use `fetchJson` directly instead of openapi-fetch's typed `client`. Pending: re-run `pnpm openapi-typescript` and add a typed wrapper if desired.

2. **Author avatar/displayName fall back to `userId`** (carried over from the backend session — not changed here).

3. **No real integration test for the comment lifecycle** (carried over from Task 13 deferral).

4. **Pre-existing typecheck and lint warnings in `web/src/pages/registry-skill.tsx`** (untracked, out of scope for this branch). My changes pass typecheck and lint cleanly when scoped to `src/features/comments/`, `src/api/client.ts`, and `src/pages/skill-detail.tsx`.

5. **`window.confirm()` for delete confirmation** in `CommentItemWithMutations` rather than a `<ConfirmDialog>`. Acceptable for v1 per the plan; can be upgraded later.

6. **No visual smoke test was performed.** The plan's Step 5 of Task 24 calls for a manual browser walkthrough against a running backend. Skipped in this session because there is no live backend running locally; verified via typecheck + 589/589 unit tests instead.

### How to resume

```bash
cd /Users/lydoc/projectscoding/skillhub
git status   # branch should be feat/skill-version-comments, 12 commits ahead of origin
cd web && pnpm vitest run src/features/comments/   # 38 tests, 11 files
cd web && pnpm typecheck   # only registry-skill.tsx errors remain (pre-existing untracked)
```

---

## 2026-04-26 — Agents Frontend MVP 实施完成 (merged later)

执行了 `docs/plans/2026-04-26-agents-frontend-mvp.md`，全部 12 个任务通过 subagent-driven-development 流程交付。

**12 个 commit**：
- i18n keys
- Agent types (`web/src/api/agent-types.ts`)
- mock 数据 (`web/src/features/agent/mock-agents.ts`)
- useAgents hook (TDD)
- useAgentDetail hook (TDD)
- **infra**: 加 `@testing-library/jest-dom` + `jsdom` + setupFiles
- AgentCard
- WorkflowSteps
- agents.tsx 列表页
- agent-detail.tsx 详情页
- 路由注册 `/agents` + `/agents/$name`
- nav 加 "Agents/智能体" 链接

**测试**: 181 files / 557 tests 全绿（合并 comments 之后变 188 / 589）
**浏览器烟雾测试**: 列表 + 三个详情 + 未知 name + en/zh 切换 全部通过

**未做的事**: agent 上传表单（UI 入口已加，但实际 publish 流程不在 MVP 范围）。

**下一步可做的跟进**（来自最终 code review，全部 NICE-TO-HAVE）：
1. AgentCard 的 `role="link"` + `tabIndex={0}` 没有键盘 (Enter/Space) 测试覆盖；改用 TanStack `<Link>` 是更彻底的修复。
2. agents.tsx 用 `navigate()` 而非 `<Link>`，cmd-click/middle-click/右键打开新标签都不工作；和现有 skill-card 同病。
3. workflow-steps.tsx 里 `WorkflowSteps` 和 `StepBody` 都各自调 `useTranslation()`，可以提到一起。
4. 三个 hook 测试都各自重建 QueryClient，可以抽出 `createWrapper()` helper（comments session 已经做了，agents 可以迁移过去）。
5. agent-detail.tsx 里 "not found" 和 "network error" 共用同一条 `agents.loadError`，等真后端接入后区分。

**关键架构决策**：mock-vs-API 切换面在 `useAgents.queryFn` 和 `useAgentDetail.queryFn` 两个函数体内，符合计划承诺的"换后端只动一处"。

---

## 2026-04-27 — Agent publish & review pipeline · Phase E (Tasks 25–30)

**Plan:** `docs/plans/2026-04-27-agent-publish-review-pipeline.md` (Phase E)
**Branch:** `main`
**Range:** `7036273b` (My Agents tab, last Phase D commit) → `c77dc275` (Hero dropdown)

### What shipped (6 commits)

```
c77dc275 feat(web): split Hero Publish CTA into Skill/Agent dropdown
aaa9cc16 feat(web): switch agent-detail route to /agents/$namespace/$slug
ee032449 feat(web): AgentReviewDetailPage with approve/reject controls
1a098375 feat(web): AgentReviewsPage (reviewer inbox)
2aca5015 feat(web): add agent review query and mutation hooks
(plus: Task 30 memo commit)
```

End-to-end Agent flow now closed: publish → review inbox → review detail (with soul.md + workflow.yaml visible) → approve/reject → public list. Hero offers dropdown CTA for both Publish Skill and Publish Agent.

### Test counts

- Web: 606 → **627 passing** (+21 new). 202 files, 0 failures.
- Backend: **broken baseline (see follow-up #1)** — did not run as part of this session.
- Typecheck: only pre-existing `registry-skill.tsx` errors.
- Lint: only pre-existing `registry-skill.tsx` warnings.

### Spec divergences from the plan (all intentional)

1. **Task 27 added a backend endpoint** — `GET /api/web/agents/reviews/{taskId}/detail` (also `/api/v1`). Phase C plan Task 16 created the `AgentReviewVersionDetailResponse` DTO but Task 17 never wired a controller to return it. The reviewer detail screen needs agent metadata + soul.md + workflow.yaml in one shot, so this commit closes that wiring. Service.getById was already in place; controller picks up `AgentRepository`/`AgentVersionRepository`/`NamespaceRepository` to assemble the joined response. Security policy gained two `*/detail` entries because the existing `/reviews/*` rule is single-segment and doesn't cross the next slash.

2. **Task 26 took a simpler shape than skills' reviews page** — agent review API requires `namespaceId`, so the inbox UI is built around a namespace selector (filtered to OWNER/ADMIN namespaces) instead of the global SKILL_ADMIN tab + namespace tab combo. Less code, fewer special cases. Dashboard sidebar grew an `Agent Reviews` card gated on the same role check.

3. **Task 28 made `/agents/$name` a redirect** — old route became `beforeLoad` redirect to `/agents/global/$name` so any cached/external links still resolve; canonical URL is now `/agents/$namespace/$slug`. `useAgentDetail` takes both segments. `AgentSummary.namespace` was added so cards can build the canonical URL without re-fetching. Distinguishes "no published version" (new i18n key) from generic load errors.

4. **mock-agents.ts deleted** — no remaining importers; backend is the source of truth. Tests still mock the hooks (`useAgents`, `useAgentDetail`) directly.

5. **Review-detail page renders soul/workflow as `<pre>` blocks, not markdown.** Workflow is YAML (not markdown), and the reviewer needs to see soul exactly as the runtime will read it. Skipping the markdown renderer also avoids the XSS concern entirely on this surface.

### Known gaps and follow-ups (in priority order)

1. **✅ RESOLVED 2026-04-27** — Backend baseline is **NOT** actually broken. Original report misdiagnosed root cause as JPA scanning/wiring issue. Real cause: `mvn -pl skillhub-app test` doesn't rebuild upstream modules, and m2 cache held a stale `skillhub-infra-0.1.0.jar` from before the agent JPA repositories were added (jar lacked `Agent*JpaRepository.class`). The fresh domain jar referenced `AgentRepository` but no impl was on the classpath → `No qualifying bean`. Fix: run `./mvnw test` from `server/` root (full reactor) instead of `-pl skillhub-app`. **Backend now: 455/455 passing**, no source code changes required. See `memo/lessons.md` 2026-04-27 multi-module maven entry for full diagnostic trail.

2. **No real integration test for the agent review approve/reject lifecycle.** Carried over from the comments-session pattern — the codebase has no `@SpringBootTest` precedent that drives real repos through the controller layer for these workflows. Worth a separate spec.

3. **Backend AgentControllerTest, AgentReviewControllerTest, AgentPublishControllerTest** — 7 + 4 + ~4 tests assuming the agent JPA wiring works; will pass once #1 is fixed.

4. **`/agents/$name` redirect uses hardcoded `'global'` namespace.** Fine for now (single-tenant deployments use `global`); when multi-namespace publishing surfaces, the legacy redirect needs a backend lookup or graceful 404 instead.

5. **AgentReviewsPage doesn't show agent name/version in the row** — only task ID and version ID. Backend list response is the bare task row; to enrich, either (a) the list endpoint joins agent metadata (mirrors `ReviewTaskResponse` carrying `skillSlug`), or (b) the page does N+1 fetches client-side. Both are scope creep for v1.

6. **No browser smoke test was performed.** The plan's Task 30 step calls for a manual walkthrough. Skipped because no live backend was running in this session and the backend baseline is broken (see #1). Verified via 627/627 unit tests + typecheck.

7. **`window.confirm` not used** — review actions go through `<ConfirmDialog>` matching the skill-review pattern.

### How to resume

```bash
cd /Users/lydoc/projectscoding/skillhub
git status   # branch: main, 6 commits ahead of origin/main since 7036273b
cd web && pnpm vitest run                       # 627/627 passing
cd web && pnpm typecheck                        # only registry-skill.tsx errors remain (pre-existing)
cd server && ./mvnw test                        # 455/455 passing — RUN FROM ROOT, not -pl skillhub-app
```

If continuing the agent rollout, the natural next stops are:
- Surface review-task list rows with agent slug + version (follow-up #5).
- Wire `Agents Frontend MVP` follow-ups #1–#4 (keyboard-nav `<Link>`s, shared `createWrapper`, etc).
- Optionally re-attempt Task 27's `AgentReviewControllerTest.getDetail_returns_full_projection` test that was held back due to the now-resolved baseline issue.

---

## 2026-04-27 — Phase E follow-up #1 resolved (backend baseline restored)

Diagnosed and fixed the "broken backend baseline" reported in the previous Phase E session. **No source code changes were needed.** The previous session's hypothesis (JPA scanning/wiring misconfiguration) was wrong; actual root cause was a stale m2 cache + the wrong maven invocation pattern.

**Diagnostic**: error stacktrace showed `agentPublishService defined in URL [jar:file:/Users/lydoc/.m2/...skillhub-domain-0.1.0.jar!/...]` — services were loading from m2 cache. Inspecting `~/.m2/.../skillhub-infra-0.1.0.jar` confirmed it was missing the `Agent*JpaRepository.class` files (jar predated the agent module additions). Fresh `./mvnw install` regenerated the jar; `./mvnw test` from server root passes all 455 tests.

**Verified state**:
- Backend: 455/455 (was: 241/456 broken)
- Web: 627/627 (unchanged)
- Typecheck: clean (only pre-existing registry-skill.tsx errors)

**Lesson recorded** at `memo/lessons.md` — multi-module maven, `-pl X test` doesn't rebuild upstream modules. Default to `./mvnw test` from root.

---

## 2026-04-27 — Phase E cleanup: follow-ups #5, #1-#4 (web), Task 27 test

Closed every remaining Phase E follow-up in one session, in C → A → B order.

### What shipped (6 commits)

```
c0a76f81 test(web): migrate agent hook tests to shared createWrapper helper
5eccf35e refactor(web): pass t down in workflow-steps instead of two useTranslation calls
27df14e3 refactor(web): use TanStack Link instead of navigate() in agent UI
c3ec0e75 feat(web): show agent name + version in review inbox row
f3363cb5 feat(api): hydrate agent review list rows with agent slug/version
9c114e21 test(api): cover AgentReviewController getDetail endpoint
```

ADR 0003 §1.1 (Agent management) for all six.

### What got resolved

- **Phase E Task 27 test (held back from prior session)** — added 4 cases to `AgentReviewControllerTest` (happy path, version-missing 404, agent-missing 404, anonymous 401) for `/api/web/agents/reviews/{taskId}/detail`.
- **Phase E follow-up #5** — review inbox rows now carry agent slug/displayName/namespace/version. Backend extends `AgentReviewTaskResponse` with four nullable fields populated by the list endpoint via the new `enriched()` factory. Single-task endpoints (getOne/approve/reject) keep the unhydrated `from()` factory. Added `AgentVersionRepository.findByIdIn` for the batch lookup. Frontend renders the new Agent column and replaces `#agentVersionId` with the version string, with `—` and `#id` fallbacks for orphaned/legacy data.
- **Agents Frontend MVP follow-ups #1+#2** — `AgentCard` now wraps in TanStack `<Link>`, dropping the role/tabIndex/onKeyDown plumbing; `agents.tsx` and `popular-agents.tsx` use `<Link>` + `buttonVariants()` for their CTAs. cmd-click / middle-click / right-click open in new tab now work.
- **Agents Frontend MVP follow-up #3** — `WorkflowSteps` lifts `useTranslation` to the parent and threads `t` down to `StepBody`.
- **Agents Frontend MVP follow-up #4** — 7 agent hook tests now use the shared `createWrapper()` helper. Added `createWrapperWithClient()` variant exposing the QueryClient for approve/reject tests that need to spy on `invalidateQueries`.

### Test counts

- Backend: 459 → **460/460** (added 1 list test for the orphaned-version fallback case; net is +5 vs baseline including Task 27 cases).
- Web: 627 → **631/631** (+4: 1 createWrapperWithClient test, 1 extra agent-card link assertion, 2 enriched/fallback row cases).
- Typecheck: clean (only pre-existing registry-skill.tsx errors).

### Spec divergences worth noting

1. **`AgentCard` lost the `onClick` prop.** It used to accept an optional click handler so callers could navigate. With the Link wrapper that's vestigial — every caller in the tree already wanted the same destination. Tests updated to assert on `href` instead of `vi.fn()` invocation. If a future caller needs click-side-effects, prefer `onClick` *additionally* on the `Card` (Link still owns navigation), not as a replacement.

2. **`createWrapperWithClient` is a new public helper, not a refactor of `createWrapper`.** Approve/reject tests need the QueryClient instance to install spies; rather than break the existing `wrapper: createWrapper()` callsite shape used by 5 comments tests + 5 agent tests, added a separate two-return helper. Rule of thumb: prefer `createWrapper()`; reach for `createWrapperWithClient` only when spying on cache ops.

3. **Backend `AgentReviewTaskResponse` now has both `from(task)` and `enriched(task, agent, version, slug)` factories.** Detail/approve/reject use `from()` (unhydrated, four `null` fields); list uses `enriched()`. The frontend treats the four enriched fields as `?: T | null` so missing data falls back gracefully — no breaking change for clients that don't read them.

### How to resume

Backend baseline + Phase E follow-up list are now empty. Natural next stops per ADR 0003:

- **Color overhaul plan** (ADR 0003 §1.2): Skills blue / Agents purple design tokens, Hero double-entry refresh, third Quick Start tab "Agent Architect".
- **Agent comments plan** (ADR 0003 §1.1): extend skill-version comments polymorphism to Agent, or fork. Requirements doc already at `docs/plans/2026-04-26-comments-feature-requirements.md`.
- **Agent search integration** (ADR 0003 §1.1): `/agents` keyword search wired into the unified search Tabs.

```bash
cd /Users/lydoc/projectscoding/skillhub
cd web && pnpm vitest run                       # 631/631 passing
cd web && pnpm tsc --noEmit                     # only registry-skill.tsx errors remain (pre-existing)
cd server && ./mvnw test                        # 460/460 passing — RUN FROM ROOT
```

---

## 2026-04-27 — P0-1a: WeaveHub design tokens migrated

**Plan:** [docs/plans/2026-04-27-weavehub-tokens.md](../docs/plans/2026-04-27-weavehub-tokens.md)
**Spec:** [docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md](../docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md) §2 + §4.1
**ADR clause:** [ADR 0003](../docs/adr/0003-fork-scope-and-upstream-boundary.md) §1.2
**Branch:** `main`
**Range:** `30c05d89` → `d4583420` (9 commits, all on top of plan commit `79483ce3`)
**Method:** subagent-driven (spec + code-quality two-stage review per task)

### What shipped (9 commits)

```
d4583420 feat(web): migrate AgentCard to glass-card visual
762b74fe feat(web): migrate SkillCard to glass-card visual
17872a11 feat(web): add opt-in glass prop to base Card
0362c05c feat(web): migrate Button default variant to btn-primary semantic class
5b069ed0 feat(web): expose brand-50..700 palette in tailwind theme
31cd3540 feat(web): add glass-card, btn-primary/secondary, nav-chip utility classes
2a796667 feat(web): drop Syne/IBM Plex Sans heading fallbacks (use Inter)
24b0c2de feat(web): switch brand color palette from indigo/violet to weavehub green
30c05d89 chore(web): add motion dependency for upcoming weavehub animations
```

End-to-end token layer migrated: brand palette indigo+violet → green-monochrome (`#34a853` / `#2c8e46` / `#23743a`),
heading font fallback `Syne / IBM Plex Sans` → `Inter`, 4 new utility classes (`.glass-card` / `.btn-primary` /
`.btn-secondary` / `.nav-chip` + modifiers), tailwind exposes `bg-brand-500` etc., Button default uses semantic
`btn-primary` class, base Card has opt-in `glass` prop with 4 new tests, SkillCard / AgentCard migrated to
`<Card glass>`. `motion` 12.38.0 installed for P0-1b.

### Tests

- Web: 631 → **635 passing** (+4 from new card.test.tsx)
- Backend: 460 (unchanged, untouched)
- Typecheck: only pre-existing `registry-skill.tsx` errors
- Lint: 3 warnings (2 in `registry-skill.tsx` pre-existing + 1 in `web/weavehub---知连/src/App.tsx` —
  this is the user-provided reference prototype, not project code; should be ignored via `.eslintrc.cjs`
  `ignorePatterns` in a separate small commit if/when we need lint to be clean again. **Not introduced by P0-1a.**)

### Visual delta

- All purple/indigo gradients → green via single token (`--brand-gradient`)
- 14 downstream `bg-brand-gradient` / `text-brand-gradient` references auto-recolored, no class renames
- SkillCard / AgentCard now glass-morphism (translucent white + backdrop-blur + rounded-3xl + hover lift)
- Default Button variant simpler: 1 semantic class instead of 4 compound utilities
- `--radius` bumped from `0.75rem` → `1rem` (rounded-lg etc. all scale up)

### Spec divergences (intentional)

1. **`web/index.html` font links not changed** — they were already correct (only Inter+JetBrains Mono loaded).
   Plan's Task 2 mentioned changing them; turned out unneeded.
2. **`web/index.html <title>` stays `SkillHub`** — title rename is in P0-1b, per spec §3.7.
3. **`web/src/app/layout.tsx` nav not yet migrated to `nav-chip` class** — current nav still uses
   `bg-brand-gradient pill` style which auto-recolors green. Class swap moved to P0-1b's nav-link reshuffle
   (cleaner to do in one shot).
4. **8 `brand-gradient` files mentioned in spec actually grep to 14 references in 7 files** — handled
   by single-token redefinition, no per-file edits needed.
5. **`motion` resolved to ^12.38.0**, not ^11.x as plan guessed. Motion 12 has no breaking changes for
   our future use.
6. **`card.test.tsx`** (.tsx not .ts as plan said) — JSX requires .tsx (per memo/lessons.md
   2026-04-26 entry).

### Known gaps for P0-1b

1. Site name still says "SkillHub" — P0-1b will change to "知连 WeaveHub"
2. Landing page nine-section structure unchanged — P0-1b rewrites to weavehub 4-section IA
3. Nav links / chip migration — P0-1b
4. `motion/react` installed but not used — P0-1b consumes it
5. The `Card glass` prop is only adopted by SkillCard + AgentCard so far. Other Card consumers
   (workflow-steps, landing-channels, landing-quick-start, skeleton-loader, landing.tsx) still default
   to plain solid `rounded-xl bg-card`. P0-1b will sweep landing-* components and decide per-component
   whether to opt in.

### How to resume (for P0-1b)

```bash
cd /Users/lydoc/projectscoding/skillhub
git status   # branch: main, P0-1a complete
cd web && pnpm vitest run   # 635/635 passing
cd web && pnpm tsc --noEmit # only registry-skill.tsx errors (pre-existing)
```

Before starting P0-1b, **start the dev server and visually confirm P0-1a output**:

```bash
cd web && pnpm dev
```

Walk: `/` (landing — 颜色变绿,卡片有 glass 感), `/search`, `/agents`, login button, /dashboard/publish.
If anything looks broken, the cause is almost certainly in commits `24b0c2de` (palette) or `31cd3540`
(utility classes). Roll back individual commits if needed; each task is its own atomic commit.

After visual confirmation, invoke `superpowers:writing-plans` against spec §4.2 to expand P0-1b into
an executable plan.

---

## 2026-04-27 — P3-2a + P2-2 + P2-4 backend triple-tap

**Backlog:** [docs/plans/2026-04-27-fork-backlog.md](../docs/plans/2026-04-27-fork-backlog.md)
**Branch:** `main`
**Range:** `5d62a75b` (P3-2a) → `67a64cb8` (P2-4)
**Method:** sequential (subagent-free, all backend-only, no frontend dependencies)

### What shipped

- **P3-2a** (`5d62a75b`) — Wired `BasicPrePublishValidator` into `AgentPublishService`. New
  `PrePublishValidator.validateEntries(entries, publisherId, namespaceId)` default-method overload lets
  the agent flow reuse the same scanner without an artificial `SkillMetadata` requirement. Failures and
  warnings throw `DomainBadRequestException` before any persistence.
- **P2-2** (`3d4d0e0d`, see snafu below) — Added Agent governance endpoints:
  `POST /api/{v1,web}/agents/{namespace}/{slug}/archive` + `/unarchive`. New `AgentLifecycleService`
  in domain (owner OR namespace ADMIN/OWNER required), new `AgentLifecycleController` in app, new
  `AgentLifecycleMutationResponse` DTO, four new entries in `RouteSecurityPolicyRegistry`, plus
  `Agent.unarchive()` to mirror the existing `archive()` method. Audit-log records under `ARCHIVE_AGENT`
  / `UNARCHIVE_AGENT` action / `AGENT` target type.
- **P2-4** (`67a64cb8`) — Added `spring-boot-starter-validation` to `skillhub-app/pom.xml`. The ~44
  pre-existing `@Valid`/`@NotBlank`/`@Email`/`@Size` annotations are now actually enforced. The
  pre-existing `GlobalExceptionHandler.handleValidation` was already wired to map
  `MethodArgumentNotValidException` to 400, so external contract is unchanged.

### Test counts

- Backend: 460 → **468/468** in skillhub-app (full reactor 1098 across 7 modules, 0 failures, 0 errors)
- New tests: AgentPublishServiceTest 6 → 8 (validator-warning + validator-failure cases),
  AgentLifecycleServiceTest 8 (owner/admin/ns-owner/stranger/null-roles/404/unarchive variants),
  AgentLifecycleControllerTest 7 (happy + at-prefix + 404×2 + 403 + 401 + unarchive),
  GlobalExceptionHandlerTest 2 → 3 (handleValidation case)
- Test fallout from validation activation: 4 cases in `LocalAuthControllerTest` previously asserted
  `verify(service).register/...(badEmail)`; updated to `verify(service, never()).<method>(any(), ...)`
  reflecting the new short-circuit at the validation layer. 400 contract assertions unchanged.

### Spec divergences worth noting

1. **P3-2a — entries-only overload, not full context refactor.** The plan said "inject validator into
   `AgentPublishService`" but `PrePublishValidator.SkillPackageContext` requires a `SkillMetadata`,
   which the agent flow doesn't have (it has `AgentMetadata`). Considered three options: (a) pass null
   metadata, (b) refactor context to be polymorphic, (c) add a new entry-only method. Picked (c) —
   minimal diff, keeps existing skill-side callers untouched, both existing impls (`Basic`, `NoOp`)
   work because neither reads the metadata field. The default method delegates to the existing
   `validate(SkillPackageContext)` with null metadata.

2. **P2-2 — `Agent.unarchive()` was missing; added it.** The backlog said "domain ready, app missing"
   but only `archive()` existed. `AgentStatus.ARCHIVED` and `ACTIVE` enum values exist; just needed
   the inverse method. No version-level archive (matches v1 scope per backlog).

3. **P2-4 — bigger blast radius than expected: validation activation broke 4 LocalAuthControllerTest
   cases.** The tests were written assuming bean validation didn't fire, so they stubbed the service
   to throw on bad email and asserted the service was called. With validation enabled, the service
   is *never* called for bad emails — the validation layer 400s first. Updated assertions to match
   reality. This is exactly the kind of latent issue the backlog called out as "潜伏炸弹".

### Snafu — P2-2 commit message is wrong (worth knowing)

Commit `3d4d0e0d` is captioned `feat(web): register /my-weave route (auth-gated)` but its actual
content is the seven P2-2 backend files (AgentLifecycleController/Service/Test, dto, security policy,
Agent.java, AgentLifecycleServiceTest) PLUS one tiny `web/src/app/router.tsx` line from the parallel
P0-1b session. A concurrent claude instance running P0-1b's `/my-weave` work executed `git add` over
the whole tree at the moment my P2-2 files were staged, then committed everything under their own
message before I could commit mine. The code is correct in main, just the commit-log archeology is
misleading. Did not amend (history is shared and this is non-destructive enough to leave). If anyone
later searches for "AgentLifecycle" in `git log` and finds nothing, look at `3d4d0e0d`.

Lesson recorded in `memo/lessons.md` 2026-04-27 parallel-claude entry.

### Known gaps (not bugs)

1. **No frontend wiring for archive/unarchive yet.** Backend endpoints are live and security-gated.
   Plan says "前端:My Agents 页 + agent-detail 加 archive/unarchive 按钮(权限可见)" — that's a
   separate frontend task. Listed as next backlog candidate.
2. **No real integration test for the lifecycle flow.** Carried over from skill comments / agent
   review pattern — codebase has no `@SpringBootTest` precedent that drives real repos through the
   controller for these mutations. Mocked controller tests + domain-service tests cover the
   contract.
3. **P3-2b (extending validator chain) is still pending.** Independent brainstorm per backlog.

### How to resume

```bash
cd /Users/lydoc/projectscoding/skillhub
git status   # branch: main, my 3 commits live (P3-2a / P2-2 conflated / P2-4)
cd server && ./mvnw test          # 1098 / 0F / 0E across 7 modules
```

Backlog updates after this session — these can be marked ✅:

- P3-2a: ✅ done (commit `5d62a75b`)
- P2-2: ✅ done (commit `3d4d0e0d` — see snafu note above)
- P2-4: ✅ done (commit `67a64cb8`)

Natural next stops per ADR 0003:

- P0-2 (Agent list search backend + frontend) — backlog says ~1 day full-stack.
- P2-1 (Agent star + rating, mirrors skill_star/skill_rating) — backlog says ~1 day full-stack.
- Wire archive/unarchive UI buttons into My Agents + agent-detail pages.
- P3-2b (extend validator rule chain — needs brainstorm per backlog).

---

## 2026-04-27 — P0-1b: WeaveHub landing IA + my-weave + nav reshuffle

**Plan:** [docs/plans/2026-04-27-weavehub-landing-ia.md](../docs/plans/2026-04-27-weavehub-landing-ia.md)
**Spec:** [docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md](../docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md) §3 + §4.2
**ADR clause:** [ADR 0003](../docs/adr/0003-fork-scope-and-upstream-boundary.md) §1.2
**Branch:** `main`
**Range:** `9402e3aa` (plan commit) → `f28ee2fe` (final task commit) — 14 task commits + 1 cleanup commit + 1 plan commit
**Method:** subagent-driven (mostly haiku, sonnet for complex tasks)

### What shipped (15 commits since plan)

```
f28ee2fe feat(web): update browser tab title to 知连 WeaveHub
ce5074b9 feat(web): reshuffle nav, rename brand to WeaveHub via i18n
3d4d0e0d feat(web): register /my-weave route (auth-gated)
08beaeb5 feat(web): add /my-weave aggregate page
65941145 feat(web): delete obsolete landing-channels/popular-agents/landing-quick-start
c9481160 feat(web): rewrite landing.tsx as weavehub 4-section IA
8dadf519 feat(web): add LandingFooter with WeaveHub brand and link columns
a396018c feat(web): add LandingWorkspace guest/auth two-state right rail
b0b70449 feat(web): add LandingRecentSection compact mixed grid
93cb9874 feat(web): add LandingHotSection mixed skill+agent grid
96ded009 feat(web): add LandingHero with weavehub visual + B copy
f9c9f482 fix(web): drop unused rest spread in resource-card test motion mock
41f963a1 feat(i18n): replace landing keys with weavehub vocabulary
16113c1c feat(web): add ResourceCard with featured + compact variants
9402e3aa docs(plans): P0-1b WeaveHub landing IA implementation plan
```

End-to-end landing page rewritten from 9-section old structure to weavehub
4-section IA: LandingHero / LandingHotSection / LandingRecentSection +
LandingWorkspace / LandingFooter. New `/my-weave` route surfaces the user's own
skills and agents. Nav reshuffled per spec §3.3:
- 未登录: 首页 / 搜索
- 已登录: 首页 / 发布 / 技能 / 智能体 / 我的 Weave / 控制台 / 搜索

Brand renamed via i18n: `nav.brand` returns "知连 WeaveHub" (zh) /
"WeaveHub" (en). Browser tab title now "知连 WeaveHub".

Visual className strings mirror prototype web/weavehub---知连/src/App.tsx
1:1 — only mock data was replaced with real `useSearchSkills` + `useAgents` +
`useMySkills` calls, and copy was simplified per the brainstorming "B" decision
(no "全球领先", Hero "持续进化的 AI 能力" + "让团队的技能包和智能体在一起协作").

### Tests

- Web: 635 → **632 passing**(net -3 from heavy delete + new tests; deleted ~12 obsolete tests, added 9 new)
- Backend: 460 (unchanged, untouched)
- Typecheck: only pre-existing `registry-skill.tsx` errors (3)
- Lint: only pre-existing `registry-skill.tsx` warnings (2) — 1 *less* than after P0-1a (the lint warning on weavehub prototype directory disappeared, likely because the prototype `App.tsx` was overwritten by the user during P0-1b)

### Spec divergences (intentional, all approved during execution)

1. **Task 9 expanded** — plan only listed 6 files to delete; final review found `web/src/i18n/landing-quick-start-locale.test.ts` also referenced deleted i18n keys. Added to plan and to deletion list.
2. **Task 6 useMySkills signature** — plan suggested `useMySkills({}, { enabled })` but the actual hook signature is `useMySkills(params)` only. Implementer correctly adapted: call unconditionally, then filter at the consumer.
3. **Task 1 vi.mock additions** — implementer added `vi.mock('@tanstack/react-router')` and `vi.mock('motion/react')` to resource-card.test.tsx because Link + motion.div both throw in jsdom without provider. Acceptable. Cleanup commit (f9c9f482) tightened the motion mock signature.
4. **Test count** — plan estimated 642ish; actual 632. Difference is from larger-than-estimated obsolete test deletions in Task 9 (landing-channels.test.tsx + popular-agents.test.tsx had 5 tests each, not the 1 each I estimated).
5. **Layout test** — plan flagged it might need updates; turns out it was a barrel-export shape test and needed no changes.

### Visual delta

- Landing page is now the weavehub 4-section IA: Hero with serif italic
  highlight + 小绿条徽章 + 搜索 + 开始探索 / 热门推荐 (3-col featured cards) / 全域动态流 (compact cards left col-span-8) + 工作台 (col-span-4 guest/auth toggle) / Footer with English-only WeaveHub brand
- Site name 知连 WeaveHub (zh) / WeaveHub (en) via i18n
- Browser title 知连 WeaveHub
- Nav 链表 reshuffled to 7 items per spec
- All mock prototype data replaced with real query hooks
- All className strings mirror prototype 1:1

### Known gaps for future work

1. **Footer 双轨**: layout.tsx still has the old SkillHub-style footer for non-landing routes; landing uses the new LandingFooter. Future cleanup could unify if desired.
2. **`/skills` 路由** is in the nav as "Skills"; it currently routes to HomePage component. May want a dedicated skills marketplace page later.
3. **WorkspaceCard "open control panel"** routes to /dashboard — consider whether it should route to /my-weave instead (more discovery-oriented).
4. **Hot/Recent section data source** uses skill `useSearchSkills(downloads/newest)` + agent `useAgents`. When agent search lands (P0-2), wire it through here too.
5. **Footer links** — API References, Cloud Sync, etc. point to `#`. They need real targets; treat as a placeholder until docs/marketing pages exist.
6. **Hero search button "开始探索"** with empty query — currently routes to `/search?q=`. Could improve UX by focusing the search input instead.

### How to resume

Browser smoke recommended before declaring done:

```bash
pnpm dev
```

Walk: `/` (landing — should match user-provided 4 screenshots), `/my-weave`
(double-list shell), `/search` (no regression), `/agents` (no regression),
login → re-test `/` to see workspace authenticated state.

Backlog after P0-1b:
- Update fork backlog to mark P0-1a + P0-1b ✅, refresh visual baseline notes
- P0-2 (Agent list search backend + frontend) — next per backlog priority

---

## 2026-04-27 — Landing 首页卡片可读性优化（留白 / 行数 / 图标）

**范围**: `web/src/shared/components`（仅首页资源卡片链路）

### 本次交付

- 两种卡片（`featured` + `compact`）统一支持：标题 `2` 行、附文 `2` 行（`line-clamp-2`）。
- `compact` 卡片新增附文展示（原来只显示标题）。
- 卡片留白微调：`compact` 内边距从 `!p-6` 提到 `!p-7`，`featured` 提到 `!p-8`，并优化图标与文本间距。
- 图标策略从“随机池 hash”改为“按类型固定”：
  - `skill` → `Wand2`
  - `agent` → `Bot`
- `LandingRecentSection` 补齐卡片附文字段映射（skill 用 `summary`，agent 用 `description`）。

### 影响文件

- `web/src/shared/components/resource-card.tsx`
- `web/src/shared/components/landing-hot-section.tsx`
- `web/src/shared/components/landing-recent-section.tsx`
- `web/src/shared/components/resource-card.test.tsx`（同步断言，compact 现在应显示附文）

### 验证

- `pnpm vitest run src/shared/components/resource-card.test.tsx src/pages/landing.test.tsx` → **7/7 通过**
- 以上 4 个变更文件 VS Code diagnostics 均为 0

### 追加调整（同日）

- 用户反馈“热门推荐图标和文字不在一行、两种卡片对齐感不足”后，`featured` 结构已改为与 `compact` 同骨架：`图标 + 标题(同一行) + type tag`，并保持附文/底部信息区节奏不变。
- 图标策略改回“稳定随机（基于 id/name hash）”，保留多样性且不抖动。
- 回归验证：`pnpm vitest run src/shared/components/resource-card.test.tsx src/pages/landing.test.tsx` 仍 **7/7 通过**。

---

## 2026-04-27 — 全局 Footer 文档/社区链接升级为真实地址

**范围**: `web/src/shared/components/landing-footer.tsx`

### 本次交付

- 基于仓库 `README.md` 中的官方地址，将 Footer 文档/社区链接由“站内兜底跳转”升级为真实外链：
  - API References → `https://zread.ai/iflytek/skillhub`
  - Cloud Sync / Integration → `https://iflytek.github.io/skillhub/`
  - Security → `https://github.com/iflytek/skillhub/security`
  - Open Source → `https://github.com/iflytek/skillhub`
  - Forum → `https://github.com/iflytek/skillhub/discussions`
  - Support → `https://github.com/iflytek/skillhub/issues`
- 为外链统一补充 `target="_blank"` 与 `rel="noopener noreferrer"`。
- 隐私条款继续保留站内路由（`/privacy`），与现有页面一致。

### 验证

- `pnpm vitest run src/shared/components/landing-footer.test.tsx src/app/layout.test.ts src/pages/landing.test.tsx` → **6/6 通过**
- `landing-footer.tsx` diagnostics: **0**

---

## 2026-04-27 — P0-2: Agent 列表搜索 + 筛选

**Plan:** [docs/plans/2026-04-27-agent-list-search.md](../docs/plans/2026-04-27-agent-list-search.md)
**ADR:** 0003 §1.1 (Agent management)
**Range:** `93bfff15` (backend) → `da7e4c68` (frontend) · 2 commits

### Brainstorming 决策

- Q1=B：`q` ILIKE 字段 = display_name + description（不含 slug）
- Q2=C：未登录传 `visibility=PRIVATE/NAMESPACE_ONLY` 时返空列表（不报错，不泄露存在性）—— 自然落到 `AgentVisibilityChecker` 的 anonymous 分支
- Q3=A：`namespace` 参数 = slug，slug 不存在 → 404（与 `getOne` 一致）
- Q4=B：**"我能看到的全部"语义** —— 匿名 PUBLIC only，登录 PUBLIC + 自己有权限的 PRIVATE/NAMESPACE_ONLY，可选 `visibility` 参数收窄

### 架构

DB 层做 keyword + namespace 预过滤（JPQL `LOWER(...) LIKE LOWER(...)`，跨方言），应用层做 visibility 过滤（复用 `AgentVisibilityChecker.canAccess`）。Trade-off：`Page.totalElements` 反映 raw repo total 不是 post-filter total，前端可能"X 结果实际可见 X-N"。P0 接受，P3-3 上 `agent_search_document` 时重做。

### 改动清单

**Backend (commit `93bfff15`):**
- `AgentRepository.searchPublic(keyword, namespaceId, pageable)` port + JPA `@Query` impl
- `AgentService.searchPublic(...)` 应用 visibility filter，旧 `listPublic(Pageable)` deprecate 但保留
- `AgentController.listPublic` 接 `q`/`namespace`/`visibility` query params + slug→id 解析 + `parseVisibility`
- `AgentControllerTest`：5 → 10（+5：q 传播 / namespace 解析 / 404 unknown / visibility 传播 / 400 invalid）
- `AgentServiceTest`：6 → 10（+4：anonymous PUBLIC only / owner PRIVATE / namespace member / visibility filter narrows）
- 后端套件：468 → **473 全绿**

**Frontend (commit `da7e4c68`):**
- `agentsApi.list` 接 `q`/`namespace`/`visibility`，导出 `AgentVisibilityFilter` 类型
- `useAgents` 接 `UseAgentsParams`，query key 含 params 对象（不同参数→不同 cache）
- `agents.tsx` 加 search Input（`useDebounce(rawQ, 300)`）+ namespace Select（`useMyNamespaces` 数据）+ visibility Select（**仅登录可见**）
- 空状态分支：filter 激活 + 0 结果 → `agents.search.noResults`，否则原 emptyTitle/emptyDescription
- i18n：`agents.search.{placeholder,allNamespaces,allVisibility,visibility{Public,Namespace,Private},noResults}` 7 keys × 2 语言
- `useAgents.test.tsx`：2 → 4（+2：参数传播 / cache key 隔离）
- 新增 `agents.test.tsx`：4 cases（debounce / visibility-selector auth-gating / noResults state）
- web 套件：632 → **641 全绿**

### Plan 偏离

1. **Plan Task 7 (`useDebouncedValue` helper) SKIPPED** —— pre-flight 发现 `web/src/shared/hooks/use-debounce.ts` 已 export `useDebounce`（同样签名 `<T>(value, delay=300)`）。直接 import 复用，没新建文件。
2. **Plan 写"6 个 commit 拆分"实际 2 个 commit** —— 决定 backend 一个 commit（repo + service + controller + 两侧 test 一起，逻辑闭合），frontend 一个 commit。这避免了"backend 改了 controller 但没接 service 测试"那种中间态出现在 history 里。也按 lessons.md 第 5 条减少 git index 暴露窗口。
3. **Backend baseline 实际 468 不是 plan 原写的"460"或"635"** —— pre-flight 时已发现并把 plan 数字同步成 468/632。

### 已知遗留

- 分页 over-count（plan §"Known limitations" #1）—— 留待 P3-3
- 无 ranking / highlight / autocomplete —— 留待 P3-3
- **`agents.tsx` Hero 视觉块还是旧蓝紫 gradient**（`from-purple-500/10 to-blue-500/10` + `bg-gradient-to-r from-purple-600 to-blue-600 bg-clip-text text-transparent`）—— P0-1a tokens 已切绿但 agents 页 Hero 没回归。**不属于 P0-2 scope**，下次 visual sweep 时一并处理。

### How to resume

```bash
cd /Users/lydoc/projectscoding/skillhub
cd server && ./mvnw test 2>&1 | tail -3   # 473/473
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -3   # 641/641
cd /Users/lydoc/projectscoding/skillhub/web && pnpm tsc --noEmit 2>&1 | grep -v "registry-skill.tsx" | tail -3   # clean
```

P0 全部清空。下一站候选（按 backlog 顺序）：P2-1 Agent star + rating，或独立 brainstorm 启动 P1-2 Agent 评论 / P3-1 Workflow Executor / P3-3 Agent search FTS。

---

## 2026-04-27 — Agents 页面对齐 Skills 风格（保留搜索，移除筛选）

**范围**: `web/src/pages/agents.tsx` + `web/src/pages/agents.test.tsx`

### 本次交付

- `agents` 页面保留搜索能力，但改为仅关键词搜索（`q`），移除命名空间与可见性筛选控件。
- 搜索输入区改用与 `skills` 同款 `SearchBar` 组件，风格与交互保持一致。
- 页面骨架向 `skills` 对齐：居中 Hero（大标题/副标题）+ 搜索区 + 登录态发布按钮；列表区保留 AgentCard 网格展示。
- 网格间距从 `gap-4` 调整为 `gap-5`，与 `skills` 页面卡片节奏一致。

### 测试与验证

- 更新 `agents.test.tsx`：删除“可见性筛选显示/隐藏”预期，改为“筛选控件不存在 + 登录态仍有发布按钮”。
- 执行：`pnpm vitest run src/pages/agents.test.tsx src/pages/home.test.tsx` → **6/6 通过**
- `agents.tsx`、`agents.test.tsx` diagnostics 均为 **0**。

---

## 2026-04-27 — 修复智能体发布页按钮被前端状态误判禁用

**范围**: `web/src/pages/dashboard/publish-agent.tsx` + `web/src/pages/dashboard/publish-agent.test.tsx`

### 问题

- 用户反馈“发布智能体全部填写完成后，发布按钮仍不可用”。
- 根因在于按钮禁用依赖前端本地状态组合（`selectedFile && namespace`），一旦某个 UI 事件未及时同步，就会被卡死，无法触发表单点击校验。

### 修复

- 按钮禁用条件改为仅 `publish.isPending`（提交中才禁用）。
- 必填校验继续保留在 `handleSubmit` 中（缺文件/缺命名空间依旧 toast 提示），避免误禁用。
- 新增测试覆盖该行为：非 pending 时提交按钮应可点击，以便触发校验逻辑。

### 验证

- `pnpm vitest run src/pages/dashboard/publish-agent.test.tsx src/pages/dashboard/publish.test.ts` → **5/5 通过**
- `publish-agent.tsx`、`publish-agent.test.tsx` diagnostics 均为 **0**。

---

## 2026-04-27 — Agent 详情页对齐 Skill 详情样式（主文档双标签）

**范围**:
- `web/src/pages/agent-detail.tsx`
- `web/src/features/agent/use-agent-detail.ts`
- `web/src/api/agent-types.ts`
- `web/src/pages/agent-detail.test.tsx`
- `web/src/i18n/locales/en.json`
- `web/src/i18n/locales/zh.json`

### 本次交付

- `agent-detail` 从单栏调整为与 `skill-detail` 同风格的双栏布局（主内容 + 侧栏）。
- 主内容改为 Tabs：`概览 / 工作流 / 版本`，并保留智能体既有业务能力（评分、收藏、归档治理）。
- 概览区实现主文档双标签切换：`agent.md` 与 `soul.md`，默认打开 `agent.md`（用户确认 B）。
- 不引入假功能：版本区仅展示后端真实返回数据（版本号、状态、时间、包大小）。
- `useAgentDetail` 透传 `visibility` 与 `versions`，供详情页真实渲染。

### 测试与验证

- 先写失败测试（新增 tabs + 双标签切换断言）后再实现，按 TDD 完成改造。
- `pnpm vitest run src/pages/agent-detail.test.tsx` → **6/6 通过**
- `pnpm vitest run src/pages/agent-detail.test.tsx src/pages/agents.test.tsx` → **10/10 通过**
- diagnostics:
  - `agent-detail.tsx` = 0
  - `use-agent-detail.ts` = 0
  - `agent-types.ts` = 0
  - `agent-detail.test.tsx` = 0

---

## 2026-04-27 — 智能体发布页可见性文案与 Skills 完全对齐

**范围**: `web/src/pages/dashboard/publish-agent.tsx` + `web/src/pages/dashboard/publish-agent.test.tsx`

### 本次交付

- `publish-agent` 可见性文案改为直接复用 `skills` 发布页同一套 i18n key：
  - `publish.visibility`
  - `publish.visibilityOptions.public`
  - `publish.visibilityOptions.namespaceOnly` / `publish.visibilityOptions.loggedInUsersOnly`
  - `publish.visibilityOptions.private`
- 命名空间为 `GLOBAL` 时，保持与 skills 一致显示 `loggedInUsersOnly`，否则显示 `namespaceOnly`。

### 验证

- 先补失败用例（RED）：断言页面不再出现 `agents.publish.visibility*`，必须出现 `publish.visibilityOptions.*`。
- 再实现（GREEN）后回归：
  - `pnpm vitest run src/pages/dashboard/publish-agent.test.tsx src/pages/dashboard/publish.test.ts` → **5/5 通过**
  - diagnostics：`publish-agent.tsx` / `publish-agent.test.tsx` 均为 **0**

### 补充（A 方案）

- 用户明确选择 `A`（按 skills 当前短文案），已将 `agents.publish.visibilityPublic/visibilityNamespace/visibilityPrivate` 也同步改为短文本（en/zh），避免任何后续误引用旧长文案造成视觉不一致。
- 回归：`pnpm vitest run src/pages/dashboard/publish-agent.test.tsx src/pages/dashboard/publish.test.ts` 继续 **5/5 通过**。

---

## 2026-04-27 — Agents 列表卡片对齐 Skills 信息层（含固定高度）

**范围**:
- `web/src/features/agent/agent-card.tsx`
- `web/src/features/agent/use-agents.ts`
- `web/src/api/agent-types.ts`
- `web/src/features/agent/agent-card.test.tsx`
- `web/src/features/agent/use-agents.test.tsx`

### 本次交付

- `AgentCard` 按 skills 卡片节奏补齐信息：
  - 标题：优先 `displayName`，回退 `name`
  - 命名空间徽标：显示 `@{namespace}`，缺省为 `@global`
  - 底部元信息：版本（无则 `—`）、下载量（占位 `—`）、收藏量（`starCount`）
- 卡片高度统一：新增 `min-h-[220px]`，并保留 `h-full + mt-auto`，使同排卡片视觉高度更一致。
- `useAgents` 透传新增字段到 `AgentSummary`：`displayName`、`version`、`starCount`（下载量仍按用户选择显示 `—`，不伪造数据）。

### 测试与验证

- TDD：先改测试使其失败，再实现。
- `pnpm vitest run src/features/agent/agent-card.test.tsx src/features/agent/use-agents.test.tsx src/pages/agents.test.tsx` → **12/12 通过**
- diagnostics:
  - `agent-card.tsx` = 0
  - `use-agents.ts` = 0
  - `agent-types.ts` = 0
  - `agent-card.test.tsx` = 0

---

## 2026-04-27 — Agent 详情页补齐 Files 视图与文件下载（对齐 Skills 体验）

**范围**:
- `web/src/pages/agent-detail.tsx`
- `web/src/pages/agent-detail.test.tsx`
- `web/src/features/agent/use-agent-detail.ts`
- `web/src/api/agent-types.ts`
- `web/src/i18n/locales/en.json`
- `web/src/i18n/locales/zh.json`

### 本次交付

- 在 `agent-detail` 的主 Tabs 中新增 `Files` 标签（对齐 skills detail 的信息架构）。
- 基于当前后端已返回的真实内容（`agent.body` / `agent.soul` / `workflowYaml`）构建虚拟文件清单：
  - `agent.md`
  - `soul.md`
  - `workflow.yaml`
- 复用现有 `FileTree` + `FilePreviewDialog` 组件，支持：
  - 文件树浏览
  - 文件内容预览
  - 单文件下载（浏览器 Blob 下载）
- `AgentDetail` 类型补充 `workflowYaml?: string`，`useAgentDetail` 同步透传该字段。
- i18n 补充 `agents.detail.tabFiles`（en/zh）。

### 测试与验证

- 先补失败测试（RED）：`agent-detail.test.tsx` 增加 Files tab 断言与文件预览交互断言。
- 再实现并回归（GREEN）：
  - `pnpm vitest run src/pages/agent-detail.test.tsx src/features/agent/agent-card.test.tsx src/features/agent/use-agents.test.tsx src/pages/agents.test.tsx` → **20/20 通过**
- diagnostics:
  - `agent-detail.tsx` = 0
  - `use-agent-detail.ts` = 0
  - `agent-types.ts` = 0
  - `agent-detail.test.tsx` = 0

---

## 2026-04-27 — Agent 详情页补齐安装/下载/分享操作区（继续对齐 Skills）

**范围**:
- `web/src/pages/agent-detail.tsx`
- `web/src/pages/agent-detail.test.tsx`

### 本次交付

- 在 `agent-detail` 侧栏新增与 skills 风格一致的操作区：
  - `Install` 卡片：复用 `InstallCommand`（`npx clawhub install ... --registry ...`）
  - `Download` 按钮：走后端真实路径 `/api/web/agents/{namespace}/{slug}/versions/{version}/download`
  - `Share` 按钮：复制 `namespace/slug + 描述 + agents 详情页 URL`
- 保持“无假功能”原则：下载直接调用真实接口 URL，不做假数据模拟。

### 测试与验证

- 更新 `agent-detail.test.tsx`，断言操作区文案与按钮存在。
- 回归：
  - `pnpm vitest run src/pages/agent-detail.test.tsx src/features/agent/agent-card.test.tsx src/features/agent/use-agents.test.tsx src/pages/agents.test.tsx` → **20/20 通过**
- diagnostics:
  - `agent-detail.tsx` = 0
  - `agent-detail.test.tsx` = 0

---

## 2026-04-27 — Agent 详情页补齐：侧栏文件浏览卡片 + 举报按钮拆分

**范围**:
- `web/src/pages/agent-detail.tsx`
- `web/src/pages/agent-detail.test.tsx`

### 本次交付

- 在 Agent 详情页右侧栏新增与 Skill 详情同风格的“文件浏览”折叠卡片（含文件数、展开/收起、`FileTree bare` 列表）。
- 保留主区 `Files` tab，同时补齐侧栏快速文件浏览入口，满足“详情页侧栏可直接看文件”的使用习惯。
- 将“举报”按钮从“收藏/评分”卡片中拆出，单独成卡片；社交卡只保留 `AgentStarButton` 与 `AgentRatingInput`。

### 测试与验证

- 新增测试：断言侧栏文件浏览卡片可见、且举报按钮所在卡片不包含收藏/评分组件。
- 回归：`pnpm vitest run src/pages/agent-detail.test.tsx` → **9/9 通过**
- diagnostics:
  - `agent-detail.tsx` = 0
  - `agent-detail.test.tsx` = 0

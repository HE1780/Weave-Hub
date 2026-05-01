# Handover:Agent ↔ Skill Parity 收尾 Polish

> 上一会话 [`b250461e`](../../) 已交付 6 步主体改造(state machine / domain service / controller / 测试 / 前端核心通路 / drop 脚本)。这份 handover 列出当时**显式延后**的 5 项,新会话从此文档接手即可独立完成,无需阅读已 SHIPPED 的 [2026-05-01-agent-skill-parity-HANDOVER.md](2026-05-01-agent-skill-parity-HANDOVER.md)。

- **基线分支**:`main`(已含全部主体改造)
- **建议工作分支**:`feat/agent-skill-parity-polish`
- **关联 memo**:[memo/memo.md 2026-05-01 条目](../../memo/memo.md)
- **预估工作量**:1.5–2 天(全做)。每项之间无强依赖,可独立切片提交。

---

## 1. 任务一句话

把 Step 5 为了"端到端跑通"做的 5 个临时简化补齐成产线质量:专用状态徽标、search 页 Agent tab、正式 i18n、admin 报告面板的 RESOLVE_AND_HIDE picker、drop 脚本实际执行。

## 2. 起点核对(开工前 1 分钟)

```
git checkout main && git pull origin main
git log --oneline -3   # 应包含 66365ff0 docs(memo)、b250461e merge
git checkout -b feat/agent-skill-parity-polish
cd web && pnpm install && pnpm tsc --noEmit && pnpm vitest run   # baseline 708/708
cd ../server && ./mvnw -q test-compile                            # baseline clean
```

baseline 不绿不要继续——先排查环境再上工。

## 3. 待办(独立切片,推荐执行顺序)

### F1:专用 version-status-badge 组件 (~3 小时,1 commit)

**问题**:Step 5 在 [agent-detail.tsx:210](../../web/src/pages/agent-detail.tsx) 的 `resolveVersionStatusLabel` 只映射了 5 个旧状态(`PUBLISHED / PENDING_REVIEW / DRAFT / REJECTED / ARCHIVED`)。SCANNING / SCAN_FAILED / UPLOADED / YANKED 走 fallback,直接渲染原始 enum 字符串。

**做什么**:
- 新建 [`web/src/features/agent/version-status-badge.tsx`](../../web/src/features/agent/),输入 `status: AgentVersionSummary['status']`,输出带颜色 + 图标的徽标。镜像 [`web/src/features/skill/version-status-badge.tsx`](../../web/src/features/skill/version-status-badge.tsx) 的 API。
- 9 种状态颜色建议:
  - `DRAFT` / `UPLOADED` — slate(中性,可编辑)
  - `SCANNING` — amber(进行中)
  - `SCAN_FAILED` / `REJECTED` — destructive
  - `PENDING_REVIEW` — blue
  - `PUBLISHED` — green
  - `ARCHIVED` — muted
  - `YANKED` — destructive 加删除线
- 在 [agent-detail.tsx:554](../../web/src/pages/agent-detail.tsx) 替换原 `resolveVersionStatusLabel` 调用;去掉本地 `statusMap`。
- 加 vitest 用例覆盖 9 个状态的渲染 + key 解析。

**i18n key 列表**(放在 `web/src/locales/{zh,en}/translation.json`):
```
agent.status.draft / scanning / scanFailed / uploaded /
  pendingReview / published / rejected / archived / yanked
```

**验证**:`pnpm vitest run web/src/features/agent/version-status-badge.test.ts` + 浏览器手测(在自己的 agent 详情页发布一个版本,看 SCANNING → UPLOADED 转场是否徽标正确)。

---

### F2:search 页 Skills/Agents Tab 切换 (~4 小时,1 commit)

**问题**:[`web/src/pages/search.tsx`](../../web/src/pages/search.tsx) 当前只搜 skills。spec 要求加 Tabs `[Skills | Agents]`,Agent tab 调 `agentsApi.list({ q })`,渲染 [`AgentCard`](../../web/src/features/agent/agent-card.tsx)。

**做什么**:
- 顶部用 [`@/components/ui/tabs`](../../web/src/components/ui/tabs.tsx)(同一个 Radix Tabs 包,skill-detail 已用),`value` 持久化到 URL `?tab=skills|agents`,默认 `skills`。
- Agent tab 复用 [`useAgents`](../../web/src/features/agent/use-agents.ts) 的搜索分支(确认它支持 `q` 入参——若不支持要先扩 hook + agentsApi.list)。
- 空态文案 i18n key:`search.tabSkills` / `search.tabAgents` / `search.emptyAgents`。
- 写一个 search.test.ts 用例:切到 agents tab → mock agentsApi.list 返回 2 条 → 检查 AgentCard 数量。

**注意点**:
- Agent 端目前没有 `agent_search_document` PG 全文索引(P3-3 未做),搜索还走 ILIKE。性能足够 P0,**不要在本任务里扩搜索基础设施**。
- 旧的 [`useAgents`](../../web/src/features/agent/use-agents.ts) 检查关键词支持。

**验证**:`pnpm vitest run web/src/pages/search.test.ts` + 浏览器(在 search 页输关键词,切 tab,看两侧结果)。

---

### F3:正式 i18n key 替换内联中文 (~1 小时,1 commit)

**问题**:Step 5 在 [agent-detail.tsx](../../web/src/pages/agent-detail.tsx) 的 `提交审核` / `确认发布` 按钮文案用 `t(key, { defaultValue: '中文' })` 形式行内 fallback。

**做什么**:
- 在 [`web/src/locales/zh/translation.json`](../../web/src/locales/zh/) 加:
  ```
  "agents.lifecycle.submitReview": "提交审核",
  "agents.lifecycle.confirmPublish": "确认发布"
  ```
- 同步 `en/translation.json`:`Submit for review` / `Confirm publish`
- 删除 agent-detail.tsx 那两行的 `defaultValue` 形参,只留 key
- 顺便检查 F1 引入的 `agent.status.*` 9 个 key 是否都齐了,否则一并补

**验证**:`pnpm vitest run` 全过 + `pnpm build` 通过(i18n 类型校验会捕获缺 key)。

---

### F4:Admin 报告面板的 RESOLVE_AND_HIDE picker UI (~2 小时,1 commit)

**问题**:[`web/src/api/types.ts:419`](../../web/src/api/types.ts) 已加 `RESOLVE_AND_HIDE` enum,但 [`web/src/pages/reports.tsx`](../../web/src/pages/reports.tsx) 的 agent 报告 panel 的 disposition Select 仍只列 `RESOLVE_ONLY` / `RESOLVE_AND_ARCHIVE`。后端已经能接,前端选不出来。

**做什么**:
- 在 reports.tsx 的 agent panel 里 grep `RESOLVE_AND_ARCHIVE`,在 Select 里加一项 `RESOLVE_AND_HIDE`,文案 i18n key `reports.disposition.resolveAndHide`(中:解决并隐藏 / 英:Resolve and hide)
- 检查 mutation hook(应该是 `useResolveAgentReport`),确认 disposition 入参类型已经是 `AgentReportDisposition`——若是,UI 加选项即可,无需改 hook
- 加 reports.test.tsx 用例:选 RESOLVE_AND_HIDE → 调 resolveAgentReportMutateAsync({ disposition: 'RESOLVE_AND_HIDE' })

**注意点**:
- skill 端 [`AdminSkillReportController`](../../server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/admin/AdminSkillReportController.java) 对 `RESOLVE_AND_HIDE` 有特殊角色门(SKILL_ADMIN 不能 hide,只 SUPER_ADMIN 能 hide,因为 hide 会让作者完全失去对自己 skill 的控制)。**Agent 端在 [`AdminAgentReportController`](../../server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/admin/AdminAgentReportController.java) 没做这个角色门**——`@PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")` 整个端点都允许 SKILL_ADMIN。
- **判断**:本任务是 UI polish,不动后端权限模型。但要在 PR description / commit message 里**明确指出**这个非对称,留待后续 governance plan 决定是否对齐 skill 端的 SUPER_ADMIN-only 限制。

**验证**:`pnpm vitest run web/src/pages/reports.test.tsx` + 浏览器(用 SUPER_ADMIN 帐号在 admin reports 页选 RESOLVE_AND_HIDE,刷新看 agent 是否 hidden)。

---

### F5:执行 drop-agent-tables.sh 重置 schema (~10 分钟,无 commit)

**问题**:[`server/scripts/drop-agent-tables.sh`](../../server/scripts/drop-agent-tables.sh) 已提交(`cecfe33a`)但**未跑过**。当前本地 PG 里的 `agent_*` 表仍是旧 schema(没有 `requested_visibility / bundle_ready / hidden / latest_version_id` 等列),应用启动时 JPA 会用 `update` 模式补列,但 `agent_version.status` 枚举从 5 值扩到 9 值在 PG enum 上**不会自动 ALTER**——必须 drop 重建。

**做什么**:
- 先 `kill` 本地 skillhub-app 进程(避免运行期间 mvn package 触发 LaunchedURLClassLoader 懒加载崩溃,见 [memo/lessons.md 2026-04-27](../../memo/lessons.md) 的 Spring Boot nested jar 教训)
- 跑 `PGPASSWORD=skillhub server/scripts/drop-agent-tables.sh`(确认 PGUSER/PGDATABASE 默认值匹配本地)
- 重启 app,检查日志没有 hibernate enum 兼容错误
- curl 几个端点冒烟:
  - `POST /api/web/agents/{ns}/publish` 发布一个 agent → 状态应该是 UPLOADED(占位扫描通过)
  - `POST /api/web/agents/{ns}/{slug}/submit-review` body={version, targetVisibility:"PUBLIC"} → 状态 PENDING_REVIEW
  - 找 SUPER_ADMIN 测试 hide:`POST /api/v1/admin/agents/{id}/hide` body={reason}
- 这一步**不产生 commit**,只是确认本地环境状态正确,把"已执行"记进 memo

**注意点**:
- 真生产数据库不要跑这个脚本——HANDOVER spec 写过 "目前全部为测试数据可直接删除",**仅限 dev 环境**
- 跑之前 `pg_dump skillhub > /tmp/skillhub-pre-drop.sql` 留个备份,以防万一

---

## 4. 风险提示

1. **F1 + F3 互相耦合**:F1 引入 9 个 `agent.status.*` key 必须 F3 也加齐 zh/en 文案,否则 build 报错。建议同会话连做。
2. **F2 的 agentsApi.list 关键词支持**:写之前先 grep `agentsApi.list.*q\|searchPublic` 确认 useAgents 钩子接受 `q` 参数。如果不接,要先扩 client + hook,F2 工作量翻倍。
3. **F4 的角色门非对称**:UI polish 不动后端,但要在 commit message 提示。后续若决定对齐 skill 端 SUPER_ADMIN-only,要改 `AdminAgentReportController` 的 `resolveReport` 端点权限并加 controller test。
4. **F5 跑 drop 脚本**前必须 stop server。**绝不**在生产 PG 跑。

## 5. Definition of Done

每项独立提交,合并 main 标准:
- F1/F2/F3/F4 各自的测试用例通过
- 整体 `pnpm tsc --noEmit` clean
- 整体 `pnpm vitest run` 不退化(当前 baseline 708/708)
- 后端 baseline 不变(F1-F4 不动后端)
- 浏览器手测对应交互正常(每项的"验证"小节)
- F5 在 memo 里追加一行 "drop-agent-tables.sh 在 dev PG 已执行,新 schema 验证 OK"

全部完成后:
- `git checkout main && git merge --no-ff feat/agent-skill-parity-polish && git push origin main`
- 删 feat 分支
- 把 [`docs/plans/2026-05-01-agent-skill-parity-FOLLOWUPS.md`](2026-05-01-agent-skill-parity-FOLLOWUPS.md) 顶部加 SHIPPED 横幅

## 6. 重要参考文件路径速查

**Skill 端的标准实现(直接对照写)**:
- 状态徽标:`web/src/features/skill/version-status-badge.tsx`
- search tab:对照 skill 在 search 页的 card 渲染分支
- i18n 全集:`web/src/locales/{zh,en}/translation.json` 里 `skill.status.*` / `skills.lifecycle.*`
- 报告 disposition picker:`web/src/pages/reports.tsx` 的 skill panel(已含 RESOLVE_AND_HIDE)

**Agent 端待改文件**:
- `web/src/features/agent/version-status-badge.tsx` (新建,F1)
- `web/src/pages/search.tsx` (F2)
- `web/src/pages/agent-detail.tsx`:550-630 范围 `resolveVersionStatusLabel` 调用 + UPLOADED 按钮文案 (F1+F3)
- `web/src/locales/{zh,en}/translation.json` (F1+F3)
- `web/src/pages/reports.tsx`:agent panel 的 disposition Select (F4)
- `web/src/api/agent-types.ts`:`AgentVersionSummary.status` 9 值 union(已是)
- `server/scripts/drop-agent-tables.sh`(F5,只跑不改)

## 7. 新会话推荐第一句话

```
请读 docs/plans/2026-05-01-agent-skill-parity-FOLLOWUPS.md 第 2 节确认 baseline,
然后从 F1(version-status-badge 组件)开始做。完成一项 commit 一项,
所有项做完合 main。
```

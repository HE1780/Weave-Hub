# 项目进度检查与功能 Gap 分析

**Date:** 2026-04-27
**Owner:** HE1780/Weave-Hub fork（知联 WeaveHub）
**Scope source:** [ADR 0003 — Fork 范围与 upstream 边界](../adr/0003-fork-scope-and-upstream-boundary.md)
**Backlog source:** [docs/plans/2026-04-27-fork-backlog.md](../plans/2026-04-27-fork-backlog.md)
**Baseline 时间戳:** 2026-04-27（main 比 origin/main 领先 54 commits）

---

## 1. Baseline 健康状态（实测）

| 维度 | 结果 | 命令 |
|---|---|---|
| Web 单测 | **641/641 通过**，204 测试文件 | `cd web && pnpm vitest run` |
| Web typecheck | **仅 4 个预存在错误**（全在 `src/pages/registry-skill.tsx`，未被路由引用） | `cd web && pnpm tsc --noEmit` |
| Backend 全套 | **BUILD SUCCESS**，`skillhub-app` 473 测试 0 fail / 0 err（reactor 总计 ~1100，与上一会话 460/460 一致） | `cd server && ./mvnw test` |
| 工作目录状态 | **17 个文件未提交修改 + 14 个未追踪文件**（详见 §2） | `git status` |

**结论:** 已交付代码全绿，没有需要紧急修的 baseline 问题。`registry-skill.tsx` 是孤立未使用文件，无影响（router.tsx 已切走）。

---

## 2. 未提交工作审计

`main` 分支当前持有一批"已写完但未提交"的视觉精修，未在 memo / backlog 中登记：

### 2.1 已修改但未提交（17 文件，+504 / -382 行）

| 文件 | 改动类别 | 与 backlog 关系 |
|---|---|---|
| `web/src/app/layout.tsx` (-89 行) | 删除内嵌 footer，改用 `<LandingFooter />`；装饰 orb 颜色对齐绿色 | 是 P0-1b 视觉收尾，但 backlog 标 P0-1b 已 ✅ |
| `web/src/shared/components/landing-footer.tsx` | Footer 外链补全（API References / Cloud Sync / Security 等指向真实 GitHub 链接） | 解决了 memo 2026-04-27 P0-1b "Known gap #5"（外链占位 `#`）|
| `web/src/pages/dashboard/publish-agent.tsx` (+191 / -? 行) | 重写为对齐 `publish.tsx` 的视觉系统（UploadZone / DashboardPageHeader / Card 节奏）+ 接通 `useMyNamespaces` | **新工作，不在任何 plan 内** |
| `web/src/pages/dashboard/publish-agent.test.tsx` (新文件) | 与 publish-agent 重写配套 | 同上 |
| `web/src/pages/my-weave.tsx` (+165 / -? 行) | 单段 list 改为 SKILL / 智能体 Tab 切换 + 卡片样式重做 | **新工作**，对应 ADR §1.2 但未立 plan |
| `web/src/pages/my-weave.test.tsx` (+110 行) | 配套测试 | 同上 |
| `web/src/shared/components/resource-card.tsx` (+47 行) | 卡片留白 / 行高 / line-clamp / 图标 / 类型徽章重构 | 是 memo 2026-04-27 "Landing 首页卡片可读性优化"那段记录的工作 |
| `web/src/shared/components/landing-{hot,recent,workspace}-section.tsx` | 配合新 ResourceCard 的字段映射调整 | 同上 |
| `web/src/pages/landing.tsx` / `landing.test.tsx` | 微调 | 同上 |
| `web/src/app/layout.test.ts` / `landing-footer.test.tsx` | 测试同步 | 同上 |
| `docs/plans/2026-04-27-fork-backlog.md` | 内容微调 | 文档维护 |
| `docs/plans/2026-04-27-weavehub-landing-ia.md` (+1 行) | 文档微调 | 文档维护 |
| `memo/memo.md` (+34 行) | 已记录"Landing 首页卡片可读性优化"和"卡片对齐感"两段 | 文档维护 |

### 2.2 未追踪（14 项）

| 路径 | 性质 | 处理建议 |
|---|---|---|
| `.opencode/` / `.trae/` | 第三方编辑器/agent 配置目录 | **应加入 `.gitignore`**，不进 commit |
| `docs/phase1-week1-summary.md` | 旧 Phase 总结 | 与 `docs/todo.md`（Phase 1 Week 1 那份古早 plan）对应；建议归档或删除 |
| `docs/plans/2026-04-26-agents-frontend-mvp.md` 等 4 个 plan 文件 | 历史 plan，全部已落地 | 应 `git add` 进入历史索引（被 ADR 0003 / backlog 引用） |
| `docs/plans/2026-04-26-agents-frontend-mvp-START-PROMPT.md` | start prompt 工件 | 可删（一次性使用） |
| `docs/plans/2026-04-26-comments-feature-requirements.md` | Agent 评论需求基线（被 backlog P1-2 引用） | **应 commit**（backlog 链了） |
| `docs/plans/2026-04-27-agent-list-search.md` | P0-2 plan，已实施完成 | **应 commit**（被 fork-backlog P0-2 ✅ 条目引用） |
| `docs/todo.md` | **过时严重**——内容是"Phase 1 Week 1 进行中"，所有已完成项当时还是 `[ ]` | **建议归档到 `docs/_archive/` 或直接删除**——它与现实严重背离，未来 LLM 读到会得出错误进度判断 |
| `memo/decisions.md` | 待研判 | 检查内容后决定 |
| `web/public/avatar-sample.svg` | 资源 | 可 commit |
| `web/src/pages/registry-skill.tsx` | **孤立未使用文件**——typecheck 报 4 个错，router.tsx 不引用 | **建议删除**（pre-existing 但无主，长期占着 typecheck/lint 噪音） |
| `documentation-page.png` / `footer-links-fixed.png` / `hv-analysis-versions.png` / `prompts-skill-detail.png` / `registry-skill-page.png` | 设计/调试截图 | 不应进 repo（约 MB 级图片污染历史） |
| `source-test/` | 不明 | 检查后决定 |

> **风险点:** 这批改动跨越了"backlog 标 ✅"的边界——未来人按 backlog 判定"P0-1b 已完成"、读 memo "已记录卡片优化"，但实际改动还在工作树。一旦清空工作树（`git stash drop` / `git reset --hard`），这些视觉精修会丢失。

---

## 3. 完成度盘点（按 ADR 0003 三条主线）

### 3.1 §1.1 Agent 管理

| 子项 | 状态 | 证据 |
|---|---|---|
| Agent 包格式（AGENT.md + soul.md + workflow.yaml） | ✅ | `domain/agent/AgentMetadataParser.java` + ADR 0001 |
| Agent 数据库模型 | ✅ | V41 + V42 migration（`agent` / `agent_version` / `agent_review_task`） |
| 发布 / 审核 / 通知 / 版本管理后端 | ✅ | `AgentPublishService` / `AgentReviewService` / `AgentNotificationListenerTest` 等 6 个 controller test |
| 列表 / 详情 / 发布 / 审核 inbox / 详情前端 | ✅ | `agents.tsx` / `agent-detail.tsx`（含 redirect）/ `dashboard/publish-agent.tsx` / `dashboard/agent-reviews.tsx` / `dashboard/agent-review-detail.tsx` |
| My Agents 仪表盘 | ✅ | `dashboard/my-agents.tsx` + `/my-weave` 聚合页 |
| Agent 列表搜索（q + namespace + visibility） | ✅ | P0-2 已落地（commits `93bfff15` / `da7e4c68`） |
| Agent 治理动作（archive / unarchive 后端） | ✅ | `AgentLifecycleService` + `AgentLifecycleController`（P2-2 已合）|
| **Agent 治理动作前端按钮** | ❌ **缺** | 后端可用，前端 My Agents / agent-detail 页**无 archive/unarchive 按钮**——backlog 明确把"前端按钮"列在 P2-2 范围 |
| **Agent 评论** | ❌ **未启动** | 无 agent_comment 表/服务，需求文档 `docs/plans/2026-04-26-comments-feature-requirements.md` 还未提交（在 untracked）|
| **Agent star / rating** | ❌ **未启动** | 无 agent_star / agent_rating 表，无 service。Skill 侧 `SkillStarService` / `SkillRatingService` 全套可 mirror（P2-1 估时 1 天）|
| 发布前安全扫描接入 Agent | ✅（P3-2a） | `AgentPublishService` 已注入 `BasicPrePublishValidator`（commit `5d62a75b`）|
| **扩展 validator 规则链（P3-2b）** | ❌ **未启动** | 单 NoOp/Basic 实装；多 validator chain + 依赖白名单 / workflow.yaml schema 校验未做 |
| **Workflow Executor / Agent 实际运行时** | ❌ **未启动**（P3-1，需独立 ADR） | 找不到任何 `WorkflowExecutor` / `AgentExecution` 类。Agent 包发出后**当前不能运行**——这是 ADR 0003 §1.3 的核心议题 |
| **Agent 后端搜索索引（P3-3）** | ❌ **未启动** | 当前是 ILIKE post-filter，无 `agent_search_document` + GIN 索引 |

### 3.2 §1.2 独立视觉 UI（知联 WeaveHub 美学）

| 子项 | 状态 | 证据 |
|---|---|---|
| Design tokens 切绿色单色阶 + glass-morphism | ✅ | P0-1a 已合（`24b0c2de` / `31cd3540`）|
| 字体切 Inter + JetBrains Mono | ✅ | P0-1a |
| Landing 4 段 IA 重写 | ✅ | P0-1b（commits `9402e3aa` → `f28ee2fe`）|
| 站名 SkillHub → 知联 WeaveHub | ✅ | `ce5074b9` + `f28ee2fe` |
| `/my-weave` 路由 + 页面 | ✅ | `08beaeb5` + `3d4d0e0d` |
| Nav 链表重排 | ✅ | `ce5074b9` |
| 卡片视觉迁移到 glass-card | ✅ | P0-1a 末尾两 commit |
| 卡片可读性优化（line-clamp / 留白 / 图标） | 🟡 **未提交** | 工作树中（见 §2.1） |
| `publish-agent` 视觉对齐 `publish` | 🟡 **未提交** | 工作树中（见 §2.1） |
| `my-weave` 改 Tab 切换 | 🟡 **未提交** | 工作树中（见 §2.1） |
| `LandingFooter` 接管全局 footer + 外链补全 | 🟡 **未提交** | 工作树中（见 §2.1） |
| **登录态 / 未登录 walk-through 浏览器烟雾测试** | ❌ **从未跑** | memo 多处记录 "skipped browser smoke"；**还未真正 visual-confirm 过最新视觉** |

### 3.3 §1.3 完整且安全的运行

| 子项 | 状态 | 证据 |
|---|---|---|
| 发布前安全扫描（Skill） | ✅ | `BasicPrePublishValidator`（密钥 + 占位符）|
| 发布前安全扫描（Agent） | ✅ | P3-2a 已接 |
| Bean validation 接通 | ✅（P2-4） | `spring-boot-starter-validation` 已加（commit `67a64cb8`）|
| 运行时审计（`audit_log`） | ✅ | 全栈在用 |
| 私有 SSO 集成 playbook | ✅ | docs/12 已写 |
| **Agent workflow 沙箱执行** | ❌ | P3-1，未启动 |
| **运行超时 / 资源配额** | ❌ | 与 P3-1 绑定 |
| **Validator 规则链扩展** | ❌ | P3-2b，未启动 |
| **Agent 评论 polymorphism 决策** | ❌ | 待 brainstorm（被 P1-2 阻塞）|

---

## 4. Gap 分析（按优先级）

### 4.1 紧急 / 阻塞类

| # | 问题 | 影响 | 建议处置 |
|---|---|---|---|
| **G1** | **17 个未提交改动 + 4 个 plan 文档未追踪** | 一次 `git reset` 就丢了一批视觉精修 + Agent 发布页重写。memo 有记录但代码不在历史 | **立刻分主题 commit**：(a) Footer 外链 + Layout 切 LandingFooter，(b) ResourceCard / landing-* 微调，(c) my-weave Tab 化，(d) publish-agent 视觉对齐，(e) plan 文档批量入历史 |
| **G2** | `docs/todo.md` 严重过时（"Phase 1 Week 1 进行中"），与现实差几个数量级 | LLM / 新人读到会得出"Agent 还没开始"的错判 | **删除或归档**——backlog 才是 source of truth |
| **G3** | `web/src/pages/registry-skill.tsx` 孤立、typecheck/lint 一直报错 | 噪音掩盖真错误，新写的代码很难辨别"是不是我引入的" | **删除**（router.tsx 不引用）|
| **G4** | 5 张 PNG 截图、`source-test/`、`.opencode/`、`.trae/` 未追踪也没 ignore | 容易被无脑 `git add .` 一并 commit；图片污染历史 | **加 `.gitignore`**：`*.png`（screenshot 类）/ `.opencode/` / `.trae/` / `source-test/` |

### 4.2 功能性 P0/P1 缺口（用户能感知）

| # | 缺口 | 对应 backlog | 估时 |
|---|---|---|---|
| **G5** | Agent archive / unarchive **前端按钮缺**（My Agents 页 + agent-detail 页，权限可见） | P2-2（前端尾巴） | ~0.5 天 |
| **G6** | Agent 评论体系**完全没有**（数据库 / API / UI 全缺），同时 backlog P1-2 还在"待 brainstorm"状态 | P1-2 | brainstorm 0.5 天 + ADR 0.5 天 + plan 0.5 天 + 实施 ~3 天 = **5 天** |
| **G7** | Agent star / rating **完全没有** | P2-1 | ~1 天（mirror Skill 模式）|
| **G8** | **没跑过完整浏览器烟雾测试** —— `/`（最新视觉）、`/my-weave` 的 Tab、`publish/agent` 的新视觉、登录态切换、agent-detail 与 review inbox 全链路从未在真浏览器跑通 | 跨多 plan | ~0.5 天，但在 G1 提交完之前不该跑（视觉还在变）|

### 4.3 长期 / 战略缺口（fork 自有路线 §1.3）

| # | 缺口 | 对应 backlog | 风险 |
|---|---|---|---|
| **G9** | **Workflow Executor / Agent 实际运行时**完全空白 | P3-1 | **战略性**：当前已发布的 Agent 包**不能运行**，"知联 WeaveHub" 主张的 Agent 主线产品价值闭环未通；ADR 0003 §1.3 的核心承诺空悬 |
| **G10** | Validator 规则链扩展（依赖白名单 / workflow.yaml schema / 可执行权限） | P3-2b | 安全发布的纵深防御薄；目前只有 Basic（密钥 + 占位符）|
| **G11** | Agent 后端搜索的 ILIKE 撑不住规模 | P3-3 | 数据量小不显，量上去会变慢；同时 P0-2 已知"`Page.totalElements` 反映 raw repo total"是 visibility 后过滤的副作用 |
| **G12** | "Agent 评论"决策点在 brainstorm 之前——**polymorphism 还是新表**没定 | P1-2 前置 | G6 启动前必须先解决，否则 plan 写不出 |

### 4.4 工程治理 / Tech debt

| # | 项目 | 备注 |
|---|---|---|
| **G13** | `main` 分支领先 origin/main **54 commits 未推**；未推的工作如果本机磁盘故障全部丢失 | 推送前确认所有 commit message 与代码内容一致（参考 memo 2026-04-27 P3-2a/P2-2/P2-4 那次"并行 claude 抢 git index"导致 `3d4d0e0d` commit message 与内容不符的事故）|
| **G14** | 后端**没有真正的 `@SpringBootTest` 集成测试**（comments / agent review 生命周期 / archive 路径全是 mocked controller test） | 不阻塞功能，但下次出现"baseline 看起来绿但生产 wiring 坏掉"的概率高（参考 lessons.md 2026-04-27 stale m2 cache 事件）|
| **G15** | "Agent 评论 polymorphism 还是新表"未决 → P1-2 阻塞 | 见 G12 |

---

## 5. 推荐下一步

**P0（立刻做，今天 / 明天）：**

1. **解决 G1**：把工作树拆成 5 个主题 commit，避免丢失。建议顺序：
   - C1: footer 外链补全 + layout 切 LandingFooter（含 layout.test 同步）
   - C2: resource-card / landing-{hot,recent,workspace} 卡片可读性精修
   - C3: my-weave 改 Tab 化
   - C4: publish-agent 视觉对齐 publish + 测试
   - C5: 把 4 个 plan 文档（agents-frontend-mvp / comments-feature-requirements / agent-list-search 等）入历史
2. **G3 / G4**：删 `registry-skill.tsx`、加 `.gitignore`、归档/删 `docs/todo.md`
3. **G2**：归档 `docs/todo.md` 到 `docs/_archive/2026-pre-fork-todo.md` 并加 README 注明"已被 fork-backlog 取代"
4. 提交后运行**完整浏览器烟雾测试（G8）**，覆盖：登录前/后 `/`、`/my-weave` Tab 切换、`/dashboard/publish/agent` 新版、`/agents` 搜索 + 筛选、`/agents/$ns/$slug`、`/dashboard/agent-reviews` 审核详情
5. `git push origin main`（解决 G13），把 54 commits 推到远端

**P1（本周）：**

6. **G5**：Agent archive/unarchive 前端按钮（~0.5 天，无前置依赖）—— 这是 P2-2 唯一缺口
7. **G7**：Agent star / rating 全栈（~1 天，mirror Skill 模式现成）

**P2（下一周或之后，需先决策）：**

8. **G12 → G6**：Agent 评论先做一次 1 小时 brainstorm，决定 polymorphism / 新表，写 ADR 0004，再写 plan，再实施（总 ~5 天）
9. **G10**：P3-2b validator 链扩展，独立 brainstorm 锁 scope

**P3（战略，独立 brainstorm + ADR）：**

10. **G9**：Workflow Executor 子项目——这是 fork 路线 §1.3 最大空白，且体量最大（沙箱 / 超时 / 配额 / 审计 / workflow.yaml schema 收敛），建议**先写 ADR 0005 锁 scope，再决定是否启动**

---

## 6. 总体判断

- **代码层面非常健康**：641 web + 473 backend 测试 0 fail；P0-1a / P0-1b / P0-2 / P2-2（后端）/ P2-4 / P3-2a 全部按期落地；fork 自有路线 §1.1 + §1.2 大部分到位。
- **工程治理有积累的小债**：未提交工作树、过时 todo、孤立文件、未推送 commit、未跑过完整烟雾。**今日 0.5 天就能全部清掉**。
- **战略空白集中在 §1.3**：已发布的 Agent **不能运行**——这是 fork 与 upstream 的最大差异化承诺，但目前 0 进度。`Workflow Executor` 应该在下一阶段进入"独立 ADR + 独立 brainstorm"流程。
- **"Agent 评论"是中期 cluster 的入口**：需要先 brainstorm 决断 polymorphism vs 新表（G12），再开 ADR 0004，再写 plan。这条阻塞 backlog P1-2。

**Check Passed**——baseline 实测、未提交工作清单、ADR / backlog 三方对齐分析完成，缺口可执行、可估时、按 ADR 0003 子条款归位。

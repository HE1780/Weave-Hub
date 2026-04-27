# Fork 自有路线 Backlog (按依赖排序)

**Date:** 2026-04-27
**Status:** Living document — 每次启动新 plan 前先看,启动后把对应条目标 🔄,完成后标 ✅
**Owner:** HE1780/Weave-Hub fork 维护者
**Scope source:** [docs/adr/0003-fork-scope-and-upstream-boundary.md](../adr/0003-fork-scope-and-upstream-boundary.md)
**Visual baseline:** [web/LANDING_PAGE_REDESIGN.md](../../web/LANDING_PAGE_REDESIGN.md)
(知连 WeaveHub 美学 — 浅色 glass-morphism + 绿色单色,2026-04-27 取代旧 Tech Weave 路线)
**Visual design spec:** [docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md](../superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md)
**Reference prototype:** [web/weavehub---知连/](../../web/weavehub---知连/)

## 排序原则

1. **用户能直接感知 > 后端能力**:视觉对齐和列表搜索先于 Agent star/rating
2. **依赖前置**:Agent 评论的 polymorphism 决策依赖一次 brainstorm,不直接进 P0
3. **风险大的留后**:Workflow Executor 涉及沙箱/超时/审计架构,先稳住主线再单独 brainstorm
4. **fork 与 upstream 隔离**:每条 backlog 标注 ADR 0003 子条款 (§1.1 / §1.2 / §1.3),
   不属于 fork 路线的工作直接拒绝纳入

## 当前快照(2026-04-27 22:00 后,Phase E follow-ups 全部收尾后)

测试 baseline:backend **460/460**,web **631/631**,typecheck/lint 仅 registry-skill.tsx 预存在错误。

代码层面已完成的部分(不再是 backlog 项):

- Agent 后端:数据库 / 实体 / 解析器 / 校验器 / 可见性 / publish service / review service / 通知 / public read endpoints / `/detail` 端点已有 controller test
- Agent 前端:列表页 / 详情页 / 发布页 / My Agents / 审核 inbox + 详情 / 8 个 hooks(已统一用 `createWrapper` helper)
- AgentReviewsPage 行展示 agent slug + version(原 P1-1 已完成)
- AgentCard 用 TanStack `<Link>`(支持 cmd-click / 中键 / 右键打开新标签)
- Hero CTA:已拆 dropdown(发布 Skill / 发布 Agent)

视觉路线 2026-04-27 重置(brainstorming A 路线)——以下旧状态**不再适用**:

- 字体三联 Syne / IBM Plex Sans / JetBrains Mono → P0-1a 改为 Inter + JetBrains Mono
- `--brand-start` indigo / `--brand-end` violet brand-gradient → P0-1a 改为绿色单色 brand-50/100/200/500/600/700
- 旧 LandingChannelsSection / PopularAgents / LandingQuickStartSection → P0-1b 删除
- 站名 SkillHub → P0-1b 改为"知连 WeaveHub"

### 2026-04-27 后端 audit 修正(本次更新)

之前的 backlog 描述与代码实际有偏差,本次更新已对齐:

- **P0-2 状态**:从"立即可启动(看似已规划)"改为"未启动" —— controller 当前只有 page/size,
  `AgentService` 没有 ILIKE 方法,整条链路要从零写
- **P2-2 状态**:从"补 service + 4 端点"改为"domain ready, app missing" ——
  `Agent.archive()` / `AgentVersion.archive()` / `AgentVisibilityChecker` 的 ARCHIVED 屏蔽**已实现**,
  实际工作量比原描述小,只缺 `AgentLifecycleService` + `AgentLifecycleController`(可 mirror SkillLifecycleController)
- **P3-2 描述纠正**:"PrePublishValidator 从 NoOp 扩展"双重过时 ——
  `BasicPrePublishValidator` 早已是真实实装(密钥扫描 + 占位符检测),问题是 `AgentPublishService` 完全没接它。
  本项拆为 P3-2a(把 validator 接入 Agent 发布)+ P3-2b(扩 rule 链)
- **新增 P2-4 接通 bean validation**:`pom.xml` 缺 `spring-boot-starter-validation`,
  全代码 ~44 处 `@Valid`/`@NotBlank` 静默失效。当前不阻塞,但是潜伏炸弹

---

## P0 — 立即可启动(无前置依赖)

### ~~P0-1a: WeaveHub design tokens + glass-morphism 体系迁移~~ ✅ 已完成 (2026-04-27)

**ADR 0003 §1.2** · 实际 ~2 小时(subagent-driven) · 纯前端 · token 层
**Plan:** [docs/plans/2026-04-27-weavehub-tokens.md](2026-04-27-weavehub-tokens.md)
**Range:** `30c05d89` → `d4583420` (9 commits)
**Tests:** 631 → 635 passing(+4 from card.test.tsx)
**Memo:** see `memo/memo.md` 2026-04-27 P0-1a session entry for divergences and known gaps

承接 [docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md](../superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md)
§4.1。**只动 design system 层,不动版面**。提交后全站颜色变绿、卡片变玻璃感,但布局不动。

范围:
- 替换 [web/src/index.css](../../web/src/index.css) `--brand-start` / `--brand-end` 为绿色单色阶
  (`--color-brand-50/100/200/500/600/700` + `--color-ink` + `--color-zinc-soft`)
- 加 glass-morphism 工具类:`.glass-header` / `.glass-card` / `.brand-gradient` / `.btn-primary` /
  `.btn-secondary` / `.nav-chip`
- [web/index.html](../../web/index.html) 字体 link 切到 Inter + JetBrains Mono(去 Syne + IBM Plex)
- [web/tailwind.config.ts](../../web/tailwind.config.ts) brand 色 plugin 配置
- [web/src/features/skill/skill-card.tsx](../../web/src/features/skill/skill-card.tsx) 视觉迁移到 glass-card
- [web/src/features/agent/agent-card.tsx](../../web/src/features/agent/agent-card.tsx) 视觉迁移到 glass-card
- [web/src/shared/ui/card.tsx](../../web/src/shared/ui/card.tsx) 兼容 glass-card override
- [web/src/app/layout.tsx](../../web/src/app/layout.tsx) nav chip 视觉(链表不变,留 P0-1b 重排)
- [web/src/shared/ui/button.tsx](../../web/src/shared/ui/button.tsx) variant 对齐新 token
- 全站 `brand-gradient` 8 个引用回归(layout / button / review-skill-detail-section / landing-channels /
  popular-agents 等)
- `pnpm add motion` 引入 `motion/react`

不在范围:
- Landing 信息架构重写(P0-1b)
- nav 链表重排(P0-1b)
- 站名变更(P0-1b)
- 新增 ResourceCard / 删除 LandingChannelsSection 等版面级组件(P0-1b)

验收:
- `pnpm vitest run` 仍 631/631
- `pnpm typecheck` / `pnpm lint` 仍 clean
- 浏览器烟雾:每个 Card 类组件颜色统一为绿色 hover、卡片有 glass 感、nav 项是 chip 形态
- 所有页面**视觉一致**变绿;不允许 P0-1a 期间出现"半旧半新"的混乱中间态

---

### P0-2: Agent 列表搜索 + 筛选

**ADR 0003 §1.1** · 估时 ~1 天 · 前后端 · 状态:**未启动**

不做完整的搜索文档表(那是 P3),先用 PostgreSQL `ILIKE` 把 list 端点扩参,把"看不到搜索框"
这件事修掉。

代码现状(2026-04-27 audit):
- [AgentController.listPublic](../../server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/AgentController.java) 当前只有 `page`/`size` 两个参数,无 `q`/`namespace`/`visibility`
- [AgentService](../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/service/AgentService.java) 没有 ILIKE 查询方法
- [AgentRepository](../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentRepository.java) 现有 `findByVisibilityAndStatus` 但没有 keyword 变体
- 整条链路要新写

范围:
- 后端:`GET /api/web/agents` 增加 `q` (display_name + description ILIKE)、
  `namespace` (slug 过滤)、`visibility` 三个查询参数,默认仍只返回 `PUBLISHED` 版本
- 后端:`AgentService.searchPublic` 新方法 + `AgentRepository` 加 keyword query method
- 前端:`agents.tsx` 增加搜索 input + namespace 选择器 + visibility 单选
- hook `useAgents` 接受参数,query key 加入参数 hash
- 安全策略不变(仍 `permitAll(GET)`)

不在范围:
- 排序选项(默认 created_at desc 即可)
- 高亮匹配片段
- 后端独立的 `agent_search_document` 表(P3,数据量大才有必要)

验收:
- 后端测试新增 ≥3 个(无参 / 带 q / 带 namespace+visibility)
- 前端搜索框有 debounce ≥300ms
- 测试通过

---

### ~~P0-1b: Landing 信息架构重写 + my-weave 路由 + nav 重排 + 站名变更~~ ✅ 已完成 (2026-04-27)

**ADR 0003 §1.2** · 实际 ~3 小时(subagent-driven) · 纯前端 · 版面层 · 依赖 P0-1a 完成
**Plan:** [docs/plans/2026-04-27-weavehub-landing-ia.md](2026-04-27-weavehub-landing-ia.md)
**Range:** `9402e3aa` (plan) → `f28ee2fe` (final task) — 14 task commits + 1 cleanup
**Tests:** 635 → 632 passing(net -3 from heavy delete + new tests)
**Memo:** see `memo/memo.md` 2026-04-27 P0-1b session entry for divergences and known gaps

承接 [docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md](../superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md)
§4.2。token 层稳定后,把 landing 信息架构按 weavehub 4 段重写。

范围:
- 整体重写 [web/src/pages/landing.tsx](../../web/src/pages/landing.tsx) 为 4 段:
  Hero(主标题"持续进化的 AI 能力" + 副标"让团队的技能包和智能体在一起协作" + 搜索 + 开始探索按钮)
  / 热门推荐(下划线 Tab 全部/技能包/智能体 + 9 卡 3 列)
  / 最新动态 col-span-8(4 紧凑卡 + Load Discovery)+ 工作台 col-span-4(登录态切换)
  / Footer
- 新增组件(均位于 `web/src/shared/components/`):
  `resource-card.tsx`(landing 用,内部 dispatch 到现有 SkillCard / AgentCard 骨架,**不**合并两个组件)
  `resource-tabs.tsx`(下划线样式 section tab)
  `landing-hot-section.tsx` / `landing-recent-section.tsx` / `landing-workspace.tsx`
- 删除组件:
  [landing-channels.tsx](../../web/src/shared/components/landing-channels.tsx) /
  [popular-agents.tsx](../../web/src/shared/components/popular-agents.tsx) /
  [landing-quick-start.tsx](../../web/src/shared/components/landing-quick-start.tsx)
- 新增 `web/src/pages/my-weave.tsx`(双段:我的技能包 + 我的智能体,复用 dashboard/skills 列表逻辑和 my-agents 逻辑)
- [web/src/app/router.tsx](../../web/src/app/router.tsx) 加 `/my-weave` 路由
- [web/src/app/layout.tsx](../../web/src/app/layout.tsx) nav 链表重排:
  未登录 = 首页 / 搜索;
  已登录 = 首页 / 发布 ▾ / 技能 / 智能体 / 我的 Weave / 控制台
- 站名 SkillHub → 知连 WeaveHub(layout logo + index.html title + i18n brand key)
- i18n 加新 keys:`landing.hero.title` / `subtitle` / `landing.hot.*` / `landing.recent.*` /
  `landing.workspace.*` / `nav.myWeave` / `nav.skills`
- i18n 删旧 keys:`landing.stats.*` / `landing.channels.*` / `landing.popularAgents.*` /
  `landing.hero.exploreSkills` / `browseAgents`
- [web/index.html](../../web/index.html) `<title>` 改 "知连 WeaveHub"

不在范围:
- 真正的统一后端搜索(留 P3-3)
- 移动端深度优化(weavehub 已 responsive,保持骨架即可)
- AgentCard / SkillCard 合并(明确不做)

验收:
- 浏览器烟雾:Hero 文案为"持续进化的 AI 能力" + 副标 / 不显示任何 stats 数字 / Tab 切换混排 /
  工作台未登录显示 "立即认证登录"、登录后显示双段列表
- 测试 631 → 应增长(~5-8 个新组件测试 + landing 重写)
- typecheck/lint 仍 clean
- 所有文档引用路径仍可点击通过

---

## P1 — 完成 P0 后启动(单点依赖)

### ~~P1-1: AgentReviewsPage 行展示 agent slug + version~~ ✅ 已完成

由 commit `074c1002` (Phase E follow-ups sweep) 收尾。后端 review list 已 JOIN agent + version
返回 `agentSlug` + `agentVersion`,前端表格列已加。原依赖关系作废。

---

### P1-2: Agent 评论(brainstorm + ADR + plan + 实现)

**ADR 0003 §1.1** · 估时 ~3–4 天 · 全栈

依赖:
- [docs/plans/2026-04-26-comments-feature-requirements.md](2026-04-26-comments-feature-requirements.md) 已有需求基线
- 需要先 brainstorm 决定:**扩展 `skill_version_comment` 为 polymorphic**,还是**新建独立的 `agent_version_comment`**
- ADR 0002(Skill 评论)的成果可以 mirror 但要先决定结构

阶段:
1. brainstorm 解决 [comments-feature-requirements.md §"Open questions"](2026-04-26-comments-feature-requirements.md) 的 12 个问题
2. 写 ADR 0004 (agent-version-comments) 锁定表结构 + 权限
3. 写 plan 文档拆任务
4. 实施(后端 + 前端,模式与 ADR 0002 类似,全栈 ~30 任务)

启动前必须:**P0 全部完成**,因为 Agent 评论会挂在 agent-detail 页,而该页面 P0-1a/P0-1b 会改样式。

---

### ~~P1-3: 粒子 Canvas 动画~~ ❌ 已抛弃

2026-04-27 brainstorming A 路线决策:Tech Weave 美学整体抛弃,粒子 Canvas 不再做。
weavehub 美学使用 `motion/react` 入场动效 + glass-card 悬浮上提替代视觉趣味,P0-1a 已包含 `motion/react` 引入。

---

## P2 — 中期(主线稳定后)

### P2-1: Agent star + rating

**ADR 0003 §1.1** · 估时 ~1 天 · 全栈,模式现成

直接 mirror `skill_star` / `skill_rating`,新建 `agent_star` / `agent_rating` 表 + service + endpoint + UI 按钮。

### P2-2: Agent 治理动作(yank / hide / archive)

**ADR 0003 §1.1** · 估时 ~0.5–1 天 · 全栈 · 状态:**domain ready, app missing**

代码现状(2026-04-27 audit):
- 领域层**已实现**:`AgentStatus.ARCHIVED` / `AgentVersionStatus.ARCHIVED` 枚举,
  [Agent.archive()](../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/Agent.java) 和
  [AgentVersion.archive()](../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentVersion.java) 方法,
  [AgentVisibilityChecker](../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/AgentVisibilityChecker.java) 已在读路径屏蔽 ARCHIVED
- **应用层缺**:`AgentService` / `AgentReviewService` 没有 archive/unarchive 方法,
  [AgentReviewController](../../server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/AgentReviewController.java) 没有 PATCH/POST archive 端点
- 现成参考可 mirror:
  [SkillLifecycleController.archiveSkill](../../server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillLifecycleController.java)
  和 `unarchiveSkill`(POST `/{namespace}/{slug}/archive` + `/unarchive`)

范围(比原描述工作量小):
- 后端:`AgentLifecycleService.archive(agentId, requesterUserId)` + `unarchive`,调用 domain `Agent.archive()`
- 后端:`AgentLifecycleController` POST `/agents/{namespace}/{slug}/archive` + `/unarchive`
- 安全策略:补 `RouteSecurityPolicyRegistry` 的 `authenticated` 入口,namespace ADMIN+ 校验复用现有 `NamespacePermissionChecker`
- 测试:service 测试覆盖权限分支 + controller 测试覆盖 200/403/404
- 前端:My Agents 页 + agent-detail 加 archive/unarchive 按钮(权限可见)

不在范围:
- AgentVersion 级别的单版本 archive(domain 层支持,但 v1 只做 Agent 整体级)
- yank(强制下架,与 archive 等价时不再单独建端点)

### ~~P2-3: 深蓝紫色基调切换~~ ❌ 已抛弃

2026-04-27 brainstorming A 路线决策:weavehub 浅色体系优先,
[DESIGN_NOTES §6](../../web/weavehub---知连/DESIGN_NOTES.md) 明确"不使用深色背景作为主基底"。深色主题不再是路线项。

如未来仍有深色主题需求,**重新启动一次独立 brainstorming**,以浅色 weavehub 为基线扩展深色变体。

---

### P2-4: 接通 bean validation(tech debt)

**ADR 0003 §1.3 边角** · 估时 ~0.5 天 · 后端 · 状态:**潜伏炸弹**

代码现状(2026-04-27 audit):
- [server/pom.xml](../../server/pom.xml) 有 `jakarta.validation-api`,**没有** `hibernate-validator` 也**没有**
  `spring-boot-starter-validation`
- 全代码 ~44 处 `@Valid` / `@NotBlank` / `@Size` 等注解**静默失效**
- 实际防线只有:domain entity 构造函数 throw + DB CHECK 约束
- Skill 评论会话已记录此事(见 `memo/memo.md` 2026-04-26 Skill 评论 backend 节"Known follow-ups #1")

为什么是 P2 不是 P3:
- **不是阻塞当前功能**(domain 守门,功能正常)
- **是阻塞未来功能**:任何后续加新字段假设 bean 校验生效就会 silently broken,
  到时找原因要查很久
- 改动 surface 小:加 starter dep + 给 controller 方法 `@Valid` + 全局 `MethodArgumentNotValidException` 映射

范围:
- `pom.xml` 加 `spring-boot-starter-validation`(自动带 `hibernate-validator`)
- `GlobalExceptionHandler` 加 `@ExceptionHandler(MethodArgumentNotValidException.class)` 返回 400 + field errors
- 给 controller 方法 body 参数补 `@Valid`(grep 找 ~10 处)
- 测试:每个补了 `@Valid` 的端点加一条"非法 body 返回 400"用例,确认校验真的触发了

不在范围:
- 把 domain 守门改成 bean 校验(domain 层守门是 defense-in-depth,保留)
- 复杂跨字段校验(`@AssertTrue` / 自定义 ConstraintValidator)留到 P3

---

## P3 — 长期(独立 brainstorm)

### P3-1: Workflow Executor 子项目

**ADR 0003 §1.1 + §1.3** · 估时未估 · 大型,需独立 ADR

ADR 0003 §1.3 的核心议题——已发布的 Agent **当前不能运行**。涉及决策面:
- 执行环境(JVM 内 vs 容器隔离 vs 远程进程)
- 沙箱边界(网络 / 文件系统 / 子进程)
- 超时 + 资源配额
- 审计(每一步 LLM 调用 / Skill 调用都要进审计日志)
- workflow.yaml 的 schema 收敛(目前 ADR 0001 显式 freeform,executor 落地时要约束)

**启动前必须**单独 brainstorm 写 ADR,**不**直接进 plan。

### P3-2: Agent 包发布前安全扫描

**ADR 0003 §1.3** · 估时 ~0.5 天(接入)+ 未估(扩 rule) · 状态:**Skill 已接,Agent 未接**

代码现状(2026-04-27 audit) —— **原描述"PrePublishValidator 从 NoOp 扩展"是双重过时的**:
- [BasicPrePublishValidator](../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/validation/BasicPrePublishValidator.java)
  **已是 `@Component` 真实实装**(密钥扫描 + 占位符检测),不是 NoOp
- 它**只**被注入到
  [SkillPublishService](../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/service/SkillPublishService.java)
- [AgentPublishService](../../server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/agent/service/AgentPublishService.java)
  **完全不调用任何 validator** —— Agent 包当前在发布前没有任何安全前置校验

本项的真正工作:
1. **(P3-2a 必做)** 把 `BasicPrePublishValidator`(或更通用的 validator interface)注入到 `AgentPublishService`,
   在 `publish()` 提交前执行扫描;失败抛 `DomainBadRequestException` 中止发布。改动很小。
2. **(P3-2b 选做)** 扩 rule 链:把 validator 改成 `List<PrePublishValidator>` 或 chain,加更多规则
   (依赖白名单、workflow.yaml schema 校验、可执行权限检查等)。这部分与 upstream Phase 5 重叠,
   **fork 是否抢先做**取决于安全运行需求紧迫度。

P3-2a 工作量小可以先做,P3-2b 留独立 brainstorm。

### P3-3: Agent 后端搜索集成

**ADR 0003 §1.1** · 估时 ~2 天

P0-2 的 `ILIKE` 端点在 Agent 数量大后会撑不住。届时建 `agent_search_document` 表 +
PostgreSQL Full-Text Index(GIN)+ 触发器维护,mirror `skill_search_document` 模式。

---

## 启动建议

2026-04-27 brainstorming 已完成,产出 design spec
[docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md](../superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md)。

下一步:**P0-1a**(WeaveHub design tokens + glass-morphism 体系迁移)。

启动方式:

1. 用户 review design spec
2. 调用 `superpowers:writing-plans` 把 spec §4.1 拆为可执行 plan,落到 `docs/plans/2026-04-XX-weavehub-tokens.md`
3. 把 backlog 的 P0-1a 标 🔄,实施
4. P0-1a 完成后再启动 P0-1b(spec §4.2),用同样流程

P0-1a 与 P0-1b 共享同一份 design spec,**不**需要再次 brainstorm。

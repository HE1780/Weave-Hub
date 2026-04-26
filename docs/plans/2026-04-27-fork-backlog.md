# Fork 自有路线 Backlog (按依赖排序)

**Date:** 2026-04-27
**Status:** Living document — 每次启动新 plan 前先看,启动后把对应条目标 🔄,完成后标 ✅
**Owner:** HE1780/Weave-Hub fork 维护者
**Scope source:** [docs/adr/0003-fork-scope-and-upstream-boundary.md](../adr/0003-fork-scope-and-upstream-boundary.md)
**Visual baseline:** [web/LANDING_PAGE_REDESIGN.md](../../web/LANDING_PAGE_REDESIGN.md) (Tech Weave 美学)

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
- Tech Weave 字体三联:Syne / IBM Plex Sans / JetBrains Mono 已接入

---

## P0 — 立即可启动(无前置依赖)

### P0-1: 双频道视觉对齐(Tech Weave 第一波落地)

**ADR 0003 §1.2** · 估时 ~1.5 天 · 纯前端

承接 [web/LANDING_PAGE_REDESIGN.md](../../web/LANDING_PAGE_REDESIGN.md) 已经定义但未落地的视觉主张。
本 plan **不**做粒子 Canvas(留 P1),先把"双频道色系 + Hero 双入口 + AgentCard 识别度"做扎实,
让用户能立即感知 Skills/Agents 是平等的两条线。

范围:
- 在 `web/src/index.css` 增 `--channel-skill` (蓝) / `--channel-agent` (紫) tokens
- `LandingChannelsSection` 两张卡片应用各自频道色(目前共用 brand-gradient)
- `AgentCard` 改用 `--channel-agent` 紫色系作为 accent / border / hover 光晕
- `SkillCard` 同步加 `--channel-skill` accent(回归测试 ≥1 现有 Skill 列表展示)
- Hero 区域改造:在现有"标题 + 搜索 + 3 CTA + 4 stats"基础上,把 Channels 块**前置**到 Stats 之前,
  并把两张 Channel 卡视觉权重提升到与搜索框平级(并列 hero 主张)
- nav 中 "Skills" / "Agents" 链接的 active state 用各自频道色
- i18n 不变(已有 keys)

不在范围:
- 粒子 Canvas 动画(P1)
- 深蓝紫色基调切换(slate-950 / indigo-950)— 涉及全站主题切换,留独立 plan
- 第三个 Quick Start Tab(P0-3)
- 统一搜索 Tabs(P0-2)

验收:
- `pnpm vitest run` 仍 627/627
- `pnpm typecheck` / `pnpm lint` 仍 clean
- 浏览器烟雾:landing / agents 列表 / 一个 agent detail / search 页面 视觉无回归

---

### P0-2: Agent 列表搜索 + 筛选

**ADR 0003 §1.1** · 估时 ~1 天 · 前后端

不做完整的搜索文档表(那是 P3),先用 PostgreSQL `ILIKE` 把 list 端点扩参,把"看不到搜索框"
这件事修掉。

范围:
- 后端:`GET /api/web/agents` 增加 `q` (display_name + description ILIKE)、
  `namespace` (slug 过滤)、`visibility` 三个查询参数,默认仍只返回 `PUBLISHED` 版本
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

### P0-3: 双频道视觉对齐第二波(Hero 主张并列 + Quick Start Architect Tab + 统一搜索 Tabs 骨架)

**ADR 0003 §1.2** · 估时 ~1 天 · 纯前端

P0-1 完成后,把 landing 页结构完成最后一步。

范围:
- Hero 区域改成"标题 + 副标题 + 双卡片并列主张(Skills/Agents)+ 统一搜索栏(下含 Tabs)+ Stats"
- 搜索栏增加 Tabs:**全部 / Skills / Agents**,Tab 切换只改前端搜索路由参数;后端搜索仍只打 Skill,
  Agents tab 在搜索结果展示一个占位"Agent 搜索基于关键词,跳转到 /agents?q=..."(复用 P0-2 的端点)
- `LandingQuickStartSection` 增加第三个 tab "Agent Architect",文案从 [web/LANDING_PAGE_REDESIGN.md](../../web/LANDING_PAGE_REDESIGN.md)
  和原 [docs/landing-page-redesign.md](../landing-page-redesign.md) 拼

不在范围:
- 真正的统一后端搜索(需要 federate Skill + Agent 搜索,留 P3)
- Architect tab 的具体落地工作流(只是引导文案)

验收:
- 测试覆盖三个 tab 切换 + Architect tab 渲染
- 浏览器烟雾:Hero 主张视觉权重清晰

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

启动前必须:**P0 全部完成**,因为 Agent 评论会挂在 agent-detail 页,而该页面 P0-1/P0-3 会改样式。

---

### P1-3: 粒子 Canvas 动画(Tech Weave 标志元素)

**ADR 0003 §1.2** · 估时 ~1 天 · 纯前端

[web/LANDING_PAGE_REDESIGN.md](../../web/LANDING_PAGE_REDESIGN.md) 设计的核心视觉 hook。
80 个粒子节点 + 动态连线,放在 Hero 背景层。

依赖:P0-1 + P0-3 完成,Hero 结构稳定后再叠加动画,避免改样式时 Canvas 反复重跑。

范围:
- 新建 `web/src/features/landing/particle-canvas.tsx`(可访问性:`prefers-reduced-motion` 时静态化)
- 粒子色用 cyan-500 + violet-500(频道色对应)
- Hero 区域 z-index 分层:Canvas 在最底层,内容在上层
- 不阻塞 SSR / 首屏(`useEffect` mount 后再启动 RAF)

验收:
- 移动端不渲染粒子(性能)
- `prefers-reduced-motion: reduce` 时退化为静态渐变
- Lighthouse Performance 不劣化超 5 分

---

## P2 — 中期(主线稳定后)

### P2-1: Agent star + rating

**ADR 0003 §1.1** · 估时 ~1 天 · 全栈,模式现成

直接 mirror `skill_star` / `skill_rating`,新建 `agent_star` / `agent_rating` 表 + service + endpoint + UI 按钮。

### P2-2: Agent 治理动作(yank / hide / archive)

**ADR 0003 §1.1** · 估时 ~1 天 · 全栈

`AgentVersionStatus` 已有 `ARCHIVED` 枚举,补 service 方法 + 4 个端点 + admin UI 按钮。
权限模型沿用 namespace ADMIN+ 与 skill 一致。

### P2-3: 深蓝紫色基调切换(Tech Weave 完整版)

**ADR 0003 §1.2** · 估时 ~2 天 · 纯前端,触面广

[web/LANDING_PAGE_REDESIGN.md](../../web/LANDING_PAGE_REDESIGN.md) 期望的 slate-950 / indigo-950 主题。
当前是浅色主题。这是 design token 大换底,影响所有页面,**最后做**避免反复返工。

范围:
- `web/src/index.css` `:root` 默认值改深色,旧浅色作为 `[data-theme="light"]`
- 全站组件视觉回归
- Skills/Agents 频道色在深底上的对比度调整

依赖:P0-1 / P0-3 / P1-3 完成,所有视觉新元素都在浅底上验过,再统一切深底。

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

**ADR 0003 §1.3** · 估时未估 · upstream Phase 5 重叠面

`PrePublishValidator` 从 NoOp 扩展为真实校验链。upstream 也计划做(Phase 5),
**fork 是否抢先做**取决于安全运行需求紧迫度。落地时与 upstream 协调避免双向冲突。

### P3-3: Agent 后端搜索集成

**ADR 0003 §1.1** · 估时 ~2 天

P0-2 的 `ILIKE` 端点在 Agent 数量大后会撑不住。届时建 `agent_search_document` 表 +
PostgreSQL Full-Text Index(GIN)+ 触发器维护,mirror `skill_search_document` 模式。

---

## 启动建议

按当前对话敲定的优先级,**下一个动作是 P0-1**(双频道视觉对齐第一波)。
启动方式:

1. 把这份 backlog 的 P0-1 标 🔄
2. 调用 `superpowers:brainstorming` 把"双卡 Hero 主张"的具体布局/文案/交互细节聊清楚(防止再次 strategy C 退化)
3. 用 `superpowers:writing-plans` 落 plan 到 `docs/plans/2026-04-XX-dual-channel-visual-pass-1.md`
4. 实施

如果 P0-1 不需要 brainstorm(范围已经够清晰),可以跳到 step 3 直接写 plan。

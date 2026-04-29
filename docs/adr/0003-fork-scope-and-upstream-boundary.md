# ADR 0003 — Fork 分支范围与 upstream 边界

**Status:** Accepted (2026-04-27 主体)/ Amended (2026-04-27 §1.1 + §1.3 重锁)
**Date:** 2026-04-27
**Owner:** HE1780/Weave-Hub fork 维护者
**Supersedes:** 无

**Amendment 2026-04-27** (诊断报告 §5 后续讨论):

- §1.1 末行原有 "Workflow Executor / Agent 实际运行时" 被**正式抛弃**;
  Agent 在 fork 内只做 registry(发布、版本、共享、社交对齐),不做执行
- §1.3 标题从 "完整且安全的运行" 改为 "上线发布前安全 + 审计 + SSO +
  功能验证";沙箱 / 超时 / 资源配额 / LLM 调用编排移出 fork 范围
- 新增 fork-backlog "Agent–Skill 能力对齐"集群(comment / star / rating /
  label / tag / report / download / CLI / promotion / 统计 全栈对齐)
**Related:**
- [docs/00-product-direction.md](../00-product-direction.md) — 上游产品定位(Skill Registry 主线)
- [docs/10-delivery-roadmap.md](../10-delivery-roadmap.md) — 上游交付路线
- [docs/adr/0001-agent-package-format.md](0001-agent-package-format.md) — Agent 包格式
- [docs/_archive/2026-04/landing-page-redesign.md](../_archive/2026-04/landing-page-redesign.md) — 双频道愿景文档(已归档)

## 背景

本仓库是 `iflytek/skillhub` 的 fork(`HE1780/Weave-Hub`,UI 称"知联 Weave Hub"),
代码包名仍为 `skillhub`。fork 与 upstream 共享 Skill 主线代码,但本分支需要在
Agent、视觉 UI、运行时安全等维度走出独立路线。

过去若干 plan 文档隐含了这一分工但从未明文写下,导致:

- Agent 主线已超额完成(发布/审核/通知/展示闭环),但 [10-delivery-roadmap.md](../10-delivery-roadmap.md)
  仍把 Agent 排除在一期 MVP 外,排期文档与现实脱节。
- [landing-page-redesign.md](../_archive/2026-04/landing-page-redesign.md) 中的双频道视觉主张(已归档)
  (Skills 蓝/Agents 紫、Hero 双入口、统一搜索 Tabs)在 plan 文档里被反复
  deferred,无人接手。
- 治理/社交/搜索增强等 upstream Phase 5 议题边界不清,容易吸走精力。

本 ADR 一次性把 fork 的范围与 upstream 的承接面固化,作为后续 plan 的取舍依据。

## 决策

### 1. Fork 自有路线(本仓库主动开发)

以下三条主线由 fork 独立设计、实施、维护,不等待 upstream:

#### 1.1 Agent 管理

- Agent 包格式(AGENT.md + soul.md + workflow.yaml),见 ADR 0001
- Agent 数据库模型(`agent` / `agent_version` / `agent_review_task`)
- Agent 发布、审核、通知、版本管理后端
- Agent 列表 / 详情 / 发布 / 审核 inbox / 审核详情前端
- My Agents 仪表盘
- Agent 评论(把 Skill 评论的 polymorphism 扩到 Agent)
- Agent 搜索后端集成(`/agents` keyword 搜索)
- 后续 Agent star / rating / 提升到全局
- **Agent 与 Skill 在 registry 层面的能力对齐(comment / star / rating /
  label / tag / report / download / CLI 拉取 / promotion / 统计)** —— 见
  [docs/plans/2026-04-27-fork-backlog.md](../plans/2026-04-27-fork-backlog.md)
  "Agent–Skill 能力对齐"集群

**明确不在本 fork 范围的(2026-04-27 决策)**:

- ❌ Workflow Executor / Agent 实际运行时 / 沙箱 / LLM 调用 / Skill 调用编排
  —— Agent 在本 registry 中只做"发布、版本管理、团队共享、社交"四件事;
  实际运行交给下游(ClawHub CLI、外部 runner、第三方编排平台)。
  registry 不持有任何执行语义。这一决策推翻了原 ADR 中
  "Workflow Executor 独立子项目"那一行

#### 1.2 独立视觉 UI

本 fork 的 UI 与 upstream 视觉解耦,允许引入只属于本分支的设计语言。

**设计基线: "知连 WeaveHub" 美学**(原始资料: [docs/_archive/2026-04/LANDING_PAGE_REDESIGN.md](../_archive/2026-04/LANDING_PAGE_REDESIGN.md)
和详细设计 [docs/_archive/2026-04/superpowers-specs/2026-04-27-weavehub-visual-overhaul-design.md](../_archive/2026-04/superpowers-specs/2026-04-27-weavehub-visual-overhaul-design.md),均已归档):

- **站名**:知连 WeaveHub(显示侧;代码包名 `skillhub` 不动)
- **视觉风格**:浅色 + glass-morphism(白色透明 + backdrop-blur)+ 双 radial-gradient 微光背景
- **主色**:绿色单色阶 `#34a853` (brand-500) / `#2c8e46` (brand-600) / `#23743a` (brand-700) + 浅色阶 brand-50/100/200
- **字体体系**:正文与标题 Inter / 代码 JetBrains Mono(不用 serif italic)
- **动效语言**:`motion/react` 渐入 + 卡片悬浮上提 + 软阴影
- **资源类型区分**:不分频道色,卡片右上角 type 灰底文字徽章("skill" / "agent")区分
- **信息架构**:浅色 + 功能型入口("热门 / 最新 / 工作台" 三段),不堆宣传数字、不堆概念解释

~~落地状态(2026-04-27 核对):~~
> ✅ **已全部完成 2026-04-27** (P0-1a tokens + P0-1b landing IA),以下 6 项 ❌ 已是历史快照,保留作为决策上下文。绿色 brand 阶 / Inter / glass-morphism / 4 段 IA / "知连 WeaveHub" 站名 / `/my-weave` 路由全部上线。

- ~~❌ 当前 `--brand-start` (#6A6DFF indigo) / `--brand-end` (#B85EFF violet) 与新方案冲突,P0-1a 整体替换~~
- ~~❌ Syne / IBM Plex Sans 字体引用要清理,只保留 Inter + JetBrains Mono~~
- ~~❌ glass-morphism 工具类未实现~~
- ~~❌ Landing 当前结构是九段(Hero+Stats+Channels+Popular+QuickStart+Features+CTA+Latest),要重写为 weavehub 四段~~
- ~~❌ 站名仍是 SkillHub~~
- ~~❌ "我的 Weave"路由(`/my-weave`)未创建~~

~~**待补的视觉/结构性改造**:~~ → ✅ **已全部完成 2026-04-27** (P0-1a + P0-1b)。以下条目保留作为决策上下文,但**不再是待办**:

- ~~design tokens 切换到绿色单色阶 + glass-morphism 工具类~~ ✅
- ~~字体引用切到 Inter + JetBrains Mono~~ ✅
- ~~Landing 信息架构重写为 Hero / 热门 / 最新 / 工作台 4 段~~ ✅
- ~~移除全部宣传数字(stats 块整体删)~~ ✅
- ~~站名 SkillHub → 知连 WeaveHub~~ ✅
- ~~新增 `/my-weave` 路由(双栏)~~ ✅
- ~~nav 项重排~~ ✅
- ~~引入 `motion/react`~~ ✅
- ~~新增 `ResourceCard` + `ResourceTabs`~~ ✅
- ~~AgentCard / SkillCard 视觉迁移到 glass-card,**不合并**为单组件~~ ✅

upstream 若在视觉层做调整,**默认不合并**;只在涉及组件结构或可访问性
回归时按需 cherry-pick。

**抛弃的旧路线**(2026-04-27 决策,A 路线全面取代):

- ❌ Tech Weave 美学(粒子 Canvas、深 slate-950/indigo-950 基调、Syne 字体、霓虹青/紫罗兰)
- ❌ 双频道色系(Skills 蓝 / Agents 紫,`--channel-skill` / `--channel-agent` token)
- ❌ Hero 双入口并列大卡片主张
- ❌ Quick Start "Agent Architect" 第三 Tab
- ❌ 统一搜索三栏 Tabs(landing 不再搁置中央搜索 + 类型 tab)
- ❌ 站点宣传数字(1000+/50K+ 等)

**视觉资产文件位置约束**:
- 全局 design tokens 在 `web/src/index.css` `@theme` 块
- glass-morphism 工具类放在同文件的 `@layer base` 之外,不污染 base 样式
- Landing 拆分组件位于 `web/src/shared/components/landing-*.tsx`

#### 1.3 上线发布前安全 + 审计 + SSO + 功能验证

**作用域(2026-04-27 重新锁定):** 本 fork 不做运行时执行,只做"发布到
共享前的把关 + 已发布资产的可追溯 + 接入企业身份体系 + 所有功能点的
端到端验证"。**任何沙箱、超时、运行时配额、LLM 调用、Skill 调用编排
均不在本 fork 路线之内**(由下游 / ClawHub CLI / 第三方 runner 承担)。

本 fork 主动建设的四件事:

- **发布前安全扫描** —— `PrePublishValidator` 默认实现 `BasicPrePublishValidator`
  (密钥扫描 + 占位符检测)已接入 Skill 与 Agent 发布路径;扩展规则链
  (依赖白名单、workflow.yaml schema 校验、可执行权限检查)在 P3-2b
- **资产审计** —— `audit_log` 表 + `SecurityAuditController` 已就绪,
  覆盖发布、审核、archive/unarchive、namespace 角色变更等关键动作
- **企业 SSO / 权限接入** —— 本地认证 + 私有 SSO playbook(见
  [docs/11](../11-auth-extensibility-and-private-sso.md) /
  [docs/12](../12-private-sso-integration-playbook.md))已就绪
- **所有功能点的端到端测试验证** —— 每条 fork 路线特性合并前必须有:
  (a) 后端单元/集成测试,(b) 前端单元/组件测试,(c) 浏览器烟雾走查;
  对照诊断报告 [docs/diagnosis/](../diagnosis/) 的 baseline 指标
  (web N/N + backend M/M + typecheck clean) 周期性回归

注:这一层与 upstream Phase 5 的"自动安全预检"有重叠面,但 fork 不等待
upstream,按本分支节奏推进。**deliberately 不在 §1.3 内的:** 工作流执行
沙箱、运行时超时、运行时资源配额、LLM token 治理 —— 这些假设"包要被
跑起来"才存在,本 fork 把"跑起来"挪出范围。

### 2. 跟随 upstream(关注更新,不主动开发)

以下议题留在 upstream 推进,fork 仅在必要时同步合并 / 兼容:

#### 2.1 Upstream Phase 5 治理 / 社交闭环

- 举报 / 标记机制(用户举报 → 管理员处理 → 隐藏 / yank)
- Webhook / 事件通知(发布通知、审核结果通知 → 外部系统)
- 后续 OAuth Provider 扩展(GitLab、Google 等)
- 向量搜索第二阶段增强

注:Skill 评论(ADR 0002)已在 fork 完成并贡献了实现;Agent 评论作为
fork 自有路线推进(见 1.1)。

#### 2.2 Upstream 平台基础设施增量

- 本地认证体系(用户名密码注册/登录 + BCrypt + 密码策略)
- 多账号合并流程
- Prometheus 指标暴露
- Docker compose / K8s 一键部署清单
- 开源就绪材料(README / CONTRIBUTING / LICENSE 等)

这些属于 upstream Phase 4 范围,fork 跟随合并即可,不主动重做。

#### 2.3 Upstream Skill 主线后续演进

- Skill 标签管理体系演进
- Skill 搜索算法调优
- ClawHub CLI 协议兼容层的协议升级
- 审计日志查询 UI / Skill 治理 admin UI(upstream Phase 4)

### 3. 边界灰区的处理规则

当一个变更同时涉及 fork 路线和 upstream 路线时:

- **优先做最小可分离的提交**:Skill 主线的修复独立成 commit,Agent 相关的
  扩展独立成 commit,便于将来反向贡献给 upstream 或合并 upstream 更新。
- **upstream PR 反向贡献**:fork 完成的 Skill 主线增强(如 Skill 评论 ADR 0002),
  在分支稳定后可以发 PR 给 upstream,但不阻塞 fork 自己的迭代。
- **upstream 合并冲突**:Agent / 视觉 UI / 安全运行涉及的文件冲突一律以 fork
  为准;Skill 主线文件冲突按 case 评估。

### 4. 文档同步原则

- [10-delivery-roadmap.md](../10-delivery-roadmap.md) 维持 upstream 视角,**不**为 fork 自有路线添加 Phase 章节,
  避免与 upstream 文档分叉太远。
- fork 自有路线的排期通过 `docs/plans/YYYY-MM-DD-*.md` 跟踪,在本 ADR 索引。
- 当某个 fork 路线特性反向贡献给 upstream 时,再视情况把它写回 `10-delivery-roadmap.md`。

## 后果

### 正面

- 后续 plan 文档有清晰的"是否属于本分支范围"的判定依据。
- Agent / 视觉 UI / 安全运行三条线的精力不会被 upstream Phase 5 议题吸走。
- upstream 的合并工作集中在跟随性更新,降低维护负担。

### 负面 / 风险

- fork 与 upstream 视觉层差异会随时间扩大,合并 upstream 的前端文件成本上升。
  → 缓解:Agent / 视觉相关文件的目录尽量与 Skill 隔离(`features/agent/*`、
  独立 design tokens 文件),减少冲突面。
- "完整且安全的运行"范围较宽,容易膨胀。
  → 缓解:每次启动该方向的工作前,先写一个具体 plan 锁定 scope。
- Agent 评论与 Skill 评论的代码会分两路演进。
  → 缓解:在 Agent 评论的 plan 里显式评估"是否合并 polymorphism",由 plan 决定。

## 当前 fork 自有路线 plan 索引

按时间倒序:

- [2026-04-27-agent-publish-review-pipeline.md](../plans/2026-04-27-agent-publish-review-pipeline.md)
  — Agent 发布/审核闭环(Phase A–E,30 任务,Phase E 收尾中)
- [2026-04-26-agents-frontend-mvp.md](../plans/2026-04-26-agents-frontend-mvp.md)
  — Agent 前端 MVP(已完成,后被 publish-review-pipeline 替换 mock 数据)
- [2026-04-26-landing-page-dual-channel.md](../plans/2026-04-26-landing-page-dual-channel.md)
  — 双频道 landing 重构(已完成 strategy C,视觉对齐部分留待新 plan)

未启动 plan 的优先级与依赖关系统一记录在 fork 自有路线 backlog:

- [docs/plans/2026-04-27-fork-backlog.md](../plans/2026-04-27-fork-backlog.md)
  — 按依赖关系排序的 P0 / P1 / P2 / P3 队列,新 plan 启动前先看这份 backlog

本 ADR 作为新 plan 的范围检查清单:每个新 plan 在开头注明"对应 ADR 0003 的
1.1 / 1.2 / 1.3 哪一条",便于审阅范围正当性。

# ADR 0003 — Fork 分支范围与 upstream 边界

**Status:** Accepted
**Date:** 2026-04-27
**Owner:** HE1780/Weave-Hub fork 维护者
**Supersedes:** 无
**Related:**
- [docs/00-product-direction.md](../00-product-direction.md) — 上游产品定位(Skill Registry 主线)
- [docs/10-delivery-roadmap.md](../10-delivery-roadmap.md) — 上游交付路线
- [docs/adr/0001-agent-package-format.md](0001-agent-package-format.md) — Agent 包格式
- [docs/landing-page-redesign.md](../landing-page-redesign.md) — 双频道愿景文档

## 背景

本仓库是 `iflytek/skillhub` 的 fork(`HE1780/Weave-Hub`,UI 称"知联 Weave Hub"),
代码包名仍为 `skillhub`。fork 与 upstream 共享 Skill 主线代码,但本分支需要在
Agent、视觉 UI、运行时安全等维度走出独立路线。

过去若干 plan 文档隐含了这一分工但从未明文写下,导致:

- Agent 主线已超额完成(发布/审核/通知/展示闭环),但 [10-delivery-roadmap.md](../10-delivery-roadmap.md)
  仍把 Agent 排除在一期 MVP 外,排期文档与现实脱节。
- [landing-page-redesign.md](../landing-page-redesign.md) 中的双频道视觉主张
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
- Workflow Executor / Agent 实际运行时(独立子项目,不绑定 registry)

#### 1.2 独立视觉 UI

本 fork 的 UI 与 upstream 视觉解耦,允许引入只属于本分支的设计语言:

- 双频道色系(Skills 蓝 / Agents 紫)的全局 design tokens
- Hero 双入口主张(Skills/Agents 并列大卡片)
- 第三个 Quick Start Tab "Agent Architect"
- 统一搜索 Tabs(全部 / Skills / Agents 三栏)
- 后续可能的品牌化改造(知联 Weave Hub 视觉资产)

upstream 若在视觉层做调整,**默认不合并**;只在涉及组件结构或可访问性
回归时按需 cherry-pick。

#### 1.3 完整且安全的运行

围绕 Agent / Skill 在企业内部分发的真实运行场景,本 fork 主动建设:

- Agent 工作流执行的安全边界(沙箱、超时、资源配额)
- Skill / Agent 包的发布前安全扫描(`PrePublishValidator` 从 NoOp 扩展)
- 运行时审计的端到端可追溯
- 与企业 SSO / 权限系统的接入(私有 SSO 集成已有 playbook,见 docs/12)

注:这一层与 upstream Phase 5 的"自动安全预检"有重叠面,但 fork 不等待
upstream,按本分支节奏推进。

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

未启动但属于 fork 自有路线的待写 plan(占位):

- 双频道视觉对齐 plan(Hero 双入口、Skills 蓝 / Agents 紫色系、统一搜索 Tabs、
  Agent Architect Quick Start Tab)
- Agent 评论 plan(在 [2026-04-26-comments-feature-requirements.md](../plans/2026-04-26-comments-feature-requirements.md)
  基础上展开)
- Agent 搜索集成 plan
- Workflow Executor 子项目 plan(完整且安全的运行)

本 ADR 作为新 plan 的范围检查清单:每个新 plan 在开头注明"对应 ADR 0003 的
1.1 / 1.2 / 1.3 哪一条",便于审阅范围正当性。

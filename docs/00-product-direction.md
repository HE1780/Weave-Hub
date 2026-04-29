# 知连 WeaveHub 产品定位与 MVP 范围

> 代码包名仍为 `skillhub`（`com.iflytek.skillhub`），UI 中文品牌为"知连 WeaveHub"，英文品牌为"WeaveHub"。本文档及代码中"skillhub"指代该平台代号。

## 1. 定位

单实例共享 **智能体（Agent）与技能（Skill）注册中心**（WeaveHub Registry），不是多租户平台。

- 平台只有一个共享注册中心实例，同时承载两类一等公民资产：
  - **Skill**：可复用的技能包（`SKILL.md` + 目录结构）
  - **Agent**：可复用的智能体包（`AGENT.md` + 关联资源）
- 隔离边界是 namespace，不是租户；Skill 与 Agent 共享同一套命名空间体系
- `@global` 是平台级公共空间，由平台管理员管理
- `@team-*` 是协作与治理边界（部门/团队），不是租户边界
- 公共资产（visibility=PUBLIC）匿名可浏览和下载（Skill 与 Agent 同等待遇）

以 ClawHub 为产品蓝本（继承产品模型，不照搬技术实现），以 OpenSkills 借鉴 `SKILL.md` 格式和目录结构约定（不兼容其客户端运行时行为）。Agent 资产模型沿用同样的"frontmatter + markdown + 目录约定"思路，独立演进。

同时，一期必须提供 ClawHub CLI 协议兼容层：服务端需要暴露一组与 ClawHub CLI 兼容的 registry API，使现有 ClawHub CLI 在不修改或仅最小配置修改的前提下可完成 registry 侧查询、解析、下载、发布、校验等核心操作（兼容层一期聚焦 Skill 通道，Agent 通道走 WeaveHub 自有 API）。

### 1.1 身份主键约束（已冻结）

- 用户身份主键全链路统一使用 `string`，不得使用 `int` / `long` / `bigint` 作为平台用户标识的正式契约类型。
- 该约束覆盖认证主体、API 入参/出参、权限判定、审计、资源 owner、creator、updater、reviewer、actor、submittedBy 等全部用户关联字段。
- 原因：平台需要兼容外部 SSO / OAuth / OIDC / SCIM 等身份源，外部 UID 通常是稳定字符串，不应先压缩为本地自增整数再作为系统主契约继续传播。
- 旧版草案中任何"整型用户标识"写法都已失效，当前唯一有效约束是"平台用户标识全链路使用字符串主键"。

### 1.2 资产坐标体系（已冻结）

WeaveHub 内部对 Skill 与 Agent 使用同一套 namespace 坐标模型：`@{namespace_slug}/{asset_slug}`，其中 `asset` 视上下文为 `skill` 或 `agent`。

ClawHub CLI 使用单一 slug 模型，slug 校验规则为 `[a-z0-9]([a-z0-9-]*[a-z0-9])?`，不允许 `/` 出现。

为同时满足两套模型，定义以下双向映射规则（仅 Skill 通道需要，Agent 通道不走 ClawHub CLI 兼容层）：

**映射规则：**

| WeaveHub 坐标 | 兼容层 canonical slug | 说明 |
|---|---|---|
| `@global/my-skill` | `my-skill` | 全局空间省略前缀，直接使用 skill slug |
| `@team-name/my-skill` | `team-name--my-skill` | 团队空间使用 `{namespace_slug}--{skill_slug}` 格式 |

**约束规则：**
- 分隔符为双连字符 `--`
- asset slug（skill/agent）和 namespace slug 均禁止包含 `--`
- slug 格式校验更新为：`[a-z0-9]([a-z0-9-]*[a-z0-9])?`，且不得包含连续两个以上的连字符 `--`
- 兼容层解析 canonical slug 时：包含 `--` 则拆分为 `namespace_slug` + `skill_slug`，不包含则视为 `@global/{slug}`
- 冲突规则：如果 `@global/team-name--my-skill` 与 `@team-name/my-skill` 产生冲突，以 `--` 拆分优先（即优先解析为团队空间技能）。全局空间的 asset slug 禁止包含 `--` 以避免歧义
- 保留字规则：namespace slug 保留词列表同样适用于 canonical slug 的 namespace 部分
- Skill 与 Agent 的 slug 命名空间互相独立：`@global/foo` 既可以是 Skill 也可以是 Agent，二者通过资产类型区分，不冲突

**显示规则：**
- Web 端始终显示完整坐标：`@global/my-skill`、`@team-name/my-skill`、`@global/my-agent`、`@team-name/my-agent`
- ClawHub CLI 兼容层返回 canonical slug：`my-skill`、`team-name--my-skill`（仅 Skill）
- WeaveHub 自有 CLI 支持两种格式输入，内部统一转换为 namespace 坐标

**Well-known 发现：**
- WeaveHub 服务端提供 `/.well-known/clawhub.json`，返回 `{ "apiBase": "/api/v1" }`
- ClawHub CLI 通过此机制自动发现兼容层 API 基地址

## 2. 参考项目取舍

### 2.1 继承 ClawHub 的部分

- Registry 的整体产品边界（Skill 通道）
- 版本、标签、下载的业务模型（Skill / Agent 共享同一套语义）
- 发布后治理机制（报告、标记、隐藏、撤回）
- Web 浏览、详情页、上传发布、管理后台的功能切分
- 公共查询 API 与 CLI API 的双通道设计
- ClawHub CLI 所依赖的 registry API 协议面
- Skill 元数据提取与服务端校验思路
- 审计、收藏、评分、统计、运营标签等扩展位

不直接继承：
- Convex 数据模型与运行时
- 向量检索的一期实现方式

### 2.2 借鉴 OpenSkills 的部分

- `SKILL.md` 格式兼容（frontmatter + markdown body）
- 技能包目录结构约定（`SKILL.md` + `references/` + `scripts/` + `assets/`）
- 四级目录优先级（`.agent/skills` → `~/.agent/skills` → `.claude/skills` → `~/.claude/skills`）
- 目录名作为 lookup key（安装后目录名 = skill slug）
- AGENTS.md `<skill>` 描述块格式兼容
- 目标：WeaveHub CLI 安装的技能可被 OpenSkills/Claude 兼容客户端发现和使用

不直接继承：
- 以 CLI 为中心的产品定位
- "无服务端"的前提

### 2.3 Agent 资产模型（自有路线）

Agent 不是 ClawHub / OpenSkills 的对应物，是 WeaveHub 自有产品方向：

- **包结构**：`AGENT.md`（frontmatter + markdown body）作为主入口，附带配套资源目录
- **校验**：服务端 `AgentPackageValidator` 校验包结构与元数据
- **生命周期**：与 Skill 对齐——`DRAFT → PENDING_REVIEW → PUBLISHED`，支持 `headlineVersion / publishedVersion / ownerPreviewVersion / resolutionMode`
- **治理**：举报、隐藏、撤回、审核流程与 Skill 同构（`AgentReport*`、`AgentReview*` 模块独立但语义对齐）
- **社交**：收藏（`AgentStar`）、评分（`AgentRating`）、版本评论（`AgentVersionComment`）已落地
- **集成出口**：可被 [astron-agent](https://github.com/iflytek/astron-agent) 等智能体框架引用与加载

## 3. 产品原则

- Hub 优先：服务端是核心，CLI 和智能体框架集成是入口能力
- 兼容优先：兼容 `SKILL.md` 及常见目录约定
- CLI 兼容优先：除 WeaveHub 自有 CLI 外，一期明确要求实现 ClawHub CLI 协议兼容层（Skill 通道）
- Skill / Agent 对称：两类资产共享命名空间、版本、标签、治理、社交、审计语义，避免双轨歧义
- 分层优先：搜索、对象存储都必须有可替换边界
- 开放认证：基于标准 OAuth2 协议，一期 GitHub 登录，架构支持后续扩展多 Provider
- 审计优先：企业内部分发平台必须保留发布、下载、删除、授权等审计链路

## 4. 一期 MVP 功能

核心能力（Skill 与 Agent 双通道，能力对称）：
- 资产发布（当前版本采用"提交 → 审核 → 上线"；`SUPER_ADMIN` 保留直发能力）
- 版本管理（semver + 标签）
- 浏览、详情、下载（公共资产匿名可访问）
- 标签管理（`latest` 系统保留只读 + 自定义标签人工维护）
- 包文件校验与元数据抽取（Skill 走 `SKILL.md`，Agent 走 `AGENT.md`）
- 基于 PostgreSQL 全文索引的搜索

命名空间与组织：
- 单一全局命名空间（`@global/{slug}`），由平台管理员管理，不支持多个平台级 namespace
- 团队/部门命名空间（`@team-slug/{slug}`）
- 命名空间成员管理
- 创建 Skill / Agent 时选择归属空间

审核流程：
- 当前版本：普通用户发布后进入审核，审核通过后上线（Skill 与 Agent 各自有独立审核任务表，语义对齐）
- `SUPER_ADMIN` 发布可直达 `PUBLISHED`
- 分级审核：团队空间由团队管理员审核，全局空间由平台管理员审核
- 团队资产提升到全局需平台管理员二次审核
- 平台管理员只负责全局空间审核与提升审核，不介入团队空间审核
- 当前不引入自动审核；`PrePublishValidator` 仅作为未来扩展点保留，默认实现为 `NoOp`
- 撤回审核语义统一为 `PENDING_REVIEW → DRAFT`，不再走删除版本记录
- 资产生命周期管理读模型统一为 `headlineVersion / publishedVersion / ownerPreviewVersion / resolutionMode`
- `hidden` 是独立治理覆盖层，不属于资产容器状态机

认证与权限：
- OAuth2 标准登录（一期 GitHub OAuth）
- CLI 认证采用 OAuth Device Flow，由 Web 授权后签发 CLI 可用凭证
- API Token 保留为平台通用凭证能力，用于自动化、兼容层和后续扩展
- ClawHub CLI 协议兼容层（一期聚焦 Skill 的 search、resolve、download、publish、whoami 等核心接口）
- RBAC 角色权限体系（平台角色：`SUPER_ADMIN` / `SKILL_ADMIN` / `USER_ADMIN` / `AUDITOR` + 命名空间角色）
- 管理后台：用户角色管理、Skill / Agent 发布审核、举报治理

社交功能（Skill / Agent 同构）：
- 收藏（`SkillStar` / `AgentStar`）
- 评分（1-5 分，`SkillRating` / `AgentRating`）
- 版本评论（`SkillVersionComment` / `AgentVersionComment`）
- 举报与治理处置（`SkillReport` / `AgentReport`）

审计：
- 发布、审核、下载、删除、举报处置等关键操作审计（Skill 与 Agent 共用同一审计基础设施）

## 5. 一期明确不做（含后续规划）

- 评论 → Phase 5 上线，含举报机制
- 自动安全扫描 → Phase 5 上线，接入 `PrePublishValidator` 扩展点
- 举报/标记机制 → Phase 5 上线，配合评论和治理闭环
- 向量搜索 → 当前进入第一阶段规划，仅做搜索增强，不引入推荐系统
- 在线编辑器 → 暂不规划
- Webhook/事件通知 → Phase 5（预留扩展点）
- 技能依赖/兼容性声明 → 暂不规划（预留 `parsed_metadata_json` 字段）

### latest 语义说明

这是有意的产品决策，不是继承 ClawHub 的回滚模型：

- `latest` 自动跟随最新已发布版本，只读，不可手动移动
- 回滚/稳定通道管理通过自定义标签实现（如 `stable`、`beta`、`stable-2026q1`）
- ClawHub 的"通过移动 latest 做回滚"能力被替换为"通过自定义标签做通道管理"

## 6. 一期核心约束

- Skill / Agent 包均视为"文本资源包"，不接受二进制大文件
- 主入口文件：Skill = `SKILL.md`；Agent = `AGENT.md`
- 元数据以包内 frontmatter 为主，数据库持久化解析结果
- 文件内容原文存对象存储，检索面向数据库中的派生字段与可索引文本
- Web 认证、CLI Device Flow 与 API Token 凭证统一汇聚到平台用户体系
- 公共资产（visibility=PUBLIC）匿名可浏览和下载，无需登录

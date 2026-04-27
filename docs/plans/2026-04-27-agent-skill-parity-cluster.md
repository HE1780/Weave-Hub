# Agent–Skill 能力对齐 — 收尾集群（A4 / A7 / A8 / A9 / A10）

**Date:** 2026-04-27
**ADR:** 0003 §1.1（Agent registry 与 Skill 在能力层面对齐；不引入运行时语义）
**Status:** A4 / A7 / A8 / A10 ✅ 完成（2026-04-27）；A9 ⏸️ 延后需独立 brainstorm
**Owner:** HE1780/Weave-Hub fork
**Backlog source:** [docs/plans/2026-04-27-fork-backlog.md](2026-04-27-fork-backlog.md) "Agent–Skill 能力对齐集群"
**Commit range:** `fb592b03` (A4) → `ef9be96f` (A7) → `1715dec7` (A8) → `8fab7f20` (A10)

## 范围

只处理 backlog 集群中**尚未落地**的 5 项；A0/A1/A2/A3/A5/A6 已合并（V40–V46 + 对应 controller/listener）。每项的实现策略一律：**镜像 Skill 侧已有结构**（mirror approach），不重新设计、不抽 polymorphism。

| 编号 | 主题 | 估时 | 决策点 |
|---|---|---|---|
| A4 | Agent Label（管理员管控全局枚举） | ~1 天 | 共享 `label` 表 vs 隔离 `agent_label` |
| A7 | Agent Download（包下载） | ~1 天 | 包格式（zip vs 目录归档） |
| A8 | CLI 协议拉取 | ~1.5 天 | ClawHubCompatController 协议扩展形式 |
| A9 | Promotion（推荐位） | ~1 天 | mirror PromotionService |
| A10 | 下载/查看统计 | ~0.5 天 | 依赖 A7 |

**依赖链:** A4 独立 → A7 → A8 → A9 → A10。A4 与 A7 可以并行启动；A10 依赖 A7 落地后才有 download 计数源。

---

## A4 — Agent Label

### 决策（开工前必须锁，brainstorm 30 分钟可定）

按 backlog 提示"按 Skill 模式"——**LabelDefinition 共享 vocabulary + agent_label 关联表隔离**。理由：

- 管理员维护一套 label 词汇表，UI 上同一个 "AI 写作" 既能给 Skill 用也能给 Agent 用，避免词汇双轨
- 关联表隔离避免 polymorphism（plan 里 backlog 已经在 A1 评论那条放弃了 polymorphism）
- 兼容 ADR 0003 §3 "最小可分离提交"原则——upstream 合并 LabelDefinition 演进时 fork 的 `agent_label` 关联表零冲突

### 后端

- **DB**：新增 V47 `agent_label`（`agent_id` + `label_id` + `created_at` + UNIQUE(agent_id, label_id) + FK on delete cascade）
- **Domain**：`AgentLabel` 实体 + `AgentLabelRepository`（mirror `SkillLabel` / `SkillLabelRepository`）
- **Domain Service**：`AgentLabelService`（mirror `SkillLabelService` 105 行的结构）
  - `attachLabel(agentId, labelSlug, requesterUserId, namespaceRoles)` — 检查 namespace ADMIN+ + label 存在 + 未重复
  - `detachLabel(agentId, labelSlug, ...)` — 同样权限
  - `listForAgent(agentId)` — public read
- **App Service**：`AgentLabelAppService`（mirror `SkillLabelAppService`）— 解析 `namespace/slug → agentId`，组装 DTO
- **Controller**：`AgentLabelController`（mirror `SkillLabelController` 75 行）
  - `GET /api/web/agents/{namespace}/{slug}/labels` — 公开（visibility 决定是否 404）
  - `POST /api/web/agents/{namespace}/{slug}/labels`（body `{ labelSlug }`）— 认证 + namespace ADMIN+
  - `DELETE /api/web/agents/{namespace}/{slug}/labels/{labelSlug}` — 同上
- **Security policy**：`RouteSecurityPolicyRegistry` 加 1 个 `permitAll(GET)` + 2 个 `authenticated`
- **Search 索引**：mirror `LabelSearchSyncListener` —— Agent 上挂 label 后,加到 agent search document 的 keywords。**当前 fork 没有 agent search document 表（P3-3 留待）**，本任务**不**做 search 同步，留 follow-up
- **测试**：service test 覆盖权限分支 + duplicate + missing label；controller test mirror `SkillLabelControllerTest` 形状

### 前端

- `useAgentLabels(namespace, slug)` query hook
- `useAttachAgentLabel` / `useDetachAgentLabel` mutation hooks
- agent-detail 页 label 区块（visible read，mutation 仅对 ADMIN+ 可见，按钮可见性 mirror skill-detail 页同位置）
- list 卡片继续不显示 label（与 SkillCard 一致）
- 测试：3 hook test + 1 detail page label-section test

### 验收

- backend：service + controller test 全部 pass，全套 `./mvnw test` 不回归
- web：vitest 全套不回归（pnpm vitest run），新增 ~6 个 test
- 浏览器烟雾：管理员账号在 agent-detail 页能 attach/detach label，匿名用户能看到 label 列表
- typecheck/lint clean

### 不在范围

- agent search document 与 label 同步（依赖未存在的 agent_search_document 表）
- label 的 i18n 已由 `LabelLocalizationService` 处理，跨资源类型零变化

---

## A7 — Agent Download（包下载）

### 决策

**包格式：tar.gz 目录归档**。理由：

- skill 现状是单个 `.skill` 文件直接走 `SkillDownloadService.DownloadResult`（`InputStreamResource` + content-type），但 Agent 包是多文件目录（AGENT.md + soul.md + workflow.yaml + 可能子目录），**不能**直接复用 skill 的单文件下载语义
- ADR 0001 已锁 Agent 包是目录结构。下载侧把目录打包为 tar.gz 流式输出，避免落盘临时文件
- tar.gz 比 zip 在跨平台 CLI 端更原生（A8 ClawHub CLI 协议扩展会直接 untar）；UI 端浏览器下载 .tar.gz 也无问题
- 如果未来要支持 .zip 也很简单——`AgentDownloadService` 多一个 format 参数，不破坏接口

### 后端

- **Domain Service**：`AgentDownloadService`（在 `domain/agent/service/`），mirror `SkillDownloadService` 但适配目录归档
  - 输入：`(agentId, version | tag | latest, requesterUserId, namespaceRoles)`
  - 权限：复用 `AgentVisibilityChecker.canAccess`（ARCHIVED/未审核版本拒绝）
  - 输出：`DownloadResult { fileName, contentType="application/gzip", inputStreamSupplier }`
  - 内部实现：从 storage 读 agent package 目录 → tar.gz piped output stream（`PipedInputStream` + 后台线程写 tar 头/数据，避免内存堆积）
  - 触发 `AgentDownloadedEvent`（mirror `SkillDownloadedEvent`）—— 给 A10 stats 提供事件源
- **Controller**：在 `AgentController` 内加 3 个端点（mirror `SkillController` 326–367 行结构）
  - `GET /api/web/agents/{namespace}/{slug}/download` —— latest 已审核版本
  - `GET /api/web/agents/{namespace}/{slug}/versions/{version}/download` —— 指定版本
  - `GET /api/web/agents/{namespace}/{slug}/tags/{tagName}/download` —— 通过 tag 解析（A5 已落地）
- **Security policy**：3 个 `permitAll(GET)`（visibility 在 service 层决定 200/403/404）
- **Storage**：复用现有 `ObjectStorageService`/`LocalFileStorageService`（agent publish 已用同一套）
- **Rate limiting**：mirror `RateLimitInterceptor` 对匿名下载的限流（`AnonymousDownloadIdentityService` 已存在，复用即可）
- **测试**：
  - service test：覆盖权限拒绝 / 版本不存在 / tar 流可读
  - controller test：覆盖 200 / 404 / 403
  - 集成：H2 + LocalFileStorageService 端到端不必做（mirror skill 侧也是 mock 测）

### 前端

- agent-detail 页加 "下载" 按钮（per-version + latest）
- 下载触发用 `<a href={url} download>` 直接走浏览器（不 fetch + blob，因为 tar.gz 可能很大）
- 列表卡片**不**加下载按钮（mirror SkillCard）
- 测试：button visibility test + href correct test

### 验收

- backend：`./mvnw test` 全套 pass，新增 ~6 个 test
- web：vitest 不回归
- 浏览器烟雾：登录 + visibility OK 的状态下，点下载得到合法 .tar.gz 文件，能 `tar -xzf` 出 AGENT.md
- 关键 follow-up：tar streaming 内存占用 spot check（>10MB 包别出 OOM）—— 不在 plan 验收里，但写入 follow-up

### 不在范围

- ZIP 格式支持（留 future，接口预留 format 参数）
- 下载历史持久化（A10 范围）
- 流量计费 / 配额（ADR 0003 §1.3 deliberately 不在 fork 范围）

---

## A8 — CLI 协议拉取

### 决策

**扩展 ClawHubCompatController 现有路径，加 `type=agent` 查询参数路由分支**。理由：

- 现有 `/api/v1/download/{canonicalSlug}` 和 `/api/v1/resolve/{canonicalSlug}` 是 ClawHub CLI 的协议契约——CLI 已部署到用户环境，**不能改路径**
- 加新路径（如 `/api/v1/agents/{slug}/pull`）等于第二套协议，CLI 端要适配两次
- 用 `?type=agent` 区分：未传 = skill（向后兼容），`type=agent` 走 agent 路径
- ADR 0003 §3 "upstream 合并冲突"原则：协议层文件冲突按 case 评估——这个 `type` 参数 fork 上加，未来 upstream 也加 agent 时再合并，冲突面只有路由分支
- 上游若先动了 ClawHubCompatController，fork 必须 rebase 适配——这条 follow-up 写进 memo

### 后端

- **App Service**：新增 `ClawHubAgentCompatAppService`（mirror `ClawHubCompatAppService`）
  - 复用 `AgentSlugResolutionService`（如不存在则新建，mirror `SkillSlugResolutionService`）
  - 复用 A7 的 `AgentDownloadService`
- **Controller 改动**（在 `ClawHubCompatController` 加 type 参数路由）：
  - `GET /api/v1/search?type=agent&q=...` —— 走 agent 搜索（P0-2 已交付的 search service 直接复用）
  - `GET /api/v1/resolve?type=agent&slug=...` —— agent slug → metadata
  - `GET /api/v1/resolve/{slug}?type=agent` —— 同上路径变体
  - `GET /api/v1/download/{slug}?type=agent[&version=...]` —— 走 A7 `AgentDownloadService`
  - `GET /api/v1/download?type=agent&slug=...` —— query 形式
  - 其余 endpoint（`/skills/*`、`/stars/*`、`/publish`、`/whoami`）**v1 不开放 agent 路径**，留 follow-up
- **Facade**：`ClawHubRegistryFacade` 加 agent 分支
- **Security**：与现有 `/api/v1/*` 一致（API token + scope filter；`ApiTokenScopeService` 加 agent 资源 scope）
- **测试**：
  - 既有 ClawHubCompatControllerTest 不回归
  - 新增 ~6 个 case 覆盖 `type=agent` 路径

### 前端

无前端改动（CLI 用）。

### 验收

- backend：`./mvnw test` 全套 pass
- 手测：用 curl 直接打 `GET /api/v1/download?type=agent&slug=ns/agent-slug -H "Authorization: Bearer <token>"` 得到 tar.gz
- ClawHub CLI 端不改的话**仍然能拉 skill**（向后兼容验证：现有 search/resolve/download 不带 type 走 skill）

### 不在范围

- ClawHub CLI 客户端代码改动（CLI 在另一仓库，本 plan 只供后端契约）
- agent 通过 CLI 发布（POST /api/v1/publish + agent 包格式）—— 留 future，CLI 推送场景目前只覆盖 skill
- agent star/unstar 通过 CLI 协议——同上

---

## A9 — Promotion（推荐位）

### 决策

**mirror PromotionService 加 `targetType` 字段**（值：`SKILL` / `AGENT`），**不**新建 `agent_promotion` 表。理由：

- Promotion 是策展型操作，所有资源类型应共享同一推荐池/审核流（"今天推荐位上有 3 个，2 skill 1 agent"）
- 数据库扩列比新建表迁移成本低，且 PromotionController 端点契约不变
- ADR 0003 §3 灰区原则：这个字段是 fork 主动加的，upstream 合并时如果 upstream 也支持多资源类型，可能会和我们字段命名冲突——follow-up 标注

### 后端

- **DB**：V48 给 `promotion` 表加 `target_type VARCHAR(16) NOT NULL DEFAULT 'SKILL'` + 索引；现有 `skill_id` 列保留并加 nullable，新增 `agent_id BIGINT NULL` + check 约束（恰好一个非空）
- **Domain**：
  - `Promotion` 实体加 `targetType` 枚举（`PromotionTargetType { SKILL, AGENT }`） + `agentId`
  - `PromotionService` 接口扩两个方法：`requestForAgent(agentId, ...)` / `listForAgent(...)`；现有 `requestForSkill` 等保留
  - 内部实现共用相同审核状态机
- **App Service**：`PromotionPortalAppService` 接受 `{ targetType, slug, namespace }`，分支解析
- **Controller**：`PromotionController` 现有 5 个端点保留，新增可选 query/body 字段 `targetType`（默认 SKILL）
- **测试**：
  - 现有 promotion 测试不回归
  - 新增 ~5 个 test：agent promotion 创建 / 审核 / 列表
- **Search index**：`PromotionService` 不接 search 同步（mirror skill 现状）

### 前端

- `usePromotionsForAgent` hook
- agent-detail 页（owner 视角）加 "申请推荐" 按钮（已有同款 skill 侧组件可 mirror）
- LandingHotSection 现状是"取最新发布混排"——**本 plan 暂不改它**，因为 backlog 提到这是"真正策展型推荐位还没接"，但接入 landing 是另一段视觉决策，留 follow-up
- 测试：~3 个 hook + 按钮可见性 test

### 验收

- backend：`./mvnw test` 全套 pass
- web：vitest 不回归
- 浏览器烟雾：agent owner 能申请 promotion，admin 能在 promotion 列表看到 agent 类型
- DB 迁移：V48 在干净库 + 既有库都能跑

### 不在范围

- LandingHotSection 接入推荐位（独立 plan）
- 推荐位排序算法演进（hot score 不动，沿用 skill 现状）

---

## A10 — 下载/查看统计

### 决策

**新增 `agent_version_stats` 表，mirror `skill_version_stats`**。理由：

- skill 的统计散在 service / projection 层，但有 `skill_version_stats` 表（V41 之前已存在，`view_count` + `download_count` 列）作为聚合表
- agent 当前 V41 注释明确写"no download counters"——彻底没数。用同样的事件 + projection 模式接进来，最一致
- 直接挂在 `agent_version` 表上更省一张表，但和 skill 拆表的现状不一致；拆表对未来加 column（如 install_count、unique_downloaders）更友好

### 依赖

- A7 必须先合并：没下载端点就没 `AgentDownloadedEvent`
- view 计数：agent-detail 页打开就 +1（mirror skill）—— 这条不依赖 A7

### 后端

- **DB**：V49 `agent_version_stats`（`agent_version_id` PK + `agent_id` + `view_count` + `download_count` + `created_at`/`updated_at`）+ 在 agent publish 时自动建一行（mirror skill publish）
- **Domain**：`AgentVersionStats` 实体 + `AgentVersionStatsRepository`（mirror `SkillVersionStats` / repo）
- **Listener**：`AgentEngagementProjectionService` 已存在（star/rating），**扩**它 / 或新建 `AgentStatsProjectionService`：
  - 监听 `AgentVersionViewedEvent`（在 `AgentService.getDetail` 触发，mirror skill 侧）
  - 监听 `AgentDownloadedEvent`（A7 触发）
  - increment counter（`UPDATE agent_version_stats SET view_count = view_count + 1 WHERE ...`，db 原子操作避免竞态）
- **Read 路径**：
  - `AgentResponse` / `AgentVersionResponse` 加 `viewCount` / `downloadCount` 字段（mirror skill response）
  - `AgentService.findPublic` / `findOwn` JOIN agent_version_stats 取数
- **测试**：
  - listener test 覆盖事件 → counter+1
  - controller test 验证 response 含计数
  - service test 验证 view 触发事件

### 前端

- AgentCard 列表卡片显示 view/download 数（mirror SkillCard）
- agent-detail 页详情 stats 区域显示数字（mirror skill-detail）
- i18n keys 加 `agent.stats.views` / `downloads` 或共享 skill 已有 keys（共享更省事）
- 测试：~3 个 component test

### 验收

- backend：`./mvnw test` 全套 pass
- web：vitest 不回归
- 浏览器烟雾：访问 agent-detail view_count +1；下载触发 download_count +1（手动验：刷 detail，再下载，再刷，看数字递增）
- 现有数据：V49 给所有现存 agent_version 反填一行 stats（count = 0）

### 不在范围

- unique downloader / IP 去重（rate limit 层做，不进 stats）
- 历史时序统计（按天 view 趋势）—— 后续独立 plan
- view 计数防刷（owner 自己刷不算，bot 不算）—— mirror skill 现状（不防）

---

## 启动顺序

```
A4 ──────────────────────────────────► (独立)
A7 ──► A10
       │
       └─► (并行) A8
                  A9 (独立，但建议在 A8 后做避免 schema 冲突)
```

**推荐合并**：A4 一个 commit，A7 + A10 一组（A10 紧跟 A7），A8 独立 commit，A9 独立 commit。每条最大 2 commit（DB + 代码），便于回滚 / cherry-pick。

## Memo / lessons.md 写入义务

每条 A 项**完成后**：

1. 在 `memo/memo.md` 加一节 `## 2026-04-XX — A{N} 完成`，列出 commit range / test deltas / 已知 follow-up
2. 在 `docs/plans/2026-04-27-fork-backlog.md` 把对应 A{N} 节标 ✅ 并附 commit hash
3. 如果走过弯路，往 `memo/lessons.md` 追加一条新教训（**只追加，不删除**）

## 跨条公共风险

- **m2 stale cache**（lessons.md 2026-04-27 已有）：每条 A 都涉及 domain → app 跨模块，跑测试一律 `./mvnw test` 从 server/ 根，不要 `-pl X test`
- **Spring Boot fat jar 不能在运行时 clean**（lessons.md 2026-04-27 已有）：开发时如服务在跑，先停服再 `mvn clean`
- **bean validation 静默**（memo P3 已知）：本 plan 涉及的所有 `@Valid` `@NotBlank` 在 P2-4 之前都不生效，domain 层守门兜底；测试用例**断言 domain 异常**而非 400
- **并行 git index**（lessons.md 2026-04-27 已有）：用户若同时跑别的 agent，本 plan 的 stage→commit 一定要 `git add ... && git commit ...` 单行
- **包管理器**（lessons.md 2026-04-26 已有）：web 子项目一律 pnpm，不 npm

## 不写在 plan 里的 follow-up（开工时再决策）

- ClawHub CLI 客户端是否同步加 agent 支持（CLI 在另一仓，需要协调）
- LandingHotSection 是否接 promotion 数据源
- agent search document 表与 label 同步（依赖 P3-3 搜索基础设施重做）
- A8 之外的 CLI 端点（publish / star）开放计划

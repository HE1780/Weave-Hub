# Agent 归档按钮 (G5) + Agent 收藏/评分 (G7)

> ✅ **SHIPPED — 已完成 2026-04-27 (2026-04-28 audit 确认全栈到位)。** A0/A2/A3 全部 in `main`(`AgentLifecycleController` + V43 + `AgentStarController` + `AgentRatingController` + 前端集成)。详见 [docs/plans/2026-04-29-spec-status-ledger.md](2026-04-29-spec-status-ledger.md)。

**Date:** 2026-04-27
**ADR clause:** [ADR 0003](../adr/0003-fork-scope-and-upstream-boundary.md) §1.1 Agent management
**Diagnosis source:** [docs/diagnosis/2026-04-27-progress-and-gap-analysis.md](../diagnosis/2026-04-27-progress-and-gap-analysis.md) §4.2 G5 + G7
**指导原则:** **agent 这套 1:1 mirror skill 现有实现**——URL、DTO、UI、i18n、测试覆盖度全部对齐 skill。每偏离一处必须有不可避免的理由（例如 agent 没有版本级归档），并写到 commit message 里。

---

## 1. G5 — Agent 归档/取消归档前端按钮

### 后端现状

已就绪，无需改动：
- `POST /api/{v1,web}/agents/{namespace}/{slug}/archive` — body `{ reason?: string }`
- `POST /api/{v1,web}/agents/{namespace}/{slug}/unarchive` — body 空
- 响应 `AgentLifecycleMutationResponse { agentId, action, status }`
- 权限：agent owner OR namespace ADMIN/OWNER（`AgentLifecycleService` 强制）

### 前端要做

| 文件 | 改动 |
|---|---|
| `web/src/api/client.ts` | 新增 `agentLifecycleApi.archiveAgent(ns, slug, reason?) / unarchiveAgent(ns, slug)`（mirror `skillLifecycleApi`）|
| `web/src/features/agent/use-agent-lifecycle.ts`（新文件） | `useArchiveAgent / useUnarchiveAgent` mutations，invalidate `['agents']` / `['agents', ns, slug]` / `['agents','my']` |
| `web/src/pages/dashboard/my-agents.tsx` | 卡片增加 ACTIVE/ARCHIVED 徽章 + 归档/取消归档按钮（`ownerId === user.userId` 才显示） |
| `web/src/pages/agent-detail.tsx` | 增加"管理"区，包含归档/取消归档按钮（同样的 owner 门控） + ConfirmDialog（mirror skill-detail 中可选 reason 输入） |
| `web/src/i18n/{en,zh}.json` | 新 keys `agents.lifecycle.{archive,unarchive,archiveConfirmTitle,archiveConfirmDescription,unarchiveConfirmTitle,unarchiveConfirmDescription,statusActive,statusArchived,reasonPlaceholder,processing}` |
| 测试 | 2 个 hook 测试 + 2 个页面测试（按钮可见性 / 点击调 mutation） |

### 已知偏离 skill 的点

- **没有版本级归档**：skill 有 archive 单个 version，agent 后端无此能力（v1 backend 决策）。前端只在 agent 主体上加按钮。
- **页面位置不同**：skill-detail 的归档按钮在右侧 sidebar；agent-detail 目前没有 sidebar 结构，按钮放主区"管理"卡片内。

---

## 2. G7 — Agent 收藏 + 评分（全栈）

### 2.1 数据库迁移 V43

```sql
-- V43__agent_social_tables.sql

CREATE TABLE agent_star (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agent(id),
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(agent_id, user_id)
);
CREATE INDEX idx_agent_star_user_id ON agent_star(user_id);
CREATE INDEX idx_agent_star_agent_id ON agent_star(agent_id);

CREATE TABLE agent_rating (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agent(id),
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    score SMALLINT NOT NULL CHECK (score >= 1 AND score <= 5),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(agent_id, user_id)
);
CREATE INDEX idx_agent_rating_agent_id ON agent_rating(agent_id);

ALTER TABLE agent
    ADD COLUMN star_count INT NOT NULL DEFAULT 0,
    ADD COLUMN rating_avg NUMERIC(3,2) NOT NULL DEFAULT 0,
    ADD COLUMN rating_count INT NOT NULL DEFAULT 0;
```

### 2.2 Domain 层（`skillhub-domain/.../domain/agent/social/`）

| 类 | mirror 自 | 关键方法 |
|---|---|---|
| `AgentStar` 实体 | `SkillStar` | 字段 `id / agentId / userId / createdAt`，构造器 `(agentId, userId)` |
| `AgentRating` 实体 | `SkillRating` | 字段 `id / agentId / userId / score / createdAt / updatedAt`，`updateScore(short)` |
| `AgentStarRepository` | `SkillStarRepository` | `save / findByAgentIdAndUserId / delete / countByAgentId` |
| `AgentRatingRepository` | `SkillRatingRepository` | `save / findByAgentIdAndUserId / averageScoreByAgentId / countByAgentId` |
| `AgentStarService` | `SkillStarService` | `star(agentId, userId) / unstar(...) / isStarred(...)` + 发 events |
| `AgentRatingService` | `SkillRatingService` | `rate(agentId, userId, score) / getUserRating(...)` + 发 events |
| `AgentStarredEvent / AgentUnstarredEvent / AgentRatedEvent` | 同 skill 事件 | record |

### 2.3 Infra 层

- `JpaAgentStarRepository extends JpaRepository, AgentStarRepository`
- `JpaAgentRatingRepository extends JpaRepository, AgentRatingRepository`（含 `@Query` 求平均）

### 2.4 App 层

| Controller | 路径 | 方法 |
|---|---|---|
| `AgentStarController` | `/api/{v1,web}/agents/{namespace}/{slug}/star` | `PUT` (star) / `DELETE` (unstar) / `GET` (查询是否 starred，未登录返 false) |
| `AgentRatingController` | `/api/{v1,web}/agents/{namespace}/{slug}/rating` | `PUT` `{score}` / `GET` 返 `{score, rated}`（未登录返 0/false） |

**路径决定 — 不可避免地偏离 skill**：skill 用 `{skillId}` 单段 ID 因为 skill 详情用 `{ns}/{slug}` (2 段)——单段 `{skillId}` 不冲突。但 **agent 详情已经是 `{ns}/{slug}` (2 段)**，如果 agent star 用 `{agentId}/star` (2 段)，会被 Spring pattern matcher 当成 `{ns}/{slug}` 解释——`/agents/123/star` 会匹配 `/api/v1/agents/*/*` 的 GET-detail 路由。所以 agent 必须用 3 段 `{ns}/{slug}/star`，跟 archive 端点一致。Service 层签名仍是 `(agentId, userId)`，controller 做 ns/slug → agentId 解析。

DTO（mirror skill 一一对应）：
- `AgentRatingRequest(Short score)` — 用 `@NotNull @Min(1) @Max(5)`
- `AgentRatingStatusResponse(short score, boolean rated)`

`AgentEngagementProjectionService`（mirror `SkillEngagementProjectionService`）：
- `refreshStarCount(agentId)` — 用 JdbcTemplate 写 `UPDATE agent SET star_count = ? WHERE id = ?`
- `refreshRatingStats(agentId)` — 写 `rating_avg / rating_count`

监听器（mirror skill 两个）：
- `AgentStarEventListener` — `@Async @TransactionalEventListener` 听 starred/unstarred
- `AgentRatingEventListener` — 同上听 rated

`AgentSummaryResponse / AgentDetailResponse` 加三字段：`starCount / ratingAvg / ratingCount`，类型对齐 `SkillSummaryResponse`（Integer / BigDecimal / Integer）。`AgentJpaRepository` 的查询要 SELECT 这些列（如果用 `findAll` / `findById` 默认就有；自定义 `@Query` 要补）。

`RouteSecurityPolicyRegistry` 加 4 条：
- `GET /api/v1/agents/*/star` AUTHENTICATED
- `GET /api/v1/agents/*/rating` AUTHENTICATED
- `GET /api/web/agents/*/star` AUTHENTICATED
- `GET /api/web/agents/*/rating` AUTHENTICATED
（PUT/DELETE 隐式要 auth）

### 2.5 前端

| 文件 | 改动 |
|---|---|
| `web/src/api/client.ts` | 新增 `agentSocialApi.{getStar, addStar, removeStar, getRating, setRating}`，签名对齐 `skillStar*` / `skillRating*` |
| `web/src/features/agent/use-agent-star.ts` | `useAgentStar(agentId) / useToggleAgentStar(agentId)` mirror `useStar / useToggleStar` |
| `web/src/features/agent/use-agent-rating.ts` | `useAgentUserRating / useRateAgent` mirror `useUserRating / useRate` |
| `web/src/features/agent/agent-star-button.tsx` | mirror `<StarButton>`，props `agentId / starCount / onRequireLogin?` |
| `web/src/features/agent/agent-rating-input.tsx` | mirror `<RatingInput>`，props `agentId / onRequireLogin?` |
| `web/src/pages/agent-detail.tsx` | 信息区显示聚合 `starCount + ratingAvg`，主区域挂 `<AgentStarButton>` + `<AgentRatingInput>`，位置参照 skill-detail 中两组件的位置 |
| `web/src/api/agent-types.ts` | `AgentDto` 加 `starCount / ratingAvg / ratingCount` |
| i18n | `agents.social.{star, unstar, rate, ...}` keys × en/zh |
| 测试 | 2 hook 测试 + 2 component 测试 |

**复用还是 fork 决策**：研究显示 `<StarButton>` 直接传 `skillId` 入 `useStar(skillId)`，hook 内部硬编码 query key `['skills', skillId, 'star']`。**fork 一份 agent 版本**，避免对 skill UI 做风险改动。

---

## 3. 验证

每个 commit 完成后：
- 后端：`cd server && ./mvnw test`（必须 root，per lessons.md 第 4 条）
- 前端：`cd web && pnpm vitest run`
- 类型：`cd web && pnpm tsc --noEmit | grep -v registry-skill.tsx | head`

最终验收：
- 后端套件 473 → 期望 +20 左右（service + controller + listener tests）
- 前端套件 641 → 期望 +10 左右（hook + component tests）

---

## 4. Commit 切分

| # | 范围 | 题目 |
|---|---|---|
| C1 | G5 全部前端 | `feat(web): agent archive/unarchive UI buttons (G5 / P2-2 frontend)` |
| C2 | DB migration V43 | `feat(db): agent_star + agent_rating tables and denorm columns (V43)` |
| C3 | Domain 层 | `feat(domain): AgentStar/AgentRating entities, repos, services, events` |
| C4 | Infra 层 | `feat(infra): JPA repositories for agent star and rating` |
| C5 | App / API + listeners + DTO 扩展 | `feat(api): agent star + rating controllers, projection listeners, summary fields` |
| C6 | G7 全部前端 | `feat(web): agent star + rating hooks, components, mounted on agent-detail` |

---

## 5. 风险 & 回滚

- **风险 1**：`ALTER TABLE agent ADD COLUMN ... NOT NULL DEFAULT 0` 在大表上慢。当前 agent 表是新建的，行数小，不是问题。
- **风险 2**：`agentSummaryResponse` 字段扩展可能破坏前端反序列化——前端用 TS interface，新字段加 optional 即可避免。
- **风险 3**：lessons.md 第 5 条警告并行 claude 抢 git index——本会话只有我一个 agent，但仍按"add → commit 同 Bash 调用"方式提交。

如果某个 commit 后测试失败：回滚单个 commit (`git reset --hard HEAD~1`)，每个 commit 都是 atomic 的。

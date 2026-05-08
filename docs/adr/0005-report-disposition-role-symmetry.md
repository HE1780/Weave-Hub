# ADR 0005 — 举报处置权限对称化:RESOLVE_AND_HIDE 仅限 SUPER_ADMIN

**Status:** Accepted
**Date:** 2026-05-08
**Owner:** HE1780/Weave-Hub fork 维护者
**Supersedes:** 无
**Related:**
- [ADR 0003 §1.1](0003-fork-scope-and-upstream-boundary.md) — Agent 治理是 fork 自有路线
- [memo 2026-05-02 F4](../../memo/memo.md) — 该不对称性首次被识别为 known follow-up

## 背景

举报处置(`*ReportDisposition`)枚举提供 3 种结果:`RESOLVE_ONLY`、`RESOLVE_AND_ARCHIVE`、`RESOLVE_AND_HIDE`。其中 `RESOLVE_AND_HIDE` 会把对应 Skill / Agent 的 `hidden` 标志置为 `true`,使其在所有公开列表、搜索、详情页对匿名/普通用户不可见 —— 是"强制下架"语义,影响产品公开面。

实现层目前不对称:

| 端 | 控制器 | RESOLVE_AND_HIDE 校验 |
|---|---|---|
| Skill | `AdminSkillReportController.java:63` | 显式校验 `principal.platformRoles().contains("SUPER_ADMIN")`,否则 403 |
| Agent | `AdminAgentReportController.java` | 仅有方法级 `@PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")`,无 disposition 级二次校验 |

这意味着 SKILL_ADMIN 当前能对 Agent 调 RESOLVE_AND_HIDE,但不能对 Skill 调。这是同一治理动作在不同资产上的语义不一致,违反 fork 一直坚持的"Skill / Agent 治理对齐"原则(见 docs/00 §1)。

## 决策

**两端对齐到 SUPER_ADMIN-only**:对 Agent 端的 RESOLVE_AND_HIDE 增加与 Skill 端相同形式的运行时校验,SKILL_ADMIN 调此 disposition 时返回 403。

### 选型理由

考虑过 3 种对齐方式:

| 方案 | 后果 | 选取 |
|---|---|---|
| A. 两端都收紧到 SUPER_ADMIN-only | Agent 收紧,Skill 不变。fail-closed,默认安全姿势 | ✅ 选取 |
| B. 两端都放开到 SKILL_ADMIN+ | Skill 端权限**回退**(已上线行为变松),且强制下架由二级管理员可触达,违反"敏感操作 high-trust"原则 | ❌ 不选 |
| C. 维持差异 + 文档说明 | 实质是放弃对称性,违反 docs/00 §1 |❌ 不选 |

### 不影响的范围

- `RESOLVE_ONLY` / `RESOLVE_AND_ARCHIVE` 两个 disposition 维持 `SKILL_ADMIN+` 可调,因为这两个不影响公开可见性,只是举报状态机推进
- `dismiss`(驳回)端点维持 `SKILL_ADMIN+`
- 控制器方法级 `@PreAuthorize("hasAnyRole('SKILL_ADMIN', 'SUPER_ADMIN')")` 不变 — disposition 级校验是在内部分支

## 实施

### 代码变更

[server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/admin/AdminAgentReportController.java](../../server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/admin/AdminAgentReportController.java) `resolveReport` 方法在解析出 `disposition` 之后、调用 service 之前,增加:

```java
if (disposition == AgentReportDisposition.RESOLVE_AND_HIDE
        && !principal.platformRoles().contains("SUPER_ADMIN")) {
    throw new ForbiddenException("RESOLVE_AND_HIDE requires SUPER_ADMIN role");
}
```

形式与 `AdminSkillReportController.java:63` 一致。

### 测试变更

[server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/AdminAgentReportControllerTest.java](../../server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/AdminAgentReportControllerTest.java) 增加 1 个测试 case:

- 给定 SKILL_ADMIN 身份 + RESOLVE_AND_HIDE disposition → 期望 403

## 后果

### 正面

- Skill 与 Agent 在所有 disposition 上的权限语义一致,治理对齐
- 强制下架(影响公开面)始终由最高权限完成,fail-closed
- 与 docs/00 §1 "Skill / Agent 对称"原则一致

### 负面 / 风险

- SKILL_ADMIN 失去"对 Agent 强制下架"的能力。**风险评估**:fork 暂未发现此能力的实际生产用例,且 SKILL_ADMIN 仍可用 RESOLVE_AND_ARCHIVE 推进举报状态机,只是不能立即下架公开面 — 紧急下架仍需 SUPER_ADMIN 介入。
- 未来若产品决定下放(例如团队 admin 在团队 namespace 内可以强制下架自己空间的资产),需要起新 ADR 并重新设计权限模型(可能从平台角色扩展到 namespace 角色)。

## 实施记录

- 2026-05-08:ADR 立 + 代码对齐 + 测试覆盖,合入 main(commit 待补)

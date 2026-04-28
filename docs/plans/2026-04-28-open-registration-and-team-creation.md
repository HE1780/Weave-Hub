# 开放注册与 Namespace 创建权限下放方案

**日期**：2026-04-28  
**类型**：产品决策 + 代码变更  
**状态**：待评审

---

## 1. 背景

当前系统已具备完善的三层治理模型，但权限链路存在一个断裂点，导致普通用户注册后无法自主创建团队，整个协作链路在入口处即中断。

本次讨论梳理了认证注册 → 团队管理 → 发布审批的完整链路，确认基础设施均已完成，仅需调整一处权限判定即可打通。

---

## 2. 系统现状分析

### 2.1 认证登录体系（已完成）

项目实现了一个完整的、多入口的认证系统：

| 登录方式 | 后端 | 前端 |
|---|---|---|
| 本地账号密码注册/登录 | `LocalAuthController` + `LocalAuthService`，BCrypt(12)，暴力锁定 | Login/Register 双 Tab 页面 |
| GitHub OAuth | `CustomOAuth2UserService` + `GitHubClaimsExtractor` | OAuth 按钮，`window.location.href` 跳转 |
| GitLab OAuth | `GitLabClaimsExtractor` | 同上 |
| Direct Auth（外部 IDP 桥接） | `DirectAuthProvider` 接口 | 预留 |

**准入策略**（`AccessPolicy`）：四种模式（OPEN / EMAIL_DOMAIN / PROVIDER_ALLOWLIST / SUBJECT_WHITELIST），通过 `skillhub.access-policy.mode` 配置。

**身份绑定**：`IdentityBindingService`，首次 OAuth 登录自动创建 `UserAccount` + `IdentityBinding`，回访登录同步更新展示名/头像/邮箱。

**新用户注册后**：自动获得平台角色 `USER`（`PlatformRoleDefaults`），自动加入 `GLOBAL` namespace 作为 `MEMBER`（`GlobalNamespaceMembershipService`）。

### 2.2 团队（Namespace）管理（已完成）

| 概念 | 实现 |
|---|---|
| 数据实体 | `Namespace`（`namespace` 表），`NamespaceMember`（`namespace_member` 表） |
| 类型 | `GLOBAL`（内置，不可变）、`TEAM`（用户创建） |
| 角色 | `OWNER` > `ADMIN` > `MEMBER` |
| 成员管理 | 管理员搜索已有用户 → 手动添加（`NamespaceMemberService.addMember`） |
| 权限校验 | `NamespaceAccessPolicy`：OWNER 独占归档/恢复，ADMIN+ 可管理成员 |
| 生命周期 | 冻结/解冻/归档/恢复，`NamespaceGovernanceService` 审计 |

**当前缺失**：无邮件邀请、无分享链接加入、无申请审批加入。成员只能由 OWNER/ADMIN 在已注册用户中搜索添加。

### 2.3 发布审批体系（已完成）

两层审批架构，均已实现：

```
发布到 TEAM 空间（非 PRIVATE）
  → 状态变为 PENDING_REVIEW
  → 创建 ReviewTask
  → Team OWNER/ADMIN 审批
  → 通过后 PUBLISHED

发布到 GLOBAL 空间（非 PRIVATE）
  → 状态变为 PENDING_REVIEW
  → 创建 ReviewTask
  → 平台 SKILL_ADMIN/SUPER_ADMIN 审批（ReviewPermissionChecker:70-71）
  → 通过后 PUBLISHED

从 TEAM 推广到 GLOBAL（PromotionRequest）
  → 提交 PromotionRequest
  → 平台 SKILL_ADMIN/SUPER_ADMIN 审批
  → 通过后 materialize 到 GLOBAL
```

**例外**：`SUPER_ADMIN` 发布直接 PUBLISHED，跳过审批；`PRIVATE` 可见性不触发审批。

### 2.4 平台角色体系（已完成）

Flyway V1 预置四个平台角色：

| 角色 | 权限 |
|---|---|
| `SUPER_ADMIN` | 全部权限（程序自动补齐） |
| `SKILL_ADMIN` | 创建 Namespace、全局技能审核、推广审批 |
| `USER_ADMIN` | 用户管理、封禁/解封、角色分配 |
| `AUDITOR` | 审计日志只读 |

**角色分配**：通过 `PUT /api/v1/admin/users/{userId}/role` 接口，由 `USER_ADMIN` 或 `SUPER_ADMIN` 操作。`SUPER_ADMIN` 只能由另一个 `SUPER_ADMIN` 授予（`AdminUserAppService:87-89` 的 guard）。

**生产初始化**：`BootstrapAdminInitializer`，通过 `BOOTSTRAP_ADMIN_ENABLED=true` 环境变量在首次部署时创建初始 `SUPER_ADMIN`。

---

## 3. 问题定位

### 3.1 断裂点

`NamespacePortalCommandAppService.java:169-172`：

```java
private boolean canCreateNamespace(PlatformPrincipal principal) {
    return principal.platformRoles().contains("SKILL_ADMIN")
            || principal.platformRoles().contains("SUPER_ADMIN");
}
```

**普通注册用户平台角色为 `USER`，不满足 `SKILL_ADMIN` 或 `SUPER_ADMIN`，因此无法创建 TEAM Namespace。**

### 3.2 影响链路

```
用户注册 → USER
  ↓  ❌ 无法创建 Team
  ↓     需要 SUPER_ADMIN 手动提权到 SKILL_ADMIN
  ↓     但 SUPER_ADMIN 本身需要 BOOTSTRAP_ADMIN_ENABLED 才能初始化
  ↓
  → 整个团队协作、Team 内审批流程无法自启动
```

### 3.3 结论

除这个权限判定外，整条链路的所有组件均已完成并可用。改动量极小。

---

## 4. 建议方案

### 4.1 核心改动：开放 Namespace 创建权限

**文件**：`server/skillhub-app/src/main/java/com/iflytek/skillhub/service/NamespacePortalCommandAppService.java`

```java
// 修改前
private boolean canCreateNamespace(PlatformPrincipal principal) {
    return principal.platformRoles().contains("SKILL_ADMIN")
            || principal.platformRoles().contains("SUPER_ADMIN");
}

// 修改后
private boolean canCreateNamespace(PlatformPrincipal principal) {
    return true;
}
```

**影响**：所有已登录用户均可创建 TEAM Namespace，创建后自动成为 OWNER。

### 4.2 配套保障（现有机制，无需修改）

改动后，以下现有机制自动生效，无需额外代码：

- **不滥用 GLOBAL 空间**：GLOBAL namespace 是系统内置的（`NamespaceType.GLOBAL`），不通过 `createNamespace` 创建，开放 TEAM 创建不影响 GLOBAL 的边界。
- **发布审批不受影响**：GLOBAL 空间的审批准入仍然由 `ReviewPermissionChecker` 强制要求 `SKILL_ADMIN`/`SUPER_ADMIN`（`ReviewPermissionChecker.java:70-71`），普通用户的 Team OWNER 身份无法审批 GLOBAL 空间的 ReviewTask。
- **推广审批不受影响**：`PromotionRequest` 的目标必须是 GLOBAL namespace（`PromotionService.java:107-109`），审批权在 `SKILL_ADMIN`/`SUPER_ADMIN`。
- **平台角色不受影响**：`SKILL_ADMIN`/`SUPER_ADMIN`/`USER_ADMIN`/`AUDITOR` 的赋权链路不变，仍通过 `/api/v1/admin/users/{userId}/role` 由授权管理员操作。

### 4.3 三层治理模型（改后生效）

```
个人层：注册即 USER → 创建 Team → 成为 OWNER → 管理 PRIVATE 内容
                                               ↘ 审批 Team 内 PUBLISH
                                                   
团队层：OWNER/ADMIN → 审核 Team 内 ReviewTask
                   → 管理 Team 成员（搜索 + 添加）
                   → 提交 PromotionRequest 到 GLOBAL
                   
平台层：SKILL_ADMIN/SUPER_ADMIN → 审核 GLOBAL ReviewTask
                                → 审批 PromotionRequest
                                → 分配平台角色（USER_ADMIN/SUPER_ADMIN）
                                → 管理全局用户
```

### 4.4 生产部署步骤

1. **首次部署**：设置环境变量 `BOOTSTRAP_ADMIN_ENABLED=true`，启动后自动创建 `SUPER_ADMIN`
2. **管理员登录**：使用配置的账号密码登录（默认 `admin` / 可配置密码）
3. **开放注册**：确认 `skillhub.access-policy.mode=OPEN`（默认值）
4. **分配管理角色**：SUPER_ADMIN 通过管理后台给需要的人分配 `SKILL_ADMIN` / `USER_ADMIN` 等平台角色
5. **后续运营**：平台管理员专注 GLOBAL 审批准入和推广审批；Team OWNER 自行管理自己的团队和内容审核

### 4.5 风险与缓解

| 风险 | 缓解 |
|---|---|
| 用户大量创建垃圾 Namespace | 可通过 `NamespaceStatus.FROZEN` / `ARCHIVED` 治理；后续可加速率限制 |
| Team OWNER 审核不负责 | 平台 `SKILL_ADMIN` 持有 GLOBAL 推广审批权作为最终关口；已发布到 GLOBAL 的内容可由平台 `SKILL_ADMIN` 撤回 |
| 废弃 Namespace 堆积 | 现有 `archive` 机制足够，后续可加自动清理策略 |

---

## 5. 变更清单

| 文件 | 改动 | 行数 |
|---|---|---|
| `server/skillhub-app/src/main/java/com/iflytek/skillhub/service/NamespacePortalCommandAppService.java` | `canCreateNamespace()` 改为 `return true` | 1 行 |

**总改动量：1 个文件，1 行。**

---

## 6. 未决事项（后续迭代）

以下能力当前缺失，但与本次改动独立，建议单独评估优先级：

- [ ] **邀请未注册用户**（邮件邀请 / 分享链接加入）
- [ ] **申请加入 Team（需审批）**
- [ ] **批量导入成员**
- [ ] **创建 Namespace 时同时添加成员**
- [ ] **Namespace 创建速率限制**（防止滥用）
- [ ] **Namespace 数量上限**（per-user quota）

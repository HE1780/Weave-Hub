# 知连 WeaveHub 上线准备总览(2026-05-08)

**Status:** Active
**Owner:** HE1780/Weave-Hub fork 维护者
**ADR:** [0003 §1.1 + §1.3](../adr/0003-fork-scope-and-upstream-boundary.md)、[0005](../adr/0005-report-disposition-role-symmetry.md)
**Source 评估:** 2026-05-08 仓库验证(memo/memo.md 待补本日条目)

## 0. 当前一句话状态

| 类别 | 现状 |
|---|---|
| **业务功能** | ✅ MVP 闭环完成,后端 584 测试全过,前端 723 测试全过 |
| **代码完整性** | ✅ Schema V50 + 9 状态机,启动 6.5s,匿名 API 200 |
| **部署产物** | ✅ ghcr.io/he1780/weavehub-* 镜像引用全部到位(D3+D4) |
| **CI/CD** | ✅ publish-images.yml 已对齐,main push + release tag 双触发(D2) |
| **品牌口径** | ✅ README + index.html + i18n + landing 全部"知连 WeaveHub"(D5) |
| **治理一致性** | ✅ ADR 0005 + Skill/Agent RESOLVE_AND_HIDE 对称(D7) |

**距离上线还差**:D6(staging 演练验证)+ D8(secrets 注入预案 + 演练后落地)。

---

## 1. D6 — Staging 部署演练指南

### 前置

- 已在 GHCR 上看到 fork 自有镜像构建产出:`ghcr.io/he1780/weavehub-{server,web,scanner}:edge`
- 一台可联外网的 staging 主机(2C4G+ 推荐)或 k8s 集群(>=3 节点)
- 准备好的 staging 域名(如 `weavehub-staging.your-domain.com`)及 DNS 解析

### 第一次部署:Docker Compose 路径

```bash
# 1. 在 staging 主机上拉仓库
git clone https://github.com/HE1780/Weave-Hub.git
cd Weave-Hub

# 2. 准备 .env(从 deploy/.env.example 起)
cp deploy/.env.example deploy/.env
# 编辑 deploy/.env,至少填:
# - SKILLHUB_PUBLIC_BASE_URL=https://weavehub-staging.your-domain.com
# - DEVICE_AUTH_VERIFICATION_URI=https://weavehub-staging.your-domain.com/cli/auth
# - POSTGRES_PASSWORD=<staging 强密码>
# - SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET=<openssl rand -hex 32>
# - SKILLHUB_STORAGE_S3_*(staging 用 MinIO 或单独 OSS bucket)
# - OAUTH2_GITHUB_CLIENT_*(staging 专用 OAuth App)
# - BOOTSTRAP_ADMIN_PASSWORD=<staging 强密码>

# 3. 起完整栈(含本地 PG/Redis)
docker compose -f compose.release.yml --env-file deploy/.env up -d

# 4. 等 30s 让 Flyway 迁移 + Spring 启动
docker compose -f compose.release.yml logs -f server | grep "Started SkillhubApplication"
```

### 验证清单(浏览器 + 命令行各跑一遍)

| # | 检查项 | 命令 / 操作 | 期望 |
|---|---|---|---|
| 1 | 健康检查 | `curl https://weavehub-staging.your-domain.com/actuator/health` | `{"status":"UP"}` |
| 2 | CLI 兼容自描述 | `curl https://.../.well-known/clawhub.json` | `{"apiBase":"/api/v1"}` |
| 3 | 匿名 Skill 列表 | 浏览器打开首页 | "知连 WeaveHub" 4 段式首页正常 |
| 4 | 匿名 Agent 列表 | 浏览器 → 智能体导航 | 列表页 200,空态文案正常 |
| 5 | 搜索 Tab 切换 | `/search?tab=agents` | URL 持久化、tab 切换流畅 |
| 6 | 登录 → 上传一个 demo Skill | 走 /publish 全流程 | 提交审核 → SUPER_ADMIN 发布 → 列表可见 |
| 7 | 登录 → 上传一个 demo Agent | 走 /publish 全流程,选 Agent 类型 | SCANNING(placeholder)→ UPLOADED → PUBLISHED |
| 8 | 举报治理 SUPER_ADMIN-only | SKILL_ADMIN 调 RESOLVE_AND_HIDE → 期望 403;SUPER_ADMIN 调 → 200 | ADR 0005 行为生效 |
| 9 | CLI 登录 | `clawhub login` 设备流 | `/cli/auth` 页面正常,授权后回 CLI |
| 10 | actuator/prometheus 鉴权 | `curl /actuator/prometheus` 匿名 | 401(只允许 SUPER_ADMIN/AUDITOR) |
| 11 | CSRF 防护 | 浏览器 POST 不带 cookie | 403 |
| 12 | 限流 | 30 秒内打 200 个 `/search` | 后续请求被限流 |

### 第二次部署:Kubernetes 路径(可选,生产推荐)

```bash
cd deploy/k8s
cp base/secret.yaml.example base/secret.yaml   # 填入与 .env 等价的值
kustomize build overlays/with-infra | kubectl apply -f -
# 或外部 PG/Redis:
kustomize build overlays/external | kubectl apply -f -

# 配置 Ingress(如已用 nginx ingress controller):
kubectl apply -f deploy/skillhub-ingress.yaml
```

### 演练完成的判定

- 上面 12 项验证全过
- 跑一次 `pnpm e2e`(若 staging 暴露给 e2e)或人工 smoke 视频归档
- 后端日志无 ERROR(除 Redis 暂时断开等已知重连)
- Agent 安全扫描 placeholder 行为符合预期(SCANNING 后立即 PASS)

---

## 2. D8 — 生产 Secrets 注入预案

### 风险清单(必须用密钥管理,不能进入 git/.env 文件)

| Secret | 用途 | 泄露后果 |
|---|---|---|
| `OAUTH2_GITHUB_CLIENT_SECRET` | GitHub OAuth | 攻击者可冒充本平台拿其他 GitHub 用户授权 |
| `SKILLHUB_STORAGE_S3_SECRET_KEY` | OSS 写入凭证 | 数据泄露/篡改/账单滥用 |
| `BOOTSTRAP_ADMIN_PASSWORD` | 第一次启动建管理员 | 直接获得 SUPER_ADMIN 控制权 |
| `SKILLHUB_DOWNLOAD_ANON_COOKIE_SECRET` | 匿名下载 cookie 签名 | 限流绕过 |
| `SPRING_MAIL_PASSWORD` | SMTP 发件 | 邮件冒发 / 配额耗尽 |
| `SPRING_DATASOURCE_PASSWORD` | PG 连接 | 数据库直接被读写 |
| `REDIS_PASSWORD`(若启用) | Redis 连接 | 限流 / session 状态被读 |

### 注入路径(三选一,按部署形态)

#### 路径 A — Docker Compose + 1Panel / Portainer

- 把 `deploy/.env` 文件 `chmod 600` 仅 root 可读
- 文件不进 git(已在 `.gitignore`)
- 同主机用磁盘加密 + 远程 backup 加密
- **不推荐**:仅适合单机 staging,不适合生产

#### 路径 B — Kubernetes Secret + Sealed Secrets / SOPS

```bash
# 用 sealed-secrets 把明文 secret 加密成 SealedSecret,可入 git
kubeseal --format yaml < base/secret.yaml > base/sealed-secret.yaml
# git add base/sealed-secret.yaml(安全可入仓)
# .gitignore 加上 base/secret.yaml(原始明文不入仓)
```

或用 **SOPS** + age 密钥:

```bash
sops --encrypt --age <PUBLIC_KEY> base/secret.yaml > base/secret.enc.yaml
```

#### 路径 C — HashiCorp Vault / 阿里云 KMS / AWS Secrets Manager

```yaml
# Spring Cloud Vault / spring-cloud-aws 注入
spring:
  config:
    import: vault://secret/weavehub/prod
```

或用 **External Secrets Operator** 把 Vault/KMS 同步成 k8s Secret。

### 上线前 Secrets 检查清单

- [ ] 7 个 secret 全部存在生产密钥管理系统中,**不在仓库 / .env 文件 / docker-compose.yml**
- [ ] 演练环境用 staging 专属密钥,与生产隔离
- [ ] `BOOTSTRAP_ADMIN_PASSWORD` 在第一次启动后立即在 Web 后台改一次,不再依赖该环境变量
- [ ] OAuth App 用生产专属(`weavehub-staging` / `weavehub-prod` 两套 GitHub OAuth App)
- [ ] OSS bucket 区分 `weavehub-staging` / `weavehub-prod`
- [ ] 数据库密码 30 字符以上,只在 PG / app 两侧出现,不外传
- [ ] 准备一份 secret rotation runbook(每季度轮换)

---

## 3. 上线 D-Day 检查表

仅在前面 D1-D8 全部完成后执行:

- [ ] 镜像 tag 锁定:`v0.1.0`(或当前 release tag),不用 `:latest` / `:edge`
- [ ] DB backup 策略到位(WAL + 每日全量,异地)
- [ ] OSS / PG / Redis 三方监控接入(Prometheus + Grafana 已就位)
- [ ] /actuator/prometheus → Prometheus scraper 配置完成
- [ ] DNS + TLS 证书生效(Let's Encrypt 或企业 CA)
- [ ] 灰度方案:先开放给内部 5-10 人,72 小时无 P0 事故后全员开放
- [ ] 回滚预案:`docker compose down && docker tag ...prev:latest ...:current && up -d` 步骤手册已 dry-run 一次

---

## 4. 已知遗留(不阻塞上线,但记录)

| 项 | 性质 | 决策 |
|---|---|---|
| Agent 安全扫描是 placeholder | ADR 1.3 故意 | 上线后接入真实 scanner 时无侵入替换 |
| Agent 翻页 totalElements 反映过滤前 | P0-2 已知 | 等 P3-3 索引化(`agent_search_document`)解决 |
| Agents 列表 Hero 仍是 inline gradient | 视觉细节 | 下次 visual sweep 时切到 design tokens |
| favicon SVG metadata 含 `SkillHubLogo.tsx` 标识 | 浏览器不可见 | 下次品牌 sweep 时换图 |
| `compose.release.yml` ports 解析有预存在 bug | 与本批改动无关 | 单独 plan 修(WEB_PORT/API_PORT 拼接形式问题) |
| CLI Agent 写通道未做 | 文档已明示一期 Skill 通道 | 二期 brainstorm 后再决定 |
| Workflow Executor / 沙箱 | ADR 1.3 独立子项目 | 不在 registry 范围 |

---

## 5. 时间预估

| 阶段 | 工作量 |
|---|---|
| D6 staging 演练(首次) | 1 天 |
| D8 secrets 接入(选定路径 B 或 C) | 1 天 |
| 内部灰度 72h | 3 天等待 + 0.5 天观察 |
| 生产切流 | 0.5 天 |
| **合计** | **~5 个工作日** |

完成后,本 plan 移到 `docs/plans/_archive/` 并把"实施记录"段加最终 commit hash。

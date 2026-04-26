# 知连 WeaveHub 首页设计方案

**Status:** Approved (2026-04-27 brainstorming, A 路线全面取代)
**Detailed spec:** [docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md](superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md)
**Reference prototype:** [web/weavehub---知连/](../web/weavehub---知连/)
**Scope clause:** [ADR 0003](adr/0003-fork-scope-and-upstream-boundary.md) §1.2

> 本文档承担 fork 路线"独立视觉 UI"的产品级愿景说明。详细技术实现见 design spec。
> 旧的"双频道 Skills 蓝 / Agents 紫"和"前端执行版双频道等权"两版方案均于 2026-04-27 抛弃。

## 🎯 设计目标

打造一个**面向团队和企业自有应用的 AI 能力协作平台**首页,核心传达:

- 平台是**持续进化**的 AI 能力枢纽
- 技能包(Skills)与智能体(Agents)在**同一平台上协作**,不刻意把两者拆成对立频道
- 首页是**功能型入口**,优先帮助用户快速完成操作(找资源 → 看详情 → 返回继续筛选)
- **不堆砌宣传数字**,不堆概念解释型文案

## 📐 信息架构

```
┌─────────────────────────────────────────────────────────────┐
│  glass-header                                               │
│  [📐 知连 WeaveHub]   [首页][发布▾][技能][智能体][我的Weave][控制台]   [🔔][👤] │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│            [✨ Redefining Intelligence Connection]          │
│                                                             │
│              持续进化的 AI 能力                             │
│                                                             │
│        让团队的技能包和智能体在一起协作                       │
│                                                             │
│           [🔍 搜索...           ⌘K]   [开始探索]            │
│                                                             │
├─────────────────────────────────────────────────────────────┤
│  ─── Handpicked selection                                   │
│  热门推荐                                                    │
│  [全部] 技能包  智能体             浏览所有 →               │
│                                                             │
│  ┌──────┐ ┌──────┐ ┌──────┐                                │
│  │ skill│ │agent │ │ skill│                                │
│  └──────┘ └──────┘ └──────┘                                │
│  ┌──────┐ ┌──────┐ ┌──────┐                                │
│  │agent │ │ skill│ │agent │                                │
│  └──────┘ └──────┘ └──────┘                                │
│  ...                                                        │
├──────────────────────────────────────┬──────────────────────┤
│  ─── Live Updates                    │  工作台               │
│  全域动态流                           │                       │
│                                      │  [未登录]              │
│  ┌──────┐ ┌──────┐                   │   [大头像]            │
│  │ ··· │ │ ··· │                   │   立即认证登录        │
│  └──────┘ └──────┘                   │                       │
│  ┌──────┐ ┌──────┐                   │  [已登录]              │
│  │ ··· │ │ ··· │                   │   ─ 我的技能包 (3)    │
│  └──────┘ └──────┘                   │   ─ 我的智能体 (3)    │
│  [Load Discovery Stream]             │   [Open Control Panel]│
└──────────────────────────────────────┴──────────────────────┘
   Footer: 品牌 + Documentation + Community + Version
```

## 🎨 设计语言

| 维度 | 决策 |
|---|---|
| 站名 | **知连 WeaveHub**(英文 locale 下显示 "WeaveHub") |
| 主色 | 绿色单色阶 `#34a853` (brand-500) / `#2c8e46` (brand-600) / `#23743a` (brand-700) |
| 浅色阶 | brand-50 (#f4fbf6) / brand-100 (#e7f4ea) / brand-200 (#c9e8d1) |
| 卡片 | glass-morphism(白色透明 + backdrop-blur + rounded-3xl) |
| 字体 | Inter(主)+ JetBrains Mono(代码) |
| 动效 | `motion/react` 渐入 + 卡片悬浮上提 |
| 类型区分 | 仅靠右上角 type 灰底文字徽章("skill" / "agent"),**不分频道色** |

## 📋 内容与信息表达原则

来自参考 prototype 的 [DESIGN_NOTES.md](../web/weavehub---知连/DESIGN_NOTES.md):

- 面向用户表达,不使用研发内部术语
- **不展示宣传型数字**(1000+ 技能包、50K+ 下载量等)
- 避免概念解释型文案堆叠
- 文案优先使用:`技能包` / `智能体` / `我的 Weave` / `发布`
- 浅色体系优先,**不引入深色基调**作为主基底

## 🔑 关键文案

### Hero 区域
- **小绿条徽章**:`Redefining Intelligence Connection`(uppercase 装饰文字)
- **主标题**:`持续进化的 AI 能力`
- **副标题**:`让团队的技能包和智能体在一起协作`
- **搜索框 placeholder**:`搜索技能包或智能体...`
- **CTA 按钮**:`开始探索`

### 核心信息块
- 热门推荐 section header:`Handpicked selection` / `热门推荐`
- 最新动态 section header:`Live Updates` / `全域动态流`
- 工作台未登录:`SYNC REQUIRED` / `Authorize now to synchronize your AI assets across all platforms.` / `立即认证登录`

## 🧭 导航规则

### 未登录(3 项)

- 首页(`/`)
- 搜索(`/search`)
- 登录(右侧 CTA,不在 chip nav 里)

### 已登录(5 项 chip + 1 个发布 dropdown)

- 首页(`/`)
- 发布 ▾(发布技能 → `/dashboard/publish` / 发布智能体 → `/dashboard/publish/agent`)
- 技能(`/skills`)
- 智能体(`/agents`)
- 我的 Weave(`/my-weave` — 新路由)
- 控制台(`/dashboard`)

## 📦 我的 Weave 页

新建 `/my-weave` 路由,展示双段:

- **我的技能包**:复用现有 `/dashboard/skills` 列表逻辑
- **我的智能体**:复用现有 [my-agents.tsx](../web/src/pages/dashboard/my-agents.tsx) 逻辑

旧 `/dashboard/skills` 路由保留向后兼容,但 nav 不再链入。

## 🚫 抛弃的旧愿景(2026-04-27 决策)

以下两版历史方案已抛弃:

**旧 Tech Weave 双频道愿景**:
- ❌ Skills 蓝 / Agents 紫双频道色系拆分
- ❌ Hero 双入口并列大卡片主张
- ❌ Quick Start "Agent Architect" 第三 Tab
- ❌ 统一搜索三栏 Tabs(landing 不再放置中央搜索 + 类型 tab)
- ❌ Tech Weave 粒子 Canvas 动画
- ❌ 深 slate-950 / indigo-950 基调
- ❌ Syne 字体
- ❌ "为 Claude 等 AI Agent 提供..." 类的概念解释型副标

**旧"前端执行版"双频道等权**(本文件 2026-04-27 之前的版本):
- ❌ "双频道等权"作为核心设计原则
- ❌ Skills 偏蓝 / Agents 偏紫的频道语义色
- ❌ Search 页 `Skills | Agents` 类型切换
- ❌ Landing+Search+Agents+Agent 详情+我的 Weave 五段信息架构
- ❌ `首页/发现/智能体/我的 Weave/发布` 顺序的导航
- ❌ `/dashboard/my-weaves` 路径
- ❌ 站点宣传数字(1000+ / 50K+ 等 stats)

**取而代之**:weavehub 浅色 glass-morphism + 绿色单色 + 类型徽章区分 + 首页 4 段架构。

## 📅 落地排期

| Plan | 范围 | 估时 |
|---|---|---|
| **P0-1a** | design tokens + glass-morphism 工具类 + 字体切换 + Card 类组件视觉迁移 | ~1.5 天 |
| **P0-1b** | Landing 信息架构重写 + `/my-weave` 路由 + nav 重排 + 站名变更 | ~1.5 天 |

详细任务拆解由 superpowers:writing-plans skill 在 design spec 通过用户 review 后生成。

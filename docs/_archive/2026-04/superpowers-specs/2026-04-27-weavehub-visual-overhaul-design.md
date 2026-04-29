# WeaveHub 视觉全面取代 — Design Spec

> ✅ **SHIPPED — 实施完成 2026-04-27 (拆为 P0-1a tokens + P0-1b landing IA)。** 详见 [docs/plans/2026-04-29-spec-status-ledger.md](../../plans/2026-04-29-spec-status-ledger.md)。

**Status:** Approved (brainstorming → implementation pending) → **Implemented 2026-04-27**
**Date:** 2026-04-27
**Owner:** HE1780/Weave-Hub fork 维护者
**Reference design:** [web/weavehub---知连/](../../../web/weavehub---知连/) (用户提供的 AI Studio prototype + 修订意见)
**Scope clause:** [ADR 0003](../../adr/0003-fork-scope-and-upstream-boundary.md) §1.2 独立视觉 UI

## 1. 背景与决策

之前 fork 视觉路线写在 ADR 0003 §1.2 / [docs/landing-page-redesign.md](../../landing-page-redesign.md) /
[web/LANDING_PAGE_REDESIGN.md](../../../web/LANDING_PAGE_REDESIGN.md) 的方案是"Tech Weave 美学 + 双频道蓝/紫色系
+ Hero 双卡主张 + 粒子 Canvas + Syne 字体 + 深 slate-950 基调"。

落地状态(2026-04-27):字体三联(Syne / IBM Plex Sans / JetBrains Mono)和 indigo+violet brand-gradient 已落地,
其余几乎都未启动。

用户提供 [web/weavehub---知连/](../../../web/weavehub---知连/) 作为新的视觉基线,与原方案完全不同
(绿色单色 / glass-morphism / 浅色 / 不分频道色 / 不要 stats / "知连 WeaveHub" 命名)。

**决策**:weavehub 路线全面取代原方案。原 Tech Weave / 双频道 / 粒子 / Syne / 深底全部抛弃。

## 2. 设计语言

### 2.1 色系

替换 [web/src/index.css](../../../web/src/index.css) 当前 `--brand-start` (#6A6DFF indigo) / `--brand-end` (#B85EFF violet)
为绿色单色阶:

```css
--color-brand-50:  #f4fbf6
--color-brand-100: #e7f4ea
--color-brand-200: #c9e8d1
--color-brand-500: #34a853   /* 主色 */
--color-brand-600: #2c8e46   /* hover/active */
--color-brand-700: #23743a   /* 强调文字 */
--color-ink:       #1f2937   /* 正文 */
--color-zinc-soft: #fcfdfc   /* 页面底色 */
```

页面背景叠双 radial-gradient(`#f0fdf4` 左上 + `#f0f9ff` 右上),attached fixed,营造浅色微光感。
不引入深色基调。

### 2.2 字体

- 正文 + 标题:**Inter** (300/400/500/600/700) via Google Fonts CDN
- 代码:**JetBrains Mono** (400/500)
- **不用** serif italic(weavehub 原稿在中英混排里会回退到系统 serif,效果不稳)

### 2.3 工具类

从 weavehub `index.css` 移植:

- `.brand-gradient` — 文字渐变 `linear-gradient(135deg, brand-600 0%, brand-500 100%)` + bg-clip-text,用于站名 + 强调
- `.glass-header` — `sticky top-0 z-50 backdrop-blur-xl bg-white/40 border-b border-white/50`
- `.glass-card` — `bg-white/50 backdrop-blur-md border border-white/60 rounded-3xl p-6`,hover 时 `bg-white/80 -translate-y-1` + 加深绿色阴影
- `.btn-primary` — 实心 `bg-brand-600` + rounded-2xl + 软阴影 + `active:scale-95`
- `.btn-secondary` — 透明白底 + 浅边框
- `.nav-chip` — `rounded-full text-sm` 胶囊导航项,active `bg-brand-600/90 text-white`

### 2.4 抛弃的内容

代码层面要明确清理:

- ❌ Syne 字体引用([web/index.html](../../../web/index.html) 移除 link)
- ❌ IBM Plex Sans 引用
- ❌ `--brand-start` / `--brand-end` indigo+violet token([web/src/index.css](../../../web/src/index.css))
- ❌ `--channel-skill` / `--channel-agent` 双频道 token(原 backlog 占位,从未实现)
- ❌ Tech Weave 粒子 Canvas(从未实现,直接放弃)
- ❌ 深 slate-950 / indigo-950 基调(从未实现,文档抹除提及)
- ❌ 站点宣传数字(landing.tsx 当前 4-stats 块整体删)

## 3. 信息架构

### 3.1 Landing 页结构

替换当前 [web/src/pages/landing.tsx](../../../web/src/pages/landing.tsx) 的"Hero 搜索 + 3 CTA + 4 Stats + Channels +
PopularAgents + QuickStart + Features + CTA + Latest"九段结构,改为四段:

```
glass-header
├ 站名 知连 WeaveHub (brand-gradient + Layout 图标)
├ 中部 chip nav
└ 右侧:语言切换 + 通知 + 用户菜单

Hero (max-w-4xl 居中)
├ 小绿条徽章(Sparkles + uppercase)
├ 主标题: 持续进化的 AI 能力 (Inter font-black tracking-tight)
├ 副标题: 让团队的技能包和智能体在一起协作 (Inter 正常字重)
└ 搜索框 (⌘K hint) + [开始探索] 按钮
   ※ 不再有 stats 数字

热门推荐 (max-w-7xl)
├ section header: 小绿条 + "热门推荐" + 浏览所有按钮
├ Tab: 全部 | 技能包 | 智能体 (下划线样式)
└ ResourceCard 9 张,3 列网格

最新 + 工作台 (12 列网格)
├ 最新动态 (col-span-8)
│   ├ section header: 小绿条 + "全域动态流"
│   ├ 4 张紧凑 ResourceCard,2 列
│   └ "Load Discovery Stream" 按钮
└ 工作台 (col-span-4)
    ├ section header: brand-600 实心 icon + "工作台"
    ├ 未登录: 大头像图标 + "立即认证登录" CTA
    └ 已登录: 我的技能段 (3 项) + 我的智能体段 (3 项) + Open Control Panel 按钮

Footer
├ 品牌区 (logo + tagline + social icons)
├ Documentation 列
├ Community 列
└ 版本信息 + Network Ready 状态徽章
```

### 3.2 Tab 组件视觉规范

热门 / 最新 区的 Tab(全部 / 技能包 / 智能体)与导航 chip 视觉**必须区分**:

- **导航 chip**(顶部 nav):rounded-full + 灰底容器 + active 绿色填充
- **section Tab**(热门/最新内):**下划线样式** — active 底部 2px brand-500 短线 + 加粗 ink 色,inactive 灰色 slate-400

理由:section 内筛选 tab 视觉重量应小于导航,符合 [DESIGN_NOTES.md §3](../../../web/weavehub---知连/DESIGN_NOTES.md) "避免概念解释型文案堆叠"的克制感。

### 3.3 导航最终列表

替换当前 [layout.tsx:43-54](../../../web/src/app/layout.tsx) 的 navItems:

**未登录**(3 项):
- 首页(`/`)
- 搜索(`/search`)
- 登录(右侧 CTA,不在 chip nav 里)

**已登录**(5 项 chip + 1 个发布 dropdown):
- 首页(`/`)
- 发布 ▾(dropdown:发布技能 → `/dashboard/publish` / 发布智能体 → `/dashboard/publish/agent`)
- 技能(`/skills` — 当前 nav 没有这个入口,需新增)
- 智能体(`/agents`)
- 我的 Weave(`/my-weave` — 新路由,P0-1b 创建)
- 控制台(`/dashboard`)

差异说明:
- 站名 `SkillHub` → `知连 WeaveHub`,左侧加 `Layout` 图标
- nav 项视觉从 brand-gradient pill → `nav-chip`
- 移除 `/dashboard/skills` 入口(并入"我的 Weave")
- "搜索"在未登录侧保留(参考稿没有,但当前未登录用户没有 dashboard 入口,搜索是唯一发现路径)
- "发布"保留 dropdown(参考稿是单链接;但 [Phase E](../../plans/2026-04-27-agent-publish-review-pipeline.md) 已实现 dropdown,不退化)

### 3.4 卡片体系

参考稿用统一的 `ResourceCard` 同时承载 skill 和 agent。fork 方案:

- 当前 [SkillCard](../../../web/src/features/skill/skill-card.tsx) / [AgentCard](../../../web/src/features/agent/agent-card.tsx) 是各自独立的组件,**不合并**
  (这两个组件的数据形状和操作差异较大,合并代价大于收益)
- 但**视觉语言统一**:都迁移到 `glass-card` 工具类 + 同样的 type 徽章 + 同样的 hover 行为
- AgentCard 不再有"紫色识别";SkillCard 不再有"蓝色识别"
- 类型区分**只靠右上角 type 徽章文字**(灰底 `bg-slate-100/80 text-slate-500`,文字 `skill` / `agent`)
- **新增** `ResourceCard` 用于 landing 页"热门 / 最新"的统一展示,内部根据 `resource.type` 走对应卡片骨架
  (位置:`web/src/shared/components/resource-card.tsx`)

### 3.5 我的 Weave 页(新路由 `/my-weave`)

[DESIGN_NOTES §5](../../../web/weavehub---知连/DESIGN_NOTES.md) 要求"我的 Weave 要明确展示两个列表:我的技能包 / 我的智能体"。

- 上部:我的技能包(复用现有 `/dashboard/skills` 列表逻辑)
- 下部:我的智能体(复用现有 [my-agents.tsx](../../../web/src/pages/dashboard/my-agents.tsx) 列表逻辑)
- 顶部 dashboard-page-header(已有共享组件)
- 不引入新的状态管理或 API,仅做视觉 + 路由整合

旧的 `/dashboard/skills` 路由保留(向后兼容),但 nav 不再链入。

### 3.6 动效

替换当前 [useInView](../../../web/src/shared/hooks/use-in-view.ts) 自定义 hook 为 `motion/react`:

- ResourceCard 入场:`initial={{ opacity: 0, y: 20 }} whileInView={{ opacity: 1, y: 0 }} viewport={{ once: true }}`
  delay 按 index*0.05 错开
- Hero 主标题:`initial={{ opacity: 0, y: 30 }} animate ease 0.16,1,0.3,1 duration 0.8`
- 工作台未登录/登录切换:`AnimatePresence mode="wait"`

需要新增依赖:`pnpm add motion`(weavehub 原稿已用 `motion/react`)。

### 3.7 站名变更

全局站名从 SkillHub 改为 **WeaveHub** 系列(显示侧)。代码包名 `skillhub` 不动。

**locale-aware 显示**:
- zh-CN locale:`知连 WeaveHub`(双语全称)
- en locale:`WeaveHub`(纯英文,品牌名不翻译,但去掉中文部分)

通过 i18n `nav.brand.name` key 实现两种显示;`<title>` 标签使用 zh 默认值 `知连 WeaveHub`(浏览器 tab 标题不随 locale 切换)。

涉及位置:
- [layout.tsx](../../../web/src/app/layout.tsx) 站名 logo 文字 — 读 `t('nav.brand.name')`
- [web/index.html](../../../web/index.html) `<title>` 标签 — 静态值 "知连 WeaveHub"
- i18n `nav.brand.name` key (en + zh)
- Footer 品牌段 — 同样读 i18n key

不涉及:
- ❌ 后端类名 / 包名 / API 路径(`@global` 命名空间不变)
- ❌ git 仓库名(`Weave-Hub` 已是)
- ❌ Docker 镜像 tag

## 4. 切片与提交策略

### 4.1 P0-1a: Design tokens + glass-morphism 体系迁移(~1.5 天)

**ADR 0003 §1.2** · 纯前端 · token 层

只动 design system 层,**不动版面**。提交后全站颜色变绿、卡片变玻璃感,但布局不动。

**改造文件清单:**

| 文件 | 动作 |
|---|---|
| [web/src/index.css](../../../web/src/index.css) | 替换 brand 色系为绿色单色阶 / 加 glass + nav-chip + btn-* 工具类 / 改字体引用 |
| [web/index.html](../../../web/index.html) | 字体 link 切到 Inter+JetBrains Mono(去 Syne+IBM Plex) |
| [web/tailwind.config.ts](../../../web/tailwind.config.ts) | brand 色 plugin 配置(brand-50/100/200/500/600/700) |
| [web/src/features/skill/skill-card.tsx](../../../web/src/features/skill/skill-card.tsx) | 视觉迁移到 glass-card,移除任何"蓝色识别"伪暗示 |
| [web/src/features/agent/agent-card.tsx](../../../web/src/features/agent/agent-card.tsx) | 视觉迁移到 glass-card,移除"紫色"伪暗示 |
| [web/src/shared/ui/card.tsx](../../../web/src/shared/ui/card.tsx) | 默认 className 调整以兼容 glass-card override |
| [web/src/app/layout.tsx](../../../web/src/app/layout.tsx) | nav 项视觉从 brand-gradient pill → nav-chip(active 绿) |
| [web/src/shared/ui/button.tsx](../../../web/src/shared/ui/button.tsx) | primary/secondary variant 对齐新 token |
| [web/src/features/review/review-skill-detail-section.tsx](../../../web/src/features/review/review-skill-detail-section.tsx) | brand-gradient 引用回归 |
| [web/src/pages/home.tsx](../../../web/src/pages/home.tsx) | brand-gradient 引用回归(如仍用) |
| [web/package.json](../../../web/package.json) | `pnpm add motion` |

**全文搜索回归点**(grep 已确认):
- `brand-gradient` / `brand-start` / `brand-end` 共 8 个文件,逐一 verify
- `--accent` token 引用(11 个文件),不动语义但需视觉确认 hover 行为正确

**验收**:
- `pnpm vitest run` 仍 631/631
- `pnpm typecheck` / `pnpm lint` 仍 clean
- 浏览器烟雾:每个 Card 类组件颜色统一为绿色 hover、卡片有 glass 感、nav 项是 chip 形态
- 不允许 P0-1a 期间 landing 出现"半旧半新"的混乱中间态(如出现,把 landing 视觉冻结到 P0-1b)

### 4.2 P0-1b: Landing + my-weave 信息架构重写(~1.5 天)

**ADR 0003 §1.2** · 纯前端 · 版面层 · 依赖 P0-1a 完成

**改造文件清单:**

| 文件 | 动作 |
|---|---|
| [web/src/pages/landing.tsx](../../../web/src/pages/landing.tsx) | 整体重写:Hero 改文案 / 删 stats / 改三段架构 |
| `web/src/shared/components/resource-card.tsx` | **新增**:landing 用资源卡,内部根据 `resource.type` dispatch 到 SkillCard / AgentCard 骨架(**不**合并两组件) |
| `web/src/shared/components/resource-tabs.tsx` | **新增**:下划线样式 Tab,用于热门/最新 |
| `web/src/shared/components/landing-hot-section.tsx` | **新增**:热门推荐区(含 Tab + 9 卡网格) |
| `web/src/shared/components/landing-recent-section.tsx` | **新增**:最新动态区(8 列) |
| `web/src/shared/components/landing-workspace.tsx` | **新增**:工作台(4 列,含登录态切换) |
| [web/src/shared/components/landing-channels.tsx](../../../web/src/shared/components/landing-channels.tsx) | **删除**:旧双频道介绍块,被新架构取代 |
| [web/src/shared/components/popular-agents.tsx](../../../web/src/shared/components/popular-agents.tsx) | **删除**:被新热门区取代 |
| [web/src/shared/components/landing-quick-start.tsx](../../../web/src/shared/components/landing-quick-start.tsx) | **删除**:不再有独立 Quick Start 区 |
| `web/src/pages/my-weave.tsx` | **新增**:`/my-weave` 路由页面 |
| `web/src/pages/my-weave.test.tsx` | **新增**:基础渲染测试 |
| [web/src/app/router.tsx](../../../web/src/app/router.tsx) | 加 `/my-weave` 路由 |
| [web/src/app/layout.tsx](../../../web/src/app/layout.tsx) | nav 项重排(加 /skills 和 /my-weave,去 /dashboard/skills) |
| [web/src/i18n/locales/en.json](../../../web/src/i18n/locales/en.json) | 加新 keys: landing.hero.title 改 / landing.hero.subtitle 改 / nav.myWeave 等 |
| [web/src/i18n/locales/zh.json](../../../web/src/i18n/locales/zh.json) | 同上 |
| [web/src/pages/landing.test.tsx](../../../web/src/pages/landing.test.tsx) | 全面更新 |
| [web/index.html](../../../web/index.html) | `<title>` 改"知连 WeaveHub" |

**i18n 新 keys**(en + zh):
- `nav.myWeave` "我的 Weave" / "My Weave"
- `nav.skills` "技能" / "Skills"
- `landing.hero.title` "持续进化的 AI 能力" / "AI capabilities that keep evolving"
- `landing.hero.subtitle` "让团队的技能包和智能体在一起协作" / "Where your team's skills and agents collaborate"
- `landing.hot.title` "热门推荐"
- `landing.hot.tab.all` / `tab.skill` / `tab.agent`
- `landing.recent.title` "全域动态流"
- `landing.recent.loadMore`
- `landing.workspace.title` "工作台"
- `landing.workspace.guestPrompt` / `guestCta`
- `landing.workspace.skills` / `workspace.agents`
- `landing.workspace.openPanel`

**i18n 删除/标 deprecated keys**:
- `landing.stats.*` (4 个)
- `landing.channels.*` (整块)
- `landing.popularAgents.*`
- `landing.hero.exploreSkills` / `browseAgents`(被"开始探索"单按钮取代)

**验收**:
- 浏览器烟雾:首页 Hero 文案为"持续进化的 AI 能力"+ 副标 / 不再显示任何 stats 数字 / Tab 切换混排 / 工作台未登录登录状态正确
- 测试 631 → 应增长(~5-8 个新组件测试 + landing 重写)
- typecheck/lint clean
- 所有 plan 文档(包括本 spec)的引用路径仍可点击通过

## 5. 不在本 spec 范围

明确**不**在 P0-1a / P0-1b 做的事:

- 后端 API 改动
- Agent / Skill 数据模型改动
- 命名空间 / 用户系统改动
- 国际化新语言(en/zh 之外)
- 移动端适配深度优化(weavehub 原稿已 responsive,保持其骨架即可)
- 深色主题(原 backlog P2-3 留作后续)
- AgentCard / SkillCard 合并为单组件
- 其它 fork 路线项目(P0-2 Agent 搜索 / P1-2 Agent 评论 / P2 治理动作 / P3 Executor 等)

## 6. 风险

| 风险 | 应对 |
|---|---|
| `motion/react` 引入新依赖,bundle 体积增长 | weavehub 已选用,SSR 影响有限;P0-1b 提交时验 bundle 体积报告 |
| nav 增 `/skills` 入口后,P0-1a 期间访问 `/skills` 路由不存在(原本就不存在) | P0-1a 只动 token + 卡片,不动 nav 链表;nav 变更全部在 P0-1b |
| 旧 i18n keys 删除可能漏改引用文件 | P0-1b 全文搜索 `landing.stats` / `landing.channels` / `landing.popularAgents` / `nav.mySkills`,确认无残留 |
| glass-card backdrop-blur 在低端设备 / 旧浏览器不支持 | tailwind 已自动 polyfill;`prefers-reduced-transparency` 时退化为不透明白底 |
| `/my-weave` 页面初次访问时拉双 list,首屏慢 | 复用现有 hooks (useSearchSkills owner filter / useAgents owner filter);两个查询并发,Skeleton 占位 |
| 站名 locale 显示策略 | 见 §3.7,zh="知连 WeaveHub" / en="WeaveHub" |

## 7. 文档配套改写(本 spec commit 时同步)

立即改写的 4 份既有文档:

| 文件 | 动作 |
|---|---|
| [docs/adr/0003-fork-scope-and-upstream-boundary.md](../../adr/0003-fork-scope-and-upstream-boundary.md) §1.2 | 改写为 WeaveHub 美学;删 Tech Weave / 双频道 / Syne 段;加绿色单色 + glass + Inter + 知连 WeaveHub 段 |
| [web/LANDING_PAGE_REDESIGN.md](../../../web/LANDING_PAGE_REDESIGN.md) | 整体改写为 WeaveHub 设计文档 |
| [docs/landing-page-redesign.md](../../landing-page-redesign.md) | 整体改写;不再有"Skills 蓝 / Agents 紫"双频道愿景;改为"知连 WeaveHub 信息架构与设计语言"|
| [docs/plans/2026-04-27-fork-backlog.md](../../plans/2026-04-27-fork-backlog.md) | P0/P1 视觉项重排(P0-1a/P0-1b 替换原 P0-1/P0-3,删 P1-3),更新当前快照 |

后续 P0-1a / P0-1b plan 文档由 superpowers:writing-plans skill 在本 spec 通过用户 review 后生成。

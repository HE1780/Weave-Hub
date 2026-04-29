# 知连 WeaveHub 落地页设计

**Status:** Approved (2026-04-27 brainstorming, A 路线全面取代旧 Tech Weave 方案)
**Detailed spec:** [docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md](../docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md)
**Reference prototype:** [web/weavehub---知连/](weavehub---知连/) (用户提供的 AI Studio prototype)
**Scope clause:** [ADR 0003](../docs/adr/0003-fork-scope-and-upstream-boundary.md) §1.2

## 设计理念

知连 WeaveHub 是面向团队和企业自有应用的 AI 能力协作平台。落地页采用**浅色 glass-morphism + 绿色单色系**,
功能型入口为主,克制不堆砌。

### 视觉特点

1. **glass-morphism 卡片**
   - 白色透明 + `backdrop-blur-md` + rounded-3xl
   - hover 时变实心白底 + 上提 `-translate-y-1` + 加深绿色软阴影
   - 营造层次感和呼吸感

2. **配色方案**
   - 主色:绿色单色阶 `#34a853` (brand-500) / `#2c8e46` (brand-600) / `#23743a` (brand-700)
   - 浅色阶:brand-50 (#f4fbf6) / brand-100 (#e7f4ea) / brand-200 (#c9e8d1)
   - 正文:`#1f2937` (ink)
   - 页面底色:`#fcfdfc` (zinc-soft)
   - 背景叠双 `radial-gradient`(`#f0fdf4` 左上 + `#f0f9ff` 右上),attached fixed

3. **字体选择**
   - 正文 + 标题:Inter (300/400/500/600/700)
   - 代码:JetBrains Mono (400/500)
   - 不用 serif italic(中英混排稳定性优先)

4. **动效设计**
   - `motion/react` 渐入(initial → whileInView,delay 按 index 错开)
   - 卡片悬浮上提 + 阴影变深
   - `AnimatePresence` 处理工作台登录态切换

### 内容结构

1. **Header(glass-header)**
   - 站名 logo:`Layout` 图标 + "知连 WeaveHub" brand-gradient 文字
   - 中部 chip nav(rounded-full 胶囊,active 绿色填充)
   - 右侧:语言切换 + 通知 + 用户菜单

2. **Hero 区域**(`max-w-4xl` 居中)
   - 小绿条徽章(Sparkles + uppercase tracking)
   - 主标题:**持续进化的 AI 能力**(Inter `font-black tracking-tight`)
   - 副标题:**让团队的技能包和智能体在一起协作**
   - 搜索框(⌘K hint)+ "开始探索" 按钮
   - **不展示宣传数字**(无 stats 块)

3. **热门推荐**(`max-w-7xl`)
   - section header:小绿条 + "热门推荐" + "浏览所有" 按钮
   - Tab(下划线样式):全部 / 技能包 / 智能体
   - ResourceCard 9 张,3 列网格

4. **最新动态 + 工作台**(12 列网格)
   - 最新动态(col-span-8):紧凑 ResourceCard 4 张,2 列;底部 "Load Discovery Stream" 按钮
   - 工作台(col-span-4):
     - 未登录:大头像图标 + "立即认证登录" CTA
     - 已登录:我的技能段(3 项)+ 我的智能体段(3 项)+ "Open Control Panel" 按钮

5. **Footer**
   - 品牌区(logo + tagline + social icons)
   - Documentation 列(API References / Cloud Sync / Security / Integration)
   - Community 列(Open Source / Forum / Privacy / Support)
   - 版本信息 + Network Ready 状态徽章

## 信息表达原则

来自 [DESIGN_NOTES.md](weavehub---知连/DESIGN_NOTES.md):

- 面向用户表达,不使用研发内部术语
- **不展示宣传型数字**(如"1000+ 技能包"、下载量等)
- 避免概念解释型文案堆叠,优先使用直接动作文案
- 文案优先使用:`技能包`、`智能体`、`我的 Weave`、`发布`

## 导航规则

### 未登录(3 项)

- 首页 / 搜索 / 登录(登录是右侧 CTA,不在 chip nav 里)

### 已登录(5 项 chip + 1 个发布 dropdown)

- 首页 / 发布 ▾(发布技能 / 发布智能体)/ 技能 / 智能体 / 我的 Weave / 控制台

## 卡片体系

- `SkillCard` / `AgentCard` **不合并**(数据形状和操作差异较大),但视觉语言统一到 `glass-card`
- 类型区分**只靠右上角 type 灰底文字徽章**("skill" / "agent"),不分频道色
- **新增** `ResourceCard`(`web/src/shared/components/resource-card.tsx`)用于 landing "热门 / 最新" 统一展示

## 我的 Weave 页(`/my-weave`)

`DESIGN_NOTES §5` 要求双栏明确:

- 上部:我的技能包(复用现有 `/dashboard/skills` 列表逻辑)
- 下部:我的智能体(复用现有 [my-agents.tsx](src/pages/dashboard/my-agents.tsx) 列表逻辑)
- 旧 `/dashboard/skills` 路由保留向后兼容,但 nav 不再链入

## 切片落地

| Plan | 范围 | 估时 |
|---|---|---|
| **P0-1a** | design tokens + glass-morphism 工具类 + 字体切换 + 既有 Card 类组件视觉迁移 | ~1.5 天 |
| **P0-1b** | Landing 信息架构重写 + `/my-weave` 路由 + nav 重排 + 站名变更 | ~1.5 天 |

详细文件改造清单见 [design spec](../docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md) §4。

## 抛弃的旧路线(2026-04-27 决策)

旧 Tech Weave 美学(粒子 Canvas / 深 slate-950 基调 / Syne 字体 / 双频道蓝/紫色系 / Hero 双入口大卡 /
Quick Start Architect Tab / 统一搜索三栏 Tabs / 宣传数字)**全部抛弃**。

## 技术实现

- React 18 + TypeScript
- TailwindCSS(`@theme` 块定义新 brand 色)
- TanStack Router + TanStack Query(已有)
- `motion/react`(新增依赖,P0-1a 引入)

## 文件变更范围

### P0-1a(token 层)

- [src/index.css](src/index.css) — brand 色系替换 + glass 工具类 + 字体引用
- [index.html](index.html) — 字体 link 切到 Inter+JetBrains Mono / `<title>` 改"知连 WeaveHub"
- [tailwind.config.ts](tailwind.config.ts) — brand 色 plugin 配置
- [src/features/skill/skill-card.tsx](src/features/skill/skill-card.tsx) — 视觉迁移到 glass-card
- [src/features/agent/agent-card.tsx](src/features/agent/agent-card.tsx) — 视觉迁移到 glass-card
- [src/shared/ui/card.tsx](src/shared/ui/card.tsx) — 兼容 glass-card override
- [src/app/layout.tsx](src/app/layout.tsx) — nav chip 视觉(链表不变)
- [src/shared/ui/button.tsx](src/shared/ui/button.tsx) — variant 对齐新 token
- 全文搜索回归 8 个引用 `brand-gradient` 的文件
- [package.json](package.json) — `pnpm add motion`

### P0-1b(版面层)

- [src/pages/landing.tsx](src/pages/landing.tsx) — 整体重写
- 新增 `src/shared/components/resource-card.tsx` / `resource-tabs.tsx` / `landing-hot-section.tsx` / `landing-recent-section.tsx` / `landing-workspace.tsx`
- 删除 [src/shared/components/landing-channels.tsx](src/shared/components/landing-channels.tsx) / [popular-agents.tsx](src/shared/components/popular-agents.tsx) / [landing-quick-start.tsx](src/shared/components/landing-quick-start.tsx)
- 新增 `src/pages/my-weave.tsx` + 测试
- [src/app/router.tsx](src/app/router.tsx) — 加 `/my-weave` 路由
- [src/app/layout.tsx](src/app/layout.tsx) — nav 项重排
- [src/i18n/locales/en.json](src/i18n/locales/en.json) + [zh.json](src/i18n/locales/zh.json) — 加新 keys / 删旧 keys

## 本地预览

```bash
cd web
pnpm install
pnpm dev
```

参考 prototype:

```bash
cd web/weavehub---知连
pnpm install
pnpm dev
```

访问 `http://localhost:5173`(主站)或 prototype 提供的端口。

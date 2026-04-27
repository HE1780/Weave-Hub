# WeaveHub Landing IA + my-weave + nav 重排 (P0-1b) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 fork landing 页从当前 9 段结构重写为 weavehub 4 段(Hero + 热门推荐 + 全域动态流+工作台 + Footer),新增 `/my-weave` 路由,nav 重排,站名换"知连 WeaveHub",所有 className 直接照搬 prototype。

**Architecture:** 整体重写 `landing.tsx` + 新增 5 个 landing 拆分组件 + 新增 ResourceCard(featured / compact 两 variant)+ 新增 my-weave 页面 + 改 layout.tsx nav 链表 + 改 router 加 /my-weave + i18n 中英分离 + index.html title 换。所有视觉照搬 [web/weavehub---知连/src/App.tsx](../../web/weavehub---知连/src/App.tsx) 的精确 className,只把 mock 替换为真实数据(useSearchSkills + useAgents)和应用 B 简化文案。

**Tech Stack:** React 18 + TanStack Router + TanStack Query + Tailwind 3 + motion/react 12.38.0 + lucide-react + react-i18next + Vitest.

**Spec reference:** [docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md](../superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md) §3 + §4.2

**Visual baseline:** [web/weavehub---知连/](../../web/weavehub---知连/) prototype + 4 张用户提供截图(Hero / 热门推荐 / 全域动态流+工作台 / Footer)

---

## Pre-flight

- [ ] **Step 0.1: Verify P0-1a complete + branch state**

```bash
cd /Users/lydoc/projectscoding/skillhub && git log --oneline -5
```

Expected: HEAD on or after `0581e80d` (P0-1a final review fix). Branch `main`. If anything else, **stop and reconcile**.

- [ ] **Step 0.2: Verify test baseline 635/635**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -5
```

Expected: `Tests  635 passed (635)`. P0-1a delivered +4 from card.test.tsx.

- [ ] **Step 0.3: Confirm motion installed and glass-card defined**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && grep '"motion"' package.json && grep -c '\.glass-card' src/index.css
```

Expected: motion present; ≥2 occurrences of `.glass-card` (rule + `:hover`).

---

## File structure

After this plan completes:

**Created:**
- `web/src/shared/components/resource-card.tsx` — visual ResourceCard (featured / compact 两 variant), 视觉照搬 prototype `ResourceCard` (line 70-104) 和紧凑卡 (line 267-286)
- `web/src/shared/components/resource-card.test.tsx` — 6 tests
- `web/src/shared/components/landing-hero.tsx` — Hero section(标语 + 主标题 + 副标 + 搜索 + 开始探索)
- `web/src/shared/components/landing-hot-section.tsx` — 热门推荐(section header + 6 featured ResourceCard)
- `web/src/shared/components/landing-recent-section.tsx` — 全域动态流(section header + 4 compact ResourceCard + Load 按钮)
- `web/src/shared/components/landing-workspace.tsx` — 工作台(登录态切换:guest 引导 / auth 双段列表)
- `web/src/shared/components/landing-footer.tsx` — Footer(品牌段 + 文档/社区列 + 版本徽章)
- `web/src/shared/components/landing-footer.test.tsx` — 2 tests
- `web/src/pages/my-weave.tsx` — `/my-weave` 路由页(双段:我的技能 / 我的智能体)
- `web/src/pages/my-weave.test.tsx` — 1 test

**Modified:**
- `web/src/pages/landing.tsx` — 整页重写(从 9 段缩到 4 段)
- `web/src/pages/landing.test.tsx` — 全面更新断言
- `web/src/app/layout.tsx` — nav 链表重排 + 站名换 + footer 删除(landing 自己有 footer 了,layout 里那个 footer 在 landing 路由要隐藏 OR 删除然后所有页面共用 landing 的 weavehub footer)
- `web/src/app/router.tsx` — 加 `agentDetailRoute` 后面 `myWeaveRoute`,加进 routeTree
- `web/src/i18n/locales/en.json` — 加 weavehub 新 keys / 删旧 stats/channels/popularAgents/quickStart keys
- `web/src/i18n/locales/zh.json` — 同上
- `web/index.html` — `<title>` 换 "知连 WeaveHub"

**Deleted:**
- `web/src/shared/components/landing-channels.tsx` + test
- `web/src/shared/components/popular-agents.tsx` + test
- `web/src/shared/components/landing-quick-start.tsx` + test

**Decision: Footer 处理** — Layout.tsx 现有 footer(SkillHub 品牌 + Home/Resources 列)和新设计的 weavehub footer 性质不同。本 plan 在 landing 路由用 weavehub footer 替代,**其他路由暂保留 layout 现有 footer**(P0-1b 范围只动 landing,其他页面 footer 改造留到后续 backlog)。Layout 的现有 footer 不删,只是 landing 用自己的 footer。

---

## Task 1: ResourceCard component (TDD, featured + compact variants)

视觉照搬 prototype line 70-104(featured) + line 267-286(compact)。两 variant 共一个组件。

**Files:**
- Create: `web/src/shared/components/resource-card.tsx`
- Create: `web/src/shared/components/resource-card.test.tsx`

- [ ] **Step 1.1: Write the failing test**

Create `web/src/shared/components/resource-card.test.tsx`:

```tsx
// @vitest-environment jsdom
import { describe, expect, it, afterEach } from 'vitest'
import { render, cleanup } from '@testing-library/react'
import { Link } from '@tanstack/react-router'
import { ResourceCard } from './resource-card'

afterEach(() => cleanup())

describe('ResourceCard', () => {
  const baseResource = {
    id: 'r1',
    title: 'Prompt Optimizer',
    type: 'skill' as const,
    category: '文案优化',
    updatedAt: '2h ago',
    href: '/skills/prompt-optimizer',
  }

  it('renders title, category, and type label in featured variant', () => {
    const { container } = render(
      <ResourceCard variant="featured" resource={baseResource} index={0} />,
    )
    expect(container.textContent).toContain('Prompt Optimizer')
    expect(container.textContent).toContain('文案优化')
    expect(container.textContent?.toLowerCase()).toContain('skill')
    expect(container.textContent?.toUpperCase()).toContain('EXPLORE')
  })

  it('renders compact variant without category subtitle and without EXPLORE row', () => {
    const { container } = render(
      <ResourceCard variant="compact" resource={baseResource} index={0} />,
    )
    expect(container.textContent).toContain('Prompt Optimizer')
    expect(container.textContent).not.toContain('文案优化')
    expect(container.textContent?.toUpperCase()).not.toContain('EXPLORE')
  })

  it('shows updatedAt in both variants', () => {
    const { container: featuredEl } = render(
      <ResourceCard variant="featured" resource={baseResource} index={0} />,
    )
    expect(featuredEl.textContent).toContain('2h ago')
    cleanup()
    const { container: compactEl } = render(
      <ResourceCard variant="compact" resource={baseResource} index={0} />,
    )
    expect(compactEl.textContent).toContain('2h ago')
  })

  it('renders agent type label correctly', () => {
    const agent = { ...baseResource, type: 'agent' as const, title: 'Code Reviewer' }
    const { container } = render(
      <ResourceCard variant="featured" resource={agent} index={0} />,
    )
    expect(container.textContent?.toLowerCase()).toContain('agent')
  })

  it('uses glass-card class for both variants', () => {
    const { container: a } = render(
      <ResourceCard variant="featured" resource={baseResource} index={0} />,
    )
    expect(a.firstChild as HTMLElement).toBeTruthy()
    expect((a.firstChild as HTMLElement).className).toContain('glass-card')
    cleanup()
    const { container: b } = render(
      <ResourceCard variant="compact" resource={baseResource} index={0} />,
    )
    expect((b.firstChild as HTMLElement).className).toContain('glass-card')
  })

  it('renders as a Link when href is provided', () => {
    const { container } = render(
      <ResourceCard variant="featured" resource={baseResource} index={0} />,
    )
    const link = container.querySelector('a')
    expect(link).toBeTruthy()
    expect(link?.getAttribute('href')).toContain('prompt-optimizer')
  })
})
```

- [ ] **Step 1.2: Run test, confirm failures**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/shared/components/resource-card.test.tsx 2>&1 | tail -15
```

Expected: 6 fail (module not found).

- [ ] **Step 1.3: Implement ResourceCard**

Create `web/src/shared/components/resource-card.tsx`:

```tsx
import { Link } from '@tanstack/react-router'
import { motion } from 'motion/react'
import { Clock, ChevronRight, Grid, type LucideIcon } from 'lucide-react'

export interface ResourceCardData {
  id: string | number
  title: string
  type: 'skill' | 'agent'
  category?: string
  updatedAt: string
  href: string
  icon?: LucideIcon
}

interface ResourceCardProps {
  resource: ResourceCardData
  variant: 'featured' | 'compact'
  index: number
}

/**
 * Visual card for one skill or agent resource on the landing page.
 *
 * Two variants:
 * - `featured` — used in 热门推荐, 3-column grid, large icon container + EXPLORE row
 * - `compact` — used in 全域动态流, 2-column grid, smaller, icon left + title right + type/time row
 *
 * Visual className strings mirror the WeaveHub prototype at
 * web/weavehub---知连/src/App.tsx (lines 70-104 and 267-286).
 */
export function ResourceCard({ resource, variant, index }: ResourceCardProps) {
  const Icon = resource.icon ?? Grid

  if (variant === 'compact') {
    return (
      <motion.div
        initial={{ opacity: 0, x: -10 }}
        whileInView={{ opacity: 1, x: 0 }}
        viewport={{ once: true }}
        transition={{ delay: index * 0.1 }}
        className="glass-card !p-6 group flex items-start gap-6 hover:bg-white border-white/40 shadow-sm"
      >
        <Link to={resource.href} className="flex items-start gap-6 flex-1 no-underline">
          <div className="w-14 h-14 rounded-2xl bg-slate-50 flex items-center justify-center text-slate-400 group-hover:bg-brand-50 group-hover:text-brand-600 transition-all duration-500 border border-white shrink-0">
            <Icon size={26} />
          </div>
          <div className="flex-1 pt-1">
            <h4 className="font-bold text-slate-800 text-lg group-hover:text-brand-600 transition-all tracking-tight leading-tight">
              {resource.title}
            </h4>
            <div className="flex items-center justify-between mt-3 text-[10px] font-black uppercase tracking-widest text-slate-400">
              <span className="bg-slate-100 px-2 py-0.5 rounded-md">{resource.type}</span>
              <span className="flex items-center gap-1">
                <Clock size={10} /> {resource.updatedAt}
              </span>
            </div>
          </div>
        </Link>
      </motion.div>
    )
  }

  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      whileInView={{ opacity: 1, y: 0 }}
      viewport={{ once: true }}
      transition={{ delay: index * 0.05, duration: 0.6, ease: [0.22, 1, 0.36, 1] }}
      className="glass-card group flex flex-col justify-between h-full"
    >
      <Link to={resource.href} className="no-underline flex flex-col justify-between h-full">
        <div>
          <div className="flex items-start justify-between mb-6">
            <div className="w-14 h-14 rounded-2xl bg-brand-50 flex items-center justify-center text-brand-600 group-hover:bg-brand-600 group-hover:text-white transition-all duration-500 shadow-sm border border-white">
              <Icon size={26} />
            </div>
            <span className="text-[10px] uppercase tracking-widest font-black px-3 py-1 rounded-lg bg-slate-100/80 text-slate-500 border border-white">
              {resource.type}
            </span>
          </div>
          <h3 className="text-xl font-bold text-slate-800 group-hover:text-brand-600 transition-colors tracking-tight mb-2">
            {resource.title}
          </h3>
          {resource.category && (
            <p className="text-xs text-slate-400 font-medium tracking-wide uppercase italic">
              {resource.category}
            </p>
          )}
        </div>
        <div className="mt-8 pt-5 border-t border-brand-50/50 flex items-center justify-between text-[11px] text-slate-400 font-bold uppercase tracking-tighter">
          <span className="flex items-center gap-1.5 opacity-70">
            <Clock size={14} /> {resource.updatedAt}
          </span>
          <div className="text-brand-600 flex items-center gap-1 group-hover:translate-x-1 transition-transform">
            EXPLORE <ChevronRight size={14} />
          </div>
        </div>
      </Link>
    </motion.div>
  )
}
```

- [ ] **Step 1.4: Run tests**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/shared/components/resource-card.test.tsx 2>&1 | tail -10
```

Expected: 6 tests pass.

- [ ] **Step 1.5: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/shared/components/resource-card.tsx web/src/shared/components/resource-card.test.tsx && git commit -m "feat(web): add ResourceCard with featured + compact variants"
```

---

## Task 2: i18n keys — add new + remove old

**Files:**
- Modify: `web/src/i18n/locales/en.json`
- Modify: `web/src/i18n/locales/zh.json`

This task replaces all landing-related keys at once. Add new keys per spec §4.2; remove old `landing.stats.*`, `landing.channels.*`, `landing.popularAgents.*`, `landing.quickStart.*`, `landing.hero.exploreSkills`, `landing.hero.browseAgents`. Keep `landing.hero.title` and `landing.hero.subtitle` keys but **redefine** their content.

- [ ] **Step 2.1: Read current en.json structure**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && grep -nE '"hero"|"channels"|"popularAgents"|"stats"|"quickStart"|"whySkillHub"|"features"' src/i18n/locales/en.json | head -30
```

This locates the blocks to remove. Your subagent may need the file's full contents — read it before editing.

- [ ] **Step 2.2: Edit en.json**

In `web/src/i18n/locales/en.json` at the `nav` object, replace the existing object with:

```json
"nav": {
  "brand": "WeaveHub",
  "landing": "Home",
  "publish": "Publish",
  "publishSkill": "Publish Skill",
  "publishAgent": "Publish Agent",
  "search": "Search",
  "skills": "Skills",
  "agents": "Agents",
  "myWeave": "My Weave",
  "dashboard": "Console",
  "mySkills": "My Skills",
  "login": "Sign in",
  "home": "Home"
},
```

Then **inside the `"landing"` object**, replace the entire `landing.hero` object plus delete `landing.channels`, `landing.popularAgents`, `landing.stats`, `landing.quickStart`, `landing.whySkillHub`, `landing.features` — replace with this complete block:

```json
"landing": {
  "hero": {
    "badge": "Redefining Intelligence Connection",
    "titleStart": "Continuously evolving",
    "titleAccent": "AI capability",
    "subtitle": "Where your team's skills and agents collaborate.",
    "searchPlaceholder": "Search skills and agents...",
    "exploreCta": "Get started",
    "publish": "Publish",
    "publishSkill": "Publish Skill",
    "publishAgent": "Publish Agent"
  },
  "hot": {
    "eyebrow": "Handpicked selection",
    "title": "Featured",
    "browseAll": "Browse all"
  },
  "recent": {
    "eyebrow": "Live Updates",
    "title": "Latest activity",
    "loadMore": "Load discovery stream"
  },
  "workspace": {
    "title": "Workspace",
    "guestEyebrow": "Sync required",
    "guestPrompt": "Authorize now to synchronize your AI assets across all platforms.",
    "guestCta": "Sign in",
    "skillsSection": "Skills",
    "agentsSection": "Agents",
    "openPanel": "Open control panel",
    "pcs": "items"
  },
  "footer": {
    "tagline": "WeaveHub: connect every fragment of intelligence and weave the future together.",
    "documentation": "Documentation",
    "community": "Community",
    "links": {
      "apiReferences": "API references",
      "cloudSync": "Cloud sync",
      "security": "Security",
      "integration": "Integration",
      "openSource": "Open source",
      "forum": "Forum",
      "privacy": "Privacy",
      "support": "Support"
    },
    "copyright": "© 2026 WeaveHub Intelligence. All rights reserved.",
    "version": "VER 0.1.0",
    "networkReady": "Network ready"
  }
},
"myWeave": {
  "title": "My Weave",
  "subtitle": "Your skills and agents in one place.",
  "skillsHeading": "My skills",
  "agentsHeading": "My agents",
  "viewAll": "View all",
  "skillsEmpty": "No skills yet",
  "agentsEmpty": "No agents yet"
},
```

(Keep `landing.cta` / `landing.empty` / any other unrelated keys in the existing object, only replace the listed sub-objects.)

- [ ] **Step 2.3: Edit zh.json with same key structure, Chinese values**

```json
"nav": {
  "brand": "知连 WeaveHub",
  "landing": "首页",
  "publish": "发布",
  "publishSkill": "发布技能",
  "publishAgent": "发布智能体",
  "search": "搜索",
  "skills": "技能",
  "agents": "智能体",
  "myWeave": "我的 Weave",
  "dashboard": "控制台",
  "mySkills": "我的技能",
  "login": "登录",
  "home": "首页"
},
```

```json
"landing": {
  "hero": {
    "badge": "REDEFINING INTELLIGENCE CONNECTION",
    "titleStart": "持续进化的",
    "titleAccent": "AI 能力",
    "subtitle": "让团队的技能包和智能体在一起协作。",
    "searchPlaceholder": "搜索技能包或智能体...",
    "exploreCta": "开始探索",
    "publish": "发布",
    "publishSkill": "发布技能",
    "publishAgent": "发布智能体"
  },
  "hot": {
    "eyebrow": "Handpicked selection",
    "title": "热门推荐",
    "browseAll": "浏览所有资源"
  },
  "recent": {
    "eyebrow": "Live updates",
    "title": "全域动态流",
    "loadMore": "Load discovery stream"
  },
  "workspace": {
    "title": "工作台",
    "guestEyebrow": "SYNC REQUIRED",
    "guestPrompt": "登录以同步你在各平台的 AI 资产。",
    "guestCta": "立即认证登录",
    "skillsSection": "技能市场资产",
    "agentsSection": "智能体开发集",
    "openPanel": "OPEN CONTROL PANEL",
    "pcs": "PCS"
  },
  "footer": {
    "tagline": "WeaveHub 知连:连接每一种智力片段,编织未来的无限可能。致力于构建全球最高效的 AI 协作枢纽。",
    "documentation": "Documentation",
    "community": "Community",
    "links": {
      "apiReferences": "API References",
      "cloudSync": "Cloud Sync",
      "security": "Security",
      "integration": "Integration",
      "openSource": "Open Source",
      "forum": "Forum",
      "privacy": "Privacy",
      "support": "Support"
    },
    "copyright": "© 2026 WEAVEHUB INTELLIGENCE. ALL RIGHTS RESERVED.",
    "version": "VER 0.1.0",
    "networkReady": "NETWORK READY"
  }
},
"myWeave": {
  "title": "我的 Weave",
  "subtitle": "你的技能包和智能体,集中查看。",
  "skillsHeading": "我的技能包",
  "agentsHeading": "我的智能体",
  "viewAll": "查看全部",
  "skillsEmpty": "暂无技能包",
  "agentsEmpty": "暂无智能体"
},
```

注意:`hot.eyebrow` / `recent.eyebrow` 等装饰文字在 zh-CN 也保留英文(weavehub 视觉特意用英文当装饰)。`workspace.guestEyebrow` / `openPanel` / `pcs` 同理。`footer.documentation` / `community` / 链接列表 / 版权 / 版本号 / NETWORK READY 全部保留英文。

- [ ] **Step 2.4: Verify JSON is valid**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && node -e "JSON.parse(require('fs').readFileSync('src/i18n/locales/en.json','utf8'))" && node -e "JSON.parse(require('fs').readFileSync('src/i18n/locales/zh.json','utf8'))" && echo OK
```

Expected: prints `OK`.

- [ ] **Step 2.5: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/i18n/locales/en.json web/src/i18n/locales/zh.json && git commit -m "feat(i18n): replace landing keys with weavehub vocabulary"
```

Note: This commit will break tests/components that reference the deleted keys (`landing.stats.*` etc.) until later tasks remove those references. That's expected — Tasks 8-10 will sweep them.

---

## Task 3: LandingHero component

**Files:**
- Create: `web/src/shared/components/landing-hero.tsx`

视觉照搬 prototype line 174-228。文案从 i18n。

- [ ] **Step 3.1: Implement**

Create `web/src/shared/components/landing-hero.tsx`:

```tsx
import { motion } from 'motion/react'
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { Search as SearchIcon, Sparkles } from 'lucide-react'
import { normalizeSearchQuery } from '@/shared/lib/search-query'

/**
 * Hero section of the WeaveHub landing page.
 *
 * Visual mirrors web/weavehub---知连/src/App.tsx lines 174-228.
 */
export function LandingHero() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  const handleSearch = (query: string) => {
    const normalized = normalizeSearchQuery(query)
    navigate({
      to: '/search',
      search: { q: normalized, sort: 'relevance', page: 0, starredOnly: false },
    })
  }

  return (
    <section className="relative">
      <div className="flex flex-col items-center text-center max-w-4xl mx-auto space-y-10">
        <motion.div
          initial={{ opacity: 0, scale: 0.9 }}
          animate={{ opacity: 1, scale: 1 }}
          className="inline-flex items-center gap-2.5 px-5 py-2 rounded-full bg-white/60 backdrop-blur-md text-brand-700 text-[11px] font-black uppercase tracking-[0.2em] border border-white shadow-sm"
        >
          <Sparkles size={14} className="text-brand-500" fill="currentColor" fillOpacity={0.2} />
          {t('landing.hero.badge')}
        </motion.div>

        <motion.h1
          initial={{ opacity: 0, y: 30 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
          className="text-6xl md:text-8xl font-black tracking-tight leading-[0.9] text-slate-900"
        >
          {t('landing.hero.titleStart')} <br />
          <span className="brand-gradient font-serif italic font-normal tracking-tight">
            {t('landing.hero.titleAccent')}
          </span>
        </motion.h1>

        <motion.p
          initial={{ opacity: 0, y: 20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.2, duration: 0.8 }}
          className="text-slate-400 text-lg md:text-2xl max-w-2xl font-medium leading-relaxed"
        >
          {t('landing.hero.subtitle')}
        </motion.p>

        <motion.div
          initial={{ opacity: 0, y: 40 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ delay: 0.4, duration: 0.8, ease: [0.16, 1, 0.3, 1] }}
          className="w-full max-w-3xl flex flex-col sm:flex-row items-stretch gap-4 pt-6"
        >
          <div className="flex-1 glass-card !p-5 !rounded-3xl flex items-center gap-5 bg-white/80 focus-within:bg-white focus-within:ring-8 ring-brand-500/5 transition-all border-white border-2 shadow-2xl shadow-brand-500/5">
            <SearchIcon size={24} className="text-slate-300" />
            <input
              type="text"
              placeholder={t('landing.hero.searchPlaceholder')}
              className="flex-1 bg-transparent outline-none text-lg font-medium placeholder:text-slate-300"
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  handleSearch((e.target as HTMLInputElement).value)
                }
              }}
            />
            <div className="hidden sm:flex items-center gap-1.5 opacity-30">
              <kbd className="px-2.5 py-1 rounded bg-slate-100 text-[11px] font-mono font-bold">⌘</kbd>
              <kbd className="px-2.5 py-1 rounded bg-slate-100 text-[11px] font-mono font-bold">K</kbd>
            </div>
          </div>
          <button
            onClick={() => handleSearch('')}
            className="btn-primary justify-center px-10 shadow-2xl !rounded-3xl"
          >
            {t('landing.hero.exploreCta')}
          </button>
        </motion.div>
      </div>
    </section>
  )
}
```

- [ ] **Step 3.2: Typecheck**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm tsc --noEmit 2>&1 | tail -10
```

Expected: only pre-existing `registry-skill.tsx` errors. No new errors.

- [ ] **Step 3.3: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/shared/components/landing-hero.tsx && git commit -m "feat(web): add LandingHero with weavehub visual + B copy"
```

---

## Task 4: LandingHotSection — featured ResourceCard grid

**Files:**
- Create: `web/src/shared/components/landing-hot-section.tsx`

接 `useSearchSkills` + `useAgents` 数据,混排成 6 张 ResourceCard。视觉照搬 prototype line 230-250。

- [ ] **Step 4.1: Implement**

Create `web/src/shared/components/landing-hot-section.tsx`:

```tsx
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { ArrowRight, Wand2, ShieldCheck, BarChart3, FileSearch, Cpu, Zap, Grid, type LucideIcon } from 'lucide-react'
import { ResourceCard, type ResourceCardData } from './resource-card'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useAgents } from '@/features/agent/use-agents'
import { SkeletonList } from './skeleton-loader'

/**
 * "热门推荐" section — mixed skill+agent grid (3 + 3 = 6 cards),
 * visual mirrors web/weavehub---知连/src/App.tsx lines 230-250.
 */
const TYPE_ICON_POOL: LucideIcon[] = [Wand2, ShieldCheck, BarChart3, FileSearch, Cpu, Zap, Grid]

function pickIcon(seed: string | number): LucideIcon {
  const hash = String(seed).split('').reduce((acc, c) => acc + c.charCodeAt(0), 0)
  return TYPE_ICON_POOL[hash % TYPE_ICON_POOL.length]
}

function relativeTime(iso?: string): string {
  if (!iso) return ''
  const ms = Date.now() - new Date(iso).getTime()
  if (ms < 0 || Number.isNaN(ms)) return ''
  const minutes = Math.floor(ms / 60000)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)
  if (days > 0) return `${days}d ago`
  if (hours > 0) return `${hours}h ago`
  if (minutes > 0) return `${minutes}m ago`
  return 'now'
}

export function LandingHotSection() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { data: skills, isLoading: skillsLoading } = useSearchSkills({
    sort: 'downloads',
    size: 3,
  })
  const { data: agents, isLoading: agentsLoading } = useAgents()

  const isLoading = skillsLoading || agentsLoading

  const skillCards: ResourceCardData[] = (skills?.items ?? []).slice(0, 3).map((s) => ({
    id: s.id,
    title: s.displayName,
    type: 'skill',
    category: s.summary ?? undefined,
    updatedAt: relativeTime(s.updatedAt),
    href: `/space/${s.namespace}/${encodeURIComponent(s.slug)}`,
    icon: pickIcon(s.id),
  }))

  const agentCards: ResourceCardData[] = (agents ?? []).slice(0, 3).map((a) => ({
    id: a.name,
    title: a.name,
    type: 'agent',
    category: a.description,
    updatedAt: '',
    href: `/agents/${a.namespace ?? 'global'}/${a.name}`,
    icon: pickIcon(a.name),
  }))

  // Interleave skills and agents: skill, agent, skill, agent, skill, agent
  const mixed: ResourceCardData[] = []
  for (let i = 0; i < 3; i += 1) {
    if (skillCards[i]) mixed.push(skillCards[i])
    if (agentCards[i]) mixed.push(agentCards[i])
  }

  return (
    <section>
      <div className="flex items-center justify-between mb-12">
        <div>
          <h3 className="text-xs font-black text-brand-600 uppercase tracking-[0.3em] mb-3 flex items-center gap-2">
            <div className="h-1 w-8 bg-brand-500 rounded-full"></div>
            {t('landing.hot.eyebrow')}
          </h3>
          <h2 className="text-4xl font-black text-slate-800 tracking-tight">
            {t('landing.hot.title')}
          </h2>
        </div>
        <button
          onClick={() => navigate({ to: '/search', search: { q: '', sort: 'downloads', page: 0, starredOnly: false } })}
          className="btn-secondary group !bg-transparent border-none hover:bg-white/40 !rounded-full"
        >
          {t('landing.hot.browseAll')}{' '}
          <ArrowRight size={20} className="group-hover:translate-x-1 transition-transform" />
        </button>
      </div>

      {isLoading ? (
        <SkeletonList count={6} />
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-10">
          {mixed.map((resource, idx) => (
            <ResourceCard key={resource.id} variant="featured" resource={resource} index={idx} />
          ))}
        </div>
      )}
    </section>
  )
}
```

- [ ] **Step 4.2: Typecheck**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm tsc --noEmit 2>&1 | tail -10
```

Expected: only `registry-skill.tsx` errors.

- [ ] **Step 4.3: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/shared/components/landing-hot-section.tsx && git commit -m "feat(web): add LandingHotSection mixed skill+agent grid"
```

---

## Task 5: LandingRecentSection — compact ResourceCard grid

**Files:**
- Create: `web/src/shared/components/landing-recent-section.tsx`

视觉照搬 prototype line 254-291(left col-span-8 of action-blocks)。

- [ ] **Step 5.1: Implement**

Create `web/src/shared/components/landing-recent-section.tsx`:

```tsx
import { useTranslation } from 'react-i18next'
import { Zap, Wand2, ShieldCheck, BarChart3, FileSearch, Cpu, Grid, PlusCircle, type LucideIcon } from 'lucide-react'
import { ResourceCard, type ResourceCardData } from './resource-card'
import { useSearchSkills } from '@/shared/hooks/use-skill-queries'
import { useAgents } from '@/features/agent/use-agents'
import { SkeletonList } from './skeleton-loader'

const TYPE_ICON_POOL: LucideIcon[] = [Wand2, ShieldCheck, BarChart3, FileSearch, Cpu, Zap, Grid, PlusCircle]

function pickIcon(seed: string | number): LucideIcon {
  const hash = String(seed).split('').reduce((acc, c) => acc + c.charCodeAt(0), 0)
  return TYPE_ICON_POOL[hash % TYPE_ICON_POOL.length]
}

function relativeTime(iso?: string): string {
  if (!iso) return ''
  const ms = Date.now() - new Date(iso).getTime()
  if (ms < 0 || Number.isNaN(ms)) return ''
  const minutes = Math.floor(ms / 60000)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)
  if (days > 0) return `${days}d`
  if (hours > 0) return `${hours}h`
  if (minutes > 0) return `${minutes}m`
  return 'now'
}

/**
 * "全域动态流" section — compact mixed grid (4 cards, 2 columns).
 * Visual mirrors web/weavehub---知连/src/App.tsx lines 254-291.
 */
export function LandingRecentSection() {
  const { t } = useTranslation()
  const { data: skills, isLoading: skillsLoading } = useSearchSkills({
    sort: 'newest',
    size: 2,
  })
  const { data: agents, isLoading: agentsLoading } = useAgents()

  const isLoading = skillsLoading || agentsLoading

  const skillCards: ResourceCardData[] = (skills?.items ?? []).slice(0, 2).map((s) => ({
    id: s.id,
    title: s.displayName,
    type: 'skill',
    updatedAt: relativeTime(s.updatedAt),
    href: `/space/${s.namespace}/${encodeURIComponent(s.slug)}`,
    icon: pickIcon(s.id),
  }))

  const agentCards: ResourceCardData[] = (agents ?? []).slice(0, 2).map((a) => ({
    id: a.name,
    title: a.name,
    type: 'agent',
    updatedAt: 'now',
    href: `/agents/${a.namespace ?? 'global'}/${a.name}`,
    icon: pickIcon(a.name),
  }))

  const mixed: ResourceCardData[] = []
  for (let i = 0; i < 2; i += 1) {
    if (skillCards[i]) mixed.push(skillCards[i])
    if (agentCards[i]) mixed.push(agentCards[i])
  }

  return (
    <section className="lg:col-span-8 flex flex-col">
      <div className="flex items-center gap-4 mb-10">
        <div className="w-14 h-14 rounded-3xl bg-white border border-brand-50 flex items-center justify-center text-brand-600 shadow-sm">
          <Zap size={28} />
        </div>
        <div>
          <h3 className="text-xs font-black text-slate-300 uppercase tracking-[0.3em] mb-1">
            {t('landing.recent.eyebrow')}
          </h3>
          <h3 className="text-4xl font-black text-slate-800 tracking-tighter">
            {t('landing.recent.title')}
          </h3>
        </div>
      </div>

      {isLoading ? (
        <SkeletonList count={4} />
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 flex-1">
          {mixed.map((resource, idx) => (
            <ResourceCard key={resource.id} variant="compact" resource={resource} index={idx} />
          ))}
        </div>
      )}

      <button className="mt-8 py-5 rounded-3xl border-2 border-dashed border-slate-200 text-slate-400 hover:text-brand-600 hover:border-brand-500 hover:bg-white transition-all text-xs font-black uppercase tracking-[0.2em]">
        {t('landing.recent.loadMore')}
      </button>
    </section>
  )
}
```

- [ ] **Step 5.2: Typecheck**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm tsc --noEmit 2>&1 | tail -10
```

- [ ] **Step 5.3: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/shared/components/landing-recent-section.tsx && git commit -m "feat(web): add LandingRecentSection compact mixed grid"
```

---

## Task 6: LandingWorkspace — guest/auth两态

**Files:**
- Create: `web/src/shared/components/landing-workspace.tsx`

视觉照搬 prototype line 293-368。逻辑接 `useAuth`,登录态显示 `useMySkills` + 个人 agents 的前 3 项。

- [ ] **Step 6.1: Implement**

Create `web/src/shared/components/landing-workspace.tsx`:

```tsx
import { useNavigate } from '@tanstack/react-router'
import { motion, AnimatePresence } from 'motion/react'
import { useTranslation } from 'react-i18next'
import { Grid, User as UserIcon, ChevronRight } from 'lucide-react'
import { useAuth } from '@/features/auth/use-auth'
import { useMySkills } from '@/shared/hooks/use-user-queries'
import { useAgents } from '@/features/agent/use-agents'

/**
 * "工作台" right rail (col-span-4). Guest sees sign-in CTA; authenticated user
 * sees their top 3 skills + top 3 agents. Visual mirrors prototype lines 293-368.
 */
export function LandingWorkspace() {
  const { t } = useTranslation()
  const { user } = useAuth()
  const navigate = useNavigate()
  const isLoggedIn = !!user

  const { data: mySkillsPage } = useMySkills(
    { page: 0, size: 3 },
    { enabled: isLoggedIn },
  )
  const { data: agentsList } = useAgents()
  const myAgents = (agentsList ?? []).filter((a) => a.namespace).slice(0, 3)
  const mySkills = mySkillsPage?.items?.slice(0, 3) ?? []

  return (
    <section className="lg:col-span-4 h-full">
      <div className="glass-card h-full flex flex-col bg-brand-50/20 border-white/80 p-8">
        <div className="flex items-center gap-4 mb-10">
          <div className="w-12 h-12 rounded-2xl bg-brand-600 text-white flex items-center justify-center shadow-xl shadow-brand-600/20">
            <Grid size={24} />
          </div>
          <h3 className="text-2xl font-black text-slate-800 tracking-tighter">
            {t('landing.workspace.title')}
          </h3>
        </div>

        <div className="flex-1">
          <AnimatePresence mode="wait">
            {!isLoggedIn ? (
              <motion.div
                key="guest"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -10 }}
                className="flex flex-col items-center justify-center h-full py-12 text-center"
              >
                <div className="w-24 h-24 rounded-full bg-white border border-brand-50 flex items-center justify-center text-slate-200 mb-8 shadow-inner shadow-slate-100">
                  <UserIcon size={48} />
                </div>
                <p className="text-sm font-black text-slate-800 mb-3 uppercase tracking-widest leading-none">
                  {t('landing.workspace.guestEyebrow')}
                </p>
                <p className="text-xs text-slate-400 mb-10 max-w-[200px] font-medium leading-relaxed">
                  {t('landing.workspace.guestPrompt')}
                </p>
                <button
                  onClick={() => navigate({ to: '/login', search: { returnTo: '/' } })}
                  className="btn-primary w-full justify-center !rounded-2xl"
                >
                  {t('landing.workspace.guestCta')}
                </button>
              </motion.div>
            ) : (
              <motion.div
                key="auth"
                initial={{ opacity: 0, y: 10 }}
                animate={{ opacity: 1, y: 0 }}
                className="space-y-8"
              >
                <div className="space-y-4">
                  <div className="flex items-center justify-between text-[11px] font-black uppercase tracking-[0.3em] text-slate-400 px-1">
                    <span>{t('landing.workspace.skillsSection')}</span>
                    <span className="text-brand-600">
                      {mySkills.length} {t('landing.workspace.pcs')}
                    </span>
                  </div>
                  {mySkills.map((skill) => (
                    <div
                      key={skill.id}
                      onClick={() => navigate({ to: `/space/${skill.namespace}/${skill.slug}` })}
                      className="flex items-center justify-between p-4 rounded-2xl bg-white/70 hover:bg-white border border-transparent hover:border-brand-200 transition-all cursor-pointer group shadow-sm"
                    >
                      <span className="text-sm font-bold text-slate-700 group-hover:text-brand-600 uppercase tracking-tight">
                        {skill.displayName}
                      </span>
                      <ChevronRight size={16} className="text-slate-300 group-hover:text-brand-500" />
                    </div>
                  ))}
                </div>

                <div className="space-y-4">
                  <div className="flex items-center justify-between text-[11px] font-black uppercase tracking-[0.3em] text-slate-400 px-1">
                    <span>{t('landing.workspace.agentsSection')}</span>
                    <span className="text-brand-600">
                      {myAgents.length} {t('landing.workspace.pcs')}
                    </span>
                  </div>
                  {myAgents.map((agent) => (
                    <div
                      key={agent.name}
                      onClick={() => navigate({ to: `/agents/${agent.namespace ?? 'global'}/${agent.name}` })}
                      className="flex items-center justify-between p-4 rounded-2xl bg-white/70 hover:bg-white border border-transparent hover:border-brand-200 transition-all cursor-pointer group shadow-sm"
                    >
                      <span className="text-sm font-bold text-slate-700 group-hover:text-brand-600 uppercase tracking-tight">
                        {agent.name}
                      </span>
                      <ChevronRight size={16} className="text-slate-300 group-hover:text-brand-500" />
                    </div>
                  ))}
                </div>

                <button
                  onClick={() => navigate({ to: '/dashboard' })}
                  className="w-full py-5 rounded-2xl bg-white text-slate-800 text-[11px] font-black uppercase tracking-[0.3em] hover:bg-brand-600 hover:text-white transition-all shadow-sm border border-slate-100"
                >
                  {t('landing.workspace.openPanel')}
                </button>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      </div>
    </section>
  )
}
```

Note on `useMySkills` 第二参数:check current signature with `cd /Users/lydoc/projectscoding/skillhub/web && grep -n "export function useMySkills" src/shared/hooks/use-user-queries.ts`. If second arg `enabled` doesn't exist, drop it and rely on `user` check internally. The implementer may need to look at the existing hook to wire correctly. If the hook doesn't accept an enabled flag, wrap in `isLoggedIn ? mySkillsPage : undefined` at the call site.

- [ ] **Step 6.2: Typecheck**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm tsc --noEmit 2>&1 | tail -10
```

- [ ] **Step 6.3: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/shared/components/landing-workspace.tsx && git commit -m "feat(web): add LandingWorkspace guest/auth two-state right rail"
```

---

## Task 7: LandingFooter

**Files:**
- Create: `web/src/shared/components/landing-footer.tsx`
- Create: `web/src/shared/components/landing-footer.test.tsx`

视觉照搬 prototype line 372-424。注意品牌段只显示英文 "WeaveHub"(无中文)。

- [ ] **Step 7.1: Write the failing test**

Create `web/src/shared/components/landing-footer.test.tsx`:

```tsx
// @vitest-environment jsdom
import { describe, expect, it, afterEach } from 'vitest'
import { render, cleanup } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'
import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'
import { LandingFooter } from './landing-footer'

i18n.use(initReactI18next).init({
  lng: 'zh',
  resources: {
    zh: {
      translation: {
        landing: {
          footer: {
            tagline: 'WeaveHub 知连:连接每一种智力片段。',
            documentation: 'Documentation',
            community: 'Community',
            links: {
              apiReferences: 'API References',
              cloudSync: 'Cloud Sync',
              security: 'Security',
              integration: 'Integration',
              openSource: 'Open Source',
              forum: 'Forum',
              privacy: 'Privacy',
              support: 'Support',
            },
            copyright: '© 2026 WEAVEHUB INTELLIGENCE.',
            version: 'VER 0.1.0',
            networkReady: 'NETWORK READY',
          },
        },
      },
    },
  },
})

afterEach(() => cleanup())

describe('LandingFooter', () => {
  it('renders WeaveHub brand (English-only) without 知连 prefix', () => {
    const { container } = render(
      <I18nextProvider i18n={i18n}>
        <LandingFooter />
      </I18nextProvider>,
    )
    const text = container.textContent ?? ''
    expect(text).toContain('WeaveHub')
    // Brand block specifically should not show "知连 WeaveHub" — only "WeaveHub" alone.
    // The tagline can mention 知连; we check the brand heading element directly.
    const brandHeading = container.querySelector('[data-testid="footer-brand"]')
    expect(brandHeading?.textContent).toContain('WeaveHub')
    expect(brandHeading?.textContent).not.toContain('知连')
  })

  it('renders Documentation and Community columns with 4 links each', () => {
    const { container } = render(
      <I18nextProvider i18n={i18n}>
        <LandingFooter />
      </I18nextProvider>,
    )
    expect(container.textContent).toContain('Documentation')
    expect(container.textContent).toContain('Community')
    expect(container.textContent).toContain('API References')
    expect(container.textContent).toContain('Open Source')
    expect(container.textContent).toContain('NETWORK READY')
  })
})
```

- [ ] **Step 7.2: Run test, confirm failures**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/shared/components/landing-footer.test.tsx 2>&1 | tail -10
```

Expected: 2 fail (module not found).

- [ ] **Step 7.3: Implement**

Create `web/src/shared/components/landing-footer.tsx`:

```tsx
import { useTranslation } from 'react-i18next'
import { Link } from '@tanstack/react-router'
import { Layout, Zap, Grid, Sparkles } from 'lucide-react'

/**
 * Landing page footer. Visual mirrors prototype lines 372-424.
 *
 * Brand block deliberately renders just "WeaveHub" (English only) — different
 * from the header's "知连 WeaveHub" — per design spec §3.7 locale-aware brand.
 */
export function LandingFooter() {
  const { t } = useTranslation()

  return (
    <footer className="mt-40 border-t border-brand-50/50 bg-white/40 backdrop-blur-md pt-20 pb-10 px-6">
      <div className="max-w-7xl mx-auto flex flex-col md:flex-row justify-between items-start gap-16 mb-20">
        <div className="space-y-8 max-w-sm">
          <div data-testid="footer-brand" className="text-3xl font-black brand-gradient flex items-center gap-3">
            <Layout size={32} className="text-brand-600" />
            WeaveHub
          </div>
          <p className="text-slate-400 text-lg leading-relaxed font-medium">
            {t('landing.footer.tagline')}
          </p>
          <div className="flex items-center gap-6">
            {[Zap, Grid, Sparkles, Layout].map((Icon, i) => (
              <div
                key={i}
                className="text-slate-400 hover:text-brand-600 transition-all cursor-pointer hover:scale-110"
              >
                <Icon size={24} />
              </div>
            ))}
          </div>
        </div>

        <div className="grid grid-cols-2 md:grid-cols-2 gap-20">
          <div className="space-y-8 font-black uppercase tracking-[0.2em] text-[11px]">
            <h4 className="text-slate-900 border-b-2 border-brand-500 w-fit pb-1">
              {t('landing.footer.documentation')}
            </h4>
            <ul className="space-y-4 text-slate-400">
              <li><a href="#" className="hover:text-brand-600 transition-colors">{t('landing.footer.links.apiReferences')}</a></li>
              <li><a href="#" className="hover:text-brand-600 transition-colors">{t('landing.footer.links.cloudSync')}</a></li>
              <li><Link to="/privacy" className="hover:text-brand-600 transition-colors">{t('landing.footer.links.security')}</Link></li>
              <li><a href="#" className="hover:text-brand-600 transition-colors">{t('landing.footer.links.integration')}</a></li>
            </ul>
          </div>
          <div className="space-y-8 font-black uppercase tracking-[0.2em] text-[11px]">
            <h4 className="text-slate-900 border-b-2 border-brand-500 w-fit pb-1">
              {t('landing.footer.community')}
            </h4>
            <ul className="space-y-4 text-slate-400">
              <li><a href="#" className="hover:text-brand-600 transition-colors">{t('landing.footer.links.openSource')}</a></li>
              <li><a href="#" className="hover:text-brand-600 transition-colors">{t('landing.footer.links.forum')}</a></li>
              <li><Link to="/privacy" className="hover:text-brand-600 transition-colors">{t('landing.footer.links.privacy')}</Link></li>
              <li><a href="#" className="hover:text-brand-600 transition-colors">{t('landing.footer.links.support')}</a></li>
            </ul>
          </div>
        </div>
      </div>

      <div className="max-w-7xl mx-auto flex flex-col md:flex-row justify-between items-center gap-6 pt-10 border-t border-brand-50/30">
        <span className="text-[10px] font-mono font-bold text-slate-300 uppercase tracking-[0.5em]">
          {t('landing.footer.copyright')}
        </span>
        <div className="flex items-center gap-8">
          <span className="text-[10px] font-mono font-bold text-slate-300 uppercase tracking-[0.2em]">
            {t('landing.footer.version')}
          </span>
          <div className="flex items-center gap-2.5 px-4 py-1.5 rounded-full bg-white text-[11px] font-black text-brand-600 uppercase tracking-widest border border-brand-50 shadow-sm">
            <div className="w-2 h-2 rounded-full bg-brand-500 animate-ping"></div>
            {t('landing.footer.networkReady')}
          </div>
        </div>
      </div>
    </footer>
  )
}
```

- [ ] **Step 7.4: Run test**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/shared/components/landing-footer.test.tsx 2>&1 | tail -10
```

Expected: 2 pass.

- [ ] **Step 7.5: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/shared/components/landing-footer.tsx web/src/shared/components/landing-footer.test.tsx && git commit -m "feat(web): add LandingFooter with WeaveHub brand and link columns"
```

---

## Task 8: Rewrite landing.tsx (4-section IA)

**Files:**
- Modify: `web/src/pages/landing.tsx`
- Modify: `web/src/pages/landing.test.tsx`

整体重写 landing.tsx,从 9 段缩到 4 段。视觉容器照搬 prototype line 109-426。

- [ ] **Step 8.1: Replace landing.tsx**

Overwrite `web/src/pages/landing.tsx` with:

```tsx
import { LandingHero } from '@/shared/components/landing-hero'
import { LandingHotSection } from '@/shared/components/landing-hot-section'
import { LandingRecentSection } from '@/shared/components/landing-recent-section'
import { LandingWorkspace } from '@/shared/components/landing-workspace'
import { LandingFooter } from '@/shared/components/landing-footer'

/**
 * 知连 WeaveHub landing page — 4-section IA per spec §3.1.
 *
 * Visual mirrors prototype web/weavehub---知连/src/App.tsx layout.
 */
export function LandingPage() {
  return (
    <>
      <main className="flex-1 max-w-7xl mx-auto w-full px-6 py-12 md:py-20 space-y-24">
        <LandingHero />
        <LandingHotSection />
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-10">
          <LandingRecentSection />
          <LandingWorkspace />
        </div>
      </main>
      <LandingFooter />
    </>
  )
}
```

- [ ] **Step 8.2: Replace landing.test.tsx**

Overwrite `web/src/pages/landing.test.tsx`:

```tsx
// @vitest-environment jsdom
import { describe, expect, it, afterEach, vi } from 'vitest'
import { render, cleanup } from '@testing-library/react'
import { LandingPage } from './landing'

vi.mock('@/shared/components/landing-hero', () => ({
  LandingHero: () => <div data-testid="hero">hero</div>,
}))
vi.mock('@/shared/components/landing-hot-section', () => ({
  LandingHotSection: () => <div data-testid="hot">hot</div>,
}))
vi.mock('@/shared/components/landing-recent-section', () => ({
  LandingRecentSection: () => <div data-testid="recent">recent</div>,
}))
vi.mock('@/shared/components/landing-workspace', () => ({
  LandingWorkspace: () => <div data-testid="workspace">workspace</div>,
}))
vi.mock('@/shared/components/landing-footer', () => ({
  LandingFooter: () => <div data-testid="footer">footer</div>,
}))

afterEach(() => cleanup())

describe('LandingPage', () => {
  it('renders 4 sections + footer in correct order', () => {
    const { container } = render(<LandingPage />)
    expect(container.textContent).toContain('hero')
    expect(container.textContent).toContain('hot')
    expect(container.textContent).toContain('recent')
    expect(container.textContent).toContain('workspace')
    expect(container.textContent).toContain('footer')
  })
})
```

- [ ] **Step 8.3: Run test**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/pages/landing.test.tsx 2>&1 | tail -10
```

Expected: 1 test passes.

- [ ] **Step 8.4: Run full suite**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -10
```

Expected: many failures coming from the deleted i18n keys (`landing.stats.*`, `landing.channels.*` etc.) referenced by old `landing-channels.tsx` / `popular-agents.tsx` / `landing-quick-start.tsx` and their tests. **That's expected.** Tasks 9-10 will delete those files.

- [ ] **Step 8.5: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/pages/landing.tsx web/src/pages/landing.test.tsx && git commit -m "feat(web): rewrite landing.tsx as weavehub 4-section IA"
```

---

## Task 9: Delete obsolete landing components

**Files:**
- Delete: `web/src/shared/components/landing-channels.tsx`
- Delete: `web/src/shared/components/landing-channels.test.tsx`
- Delete: `web/src/shared/components/popular-agents.tsx`
- Delete: `web/src/shared/components/popular-agents.test.tsx`
- Delete: `web/src/shared/components/landing-quick-start.tsx`
- Delete: `web/src/shared/components/landing-quick-start.test.ts`
- Delete: `web/src/i18n/landing-quick-start-locale.test.ts` (locale-shape test specifically for the deleted i18n quickStart block)

These components were used by the old 9-section landing. Now obsolete.

- [ ] **Step 9.1: Verify no remaining importers**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && grep -rn "landing-channels\|popular-agents\|landing-quick-start" src/ --include="*.tsx" --include="*.ts" 2>&1 | grep -v "shared/components/landing-channels\|shared/components/popular-agents\|shared/components/landing-quick-start"
```

Expected: no output. (All `.tsx`/`.ts` references should only be in the files being deleted themselves.) If anything else imports these — investigate before deleting.

- [ ] **Step 9.2: Delete files**

```bash
cd /Users/lydoc/projectscoding/skillhub && rm web/src/shared/components/landing-channels.tsx web/src/shared/components/landing-channels.test.tsx web/src/shared/components/popular-agents.tsx web/src/shared/components/popular-agents.test.tsx web/src/shared/components/landing-quick-start.tsx web/src/shared/components/landing-quick-start.test.ts
```

- [ ] **Step 9.3: Run tests + typecheck**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -10 && pnpm tsc --noEmit 2>&1 | tail -10
```

Expected: tests pass (overall count drops because we removed test files); typecheck only `registry-skill.tsx`.

- [ ] **Step 9.4: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add -A web/src/shared/components/ && git commit -m "feat(web): delete obsolete landing-channels/popular-agents/landing-quick-start"
```

---

## Task 10: my-weave page (new route)

**Files:**
- Create: `web/src/pages/my-weave.tsx`
- Create: `web/src/pages/my-weave.test.tsx`

简洁双段聚合页:我的技能(复用 `useMySkills`)+ 我的智能体(复用 my-agents 风格但更紧凑)。

- [ ] **Step 10.1: Implement**

Create `web/src/pages/my-weave.tsx`:

```tsx
import { useNavigate } from '@tanstack/react-router'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { useAuth } from '@/features/auth/use-auth'
import { useMySkills } from '@/shared/hooks/use-user-queries'
import { agentsApi, type AgentDto } from '@/api/client'
import { Card } from '@/shared/ui/card'
import { Button } from '@/shared/ui/button'
import { DashboardPageHeader } from '@/shared/components/dashboard-page-header'
import { EmptyState } from '@/shared/components/empty-state'

/**
 * "My Weave" aggregate page — surfaces the user's own skills and agents in one place.
 *
 * Reuses existing dashboard hooks; this is a discovery shell, not a governance page.
 * Governance actions (archive, withdraw, promote) stay on /dashboard/skills.
 */
export function MyWeavePage() {
  const { t } = useTranslation()
  const navigate = useNavigate()
  const { user } = useAuth()

  const { data: skillsPage, isLoading: skillsLoading } = useMySkills({
    page: 0,
    size: 10,
  })

  const { data: agentsList, isLoading: agentsLoading } = useQuery({
    queryKey: ['agents', 'my', user?.userId],
    queryFn: async () => {
      const page = await agentsApi.list({ page: 0, size: 200 })
      return page.items
    },
    enabled: !!user?.userId,
  })

  const myAgents = (agentsList ?? []).filter((a: AgentDto) => a.ownerId === user?.userId)
  const skills = skillsPage?.items ?? []

  return (
    <div className="max-w-5xl mx-auto px-4 sm:px-6 lg:px-8 py-12 space-y-12">
      <DashboardPageHeader
        title={t('myWeave.title', { defaultValue: 'My Weave' })}
        subtitle={t('myWeave.subtitle', { defaultValue: 'Your skills and agents in one place.' })}
      />

      <section>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-bold tracking-tight">
            {t('myWeave.skillsHeading', { defaultValue: 'My skills' })}
          </h2>
          <Button variant="outline" size="sm" onClick={() => navigate({ to: '/dashboard/skills' })}>
            {t('myWeave.viewAll', { defaultValue: 'View all' })}
          </Button>
        </div>
        {skillsLoading ? (
          <p className="text-muted-foreground">{t('agents.loading')}</p>
        ) : skills.length === 0 ? (
          <EmptyState title={t('myWeave.skillsEmpty', { defaultValue: 'No skills yet' })} />
        ) : (
          <div className="space-y-3">
            {skills.slice(0, 5).map((skill) => (
              <Card
                key={skill.id}
                className="p-4 cursor-pointer hover:bg-muted/30 transition-colors"
                onClick={() => navigate({ to: `/space/${skill.namespace}/${skill.slug}` })}
              >
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <div className="font-semibold">{skill.displayName}</div>
                    {skill.summary && (
                      <div className="mt-1 text-sm text-muted-foreground">{skill.summary}</div>
                    )}
                  </div>
                  <span className="text-xs px-2 py-0.5 rounded border bg-secondary/40 text-muted-foreground">
                    @{skill.namespace}
                  </span>
                </div>
              </Card>
            ))}
          </div>
        )}
      </section>

      <section>
        <div className="flex items-center justify-between mb-4">
          <h2 className="text-xl font-bold tracking-tight">
            {t('myWeave.agentsHeading', { defaultValue: 'My agents' })}
          </h2>
          <Button variant="outline" size="sm" onClick={() => navigate({ to: '/dashboard/my-agents' })}>
            {t('myWeave.viewAll', { defaultValue: 'View all' })}
          </Button>
        </div>
        {agentsLoading ? (
          <p className="text-muted-foreground">{t('agents.loading')}</p>
        ) : myAgents.length === 0 ? (
          <EmptyState title={t('myWeave.agentsEmpty', { defaultValue: 'No agents yet' })} />
        ) : (
          <div className="space-y-3">
            {myAgents.slice(0, 5).map((agent) => (
              <Card
                key={agent.id}
                className="p-4 cursor-pointer hover:bg-muted/30 transition-colors"
                onClick={() =>
                  navigate({
                    to: '/agents/$namespace/$slug',
                    params: { namespace: agent.namespace, slug: agent.slug },
                  })
                }
              >
                <div className="flex items-start justify-between gap-4">
                  <div>
                    <div className="font-semibold">{agent.displayName}</div>
                    {agent.description && (
                      <div className="mt-1 text-sm text-muted-foreground">{agent.description}</div>
                    )}
                  </div>
                  <span className="text-xs px-2 py-0.5 rounded border bg-secondary/40 text-muted-foreground">
                    {agent.visibility}
                  </span>
                </div>
              </Card>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
```

- [ ] **Step 10.2: Implement minimal test**

Create `web/src/pages/my-weave.test.tsx`:

```tsx
import { describe, expect, it } from 'vitest'
import * as mod from './my-weave'

describe('MyWeavePage', () => {
  it('exports MyWeavePage', () => {
    expect(typeof mod.MyWeavePage).toBe('function')
  })
})
```

(Following the existing pattern of barrel-export-shape tests for shell pages — see `web/src/pages/dashboard/my-agents.test.tsx`-style tests; we're not introducing query mocking here because the page is a thin shell.)

- [ ] **Step 10.3: Run tests**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/pages/my-weave.test.tsx 2>&1 | tail -5
```

Expected: 1 test passes.

- [ ] **Step 10.4: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/pages/my-weave.tsx web/src/pages/my-weave.test.tsx && git commit -m "feat(web): add /my-weave aggregate page"
```

---

## Task 11: Wire /my-weave route

**Files:**
- Modify: `web/src/app/router.tsx`

- [ ] **Step 11.1: Add lazy import**

In `web/src/app/router.tsx` find the lazy imports section near line 71-73, add **after** `AgentDetailPage`:

```ts
const MyWeavePage = createLazyRouteComponent(() => import('@/pages/my-weave'), 'MyWeavePage')
```

- [ ] **Step 11.2: Add route declaration**

After the `agentDetailRoute` declaration (around line 242-249), add:

```ts
const myWeaveRoute = createRoute({
  getParentRoute: () => rootRoute,
  path: 'my-weave',
  beforeLoad: requireAuth,
  component: MyWeavePage,
})
```

- [ ] **Step 11.3: Add to routeTree**

In the `rootRoute.addChildren([...])` array (around line 479-520), add `myWeaveRoute` next to `agentDetailRoute`:

```ts
  agentDetailRoute,
  myWeaveRoute,
  termsRoute,
```

- [ ] **Step 11.4: Typecheck**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm tsc --noEmit 2>&1 | tail -10
```

Expected: only `registry-skill.tsx`. If any new error mentions `'/my-weave'`, the route registration is incomplete.

- [ ] **Step 11.5: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/app/router.tsx && git commit -m "feat(web): register /my-weave route (auth-gated)"
```

---

## Task 12: layout.tsx — nav reshuffle + brand rename

**Files:**
- Modify: `web/src/app/layout.tsx`

- [ ] **Step 12.1: Replace navItems and brand**

In `web/src/app/layout.tsx`:

Replace the `navItems` array (lines 43-55) with:

```ts
  const navItems: Array<{
    label: string
    to: string
    exact?: boolean
    auth?: boolean
  }> = [
    { label: t('nav.landing'), to: '/', exact: true },
    { label: t('nav.publish'), to: '/dashboard/publish', auth: true },
    { label: t('nav.skills'), to: '/skills' },
    { label: t('nav.agents'), to: '/agents' },
    { label: t('nav.myWeave'), to: '/my-weave', auth: true },
    { label: t('nav.dashboard'), to: '/dashboard', auth: true },
    { label: t('nav.search'), to: '/search' },
  ]
```

(Per spec §3.3: 未登录 home/search; 已登录 home/publish/skills/agents/myWeave/dashboard. The `auth: true` flag handles guest filtering. Search shows for both since unauth users still need a discovery path.)

Replace the brand block (line 76-78) with:

```tsx
        <Link to="/" className="text-xl font-semibold tracking-tight text-brand-gradient">
          {t('nav.brand')}
        </Link>
```

(`nav.brand` is "知连 WeaveHub" in zh, "WeaveHub" in en — see Task 2 i18n keys.)

Also update the footer brand block in the SAME layout.tsx file (lines 141-145) — this footer is shown on **non-landing routes** (landing has its own footer):

```tsx
              <div className="flex items-center gap-2 mb-3">
                <div className="w-9 h-9 rounded-lg flex items-center justify-center text-white text-sm font-bold shadow-sm bg-brand-gradient">
                  W
                </div>
                <span className="text-lg font-bold text-brand-gradient">{t('nav.brand')}</span>
              </div>
```

(Brand initial letter from "S" → "W".)

- [ ] **Step 12.2: Typecheck + run tests**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm tsc --noEmit 2>&1 | tail -10 && pnpm vitest run 2>&1 | tail -10
```

Expected: typecheck clean (registry-skill.tsx only). Tests pass — there's a layout test that may need updating; if so, fix the assertion:

```bash
cd /Users/lydoc/projectscoding/skillhub/web && grep -n "SkillHub\|nav.mySkills" src/app/layout.test.* 2>&1
```

If layout.test asserts on "SkillHub" literal or on the old `nav.mySkills` link, update those assertions to match new structure.

- [ ] **Step 12.3: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/app/layout.tsx web/src/app/layout.test.* 2>/dev/null && git commit -m "feat(web): reshuffle nav, rename brand to WeaveHub via i18n"
```

(`web/src/app/layout.test.*` is added with `2>/dev/null` since the file may not exist; if it exists, the changes get included; if it doesn't, the redirect silently no-ops.)

---

## Task 13: index.html title

**Files:**
- Modify: `web/index.html`

- [ ] **Step 13.1: Edit title**

Use Edit tool. Find:

```html
    <title>SkillHub</title>
```

Replace with:

```html
    <title>知连 WeaveHub</title>
```

- [ ] **Step 13.2: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/index.html && git commit -m "feat(web): update browser tab title to 知连 WeaveHub"
```

---

## Task 14: Final verification + memo

**Files:**
- Modify: `memo/memo.md`

- [ ] **Step 14.1: Final test pass + typecheck + lint**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -5 && pnpm tsc --noEmit 2>&1 | tail -10 && pnpm lint 2>&1 | tail -10
```

Expected:
- Tests: number depends on net new — should be roughly 635 - (deleted tests in Task 9) + (new tests in Tasks 1, 7, 8, 10). Approximately 635 - 3 + 6 + 2 + 1 + 1 = **642ish**. Verify all pass; specific count documented in memo.
- Typecheck: only pre-existing `registry-skill.tsx` errors.
- Lint: same baseline as P0-1a.

If any test fails outside of registry-skill.tsx, **fix before continuing**.

- [ ] **Step 14.2: Append session memo**

Append to `memo/memo.md` (above any closing rule):

```markdown

---

## 2026-04-XX — P0-1b: WeaveHub landing IA + my-weave + nav reshuffle

**Plan:** [docs/plans/2026-04-27-weavehub-landing-ia.md](../docs/plans/2026-04-27-weavehub-landing-ia.md)
**Spec:** [docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md](../docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md) §3 + §4.2
**ADR clause:** [ADR 0003](../docs/adr/0003-fork-scope-and-upstream-boundary.md) §1.2
**Branch:** `main`
**Method:** subagent-driven

### What shipped

End-to-end landing page rewritten from 9-section old structure to weavehub
4-section IA: LandingHero / LandingHotSection / LandingRecentSection +
LandingWorkspace / LandingFooter. New /my-weave route surfaces the user's own
skills and agents. Nav reshuffled per spec §3.3. Brand renamed to "知连
WeaveHub" (zh) / "WeaveHub" (en) via i18n. Browser tab title updated.

Visual className strings mirror prototype web/weavehub---知连/src/App.tsx
1:1 — only mock data was replaced with real `useSearchSkills` + `useAgents` +
`useMySkills` calls, and copy was simplified per the brainstorming "B" decision
(no "全球领先", Hero "持续进化的 AI 能力" + "让团队的技能包和智能体在一起协作").

### Tests

- Web: 635 → ~642 (specific count to be filled in after final run)
- Backend: 460 (unchanged, untouched)
- Typecheck: only pre-existing `registry-skill.tsx` errors
- Lint: unchanged baseline

### Spec divergences (intentional)

To be filled by implementer based on what came up. Common ones expected:

1. `useMySkills` hook signature — if it doesn't accept enabled flag, wrapped at call site.
2. relativeTime helper — written inline rather than reusing existing date utils.
3. Route position — myWeaveRoute placed next to agentDetailRoute for clarity.

### Known gaps for future work

1. **Footer 双轨**: Layout.tsx still has the old SkillHub-style footer for non-landing routes; landing uses LandingFooter. Future cleanup could unify if desired.
2. **`/skills` 路由** is in the nav as "Skills"; it currently routes to HomePage component. May want a dedicated skills marketplace page later.
3. **WorkspaceCard "open control panel"** routes to /dashboard — consider whether it should route to /my-weave instead (more discovery-oriented).
4. **Hot/Recent section data source** uses skill `useSearchSkills(downloads/newest)` + agent `useAgents`. When agent search lands (P0-2), wire it through here too.
5. **Footer links** — API References, Cloud Sync, etc. point to `#`. They need real targets; treat as a placeholder until docs/marketing pages exist.
```

(Replace `XX` with the actual day.)

- [ ] **Step 14.3: Commit memo**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add memo/memo.md && git commit -m "docs(memo): record P0-1b WeaveHub landing IA session"
```

- [ ] **Step 14.4: Verify final state**

```bash
cd /Users/lydoc/projectscoding/skillhub && git log --oneline 0581e80d..HEAD | head -20
```

Should show the 14 commits from this plan in order.

---

## Risk register

| Risk | Mitigation |
|---|---|
| `useMySkills` hook signature mismatch | Task 6 step 6.1 has explicit grep instruction; falls back to inline filtering. |
| `useSearchSkills` returns more than expected items, blowing the 3-item limit | All consumers `.slice(0, 3)`; harmless overshoot. |
| Translation keys clash with existing `landing.*` namespace consumers | Task 8 deletes the only consumers (the 3 obsolete components); after Task 9 there should be no remaining references to `landing.stats.*` etc. |
| `motion/react` SSR — TanStack Router doesn't SSR by default in this fork, no risk. |
| Layout's existing footer still shows SkillHub on non-landing pages briefly during this PR | Brand rename in Task 12 fixes that side too. |
| `/my-weave` page initial empty state when user has no skills/agents | EmptyState component used; tested implicitly by my-weave.test.tsx (renders without throw). |
| `agentsApi.list({ page, size })` may not allow size=200 | Check existing usage; my-agents.tsx already does this — same precedent. |
| Tasks 8-9 leave broken state intermediate (deleted i18n keys but not yet deleted components) | Tasks 8-9 are sequential atomic commits; `pnpm vitest run` between them may show transient failures. **Acceptable — final state is clean.** |
| ResourceCard hard-coded `to: resource.href` may not be a valid TanStack route | The href strings (`/space/...`, `/agents/...`, `/skills/...`) are all real routes. Verified via router.tsx audit. |

---

## Self-review

Spec coverage:
- ✅ Hero with B copy — Task 3 + i18n Task 2
- ✅ ResourceCard featured + compact — Task 1
- ✅ Hot section (mixed grid) — Task 4
- ✅ Recent section + Workspace — Tasks 5, 6
- ✅ Footer (English brand) — Task 7
- ✅ 4-section landing.tsx — Task 8
- ✅ Delete obsolete components — Task 9
- ✅ /my-weave page + route — Tasks 10, 11
- ✅ Nav reshuffle — Task 12
- ✅ Brand rename — Tasks 2 (i18n), 12 (consumed), 13 (title)
- ✅ Memo — Task 14

Type consistency:
- `ResourceCardData` defined in Task 1, used in Tasks 4, 5
- `relativeTime` defined inline in both Task 4 and Task 5 (slight DRY violation but each implementation slightly different — Task 4 uses "ago" suffix, Task 5 uses bare units like "2d"). Acceptable.

Placeholder scan:
- No "TBD"/"TODO"/"add validation" patterns.
- `defaultValue` props on `t()` calls in Task 10 are intentional fallbacks (those `myWeave.*` keys are added in Task 14 if needed; checked — not added yet, so defaults will render).

**Missing**: `myWeave.*` i18n keys are not added in Task 2. They'll render via `defaultValue` in English on both locales. **Fix inline:** add to Task 2's en.json and zh.json blocks. (Updating now.)

---

## Plan complete

Save to `docs/plans/2026-04-27-weavehub-landing-ia.md`.

Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.
2. **Inline Execution** — Execute tasks in this session with checkpoints.

Which approach?

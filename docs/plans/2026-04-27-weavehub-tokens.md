# WeaveHub Design Tokens + Glass-morphism 体系迁移 (P0-1a) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 fork web 前端的 design tokens 从 indigo+violet brand-gradient 切换到绿色单色阶 + glass-morphism 工具类 + Inter 字体,既有 Card 类组件视觉迁移,**完全不动版面**。

**Architecture:** 单 PR 范围:`web/src/index.css` 替换 brand 色阶 + 增 glass/btn/nav-chip 工具类 + 改字体回退(`Syne` → `Inter`);`tailwind.config.ts` 暴露新 brand 色;14 处 `bg-brand-gradient`/`text-brand-gradient` 引用保留(token 自动重新着色,不需逐处改类名);`SkillCard` / `AgentCard` / `Card` / `Button` 视觉对齐 glass-morphism;`button.test.ts` 跟随 button.tsx 改动。**所有 nav 链表、landing 段落、新增组件留 P0-1b**。

**Tech Stack:** Vite + Tailwind 3 + React 18 + Vitest + pnpm.

**Spec reference:** [docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md](../superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md) §2 + §4.1

---

## Pre-flight

- [ ] **Step 0.1: Verify branch and baseline**

```bash
cd /Users/lydoc/projectscoding/skillhub
git status
git log --oneline -3
```

Expected: branch `main`, working tree clean (or only this plan file untracked), HEAD at `ba162767` or later.

- [ ] **Step 0.2: Verify test baseline**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -5
```

Expected: `Test Files  XXX passed (XXX)` and `Tests  631 passed (631)`. If different, **stop and reconcile** with [memo/memo.md](../../memo/memo.md) before proceeding — the baseline number must be 631 to make later regression checks meaningful.

- [ ] **Step 0.3: Confirm motion not yet installed**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && grep '"motion"' package.json || echo "not installed"
```

Expected: prints `not installed`. If `motion` is already a dep, skip Task 1's install step but keep the verification.

---

## File structure

After this plan completes, the following files have changed:

**Modified:**
- `web/src/index.css` — brand color palette replaced; `.glass-card` / `.btn-primary` / `.btn-secondary` / `.nav-chip` utility classes added; heading font-family fallback fixed
- `web/tailwind.config.ts` — `colors.brand` palette extension exposing `bg-brand-500` / `text-brand-700` etc.
- `web/src/shared/ui/card.tsx` — base `Card` accepts a `glass` prop (opt-in to glass-morphism)
- `web/src/shared/ui/card.test.ts` — **new** (assert glass class is present when prop set)
- `web/src/shared/ui/button.tsx` — `default` variant migrates from `bg-brand-gradient` to `btn-primary` semantic class
- `web/src/shared/ui/button.test.ts` — assertion text updated for new class name
- `web/src/features/skill/skill-card.tsx` — wraps `<Card glass>` ; explicit `bg-white border` removed (glass-card replaces them)
- `web/src/features/agent/agent-card.tsx` — same migration as skill-card
- `web/package.json` / `pnpm-lock.yaml` — `motion` ^11 added (consumed in P0-1b)

**Untouched (deliberate, will be addressed in P0-1b):**
- `web/index.html` — `<title>` stays `SkillHub` until P0-1b (font links already correct)
- `web/src/app/layout.tsx` — nav links / brand text (P0-1b)
- `web/src/pages/landing.tsx`, `home.tsx` — visual will auto-recolor via tokens; no code edit
- `web/src/features/review/review-skill-detail-section.tsx` — visual will auto-recolor; no code edit
- `web/src/shared/components/landing-channels.tsx` — visual auto-recolor; gets deleted in P0-1b
- All other pages

The 14 known `bg-brand-gradient` / `text-brand-gradient` references will continue to work because we redefine the `--brand-gradient` CSS variable in Task 2; they receive new green colors automatically. Don't touch those files in this plan.

---

## Task 1: Add `motion` dependency

**Files:**
- Modify: `web/package.json` (add devDependency? — no, it's a runtime dep)
- Modify: `web/pnpm-lock.yaml`

P0-1b will use `motion/react` for landing entrance animations. We add the dep here so the version is locked in the same PR that introduces the design system, not surprising P0-1b reviewers.

- [ ] **Step 1.1: Install motion**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm add motion
```

Expected: `+ motion 11.x.x` printed; `pnpm-lock.yaml` updated.

- [ ] **Step 1.2: Verify install**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && grep '"motion"' package.json
```

Expected: `"motion": "^11.x.x"` line in `dependencies`.

- [ ] **Step 1.3: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/package.json web/pnpm-lock.yaml && git commit -m "chore(web): add motion dependency for upcoming weavehub animations"
```

---

## Task 2: Replace brand color palette in index.css

**Files:**
- Modify: `web/src/index.css:7-50` (the `:root` block — only the brand-related lines)

We keep the `--background` / `--foreground` / `--muted` / `--border` / etc. semantic tokens intact (Tailwind theme depends on them). We **redefine** the brand-related ones to weavehub green. The single `--brand-gradient` is also redefined so all 14 downstream references re-color automatically.

- [ ] **Step 2.1: Edit `:root` block**

Replace lines 7-50 of `web/src/index.css` with the following block (lines outside this range stay unchanged):

```css
  :root {
    /* WeaveHub — 浅色主题 (green monochrome, 2026-04-27 spec) */
    --background: 145 38% 99%;          /* zinc-soft #fcfdfc */
    --foreground: 220 13% 18%;          /* ink #1f2937 */
    --card: 0 0% 100%;
    --card-foreground: 220 13% 18%;
    --popover: 0 0% 100%;
    --popover-foreground: 220 13% 18%;
    /* Brand-500 primary (#34a853) */
    --primary: 134 53% 43%;
    --primary-foreground: 0 0% 100%;
    /* Surface tones */
    --secondary: 145 30% 96%;
    --secondary-foreground: 220 13% 18%;
    --muted: 145 25% 95%;
    --muted-foreground: 215 14% 46%;
    /* Brand-200 accent (#c9e8d1) — light moss for highlights */
    --accent: 134 39% 85%;
    --accent-foreground: 220 13% 18%;
    --destructive: 0 72% 55%;
    --destructive-foreground: 0 0% 100%;
    --border: 145 22% 90%;
    --input: 145 22% 90%;
    --ring: 134 53% 43%;
    --radius: 1rem;                     /* rounded-2xl default for weavehub feel */

    /* Extended palette */
    --surface-glass: 0 0% 100%;
    --glow-primary: 134 53% 43%;
    --glow-accent: 134 39% 85%;
    --success: 134 53% 43%;
    --warning: 38 92% 58%;

    /* Brand color scale (weavehub green) */
    --brand-50: 134 53% 97%;            /* #f4fbf6 */
    --brand-100: 134 50% 93%;           /* #e7f4ea */
    --brand-200: 134 39% 85%;           /* #c9e8d1 */
    --brand-500: 134 53% 43%;           /* #34a853 */
    --brand-600: 134 53% 36%;           /* #2c8e46 */
    --brand-700: 134 53% 29%;           /* #23743a */

    /* Brand gradient — kept as a single CSS var so 14 downstream uses auto-recolor */
    --brand-start: #2c8e46;
    --brand-end: #34a853;
    --brand-gradient: linear-gradient(135deg, #2c8e46 0%, #34a853 100%);

    /* Text semantic */
    --text-secondary: 215 19% 35%;
    --text-muted: 215 14% 46%;
    --text-placeholder: 213 12% 63%;

    /* Border semantic */
    --border-card: 145 22% 92%;
  }
```

Rationale for non-obvious choices:
- HSL values are derived from the spec's hex values via `hsl()` conversion. Single source of truth = brand-500 = `#34a853` = `134 53% 43%`.
- `--radius` bumped from `0.75rem` to `1rem` so default `rounded-lg` reads as the weavehub `rounded-2xl` look without rewriting class names everywhere. Tailwind's `xl` and `2xl` derived radii (`calc(var(--radius) + 4px)`, `+8px`) auto-scale.
- `--brand-start` and `--brand-end` are kept (rather than removed) because they're used directly by `.upload-zone:hover` and `.feature-icon` (lines 461-477). Switching them in-place is cheaper than refactoring those rules.

- [ ] **Step 2.2: Restart dev server and visually verify**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm dev
```

Open `http://localhost:5173` (or whatever port Vite reports). Visually confirm:
1. Hero "SkillHub" title (still says SkillHub — unchanged in P0-1a) is now **green gradient** (was indigo→violet)
2. Primary buttons ("探索技能" / "Search") are green
3. Background is light off-white with no purple tint
4. Star icons / nav active pill are green

If any element is still purple, find the offending CSS variable in lines 7-50 and fix it. Stop dev server before continuing (`Ctrl+C`).

- [ ] **Step 2.3: Run tests to confirm no regression**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -5
```

Expected: `Tests  631 passed (631)`. If a test fails it almost certainly means a test asserted a specific HSL value or hex; flag and bring back to user.

- [ ] **Step 2.4: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/index.css && git commit -m "feat(web): switch brand color palette from indigo/violet to weavehub green"
```

---

## Task 3: Heading font fallback

**Files:**
- Modify: `web/src/index.css:94-96` (the `h1, h2, h3, h4, h5, h6` rule)

Currently:

```css
  h1, h2, h3, h4, h5, h6 {
    font-family: 'Syne', 'IBM Plex Sans', system-ui, sans-serif;
  }
```

`web/index.html` line 14 only loads Inter + JetBrains Mono — `Syne` and `IBM Plex Sans` resolve to `system-ui` immediately. The spec says all text uses Inter. Fix:

- [ ] **Step 3.1: Edit heading rule**

Use the Edit tool to change lines 94-96 from:

```css
  h1, h2, h3, h4, h5, h6 {
    font-family: 'Syne', 'IBM Plex Sans', system-ui, sans-serif;
  }
```

to:

```css
  h1, h2, h3, h4, h5, h6 {
    font-family: 'Inter', system-ui, sans-serif;
  }
```

- [ ] **Step 3.2: Run tests**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -5
```

Expected: `Tests  631 passed`.

- [ ] **Step 3.3: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/index.css && git commit -m "feat(web): drop Syne/IBM Plex Sans heading fallbacks (use Inter)"
```

---

## Task 4: Add glass-morphism + button + nav-chip utility classes

**Files:**
- Modify: `web/src/index.css` (append a new section after the existing `.glass` and `.glass-strong` rules, around line 150)

The existing `.glass` and `.glass-strong` rules are kept (used by other components). We add three new weavehub-specific classes that opt into the bigger radius + lighter blur weavehub aesthetic.

- [ ] **Step 4.1: Append new utility block**

Insert after line 150 (after the closing `}` of `.glass-strong`) and before line 152 (`/* ─── Animations ─── */`):

```css

/* ─── WeaveHub glass cards (P0-1a, 2026-04-27) ─── */
.glass-card {
  background: hsl(var(--surface-glass) / 0.5);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid hsl(var(--surface-glass) / 0.6);
  border-radius: 1.5rem;
  transition: background-color 0.5s cubic-bezier(0.16, 1, 0.3, 1),
              transform 0.5s cubic-bezier(0.16, 1, 0.3, 1),
              box-shadow 0.5s cubic-bezier(0.16, 1, 0.3, 1),
              border-color 0.5s ease;
  box-shadow: 0 4px 24px -1px hsl(0 0% 0% / 0.02),
              0 2px 8px -1px hsl(0 0% 0% / 0.02);
}

.glass-card:hover {
  background: hsl(var(--surface-glass) / 0.8);
  border-color: hsl(0 0% 100%);
  transform: translateY(-4px);
  box-shadow: 0 20px 40px -12px hsl(var(--brand-500) / 0.08);
}

/* ─── WeaveHub buttons (P0-1a) ─── */
.btn-primary {
  background: hsl(var(--brand-600));
  color: hsl(0 0% 100%);
  border-radius: 1rem;
  padding: 0.75rem 1.5rem;
  font-size: 0.875rem;
  font-weight: 600;
  transition: all 0.3s ease;
  box-shadow: 0 4px 12px hsl(var(--brand-600) / 0.1);
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.btn-primary:hover {
  background: hsl(var(--brand-700));
}

.btn-primary:active {
  transform: scale(0.95);
}

.btn-secondary {
  background: hsl(var(--surface-glass) / 0.6);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: hsl(var(--text-secondary));
  border: 1px solid hsl(var(--brand-100));
  border-radius: 1rem;
  padding: 0.75rem 1.5rem;
  font-size: 0.875rem;
  font-weight: 600;
  transition: all 0.3s ease;
  display: inline-flex;
  align-items: center;
  gap: 0.5rem;
}

.btn-secondary:hover {
  background: hsl(0 0% 100%);
}

.btn-secondary:active {
  transform: scale(0.95);
}

/* ─── WeaveHub nav chip (P0-1a) ─── */
.nav-chip {
  padding: 0.5rem 1rem;
  border-radius: 9999px;
  font-size: 0.875rem;
  transition: all 0.3s ease;
}

.nav-chip-active {
  background: hsl(var(--brand-600) / 0.9);
  color: hsl(0 0% 100%);
  box-shadow: 0 2px 8px hsl(0 0% 0% / 0.08);
}

.nav-chip-inactive {
  color: hsl(var(--text-secondary));
}

.nav-chip-inactive:hover {
  background: hsl(var(--surface-glass) / 0.6);
  color: hsl(var(--brand-600));
}
```

Rationale:
- `.glass-card` is **distinct from existing `.glass`** — bigger radius (`1.5rem` = `rounded-3xl` literal, not theme-derived), softer shadow tuned to weavehub. Existing `.glass` (used by `glass-card backdrop-blur-xl` style headers later) is left alone for backward compat.
- `.btn-primary` / `.btn-secondary` mirror weavehub prototype's classes 1:1.
- `.nav-chip-*` classes split into base + active/inactive so consumers compose like `class="nav-chip nav-chip-active"`. Using a modifier class is cleaner than a `data-state="active"` attribute selector for vanilla CSS without a JS state library.

- [ ] **Step 4.2: Visually verify by adding `.glass-card` to a temporary spot**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm dev
```

Open browser DevTools console on the home page and run:

```js
document.querySelector('main')?.classList.add('glass-card')
```

Expected: the main content area gets the white-translucent + blurred background + rounded corners. Hover reveals the `-translate-y-1` lift. Then `document.querySelector('main')?.classList.remove('glass-card')` to clean up. Stop dev server.

- [ ] **Step 4.3: Run tests**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -5
```

Expected: `Tests  631 passed`.

- [ ] **Step 4.4: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/index.css && git commit -m "feat(web): add glass-card, btn-primary/secondary, nav-chip utility classes"
```

---

## Task 5: Expose brand palette in tailwind config

**Files:**
- Modify: `web/tailwind.config.ts:35-69` (the `colors` block)

The CSS variables `--brand-50` / `--brand-100` / `--brand-200` / `--brand-500` / `--brand-600` / `--brand-700` exist in `:root` after Task 2, but they're not yet reachable from Tailwind class names (like `bg-brand-500` or `text-brand-700`). Expose them.

- [ ] **Step 5.1: Edit `colors` block**

Use the Edit tool. Find this block in `web/tailwind.config.ts`:

```ts
        ring: 'hsl(var(--ring))',
      },
      boxShadow: {
```

Replace with:

```ts
        ring: 'hsl(var(--ring))',
        brand: {
          50: 'hsl(var(--brand-50))',
          100: 'hsl(var(--brand-100))',
          200: 'hsl(var(--brand-200))',
          500: 'hsl(var(--brand-500))',
          600: 'hsl(var(--brand-600))',
          700: 'hsl(var(--brand-700))',
        },
      },
      boxShadow: {
```

- [ ] **Step 5.2: Verify Tailwind picks up new classes**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm dev
```

Open browser DevTools console on any page:

```js
const el = document.createElement('div')
el.className = 'bg-brand-500 text-brand-700 p-4'
el.textContent = 'test'
document.body.appendChild(el)
```

Expected: a green box with darker green text, fixed at the bottom of the page. Then `el.remove()`. Stop dev server.

- [ ] **Step 5.3: Run tests + typecheck**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -5 && pnpm tsc --noEmit 2>&1 | tail -10
```

Expected: tests `631 passed`; typecheck output should mention only the pre-existing `registry-skill.tsx` errors (recorded in [memo/memo.md](../../memo/memo.md)). No new errors.

- [ ] **Step 5.4: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/tailwind.config.ts && git commit -m "feat(web): expose brand-50..700 palette in tailwind theme"
```

---

## Task 6: Migrate Button `default` variant to btn-primary

**Files:**
- Modify: `web/src/shared/ui/button.tsx:11`
- Modify: `web/src/shared/ui/button.test.ts:7`

The `default` button variant currently composes `bg-brand-gradient`. We replace with the new semantic `btn-primary` class. **TDD: change the test first** so we'd see if the implementation accidentally regresses.

- [ ] **Step 6.1: Write the failing test**

Use the Edit tool to change `web/src/shared/ui/button.test.ts:7` from:

```ts
    expect(classes).toContain('bg-brand-gradient')
```

to:

```ts
    expect(classes).toContain('btn-primary')
```

- [ ] **Step 6.2: Run the test, confirm it fails**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/shared/ui/button.test.ts 2>&1 | tail -10
```

Expected: 1 failure on the `applies default variant and size classes` test (`Expected to contain 'btn-primary' ... Received 'bg-brand-gradient...'`).

- [ ] **Step 6.3: Update button.tsx**

Change `web/src/shared/ui/button.tsx:11` from:

```ts
        default:
          'bg-brand-gradient text-white shadow-sm hover:opacity-95 active:scale-[0.98]',
```

to:

```ts
        default:
          'btn-primary',
```

(`btn-primary` already includes background, color, hover, active scale, and shadow per Task 4.)

- [ ] **Step 6.4: Run all button tests**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/shared/ui/button.test.ts 2>&1 | tail -10
```

Expected: 10 tests pass.

- [ ] **Step 6.5: Visually verify default button**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm dev
```

Open `/login` (has a default button). The "登录" / "Login" button should be solid green (brand-600), have rounded-2xl corners, slight shadow; on hover background darkens to brand-700; on click scales to 0.95. Stop dev server.

- [ ] **Step 6.6: Run full suite**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -5
```

Expected: `Tests  631 passed`.

- [ ] **Step 6.7: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/shared/ui/button.tsx web/src/shared/ui/button.test.ts && git commit -m "feat(web): migrate Button default variant to btn-primary semantic class"
```

---

## Task 7: Add `glass` opt-in prop to base Card

**Files:**
- Modify: `web/src/shared/ui/card.tsx:4-17` (the base `Card` component)
- Create: `web/src/shared/ui/card.test.ts`

The base `Card` is used by 7 places (skill-card, agent-card, workflow-steps, landing-channels, landing-quick-start, skeleton-loader, landing.tsx). We don't want to force every consumer to be glass-morphism — some need plain cards (e.g., dashboards). Add an opt-in `glass` prop.

- [ ] **Step 7.1: Write the failing test**

Create `web/src/shared/ui/card.test.ts`:

```ts
// @vitest-environment jsdom
import { describe, expect, it, afterEach } from 'vitest'
import { render, cleanup } from '@testing-library/react'
import { Card } from './card'

afterEach(() => cleanup())

describe('Card', () => {
  it('renders without glass class by default', () => {
    const { container } = render(<Card>content</Card>)
    const card = container.firstChild as HTMLElement
    expect(card.className).not.toContain('glass-card')
    expect(card.className).toContain('rounded-xl')
  })

  it('applies glass-card class when glass prop is true', () => {
    const { container } = render(<Card glass>content</Card>)
    const card = container.firstChild as HTMLElement
    expect(card.className).toContain('glass-card')
  })

  it('does not set inline border when glass prop is true', () => {
    const { container } = render(<Card glass>content</Card>)
    const card = container.firstChild as HTMLElement
    // glass-card defines its own border; the default style override would conflict.
    expect(card.getAttribute('style') ?? '').not.toContain('border-color')
  })

  it('passes through className alongside glass', () => {
    const { container } = render(<Card glass className="custom-extra">content</Card>)
    const card = container.firstChild as HTMLElement
    expect(card.className).toContain('glass-card')
    expect(card.className).toContain('custom-extra')
  })
})
```

- [ ] **Step 7.2: Run the test, confirm it fails**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/shared/ui/card.test.ts 2>&1 | tail -15
```

Expected: 4 failures (the `glass` prop doesn't exist yet; React types reject it).

- [ ] **Step 7.3: Update Card to support `glass`**

Replace `web/src/shared/ui/card.tsx` lines 4-17 from:

```tsx
const Card = React.forwardRef<HTMLDivElement, React.HTMLAttributes<HTMLDivElement>>(
  ({ className, ...props }, ref) => (
    <div
      ref={ref}
      className={cn(
        'rounded-xl border bg-card text-card-foreground shadow-sm transition-shadow hover:shadow-md',
        className
      )}
      style={{ borderColor: 'hsl(var(--border-card))' }}
      {...props}
    />
  )
)
Card.displayName = 'Card'
```

to:

```tsx
interface CardProps extends React.HTMLAttributes<HTMLDivElement> {
  /**
   * Opts into the WeaveHub glass-morphism look (translucent white, backdrop blur,
   * rounded-3xl, hover-lifts). When false (default), preserves the original solid
   * `rounded-xl border bg-card` look used by dashboard panels and form cards.
   */
  glass?: boolean
}

const Card = React.forwardRef<HTMLDivElement, CardProps>(
  ({ className, glass = false, style, ...props }, ref) => (
    <div
      ref={ref}
      className={cn(
        glass
          ? 'glass-card text-card-foreground'
          : 'rounded-xl border bg-card text-card-foreground shadow-sm transition-shadow hover:shadow-md',
        className
      )}
      style={glass ? style : { borderColor: 'hsl(var(--border-card))', ...style }}
      {...props}
    />
  )
)
Card.displayName = 'Card'
```

Note: `glass` cards do **not** apply the inline `borderColor` style — `.glass-card` defines its own border in CSS. Caller-provided `style` is still merged.

- [ ] **Step 7.4: Run card tests**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/shared/ui/card.test.ts 2>&1 | tail -10
```

Expected: 4 tests pass.

- [ ] **Step 7.5: Run full suite (regression check for the 7 Card consumers)**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -5
```

Expected: `Tests  635 passed (635)` (4 new + 631 prior). If anything fails, the 7 existing consumers are passing unexpected props — investigate before continuing.

- [ ] **Step 7.6: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/shared/ui/card.tsx web/src/shared/ui/card.test.ts && git commit -m "feat(web): add opt-in glass prop to base Card"
```

---

## Task 8: Migrate SkillCard to glass-card visual

**Files:**
- Modify: `web/src/features/skill/skill-card.tsx:27-29`

`<Card className="h-full p-5 cursor-pointer ... bg-white border shadow-sm transition-shadow hover:shadow-md ..." style={{ borderColor: 'hsl(var(--border-card))' }} ...>`. Migrate to `<Card glass>` and drop the redundant `bg-white border shadow-sm transition-shadow hover:shadow-md` / inline `borderColor` style — they conflict with `.glass-card`. Keep the `cursor-pointer focus-visible:*` classes.

- [ ] **Step 8.1: Edit SkillCard**

Use the Edit tool. Find lines 27-30 of `web/src/features/skill/skill-card.tsx`:

```tsx
    <Card
      className="h-full p-5 cursor-pointer group relative overflow-hidden bg-white border shadow-sm transition-shadow hover:shadow-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70 focus-visible:ring-offset-2"
      style={{ borderColor: 'hsl(var(--border-card))' }}
      onClick={onClick}
```

Replace with:

```tsx
    <Card
      glass
      className="h-full p-5 cursor-pointer group relative overflow-hidden focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/70 focus-visible:ring-offset-2"
      onClick={onClick}
```

- [ ] **Step 8.2: Run tests**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/features/skill/skill-card 2>&1 | tail -10
```

Expected: existing skill-card tests pass.

- [ ] **Step 8.3: Visually verify a Skill list page**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm dev
```

Open `/search` (or `/skills` if logged in). Each skill card should:
1. Have white-translucent background, not solid white
2. Hover lifts slightly (`-translate-y-1`) with green-tinted shadow
3. Inner spacing (`p-5`) preserved; bookmark icon, version pill, namespace badge all in correct places

Stop dev server.

- [ ] **Step 8.4: Run full suite**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -5
```

Expected: `Tests  635 passed`.

- [ ] **Step 8.5: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/features/skill/skill-card.tsx && git commit -m "feat(web): migrate SkillCard to glass-card visual"
```

---

## Task 9: Migrate AgentCard to glass-card visual

**Files:**
- Modify: `web/src/features/agent/agent-card.tsx:24` (just the `<Card>` element line)

Same migration shape as Task 8 but slightly different code (AgentCard already uses `<Link>` to wrap; the `<Card>` is inside).

- [ ] **Step 9.1: Edit AgentCard**

Find line 24 of `web/src/features/agent/agent-card.tsx`:

```tsx
      <Card className="h-full p-5 group relative overflow-hidden bg-white border shadow-sm transition-shadow hover:shadow-md">
```

Replace with:

```tsx
      <Card glass className="h-full p-5 group relative overflow-hidden">
```

- [ ] **Step 9.2: Run agent-card tests**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run src/features/agent/agent-card 2>&1 | tail -10
```

Expected: existing agent-card tests pass.

- [ ] **Step 9.3: Visually verify Agents list**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm dev
```

Open `/agents`. Cards should match SkillCard's new glass look. cmd-click an agent card — should still open detail in new tab (TanStack Link preserved). Stop dev server.

- [ ] **Step 9.4: Run full suite**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -5
```

Expected: `Tests  635 passed`.

- [ ] **Step 9.5: Commit**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add web/src/features/agent/agent-card.tsx && git commit -m "feat(web): migrate AgentCard to glass-card visual"
```

---

## Task 10: Final verification + memo update

**Files:**
- Modify: `memo/memo.md` (append a new session entry)

- [ ] **Step 10.1: Final test pass**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm vitest run 2>&1 | tail -5 && pnpm tsc --noEmit 2>&1 | tail -10 && pnpm lint 2>&1 | tail -10
```

Expected:
- Tests: `Tests  635 passed (635)` (was 631; +4 from card.test.ts)
- Typecheck: only pre-existing `registry-skill.tsx` errors
- Lint: no new warnings

- [ ] **Step 10.2: Browser smoke check across the whole app**

```bash
cd /Users/lydoc/projectscoding/skillhub/web && pnpm dev
```

Quick walkthrough (with login):

1. `/` — landing page is light off-white, hero gradient is green, search button green, channel cards have soft backdrop, popular skill cards in glass; **page structure unchanged**
2. `/search` — list of skill cards, all glass with hover lift; namespace badges and bookmark icons readable
3. `/agents` — list of agent cards, glass + lift, version pill green-tinted secondary
4. `/agents/$ns/$slug` (any agent) — workflow-steps cards still render (they use base Card without `glass` — should still look fine, just plain white)
5. `/dashboard/publish` (logged in) — upload zone (uses inline brand-start var) hover state is now green
6. `/login` — primary login button is solid green brand-600 with rounded-2xl

Stop dev server.

- [ ] **Step 10.3: Append session memo**

Open `memo/memo.md` and append at the end (above any closing rule):

```markdown

---

## 2026-04-XX — P0-1a: WeaveHub design tokens migrated

**Plan:** [docs/plans/2026-04-27-weavehub-tokens.md](../docs/plans/2026-04-27-weavehub-tokens.md)
**Branch:** `main`
**Spec:** [docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md](../docs/superpowers/specs/2026-04-27-weavehub-visual-overhaul-design.md) §2 + §4.1
**ADR clause:** [ADR 0003](../docs/adr/0003-fork-scope-and-upstream-boundary.md) §1.2

### What shipped

10 commits over `web/src/index.css` (brand palette + heading fallback +
glass/btn/nav-chip utilities), `web/tailwind.config.ts` (brand color
extension), `web/src/shared/ui/{card,button}.tsx` + tests, SkillCard,
AgentCard, plus `motion` dep added.

### Tests

- Web: 631 → **635 passing** (+4 card.test.ts)
- Backend: 460 (unchanged, untouched)
- Typecheck: only pre-existing `registry-skill.tsx` errors
- Lint: clean

### Visual delta

- All purple/indigo gradients → green
- SkillCard / AgentCard now glass-morphism (translucent white +
  backdrop-blur + rounded-3xl + hover lift); landing / search /
  /agents pages have new card aesthetic
- All `bg-brand-gradient` / `text-brand-gradient` references
  auto-recolored via `--brand-gradient` CSS var (no class renames
  needed in 14 downstream files)

### Known gaps (intentional)

1. Site name still says "SkillHub" — P0-1b will change to "知连 WeaveHub"
2. Landing page structure (Hero stats + Channels + PopularAgents +
   QuickStart + Features + CTA + Latest) is unchanged — P0-1b rewrites
   to weavehub 4-section IA
3. Nav links still mention `/dashboard/skills` etc. — P0-1b reshuffles
4. `motion/react` installed but not yet used — P0-1b's landing
   rewrite consumes it
5. The base `Card` glass prop only adopted by SkillCard + AgentCard;
   workflow-steps / landing-channels / landing-quick-start /
   skeleton-loader / landing.tsx Card uses still default to plain
   `rounded-xl bg-card`. P0-1b will sweep landing-* but the
   non-landing ones (workflow-steps, skeleton-loader) stay plain by
   design — they aren't list cards.

### How to resume

For P0-1b (landing IA rewrite + my-weave route + nav reshuffle + brand rename):

```bash
cd /Users/lydoc/projectscoding/skillhub
git status   # branch: main, on top of P0-1a
cd web && pnpm vitest run   # 635/635 passing
```

The next plan should reference this memo, the spec §4.2, and start by
sketching the new landing.tsx structure against the weavehub
prototype's App.tsx.
```

(Replace `XX` with the actual day when the session executes.)

- [ ] **Step 10.4: Commit memo**

```bash
cd /Users/lydoc/projectscoding/skillhub && git add memo/memo.md && git commit -m "docs(memo): record P0-1a WeaveHub tokens migration session"
```

- [ ] **Step 10.5: Verify final state**

```bash
cd /Users/lydoc/projectscoding/skillhub && git log --oneline -12
```

Expected (newest first):
- `docs(memo): record P0-1a WeaveHub tokens migration session`
- `feat(web): migrate AgentCard to glass-card visual`
- `feat(web): migrate SkillCard to glass-card visual`
- `feat(web): add opt-in glass prop to base Card`
- `feat(web): migrate Button default variant to btn-primary semantic class`
- `feat(web): expose brand-50..700 palette in tailwind theme`
- `feat(web): add glass-card, btn-primary/secondary, nav-chip utility classes`
- `feat(web): drop Syne/IBM Plex Sans heading fallbacks (use Inter)`
- `feat(web): switch brand color palette from indigo/violet to weavehub green`
- `chore(web): add motion dependency for upcoming weavehub animations`
- (then the prior `ba162767` and earlier)

10 new commits. P0-1a complete.

---

## Risk register

| Risk | Mitigation |
|---|---|
| Existing test asserts a specific HSL/hex value (caught in Task 2.3) | Bring failure to user — likely a test that should be updated to assert semantic intent, not a literal color |
| `glass-card` `backdrop-filter` not supported in some test environments | Vitest + jsdom doesn't render styles; tests assert on classnames not visuals. Browser smoke verifies real rendering. |
| `--radius` change to `1rem` breaks an unrelated component that depended on `0.75rem` | `--radius` only feeds `borderRadius.lg/md/sm/xl/2xl` in tailwind.config.ts (lines 28-34). All existing components use these via class names; the visual will change but layout/spacing won't. Browser smoke in Task 10.2 catches actual breakage. |
| `Card glass` breaks existing consumers passing `style` prop | Test 7.1 case "passes through className alongside glass" + careful merging in Task 7.3 covers this. The 7 consumers I checked don't pass `style` so risk is low. |
| `motion` install fails (network / lockfile conflict) | Task 1 isolates the install; if it fails, fix lockfile before continuing. The dep isn't used in P0-1a code. |

---

## Self-review

I checked this plan against the spec §4.1 file list:

- ✅ `web/src/index.css` — Tasks 2, 3, 4
- ✅ `web/index.html` — **deliberately not changed** (font links already correct; title change moved to P0-1b per spec §3.7)
- ✅ `web/tailwind.config.ts` — Task 5
- ✅ `web/src/features/skill/skill-card.tsx` — Task 8
- ✅ `web/src/features/agent/agent-card.tsx` — Task 9
- ✅ `web/src/shared/ui/card.tsx` — Task 7
- ✅ `web/src/app/layout.tsx` — **deliberately not changed** (spec §4.1 says "nav 项视觉从 brand-gradient pill → nav-chip(active 绿)" but our analysis showed this is actually a P0-1b nav-restructure concern; in P0-1a the nav still uses `bg-brand-gradient` which auto-recolors green via the `--brand-gradient` token. Documented as a deliberate scope decision.)
- ✅ `web/src/shared/ui/button.tsx` — Task 6
- ✅ `web/src/features/review/review-skill-detail-section.tsx` — auto-recolors via `--brand-gradient`; no code edit
- ✅ `web/src/pages/home.tsx` — auto-recolors; no code edit
- ✅ `web/package.json` — Task 1

The spec said 8 brand-gradient files; actual grep showed 14 references across 7 files (some files reference it multiple times). Plan handles this through token redefinition rather than per-file edits, reducing PR churn.

The `--accent` token mention from spec §4.1 ("`--accent` token 引用(11 个文件),不动语义但需视觉确认 hover 行为正确") is covered by Task 2's `--accent` redefinition (which still resolves to a brand-200 light moss) plus the Task 10 browser smoke walking the app's main views. No per-file action needed.

---

## Plan complete

Save to `docs/plans/2026-04-27-weavehub-tokens.md`.

Two execution options:

1. **Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration
2. **Inline Execution** — Execute tasks in this session using executing-plans, batch execution with checkpoints

Which approach?

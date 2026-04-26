# 错误教训记录

此文件记录走弯路的教训，只追加不删除。

---

## 2026-04-26 — web/ 用 pnpm 而非 npm

**症状**: 在 `web/` 跑 `npm install` 报 `Cannot read properties of null (reading 'matches')`（npm arborist crash）。

**原因**: 项目锁文件是 `pnpm-lock.yaml`（pnpm 10.x），不是 npm。Plan 文档里的 `npm install / npm test / npx tsc` 命令是写错的；该项目的实际包管理器是 pnpm。

**规则**: 在 `web/` 子项目里执行依赖安装/测试/构建，命令前缀一律换成 pnpm：
- `npm install` → `pnpm install`
- `npm test -- --run <args>` → `pnpm vitest run <args>`
- `npx tsc --noEmit` → `pnpm tsc --noEmit`（或 `pnpm exec tsc --noEmit`）
- `npm run dev` → `pnpm dev`

如果计划文档里写的是 npm 命令，直接翻译成 pnpm 等价命令；不要去 `npm install` 强行污染锁文件。

## 2026-04-26 — agents 前端计划遗漏了 testing-library 依赖

**症状**: Task 4 (`useAgents` 钩子的 TDD) 的测试代码 import 了 `@testing-library/react` 的 `renderHook` 和 `waitFor`，但 `web/package.json` 一开始没有这个 dep（只有 `@testing-library/dom`），也没有 `jsdom`。

**原因**: 计划编排时没核对 web 项目的 devDeps，写出的测试代码超出了既有依赖。原有的 skill-card.test.ts 只做 `import * as mod from './skill-card'` 的导出形状校验，没引入 testing-library，因此假设它已存在是错的。

**规则**:
- 写涉及 React Hook/组件渲染的测试前，先 `grep '@testing-library/react' web/package.json`，确认 dep 存在再写。
- vitest + JSX 必须放 `.tsx` 文件，`tsx` 在 `.ts` 里 esbuild 不识别。计划里写 `.test.ts` 但内容含 JSX 时直接命名为 `.test.tsx`。
- `renderHook` / `render` 需要 jsdom 环境；如果项目 vite/vitest 配里没全局开 `environment: 'jsdom'`，要在测试文件顶部加 `// @vitest-environment jsdom` pragma，单文件作用域，避免污染全局。
- 这种缺失 dep 的修复会导致 `package.json` + `pnpm-lock.yaml` 也进 commit，超出原计划文件清单。这是不可避免的合理扩散，但要在 commit message 或后续报告里记录。


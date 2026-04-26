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

---

## 2026-04-27 — Phase 完成 ≠ 测试已 verify（要核对 baseline）

**症状**: Phase E 任务 27 想给 `AgentReviewControllerTest` 加一个 `getDetail` 用例，结果发现 `mvn -pl skillhub-app test` 全部 7 个 agent review controller 测试都因 ApplicationContext 加载失败而 error。继续跑全 app 套件，**456 测试 / 241 errors** ——baseline 在我开 Phase E 之前就已坏掉。git stash 验证后确认是 Phase A–D 留下的预存在 bug（`AgentJpaRepository` 等无法 autowire `AgentRepository` 类型）。

**原因**: 上一会话写 Phase A–D commit message 时声称 "backend 432 + ~30 = ~460 passing"，但实际并没真跑全测试就提交。这种 "我想它会过" 的声明留下隐形的破窗。

**规则**:
- 接手 plan 的中间 phase，**第一件事**不是看 plan 的 next-task；而是跑一次完整 baseline (`pnpm vitest run` + `mvn test`) 核对当前是否绿。Phase 之间断电时极易引入未发现的 regression。
- 上一 phase 的 commit message 不是测试通过的证据；测试本身才是。Plan 的 effort summary "X tests passing" 也不能信，要自己跑。
- 当 baseline 已坏不在我能修的范围内时，明确分流：(a) 当前任务必须的代码改动正常做，(b) 但**不要再加新测试到那个坏掉的套件里**——那只会增加噪音，让坏的更难定位，新测试不可能验证；(c) 在 memo 的 follow-up 里高优先级标注，给下游接手的人足够上下文。
- 这次 Task 27 的 controller 改动是必要的功能（前端拿不到 detail 数据），保留；但 controller test 的更改撤回了，并把 follow-up #1 标 🔥 写进 memo。

---

## 2026-04-27 — Bash 工具的 cwd 不会跨调用持久

**症状**: 多次 `cd /path/to/web` 之后想直接 `pnpm test`，但报 `ERR_PNPM_RECURSIVE_EXEC_FIRST_FAIL Command not found`，因为 cwd 已回到 repo root。

**原因**: Claude Code 的 Bash 工具每次调用是 fresh shell，工作目录和 shell state 不持久。即使之前 `pwd` 显示在 `/web`，下一次 Bash call 也会重置。早些时候有一次似乎持久了——那是 mvn 构建在 cwd 留了 `target/` 残留导致后续 `pwd` 输出看起来像没变。

**规则**:
- **每次**需要在子目录跑命令时，命令前面写完整 `cd /Users/lydoc/projectscoding/skillhub/web && <cmd>`，不要假设上一次 cd 还在。
- 后端测试统一前缀：`cd /Users/lydoc/projectscoding/skillhub/server && ./mvnw ...`。
- 不要因这个去 polling `pwd`——每次写完整 cd 链就行。


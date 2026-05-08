CLAUDE.md
Behavioral guidelines to reduce common LLM coding mistakes. Merge with project-specific instructions as needed.

## 关于本仓库身份(FIRST READ — 防上游信息干扰)

- 本仓库是 `iflytek/skillhub` 的 fork:`HE1780/Weave-Hub`(`origin`)。
- **产品名是"知连 WeaveHub"**(中文,注意"连"非"联"、品牌中间无空格);英文品牌"WeaveHub"。
- **代码包名 `com.iflytek.skillhub` 是历史命名,不要据此推断产品定位**。
- 产品定位、Skill/Agent 双通道、命名空间、CLI 兼容层等业务口径**唯一以 `docs/00-product-direction.md` 为准**。
- fork 自有路线(Agent 管理 / 独立视觉 UI / 安全运行)边界以 `docs/adr/0003-fork-scope-and-upstream-boundary.md` 为准;Agent Promotion 决策见 `docs/adr/0004-agent-promotion.md`。
- **不要从下列来源推断产品定位**(它们是上游残留或归档,不代表 fork 现状):
  - `README.md` / `README_zh.md` 顶部仍自称 "SkillHub"(待修)
  - `docs/_archive/upsteam-doc/`(上游 vitepress 站点归档)
  - `docs/_archive/`(已废弃的 plan/spec)
  - 仓库根的 `*-page.png` / `*-skill-detail.png` 等截图(来源不明)
- 任何新 plan 必须在顶部声明它对应 ADR 0003 的 §1.1 / §1.2 / §1.3 哪一条;不在 0003 范围内的需求要先与维护者确认是否要做。
- 同步上游时:upstream 的 plan/spec 文档**不进 `docs/plans/`**,扔到 `docs/_archive/upstream-incoming/<日期>/`,代码按 ADR 0003 §3 规则处理冲突。


Tradeoff: These guidelines bias toward caution over speed. For trivial tasks, use judgment.

1. Think Before Coding
Don't assume. Don't hide confusion. Surface tradeoffs.

Before implementing:

State your assumptions explicitly. If uncertain, ask.
If multiple interpretations exist, present them - don't pick silently.
If a simpler approach exists, say so. Push back when warranted.
If something is unclear, stop. Name what's confusing. Ask.
2. Simplicity First
Minimum code that solves the problem. Nothing speculative.

No features beyond what was asked.
No abstractions for single-use code.
No "flexibility" or "configurability" that wasn't requested.
No error handling for impossible scenarios.
If you write 200 lines and it could be 50, rewrite it.
Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

3. Surgical Changes
Touch only what you must. Clean up only your own mess.

When editing existing code:

Don't "improve" adjacent code, comments, or formatting.
Don't refactor things that aren't broken.
Match existing style, even if you'd do it differently.
If you notice unrelated dead code, mention it - don't delete it.
When your changes create orphans:

Remove imports/variables/functions that YOUR changes made unused.
Don't remove pre-existing dead code unless asked.
The test: Every changed line should trace directly to the user's request.

4. Goal-Driven Execution
Define success criteria. Loop until verified.

Transform tasks into verifiable goals:

"Add validation" → "Write tests for invalid inputs, then make them pass"
"Fix the bug" → "Write a test that reproduces it, then make it pass"
"Refactor X" → "Ensure tests pass before and after"
For multi-step tasks, state a brief plan:

1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
Strong success criteria let you loop independently. Weak criteria ("make it work") require constant clarification.

These guidelines are working if: fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

## Session Protocol

**At session start, ALWAYS read:**
- `memo/memo.md` — Project context, recent updates, completed features
- `memo/lessons.md` — **CRITICAL**: Known issues, failed approaches, debugging patterns

**Before fixing any bug**, check `memo/lessons.md` for similar issues.
**After any user correction**, update `memo/lessons.md` with the pattern.
**At session end**, save context to `memo/memo.md`.

## Workflow

All work follows the OpenSpec cycle: `/opsx:explore` → `/opsx:propose <name>` → `/opsx:apply <name>` → `/opsx:archive <name>`

## Core Rules

- **Plan first**: Enter plan mode for any task with 3+ steps
- **Verify before done**: Run tests, check logs, demonstrate correctness — reply "Check Passed" after each verified task
- **Minimal impact**: Touch only what's necessary
- **Autonomous bugs**: Fix without hand-holding — check memo files first
- **Session continuity**: Read memo files at start, update at end
- **Project artifacts stay in the project** (CRITICAL): Any document describing THIS project — plans, diagnostic reports, architecture analyses, ADRs, review notes, improvement proposals — MUST be written inside this repo, never to global locations like `~/.claude/plans/` or `~/.claude/projects/`. Target directories:
  - Implementation plans → `docs/plans/YYYY-MM-DD-<slug>.md`
  - Diagnostic/root-cause reports → `docs/diagnosis/YYYY-MM-DD-<slug>.md`
  - Architecture decisions → `docs/adr/NNNN-<slug>.md`
  - Session memos → `memo/memo.md`
  - Lessons learned → `memo/lessons.md`
  Global `~/.claude/` is for cross-project harness config only (skills, settings, hooks). When plan mode assigns a global plan path like `/Users/lydoc/.claude/plans/xxx.md`, override it and write to the project-local equivalent, then inform the user.

## git策略
长期采用“双线模型”：`upstream/main` 作为上游官方基线，`sync-main` 作为仅用于同步上游的镜像分支（禁止功能开发），`origin/main` 作为私有长期产品主线（承载日常开发与集成）。通过“定期同步 upstream -> sync-main，再择机合入 main”保持可升级与自主演进。

### 同步与合流规则
1) **同步节奏**：每周一次，或 `upstream/main` 领先 `sync-main` ≥ 10 个提交时触发。流程：
   ```
   git fetch upstream
   git push origin upstream/main:sync-main
   git checkout -b chore/sync-upstream-YYYYMMDD main
   git merge --no-ff sync-main   # 必须 merge，禁止 rebase（避免改写本地 100+ 提交哈希）
   ```
   解决冲突后合回 `main` 并 `push origin main`。
2) **冲突归属（按 ADR 0003）**：合入冲突由触发同步的人解决；冲突文件位于 ADR 0003 划归"fork 主动做"的目录（agent/*、独立 UI 改名、安全运行）时，**保留 fork 实现，丢弃 upstream 改动**；位于"跟随 upstream"目录（治理、社交、搜索基础）时，优先接受 upstream，必要时再补丁。
3) **feat 分支边界**：若 feat 分支改动了 ADR 0003 中"跟随 upstream"的目录，合入 `main` 之前先 rebase 到最新 `sync-main` 基线（`git rebase sync-main`），减少下次同步的冲突面。fork 自有目录的 feat 分支不受此约束。

## 分支操作约束
1) 仅允许在 `feat/*` 分支修改代码。  
2) `sync-main` 只用于同步 `upstream`，不做功能开发。  
3) 所有功能分支从 `main` 创建，并合并回 `main`。  
4) 每次改动前先输出当前分支与 `git status -sb`。  
5) 每个 `feat/*` 分支在“最小可用且测试通过”后，必须立即合并到 `main` 并执行 `push origin main`，不额外走 PR。  
6) 只修改本任务相关目录；若涉及关键文件（锁文件、构建配置、部署配置），先暂停并询问我。  
7) 提交前必须通过与本次改动相关的最小检查集：`lint`、`test`、`build`。  
8) 每次提交必须小步、单一目的，并给出变更摘要与影响范围。  
9) 禁止危险 Git 操作：`git reset --hard`、强制推送（`--force`）、删除分支。  

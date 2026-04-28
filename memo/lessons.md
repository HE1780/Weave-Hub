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

---

## 2026-04-27 — 多模块 maven 别用 `-pl X test`，要么从 root 跑要么先 install

**症状**: Phase E 接手时 `./mvnw -pl skillhub-app test` 报 241/456 ApplicationContext load failure，错误是 `No qualifying bean of type AgentRepository`。Memo 里前一会话猜是 `@EnableJpaRepositories` 配错或 JPA 扫描漏包。实际原因完全不是。

**根因**: stale m2 cache。错误 stacktrace 显示 `agentPublishService defined in URL [jar:file:/Users/lydoc/.m2/repository/com/iflytek/skillhub/skillhub-domain/0.1.0/skillhub-domain-0.1.0.jar!/...]`——服务从 m2 jar 加载，而 m2 里的 `skillhub-infra-0.1.0.jar` 是较早装的版本，**不含** `Agent*JpaRepository.class`。Domain jar 已含 `AgentPublishService`（依赖 `AgentRepository`）但 infra jar 没有 impl，autowire 必然失败。`./mvnw -pl skillhub-app test` 不会触发上游模块 build/install，所以源码改动到不了运行时。

**修复**: `./mvnw test`（从 server/ 根跑）走 reactor 全模块构建+install reactor 内构件，455/455 pass。等价的精确修法是 `./mvnw -pl skillhub-domain,skillhub-infra install -DskipTests` 然后 `./mvnw -pl skillhub-app test`。

**规则**:
- 多模块 maven 项目跑测试，**默认从 root 跑** (`./mvnw test`)。`-pl` 只在你确认上游 jar 是新鲜的时候用。
- `mvn -pl X -am test` 看似解决，但 `-am` 把 test goal 也下沉到上游模块，会因为 `-Dtest=Foo` 在上游模块里找不到匹配类而失败——不是替代方案。正确的 -am 用法是配 `compile`/`install` 而不是 `test`：`./mvnw -pl skillhub-app -am compile` 或 `./mvnw -am install -DskipTests`，再单独 `-pl X test`。
- 看到 "No qualifying bean of type X" 错误时，**先看异常里的 jar 路径**：如果是 `~/.m2/repository/...` 而不是 `target/classes/...`，几乎一定是 stale cache，不是配置问题。
- 这种问题不留在源码里，git diff 看不到，只能跑测试触发；所以接手 plan 中间 phase 的 baseline 验证仍然要跑 `./mvnw test` 而不是 `./mvnw -pl X test`。

---

## 2026-04-27 — Spring Boot 服务运行时跑 mvn clean,nested jar 类懒加载会炸

**症状**: 前端点击 prompts 卡片跳到 skill-detail,toast 弹 "服务器错误,请稍后重试"。日志显示 `GET /api/web/skill-versions/14/comments` 500;栈是 `NoClassDefFoundError: org/hibernate/event/spi/ClearEvent`(以及 `ConcreteSqmSelectQueryPlan$1` 也加载失败)。诡异处是这两个类在 hibernate-core-6.4.4.Final.jar 里**确实存在**,启动时 `org.hibernate.Version` 也打印 `6.4.4.Final`。

**根因**: 后端进程启动时打开的是 `target/skillhub-app-0.1.0.jar`(Spring Boot 的 fat jar),期间有人跑了 `mvn clean` 或重打包,把那个 jar 文件删掉/换掉了。Spring Boot 的 `LaunchedURLClassLoader` 加载 nested jar (`BOOT-INF/lib/*.jar`) 是**懒加载**的:启动时已加载的类继续可用,但首次需要的类(像 `ClearEvent`,只有 `@Transactional(readOnly=true)` 收尾时调 `entityManager.clear()` 才会触发加载)就找不到了——因为外层 jar 已经不在了,nested entry 也就读不到。`lsof` 显示进程在抓 `skillhub-app-0.1.0.jar.original`(thin jar,559K),fat jar 文件 inode 没了,这是关键证据。

**修复**: 重打包 + 重启进程(`./mvnw -DskipTests package` 然后重启 `java -jar`)。源码无需改动。

**规则**:
- **Spring Boot 服务运行期间不要 `mvn clean`,也不要重打包**。要么先 `kill` 服务再 clean,要么用 `-Dmaven.clean.skip=true` 之类方式跳过 clean。
- 看到 `NoClassDefFoundError` + `ClassNotFoundException` 找的是某个**应该存在**的依赖里的类,且日志开头 `org.hibernate.Version`/`Started`Application 之类启动信息正常 —— 第一反应**不是**升降级依赖,而是 `lsof -p <pid> | grep jar` 看看 fat jar 文件还在不在。
- 大多数请求看起来正常、只有少数偶发 500 也是这个症状的特征:经常用的类启动时已加载,只有冷门 code path 触发的类才会现场加载现场失败。
- Backend 重启完一定要 curl 复跑一次原失败 endpoint 验证 200,而不是只看启动日志没报错就完事。

---

## 2026-04-27 — 并行 claude 实例之间会抢 git index

**症状**: 在 P3-2a → P2-2 → P2-4 三连任务里，P2-2 用 `git add <files...>` 暂存了七个后端文件后，紧接着 `git commit -m '...'` 命令报 "no changes added to commit"。`git log` 显示我刚暂存的七个 P2-2 文件出现在了**另一个 commit** 里（`3d4d0e0d feat(web): register /my-weave route (auth-gated)`），跟一个完全无关的 `web/src/app/router.tsx` 改动放一起。该 commit message 描述的是 P0-1b 工作，但内容大头是我的 P2-2。

**原因**: 用户在另一个会话/agent 里同时跑 P0-1b plan（我也在做 P2-x）。git index（暂存区）在仓库内是**单一全局状态**，不是 per-process 的。`git add` 暂存的文件被另一个并行进程的 commit 吞掉了——它执行 `git add web/src/app/router.tsx` 时，我已经 stage 的所有文件作为 "changes to be committed" 一并被它的 `git commit` 收入。

**规则**:
- 用户明确说"两条线在并行"时，**stage → commit 之间不能有任何能让出 cwd 的操作**。一行命令完成：`git add <files> && git commit -m "..."`，不要分两个 Bash 调用。
- 即便分两个调用，**commit 命令的 message 写好后立即提交**，中间不要穿插任何其他工具调用——index 在那期间是并行 agent 也能写的。
- 如果还是被抢了：**不要 rebase 别人的 commit**（他们也在改写历史的话会更乱）。最稳妥是接受现状，在 memo 里写清楚 "X 文件其实在 commit Y 里，虽然 message 说的是 Z"，方便未来搜索。
- 对于"我自己的多个独立 commit"场景，**先全部 stage + commit 完才开下一个任务**；不要中途让 cwd 暴露给并行 agent。

---

## 2026-04-27 — backlog 估时不要照搬，先 audit 既有代码

**症状**：fork-backlog A9 标"~1 天 · mirror PromotionService"，实际打开 `PromotionService` 360 行，硬编码 `sourceSkillId/sourceVersionId/targetSkillId`，approve 路径还要**物化资源**（拷 SkillVersion + 文件 metadata + 重置 latestVersionId）。Mirror 到 agent 端要拷 AgentVersion + `package_object_key`（涉及对象存储） + AgentTag + AgentVersionStats，外加 controller DTO 都是 skill-only — 实际工作量 2-3 天 + brainstorm + ADR。同 commit 集群里另一项 A7 也类似：plan 假设要 tar.gz 重打包，audit 后发现 `package_object_key` 本就是发布时上传的 .zip，直接流式回传就行——工作量从 1 天降到 1.5 小时。

**原因**：backlog 的"mirror X"描述是签名级别的相似性，不代表实现级别的相似性。estimate 写出来时没人逐个 audit 既有 service 的复杂度，于是在 plan 里固化成了错误的工作量假设。

**规则**：
- **执行 plan 前先抽样 audit 一两个"mirror"项的既有 service 行数和职责**：< 100 行的 service mirror 一般估时是对的；200+ 行且涉及 cross-entity 物化/复制的 service mirror 一定是低估。
- backlog 的 estimate 是 hint 不是合同。看到 plan 的 hour 估时和 audit 出来的实际复杂度差超过 2x 时，**不要硬扛着按 plan 干**。停下来重新写 plan 那一节、明确"这条比想象的大"，然后挑下一条做。
- 如果一条延后了，**当场把延后的原因写进 backlog 那条目录**（不是只在 commit 里说），未来接手的人才能在打开 backlog 第一眼就看到 "这条还没做且原因是 X"。
- 用 backlog 评估"这一会话能搞定几条"时，按 audit 后的真实估时算，不是 backlog 上写的。

## 2026-04-28 — 写 plan 前必须 grep 既有 entity / event / class 是否同名

**症状**：A9 plan Task 2 写"创建 AgentPublishedEvent record"，sub-agent 照写覆盖了文件。结果该文件**已存在**且被 6 处生产代码使用（AgentPublishService / AgentLifecycleService / AgentReviewService 等），narrowed record 把 5 个字段砍成 3 个，3 处 caller 编译失败。`mvn -q` 因为增量编译 stale，初次跑甚至没暴露 — `mvn clean compile` 才看到 BUILD FAILURE。需要 `git revert` + 改 plan + 改 spec。

**原因**：写 plan 时没 `find server -name "AgentPublishedEvent.java"`，凭"应该不存在因为对应的 SkillPublishedEvent 才有"主观推断。spec / plan 的"create new X"陈述变成了破坏性 overwrite 指令。

**规则**：
- plan 里每一个 "Create file at <path>" 步骤之前，先 `find <project-root> -name "<basename>"` 确认目标路径**不存在**。如果存在，plan 应改为 "Modify" 或 "Skip — already exists"。
- sub-agent 拿到"create"指令默认覆盖，不会自己 grep 重名。这是 controller 的责任。
- **`mvn -q compile` 不可信** — 增量编译会用 stale `.class`，本地修改可能完全没编译。验证 plan 的修改是否破坏 build，必须用 `mvn clean compile`（或至少 `mvn -o test-compile` 后 `mvn test-compile -Dmaven.compiler.useIncrementalCompilation=false`）。
- sub-agent 报"BUILD SUCCESS"前应被指示用 clean 编译；implementer-prompt 里加一条 "If you modify or replace an existing file in `server/`, run `mvn -pl <module> clean compile` not `mvn ... -q compile`."



---

## 2026-04-28 — 测试失败先读断言再定性 regression

**症状**：memo 04-28 把 `NamespaceBatchMemberControllerTest.batchAddMembers_emptyArray_returnsError` 标为 "upstream regression"（baseline 上跑得更糟），下一会话审视时发现：测试**断言期望 500，实际 400**，但 400 才是合理结果——`@NotEmpty` 在 record-typed `@RequestBody` 上抛 `MethodArgumentNotValidException` → 400 是 Spring Boot 标准 bean-validation 行为。注释里还自圆其说写了 "Spring Boot 3.2+ raises HandlerMethodValidationException (500) for record bodies"——这是**写测试的人错误推断**了框架行为。

baseline 上"更糟"的现象（`Failed to load ApplicationContext`）也另有原因：`mvn test -pl skillhub-app` 隔离运行时如果 `~/.m2` 里的 `skillhub-domain` jar 是旧版（没有最近改动的类），就会 NoClassDefFoundError；实际跑 `mvn install` 同步 m2 后测试整套 6/6 通过。

**规则**：
- 测试报错 "expected X but was Y" 时，**第一步问"X 是对的吗？"**，不是 "为什么 Y 不是 X"。读测试自己的注释、比对生产代码行为，断言写错的概率比框架/生产 regression 更高。
- "baseline 上跑也不通过"**不等于** "本地改动无关"——可能两边都有同一个 m2 stale 或 incremental compile bug。重新装一遍 m2 (`mvn -DskipTests install`) 再跑隔离测试是廉价的鉴别。
- 把测试失败定性为 "regression" 之前必须给出**机制级解释**：哪个 PR/commit 引入了什么改动 + 改动如何破坏断言。给不出机制就是猜。

---

## 2026-04-28 — sub-agent audit 报告必须人工交叉校验

**症状**：sub-agent 帮做 A2-A6 后端 audit，报告结构清晰看似可信，但发现：
- 把 `AgentTag.java` 路径说成 `domain/agent/social/`（实际在 `domain/agent/`）；
- 把 A2 / A3 frontend 报为 ✅ 但落到具体 hook 路径（`useAgentStar` 在 `social/use-agent-star.ts`）漏报或错位；
- A6 admin moderation dashboard 完全没对齐到 reports.tsx 的 skill-only 现状；
- 漏报 A1 (Agent 评论) 全栈已完成；
- 漏报 PromotionPortalControllerTest 文件已存在（虽然测试覆盖不全）。

如果直接拿 audit 报告写 backlog 校正，会把多处状态写错。

**规则**：
- sub-agent 给的"路径 + 状态"清单**必须**用 `ls`/`find`/`grep` 抽样核 3-5 个再引用。一次 grep 命中或失误就能识别 sub-agent 的精度上限。
- 让 sub-agent **引用 commit hash 或文件行号**，而不是泛泛说"已完成"。无引用的判断不要直接采信。
- 用 sub-agent 做 audit 时，prompt 里就指定 "report file paths I can verify with `ls`" — 给它压力让它必须输出可验证证据，比让它自由叙述准确率高。
- audit 找到的"前端缺失"还要做一步反向校验：**对齐目标本身有没有这个东西**。Skill 侧 `skill_tag` 也没前端 UI 这件事，sub-agent 可能漏了，结果把"Agent 没 UI"当成 gap，但实际没有缺口。

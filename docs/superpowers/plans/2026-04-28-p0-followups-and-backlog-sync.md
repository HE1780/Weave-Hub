# Plan — P0 Follow-ups + Fork Backlog Sync

**Spec:** [docs/superpowers/specs/2026-04-28-p0-followups-and-backlog-sync-design.md](../specs/2026-04-28-p0-followups-and-backlog-sync-design.md)
**Branch:** `feat/p0-followups-and-backlog-sync`
**Total:** 8 任务

---

## Task 1 — 修 NamespaceBatchMemberControllerTest 期望 400

- [ ] 改 `batchAddMembers_emptyArray_returnsError` 期望 `status().isBadRequest()` 替代 `isInternalServerError()`
- [ ] 改注释为 `// @NotEmpty on BatchMemberRequest.members triggers MethodArgumentNotValidException → 400. Earlier comment incorrectly assumed Spring Boot 3.2+ raises HandlerMethodValidationException for record bodies; in practice the @Valid annotation on the @RequestBody record falls under standard bean-validation handling.`
- [ ] **验证**: `cd server && mvn test -pl skillhub-app -Dtest=NamespaceBatchMemberControllerTest`
- [ ] commit `fix(test): namespace batch members empty-array expects 400`

## Task 2 — 加 PromotionController agent 路径单元测试

- [ ] 找到现有 PromotionController 测试类（`server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/.../PromotionPortalControllerTest.java` 或同名文件）
- [ ] 加 3 个 case：
  - `submitAgentPromotion_dispatchesToAgentService` — POST sourceType=AGENT + 必填字段，验证 200 + AgentPromotionService 被调用
  - `submitAgentPromotion_missingTargetAgentId_returns400` — 缺 sourceAgentId → 400
  - `listPromotions_filtersBySourceType` — GET ?sourceType=AGENT 只返 agent promotions
- [ ] **验证**: `cd server && mvn test -pl skillhub-app -Dtest=PromotionPortalControllerTest`
- [ ] commit `test(api): PromotionController agent dispatch + sourceType filter cases`

## Task 3 — promotions.tsx source-type badge tokenize

- [ ] 读 `web/src/pages/promotions.tsx`，定位 SourceTypeBadge 组件，提取 `bg-blue-*`/`bg-purple-*`/`text-*` 到组件内 `const sourceTypeStyles: Record<SourceType, string>` map
- [ ] 检查是否有别处复用同色（grep `text-blue-700|text-purple-700` 全 web/src）
- [ ] **验证**: `cd web && npm test -- promotions` 通过；快速本地起 dev server 看 promotions 页 badge 颜色未变
- [ ] commit `refactor(web): tokenize promotions source-type badge styles`

## Task 4 — 更新 fork-backlog A0/A2/A3/A5/A6

- [ ] A0 (实际是新增段，原 backlog 拆在 A0/P2-2 双处) → 在 P2-2 段加 `~~已完成 (2026-04-27)~~ ✅`，承载文件 `AgentLifecycleController.java` + `use-archive-agent.ts`
- [ ] A2 Agent Star → 标 `~~已完成~~ ✅ (2026-04-28 之前)`，承载 `AgentStarController.java` + `agent-star-button.tsx`
- [ ] A3 Agent Rating → 同 A2 模板
- [ ] A5 Agent Tag → 标 `~~已完成 (admin/CLI surface only, no UI per Skill parity)~~ ✅`，注明 `agent_tag` 表是 named version tag
- [ ] A6 Agent Report → 标 `🟡 部分完成`：submit 端 ✅（`use-report-agent.ts`），moderation dashboard ❌（独立 follow-up，reports.tsx 仅接 skill）
- [ ] commit `docs(backlog): sync A0/A2/A3/A5/A6 with actual code state`

## Task 5 — memo/lessons.md 加两条

- [ ] "sub-agent audit 报告必须人工交叉校验"——sub-agent 把 AgentTag 位置说成 social/，把 A2/A3 frontend 报成 ✅ 但路径写错，而把 A6 moderation 漏报；不验证就引用会写错 backlog
- [ ] "测试失败别上来就定性为 regression"——读测试断言本身，可能是断言写错；跑 baseline 上的测试也只能确认行为一致，不等于行为正确。需对比断言的"应该"与现实的"实际"
- [ ] commit `docs(lessons): cross-verify sub-agent audits + read test assertions before regression triage`

## Task 6 — memo/memo.md 04-28 第一条修正 + 新增第三条

- [ ] 在 04-28 "开放注册" entry 的"关于唯一未通过的测试"段加补丁说明：实际是测试断言写错，已在 `feat/p0-followups-and-backlog-sync` 修复（链接 commit）
- [ ] 在 memo 顶部加新 entry：`## 2026-04-28 — P0 follow-ups + 后续 backlog 校正`，列 8 个 task 完成 + 验证结果 + 真实剩余 follow-up
- [ ] commit `docs(memo): 2026-04-28 P0 followups session entry + correction`

## Task 7 — 全测试回归

- [ ] `cd server && mvn test` 整套通过（期望 561/561，A.1 修好那条）
- [ ] `cd web && npm test` 整套通过（期望 684/684，没有 promotions 测试退化）

## Task 8 — 合回 main

- [ ] `git checkout main && git merge --no-ff feat/p0-followups-and-backlog-sync`
- [ ] `git push origin main`
- [ ] 删 feat 分支 `git branch -d feat/p0-followups-and-backlog-sync`

---

## 失败/偏离时的处理

- **Task 1 跑出来不是 400 也不是 500**——立刻 stop and re-plan，原假设崩了
- **Task 2 PromotionPortalControllerTest 需要的 mock infra 缺失**——退而在文件本身建独立 `PromotionAgentDispatchTest` 类，避免污染既有 fixture
- **Task 3 grep 出多处复用同色**——只 tokenize promotions.tsx 内部，跨文件复用列入 follow-up
- **任意一步 mvn/npm 跑挂**——记录失败输出，分析后续策略，不强行 push

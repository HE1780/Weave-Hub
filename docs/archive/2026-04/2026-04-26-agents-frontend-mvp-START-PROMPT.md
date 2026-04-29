# Start Prompt — Agents Frontend MVP (Subagent-Driven Execution)

> Paste the block below into a fresh Claude Code session opened at the repo root
> (`/Users/lydoc/projectscoding/skillhub`). It is self-contained — the new session
> has no prior context from the planning conversation.

---

```
Execute the implementation plan at docs/plans/2026-04-26-agents-frontend-mvp.md
using subagent-driven development.

Required reading before you start (in this order):
1. docs/plans/2026-04-26-agents-frontend-mvp.md — the plan you will execute.
2. docs/adr/0001-agent-package-format.md — the locked schema spec the plan
   implements against. The TypeScript shape in Task 2 of the plan must stay
   consistent with the "Frontend Implications" section of this ADR.

Workflow:
- Use the superpowers:subagent-driven-development skill. Follow it strictly:
  fresh implementer subagent per task, then a spec-compliance reviewer
  subagent, then a code-quality reviewer subagent, iterate until approved,
  mark the task complete, move to the next task.
- Do NOT run multiple implementer subagents in parallel — tasks in this plan
  have file dependencies (later tasks edit files earlier tasks created).
- Each implementer subagent gets the full text of its task plus this scene-
  setting context: "This plan adds an Agents UI to a React + TanStack Router +
  TanStack Query + i18next + Tailwind app. Mock data only — no backend in
  this plan. The agent package shape is defined in docs/adr/0001-agent-
  package-format.md. Follow TDD as the plan steps prescribe."

Project conventions (from CLAUDE.md, must be respected by every subagent):
- Read memo/memo.md and memo/lessons.md at the start.
- Update memo/lessons.md after any user correction during execution.
- All artifacts (plans, ADRs, diagnoses) belong inside this repo, never under
  ~/.claude/.
- Run tests, type-check, and verify before claiming a task complete. The plan
  embeds the exact commands.
- Touch only files listed in each task. Do not "improve" adjacent code.
- Commit per the steps in each task. Each task ends with a commit step using
  the message shown in the plan; do not change those messages.

Pre-flight before Task 1:
- Confirm working directory is /Users/lydoc/projectscoding/skillhub.
- Confirm git working tree state: there are unrelated dirty files from prior
  work — leave them alone, your edits should be limited to the plan's file
  list. Verify by running `git status --short` and noting which files are
  yours vs pre-existing.
- Run the plan's "Pre-flight Verification" section (Steps 0.1–0.3) yourself
  before dispatching the first implementer subagent.

After all 12 tasks pass review:
- Run the Final Verification Checklist at the bottom of the plan.
- Do NOT push, do NOT open a PR, do NOT merge — stop and summarize for the
  user. The user decides next steps.

Begin.
```

---

## Notes for the user (don't paste this part into the new session)

- The plan is at `docs/plans/2026-04-26-agents-frontend-mvp.md` (12 tasks, all TDD).
- The locked ADR it implements against is `docs/adr/0001-agent-package-format.md`.
- The new session will dispatch ~36 subagents in total (12 implementers + 12 spec reviewers + 12 quality reviewers, plus a final code reviewer at the end). Reviews loop on issues, so the actual count is higher.
- If the new session veers off-plan, interrupt and remind it: "Stick to the task list in `docs/plans/2026-04-26-agents-frontend-mvp.md`. Do not add scope."
- The plan deliberately does NOT push commits or open a PR — that's a separate decision after all tasks pass.

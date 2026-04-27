# Comments Feature — Requirements Memo

**Status:** Requirements captured, design pending.
**Captured:** 2026-04-26
**Schedule:** Brainstorm + spec + implementation plan starts AFTER the Agent Frontend MVP (`docs/plans/2026-04-26-agents-frontend-mvp.md`) ships.

This memo records the locked decisions and open questions from the requirements conversation so the next session can resume without re-litigating them.

## Locked decisions

| # | Decision | Value |
|---|---|---|
| 1 | What is commented on | Each **version** of a Skill *and* each **version** of an Agent. Not per-package. |
| 2 | Comment shape | Flat plain-text or markdown body. `author + timestamp + body`. No threading, no replies, no reactions in v1. |
| 3 | Edit / delete perms | **Author only** for edit and delete. Namespace admins also have delete (moderation). No edit-by-anyone. |
| 4 | Read perms | **Inherit the skill/agent version's visibility.** Public version → anyone with view access can read. Private/namespaced version → only members who can already see the version. |
| 5 | Post perms — global versions | **Any logged-in user** can post a new comment. |
| 6 | Post perms — non-global versions | Only users who can already see the version (i.e., have view access). |
| 7 | Pin / highlight | **Namespace members** of the owning namespace can mark certain comments as pinned/highlighted. End users see the highlight but cannot pin. Two-tier: chatter (anyone) + curated (namespace members). |
| 8 | Primary motivation | **Internal change notes per version.** Maintainers and reviewers drive the conversation; end-users can chime in. Closer to commit/release notes than public discussion. |
| 9 | CLI surface | Dedicated subcommands: `skill comments <id>` and `agent comments <id>`. Both accept `--query <text>` for content search. |
| 10 | Search endpoint | Separate `GET /api/comments?q=<text>&scope=<global\|namespace>`. Simple SQL LIKE / ILIKE for MVP. Full-text indexing deferred. |

## Open questions (resolve during brainstorming)

These were not asked during the requirements pass and need answers before a design is locked:

- **Table layout.** One `version_comments` table polymorphic on `(target_type, target_id)`, or two separate tables (`skill_version_comments`, `agent_version_comments`)? Tradeoff: shared queries vs. clean foreign keys.
- **`pinned` semantics.** A boolean per comment, or a separate `pinned_comment_id` per version (max one)? Does pinning have a TTL or stay until unpinned?
- **"Namespace member" definition.** Concretely, which existing role rows count? Anyone with `MEMBER` or above? Owner only? This must map to an existing permission predicate, not invent a new one. Worth reading `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/namespace/...` before deciding.
- **CLI auth → post permission.** When a user is authenticated via CLI token, how does the existing token-scopes model translate to "can post a comment"? Does any token suffice or do we need a new scope?
- **Body format.** Plain text only, or markdown rendered in the web UI but raw in CLI? (Affects whether the column needs sanitization on input.)
- **Body length cap.** Reasonable upper bound (1KB? 8KB? 64KB?) — pick one and document it.
- **Edit history.** Author can edit — do we keep the prior body for audit? If yes, an `edits` table or just a `last_edited_at` timestamp?
- **Delete model.** Hard delete (row removed) or soft delete (`deleted_at`, body replaced with a tombstone in lists)?
- **Listing / pagination.** Default sort (newest first? pinned first then newest?). Page size. Whether the version-detail page eagerly loads comments or lazy-loads.
- **Notifications.** Does posting a comment notify the namespace owner / version author? Reuses the existing notification system (`server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/notification/...`) or skipped for v1?
- **Rate limiting.** Anti-spam needed? If yes, per-user-per-version-per-window?
- **Search scope semantics.** When `scope=global` is passed, does it mean "search comments on globally visible versions" or "search across all comments the caller can see, regardless of where they live"? Pick one and document.
- **Returned fields in CLI.** What does `skill comments <id>` print per row? At minimum `author`, `created_at`, `body`. Do we include `pinned`, `edited_at`, version label?

## Things to verify before designing

These are codebase questions, not design questions — answer by reading the repo at design time:

- The actual schema of `skill_version` and any equivalent `agent_version` table (the agent backend isn't built yet — comments may need to wait for the agent versioning model OR be designed to plug in once it exists).
- The existing rating/star tables (`server/skillhub-storage/.../skill_rating.sql` or similar) — they're the closest existing pattern to comments and likely the cleanest reference for table design, indexes, and foreign keys.
- The existing `WEB_API_PREFIX` controller layout to decide whether `/api/comments` belongs on the portal controller or a new `CommentController`.
- The CLI's existing subcommand structure — where in the CLI codebase do `skill` and `agent` subcommand groups live, and what's the convention for adding a sub-subcommand.

## What's deliberately NOT in this memo

- Database column types and constraints — design-time decision after reading the repo.
- API request/response JSON shapes — design-time.
- React component structure for the web UI — design-time.
- i18n keys — implementation-time.
- The actual Brainstorming → Writing-Plans cycle output (those are the next two artifacts to produce, not this memo).

## Next session checklist

When picking this up:

1. Confirm the Agent Frontend MVP has shipped (or explicitly scope-out the parts that depend on it).
2. Read this memo top to bottom.
3. Read the codebase areas listed under "Things to verify before designing".
4. Run `superpowers:brainstorming` to resolve the open questions, producing an ADR at `docs/adr/000N-version-comments.md`.
5. Run `superpowers:writing-plans` to produce `docs/plans/YYYY-MM-DD-version-comments.md`.
6. Then choose execution mode (subagent-driven recommended).

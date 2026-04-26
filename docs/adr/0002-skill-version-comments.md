# Skill Version Comments — Design

**Status:** Locked (2026-04-26), awaiting implementation plan.
**Owner:** Comments feature stream
**Supersedes:** Open questions in `docs/plans/2026-04-26-comments-feature-requirements.md` (locked decisions in that memo are carried forward; the 12 open questions are resolved here).
**Predecessor planning context:** Agent Frontend MVP shipped (12 commits, see `memo/memo.md` 2026-04-26 entry).

## 1. Scope

### 1.1 In scope (v1)

Comments attached to **skill versions only**, surfaced through the **web UI** only. New REST endpoints, new database table, new web components.

### 1.2 Out of scope (deferred to follow-up specs)

| # | Deferred item | Trigger to revisit |
|---|---|---|
| 1 | Comments on agent versions | When `agent_version` table exists |
| 2 | CLI `skill comments <id>` subcommand | When CLI codebase exists |
| 3 | `GET /api/comments?q=...` search endpoint | When a consumer (CLI, global search bar, mod tool) exists |
| 4 | Edit history (`comment_edit` table) | If "what did this comment used to say" requirement appears |
| 5 | Reply / threading | Never (per requirements memo decision #2) |
| 6 | Reactions | Never (per requirements memo decision #2) |
| 7 | Rate limiting | If abuse pattern emerges |
| 8 | Optimistic UI | Once basic flow is stable in production |
| 9 | Comment permalinks (`#comment-{id}`) | Post-v1 nice-to-have |
| 10 | Comment count badge on version listing | Requires modifying existing version-list response — violates additive-only constraint |
| 11 | Notify namespace owners / participation-based subscriptions | If author-only proves too narrow |
| 12 | Markdown toolbar in composer | If users complain about raw markdown |
| 13 | GFM extensions (tables, task lists) | If users ask, with re-test of sanitizer |
| 14 | Soft-delete restore / admin moderation view | When moderation workload demands it |
| 15 | Body length increase past 8 KB | If 8 KB rejection rate is non-trivial |

The original requirements memo's decisions about agents (decision #1's "and Agents" clause) and CLI (decisions #9 and #10) are explicitly **deferred** because the prerequisite codebases do not exist:

- No `agent_version` table exists in the repo.
- No CLI codebase exists in the repo.

## 2. Constraints

- **API additive only.** No existing endpoint signature, response shape, or behavior is modified. All comment functionality lives on new routes; existing skill / version reads return byte-identical responses.
- **Reuse existing predicates.** No new permission concepts. Read perms reuse `VisibilityChecker.canAccess`. Admin-tier perms (delete-others, pin) reuse the `OWNER || ADMIN` pattern from `NamespaceAccessPolicy`.
- **Mirror the closest existing pattern.** `skill_rating` (table + entity + repo + service + controller layering) is the template.
- **Single-deploy rollout.** No feature flag.

## 3. Locked decisions (resolved open questions)

Captured here so future readers see the chosen answer next to the alternatives that were rejected.

| Q | Decision | Rejected alternatives |
|---|---|---|
| Q0 — Scope | Skill-only, web-only v1 | Polymorphic table for future agent comments; pause feature until agent versioning ships |
| Q1 — Table layout | Narrow `skill_version_comment`, FK to `skill_version(id)` | Polymorphic `(target_type, target_id)` |
| Q2 — Body format | Markdown, 8 KB cap | Plain text 4 KB; markdown 64 KB |
| Q3 — Edit/delete | Soft delete (`deleted_at` + `deleted_by`); `last_edited_at` for edits; no edit-history table | Hard delete + minimal edit timestamp; full `comment_edit` audit table |
| Q4 — Pin | Boolean `pinned` column, multiple pins allowed, no TTL | Single `pinned_comment_id` per version; pin with TTL |
| Q5 — Listing | `ORDER BY pinned DESC, created_at DESC`, page size 20, first page eager-loaded | Chronological order; lazy-load behind a click |
| Q6 — Notifications | Notify version author only on new comment, skip self-notify | None; also notify namespace owners; participation-based subscriptions |
| Q7 — Search | No search endpoint in v1 | Ship `GET /api/comments?q=...` immediately |
| Q8 — Rate limit | None in v1 | Per-user-per-version cap; global per-user-per-hour |
| Q9 — Sanitization | Server stores raw markdown; web sanitizes at render via `react-markdown` + `rehype-sanitize` | Server-side input sanitization; both layers (belt-and-suspenders) |

## 4. Data model

### 4.1 New table

```sql
-- V{N}__skill_version_comments.sql
-- N = next available migration number after V3

CREATE TABLE skill_version_comment (
    id               BIGSERIAL PRIMARY KEY,
    skill_version_id BIGINT NOT NULL REFERENCES skill_version(id) ON DELETE CASCADE,
    author_id        VARCHAR NOT NULL REFERENCES user_account(id),
    body             TEXT NOT NULL,
    pinned           BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP NOT NULL DEFAULT now(),
    last_edited_at   TIMESTAMP,
    deleted_at       TIMESTAMP,
    deleted_by       VARCHAR REFERENCES user_account(id),
    CONSTRAINT body_length CHECK (char_length(body) BETWEEN 1 AND 8192),
    CONSTRAINT body_not_blank CHECK (btrim(body) <> ''),
    CONSTRAINT deleted_consistency CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);

CREATE INDEX idx_skill_version_comment_version
    ON skill_version_comment(skill_version_id, pinned DESC, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_skill_version_comment_author
    ON skill_version_comment(author_id);
```

### 4.2 Schema invariants

- `ON DELETE CASCADE` on `skill_version_id`: when a version row is hard-deleted at the DB level, its comments are removed by the cascade. This is distinct from the application-level soft-delete in §6 (which tombstones a single comment via `deleted_at` while leaving the row in place). Matches the cascade behavior of `skill_rating` (verify exact wording at implementation time).
- `author_id VARCHAR` matches `user_account.id` per existing schema convention.
- Soft-delete invariant: `deleted_at IS NULL` iff `deleted_by IS NULL`, enforced in SQL.
- Body invariant: 1–8192 characters after storage, non-blank after trim.
- The partial index on `WHERE deleted_at IS NULL` keeps tombstones out of the hot listing query.
- No `updated_at` distinct from `last_edited_at`. The "edited" badge predicate is `last_edited_at IS NOT NULL`.

### 4.3 No changes to existing tables

`skill_version` is not modified. No `pinned_comment_id` column (we chose multi-pin per Q4).

## 5. API surface

Five new endpoints across two new controllers. All paths use the dual-prefix convention (`/api/v1/...` and `/api/web/...`) per the existing `SkillRatingController` pattern.

### 5.1 Routes

```
GET    /api/v{1,web}/skill-versions/{versionId}/comments?page=0&size=20
POST   /api/v{1,web}/skill-versions/{versionId}/comments
PATCH  /api/v{1,web}/comments/{commentId}
DELETE /api/v{1,web}/comments/{commentId}
POST   /api/v{1,web}/comments/{commentId}/pin
```

### 5.2 Controller split

- **`SkillVersionCommentController`** — `@RequestMapping({"/api/v1/skill-versions", "/api/web/skill-versions"})`. Handles list + create.
- **`CommentController`** — `@RequestMapping({"/api/v1/comments", "/api/web/comments"})`. Handles edit, delete, pin. (Single-comment operations resolve the parent version from the comment row to run perm checks.)

### 5.3 Request / response shapes

```json
// GET /skill-versions/{versionId}/comments
{
  "page": 0,
  "size": 20,
  "totalElements": 47,
  "hasNext": true,
  "content": [
    {
      "id": 12345,
      "skillVersionId": 9876,
      "author": { "userId": "u_abc", "displayName": "Alice", "avatarUrl": "https://..." },
      "body": "## What changed\n- Fixed the X bug",
      "pinned": true,
      "createdAt": "2026-04-26T10:23:00Z",
      "lastEditedAt": null,
      "deleted": false,
      "permissions": { "canEdit": true, "canDelete": true, "canPin": false }
    }
  ]
}

// POST /skill-versions/{versionId}/comments
// Request:  { "body": "## What changed\n- ..." }
// Response: 201 Created, body shaped like one element of GET content[]

// PATCH /comments/{commentId}
// Request:  { "body": "edited body" }
// Response: 200 OK, updated row (lastEditedAt set)

// DELETE /comments/{commentId}
// Response: 204 No Content (sets deleted_at = now(), deleted_by = caller)

// POST /comments/{commentId}/pin
// Request:  { "pinned": true }
// Response: 200 OK, updated row
```

### 5.4 Response notes

- `body` is **raw markdown**. Sanitization happens at render in the web client (per Q9).
- `permissions` is computed server-side per row for the calling user. The web does not re-derive perms.
- Soft-deleted rows are **excluded** from default GET. Admin-moderation views (future, item #14) would pass `?includeDeleted=true`; not implemented in v1.

### 5.5 Auth

- Every controller method takes `@AuthenticationPrincipal PlatformPrincipal principal`.
- `GET` accepts `principal == null` (read perms inherit version visibility, which already permits anonymous on PUBLIC).
- `POST/PATCH/DELETE/POST pin` reject `principal == null` with 401.

### 5.6 Error model

Reuse the `@ControllerAdvice` shape returned by `SkillRatingController` on 401/403/404/400. Implementation step verifies the exact response body convention; design contract is "match existing pattern, do not invent new shape."

### 5.7 CSRF

Spring Security CSRF tokens on all mutating endpoints (POST/PATCH/DELETE). Implementation verifies that `SkillRatingController`'s mutating routes use them; if so, the new controllers inherit the same configuration. If not, this is a blocker called out in the implementation plan.

## 6. Permissions matrix

| Action | Predicate | Reuses |
|---|---|---|
| Read comments on version V | `VisibilityChecker.canAccess(V.skill, principal, ...)` returns true | Existing |
| Post comment on version V | Authenticated AND `VisibilityChecker.canAccess(V.skill, ...)` returns true | Existing |
| Edit comment C | `principal.userId == C.author_id` AND `C.deleted_at IS NULL` | New, single-line check |
| Delete comment C | `principal.userId == C.author_id` OR caller has `OWNER`/`ADMIN` role in `V.skill.namespace` | Existing pattern from `NamespaceAccessPolicy` |
| Pin/unpin comment C | Caller has `OWNER`/`ADMIN` role in `V.skill.namespace` | Existing pattern |

### 6.1 Predicate location

```
SkillVersionCommentService
  ├── canRead(version, principal)      → delegates to VisibilityChecker
  ├── canPost(version, principal)      → canRead AND principal != null
  ├── canEdit(comment, principal)      → author check, blocked if deleted
  ├── canDelete(comment, principal)    → author OR namespace admin
  └── canPin(comment, principal)       → namespace admin
```

### 6.2 Permission rules

- **Read perms = post perms.** Memo decisions #4, #5, #6 collapse into one rule: if you can read, you can post (provided you're authenticated). PUBLIC visibility already permits any logged-in user, so the "any-logged-in-user on global versions" carve-out is automatic.
- **Edit-after-delete is blocked.** Once `deleted_at` is set, the author cannot edit. Forces deliberate undelete (deferred item #14) before re-edit.
- **Admin delete preserves authorship.** `author_id` stays original; `deleted_by` records the moderator.
- **Pin only on live comments.** Pinning a soft-deleted comment returns 404 (caller shouldn't have seen it in the list). Deleting a pinned comment is allowed; `pinned` stays true on the tombstone but the partial index excludes it from queries.
- **Per-row perm computation cost.** A 20-row page calls `canEdit/canDelete/canPin` 60 times. Version + namespace-role data is loaded once per request and reused for all rows. Not a hotspot.

## 7. Notifications

- New `NotificationCategory` event type: `COMMENT_POSTED`.
- Recipient: `version.createdBy` (the author of the skill version).
- Skip if `commentAuthor == version.createdBy` (no self-notify).
- Plugs into the existing `NotificationDispatcher` per the recon's path
  (`server/skillhub-notification/.../NotificationDispatcher`). One new event type, one new handler.
- Notification payload `body_json`: `{ commentId, commentBodyExcerpt, skillVersionId, skillName, skillVersionLabel, authorDisplayName }` where `commentBodyExcerpt` is the **first 200 raw markdown characters** (no rendering on the server side; the notification UI is responsible for safe rendering, same as existing notifications).
- Failure mode: notification dispatch failure must NOT roll back the comment write. Wrap in a try/catch; log on failure.

## 8. Web UI

### 8.1 Component tree

```
<VersionCommentsSection versionId={...} />
├── <CommentComposer />          // shown if canPost
├── <CommentList />               // pinned-first then newest, paginated
│   └── <CommentItem comment={...} />
│       ├── markdown body (react-markdown + rehype-sanitize)
│       ├── author + timestamp + "edited" badge (if lastEditedAt)
│       ├── pin badge (if pinned)
│       └── action menu (edit/delete/pin) — gated on permissions[]
└── <LoadMoreButton />            // visible when hasNext
```

### 8.2 Data layer

- New hook: `useVersionComments(versionId)` — TanStack Query infinite query, `queryKey: ['version-comments', versionId]`, page size 20, `getNextPageParam` derived from `hasNext`.
- Mutations: `usePostComment(versionId)`, `useEditComment(commentId)`, `useDeleteComment(commentId)`, `useTogglePinComment(commentId)`. All four invalidate `['version-comments', versionId]` on success.
- Mock-vs-API switch lives inside each hook's `queryFn` / `mutationFn` — same single-point-of-switch pattern as `useAgents` / `useAgentDetail`.

### 8.3 Markdown rendering

- New deps: `react-markdown` + `rehype-sanitize`. Verify neither is already installed at implementation time.
- Renderer config: GFM **off** for v1 (no tables, no task lists, no autolinks). Smaller sanitization surface. Adding GFM later (item #13) requires re-testing the sanitizer allowlist.
- Composer has a "Write / Preview" tab toggle. Same renderer used for preview.

### 8.4 Eager-load + pagination

- The page hosting `<VersionCommentsSection>` prefetches `useVersionComments` on mount. First page visible immediately.
- Page 2+ loads on click of "Load more" — explicit, not infinite-scroll.

### 8.5 Empty state

"No comments yet. Be the first to add a change note." (i18n keyed.) Composer remains visible.

### 8.6 Optimistic UI

None in v1. Mutations show a spinner on the action button and re-fetch on success.

### 8.7 i18n keys

Add a `comments.*` section to `web/src/i18n/locales/en.json` and `zh.json`:

| Key | English | Chinese |
|---|---|---|
| `comments.empty` | "No comments yet. Be the first to add a change note." | "暂无评论。来写第一条变更说明。" |
| `comments.composer.placeholder` | "Add a comment (markdown supported)" | "添加评论（支持 markdown）" |
| `comments.composer.submit` | "Post" | "发布" |
| `comments.composer.preview` | "Preview" | "预览" |
| `comments.composer.write` | "Write" | "编辑" |
| `comments.action.edit` | "Edit" | "编辑" |
| `comments.action.delete` | "Delete" | "删除" |
| `comments.action.pin` | "Pin" | "置顶" |
| `comments.action.unpin` | "Unpin" | "取消置顶" |
| `comments.badge.pinned` | "Pinned" | "已置顶" |
| `comments.badge.edited` | "Edited" | "已编辑" |
| `comments.confirm.delete` | "Delete this comment? This cannot be undone." | "确认删除该评论？此操作无法撤销。" |
| `comments.error.loadFailed` | "Failed to load comments. Try again." | "加载评论失败。请重试。" |
| `comments.error.tooLong` | "Comment is too long (max 8192 characters)." | "评论过长（最多 8192 字符）。" |
| `comments.error.empty` | "Comment cannot be empty." | "评论不能为空。" |
| `comments.loadMore` | "Load more" | "加载更多" |

Final wording is implementation-time; the keys above are the contract.

### 8.8 UI tests

- Hook tests (one file per hook): `useVersionComments` returns paginated results; each mutation invalidates the right key; error paths surface correct i18n keys. Use the shared `createWrapper()` helper landed as part of the agent-feature follow-ups (memo entry #4).
- Component test for `<CommentItem>`: action menu visibility per `permissions` flags.
- Component test for `<CommentComposer>`: 8192-char length cap blocks submit; empty body blocks submit.
- Smoke test for `<VersionCommentsSection>`: render with mock data, assert pinned-first ordering.

## 9. Security

### 9.1 XSS / injection

- Server stores **raw markdown** as received (within length and non-blank constraints). No input-side HTML scrubbing.
- Web renders via `react-markdown` configured with `rehype-sanitize` using the default schema (strips `<script>`, event handler attributes like `onerror`, `javascript:` URLs, inline styles).
- **XSS regression test (mandatory):** one test file with a fixture comment containing each of `<script>alert(1)</script>`, `<img src=x onerror=alert(1)>`, `[click](javascript:alert(1))`. Assert the rendered DOM contains none of those handlers/URLs/script tags. Runs in CI on every web change.

### 9.2 SQL injection

Not applicable. JPA parameter binding everywhere; no string-concatenated queries.

### 9.3 Validation

- Body: non-empty after trim, ≤ 8192 chars (DB CHECK + service-layer pre-check).
- All inputs are valid UTF-8 (Spring default).
- Path parameters (`versionId`, `commentId`) are typed `Long`; framework rejects non-numeric.

### 9.4 CSRF

See §5.7.

### 9.5 Audit log

- Implementation step verifies whether an audit log infrastructure exists in the repo (recon-1 did not check).
- If yes: hook `comment.create / edit / delete / pin` events into it.
- If no: skip — adding a new audit infrastructure is out of scope.

## 10. Rollout

- Single deploy. No feature flag.
- Commit ordering for revertability:
  1. SQL migration (`V{N}__skill_version_comments.sql`) — table + indexes only, no code reads/writes it yet.
  2. Backend domain layer (entity + repository + service) + unit tests.
  3. Backend controllers + integration tests. After this commit the API exists and is callable.
  4. Frontend API client + hooks (against mocks initially) + hook tests.
  5. Frontend components + i18n + component tests.
  6. Wire `<VersionCommentsSection>` into the version-detail page. After this commit the feature is user-visible.
  7. Notification dispatcher hook (`COMMENT_POSTED` event type wired into `NotificationDispatcher`).

If step 6 ships and step 7 is delayed, the feature works without notifications — graceful degradation.

## 11. Performance

- **Dominant query** (list comments for one version, page 1): single index scan with `LIMIT 20` on `idx_skill_version_comment_version`. O(20) reads.
- **Notification fanout:** 1 row insert per comment (single recipient).
- **Admin namespace-role lookup:** 1 query per request, already cached by `VisibilityChecker` flow.
- **Per-row perm computation:** in-memory after data is loaded once per request. 60 calls per page is negligible.
- **No new N+1 risks introduced.**

## 12. Open verification at implementation time

These are codebase facts the design assumes but recon did not pin down. The implementation plan must confirm or adjust:

- Exact `@ControllerAdvice` error response shape used by `SkillRatingController`.
- CSRF configuration on existing mutating endpoints.
- Existence (and shape) of an audit log infrastructure.
- Whether `react-markdown` and `rehype-sanitize` are already in `web/package.json`.
- Whether a Flyway migration test harness exists (vs applying migrations against a fresh test schema).
- Next available Flyway migration number (`V{N}`) — likely V4 but verify.
- Exact cascade behavior of `skill_rating` (to mirror it for comments).

## 13. Predecessor work this depends on

The shared `createWrapper()` test helper (memo follow-up #4) lands **before** the comment hook tests are written, so the new hook tests use it from day one rather than being retrofitted. This is a small precondition, not a separate spec.

## 14. References

- Requirements memo: `docs/plans/2026-04-26-comments-feature-requirements.md`
- Closest existing pattern: `skill_rating` table + `SkillRatingController`
- Read-perm predicate: `VisibilityChecker.canAccess`
- Admin-perm pattern: `NamespaceAccessPolicy` (OWNER/ADMIN check)
- Notification system: `server/skillhub-notification/.../NotificationDispatcher`
- Predecessor session: agent frontend MVP (memo entry 2026-04-26)

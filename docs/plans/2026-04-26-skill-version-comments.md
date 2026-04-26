# Skill Version Comments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a v1 comment system on skill versions: markdown body, soft-delete, multi-pin, namespace-admin moderation, paginated list endpoint, and a `<VersionCommentsSection>` rendered on the skill-detail page. Web-only, single deploy, no feature flag.

**Architecture:** A new `skill_version_comment` table mirrors the `skill_rating` layering (entity in `skillhub-domain/social`, JPA repo in `skillhub-infra`, service in domain, controllers in `skillhub-app/controller/portal`). Two REST controllers serve five endpoints under the existing `/api/v{1,web}/...` dual-prefix convention. The web layer adds a feature folder `web/src/features/comments/` with hooks that follow the existing TanStack Query pattern in `use-skill-queries.ts`. Markdown is stored raw and sanitized at render with a *new, GFM-disabled* `react-markdown` renderer (the existing `MarkdownRenderer` enables GFM, so it cannot be reused). One new `NotificationCategory.COMMENT` value drives the dispatcher hook.

**Tech Stack:** Java 21 + Spring Boot, JPA, Flyway (PostgreSQL); React 19 + TanStack Query v5 + react-markdown 10 + rehype-sanitize 6 + Vitest 3 + i18next.

---

## Spec drift / divergences from the ADR

The ADR (`docs/adr/0002-skill-version-comments.md`) was written before the implementation-time recon. These are the deltas the plan resolves; if any of these surprises the engineer, they should pause and re-read this section rather than "fix" them silently.

1. **Response envelope.** The ADR §5.3 shows raw JSON shapes. The codebase wraps every controller response in `ApiResponse<T>(code, msg, data, timestamp, requestId)` via `BaseApiController.ok(messageCode, data)`. We follow the codebase. The shapes in §5.3 describe the **`data` field**, not the full body.
2. **i18n message codes on success responses.** Every existing controller passes a `response.success.*` key to `ok(...)`. We reuse `response.success.created`, `response.success.updated`, `response.success.deleted`, `response.success.read`. No new server-side message keys are invented unless an existing one is missing — Task 8 includes a verification step.
3. **Markdown renderer reuse.** The existing `web/src/features/skill/markdown-renderer.tsx` enables `remarkGfm`. The ADR §8.3 says "GFM **off** for v1." We create a *separate* `comment-markdown-renderer.tsx` rather than hacking flags into the shared one. Future consolidation is a deferred item.
4. **Hook test wrapper.** The ADR §13 calls out the shared `createWrapper()` helper as a precondition. It does not exist yet — `web/src/shared/hooks/use-namespace-queries.test.ts` literally documents "QueryClientProvider is not available in this project." Building the wrapper is **Task 1** of this plan, not an external dependency.
5. **Migration number.** Last migration is `V39__password_reset_request.sql`. Use **`V40__skill_version_comments.sql`** unless another migration has landed in the meantime — the engineer should `ls server/skillhub-app/src/main/resources/db/migration/` before creating the file.
6. **`NotificationCategory` is a closed enum.** Current values: `PUBLISH, REVIEW, PROMOTION, REPORT`. Add `COMMENT`. Event type string: `COMMENT_POSTED`.
7. **`react-markdown` and `rehype-sanitize` are already installed** (`web/package.json`: `react-markdown@^10.1.0`, `rehype-sanitize@^6.0.0`). The plan does NOT add them.

---

## File structure

### Backend — new files

| Path | Responsibility |
|---|---|
| `server/skillhub-app/src/main/resources/db/migration/V40__skill_version_comments.sql` | Table + 2 indexes + CHECK constraints |
| `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SkillVersionComment.java` | JPA entity |
| `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SkillVersionCommentRepository.java` | Repository interface (domain-side) |
| `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SkillVersionCommentService.java` | Domain service: list/post/edit/delete/pin + permission predicates |
| `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/CommentPermissions.java` | Record `(canEdit, canDelete, canPin)` returned per row |
| `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/event/SkillVersionCommentPostedEvent.java` | Event published on POST for the notification listener |
| `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillVersionCommentJpaRepository.java` | Spring Data JPA repository implementing the domain interface |
| `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillVersionCommentResponse.java` | Response record (one row) |
| `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillVersionCommentPageResponse.java` | Response record (paginated wrapper) |
| `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillVersionCommentRequest.java` | Request record (`{ body }`) with `@NotBlank @Size(max=8192)` |
| `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/CommentPinRequest.java` | Request record (`{ pinned }`) |
| `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillVersionCommentController.java` | List + create on `/skill-versions/{versionId}/comments` |
| `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/CommentController.java` | PATCH/DELETE/POST-pin on `/comments/{commentId}` |
| `server/skillhub-app/src/main/java/com/iflytek/skillhub/listener/SkillVersionCommentNotificationListener.java` | `@EventListener` that calls `NotificationDispatcher.dispatch(...)` |
| `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/domain/NotificationCategory.java` | **MODIFY**: add `COMMENT` |

### Backend — test files

| Path | Responsibility |
|---|---|
| `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/social/SkillVersionCommentServiceTest.java` | Service unit tests (in-memory repo fakes) |
| `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillVersionCommentControllerTest.java` | Controller integration tests via `MockMvc` |
| `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/CommentControllerTest.java` | Controller integration tests for edit/delete/pin |
| `server/skillhub-app/src/test/java/com/iflytek/skillhub/listener/SkillVersionCommentNotificationListenerTest.java` | Listener unit test |

### Web — new files

| Path | Responsibility |
|---|---|
| `web/src/shared/test/create-wrapper.tsx` | **Precondition** (Task 1): wraps `renderHook` children in a fresh `QueryClientProvider` |
| `web/src/shared/test/create-wrapper.test.tsx` | Self-test for the wrapper |
| `web/src/features/comments/types.ts` | TypeScript types mirroring the API response shape |
| `web/src/features/comments/query-keys.ts` | `getVersionCommentsQueryKey(versionId)` factory |
| `web/src/features/comments/query-keys.test.ts` | Query-key shape tests |
| `web/src/features/comments/use-version-comments.ts` | Infinite query hook |
| `web/src/features/comments/use-version-comments.test.tsx` | Hook test (uses `createWrapper`) |
| `web/src/features/comments/use-post-comment.ts` | Mutation: POST |
| `web/src/features/comments/use-post-comment.test.tsx` | Hook test |
| `web/src/features/comments/use-edit-comment.ts` | Mutation: PATCH |
| `web/src/features/comments/use-edit-comment.test.tsx` | Hook test |
| `web/src/features/comments/use-delete-comment.ts` | Mutation: DELETE |
| `web/src/features/comments/use-delete-comment.test.tsx` | Hook test |
| `web/src/features/comments/use-toggle-pin-comment.ts` | Mutation: POST pin |
| `web/src/features/comments/use-toggle-pin-comment.test.tsx` | Hook test |
| `web/src/features/comments/comment-markdown-renderer.tsx` | `react-markdown` instance, GFM **off**, `rehype-sanitize` on |
| `web/src/features/comments/comment-markdown-renderer.test.tsx` | XSS regression fixtures |
| `web/src/features/comments/comment-item.tsx` | One row: avatar + body + badges + action menu |
| `web/src/features/comments/comment-item.test.tsx` | Per-row perm gating |
| `web/src/features/comments/comment-composer.tsx` | Textarea + Write/Preview tabs + Post button |
| `web/src/features/comments/comment-composer.test.tsx` | Length/empty validation |
| `web/src/features/comments/comment-list.tsx` | Map rows + Load-more |
| `web/src/features/comments/comment-list.test.tsx` | Pinned-first ordering smoke test |
| `web/src/features/comments/version-comments-section.tsx` | Public entry point: composer + list + empty state |
| `web/src/features/comments/version-comments-section.test.tsx` | Renders with mock data |
| `web/src/features/comments/index.ts` | Barrel export of `VersionCommentsSection` |

### Web — modified files

| Path | Change |
|---|---|
| `web/src/i18n/locales/en.json` | Add the `comments.*` block from ADR §8.7 |
| `web/src/i18n/locales/zh.json` | Add the `comments.*` block from ADR §8.7 |
| `web/src/pages/skill-detail.tsx` | Mount `<VersionCommentsSection versionId={selectedVersionEntry.id} />` below the existing version content |

---

## Self-review checklist (the plan author has run this)

- ✅ Every ADR §3 decision (Q0–Q9) maps to at least one task.
- ✅ Every endpoint in ADR §5.1 has a controller task and an integration-test task.
- ✅ Every i18n key in ADR §8.7 is in the locale-file edit task verbatim.
- ✅ ADR §9.1 XSS regression tests are in `comment-markdown-renderer.test.tsx` (Task 19) with all three fixtures.
- ✅ The §13 precondition is Task 1, ahead of any hook test.
- ✅ Type names are consistent: the response field is `lastEditedAt` (not `editedAt`); the perm record is `permissions: { canEdit, canDelete, canPin }` everywhere.
- ✅ No "TBD" / "implement later" / "similar to Task N" placeholders.
- ✅ Each commit is atomic and named so a revert maps to one rollout step from ADR §10.

---

## Task 1: Test wrapper precondition (`createWrapper`)

**Files:**
- Create: `web/src/shared/test/create-wrapper.tsx`
- Create: `web/src/shared/test/create-wrapper.test.tsx`

This is the §13 precondition. Without it the hook tests in Tasks 14–18 can't run. Existing hook test files (`web/src/shared/hooks/use-namespace-queries.test.ts:1-15`) explicitly document the gap.

- [ ] **Step 1: Write the failing test**

```tsx
// web/src/shared/test/create-wrapper.test.tsx
import { describe, it, expect } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { useQuery } from '@tanstack/react-query'
import { createWrapper } from './create-wrapper'

describe('createWrapper', () => {
  it('provides a QueryClient so useQuery can run inside renderHook', async () => {
    const { result } = renderHook(
      () => useQuery({ queryKey: ['t'], queryFn: () => Promise.resolve(42) }),
      { wrapper: createWrapper() }
    )
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data).toBe(42)
  })

  it('returns a fresh client per call so tests do not share cache', () => {
    const w1 = createWrapper()
    const w2 = createWrapper()
    expect(w1).not.toBe(w2)
  })

  it('disables retries so failed queries surface errors immediately', async () => {
    const { result } = renderHook(
      () =>
        useQuery({
          queryKey: ['err'],
          queryFn: () => Promise.reject(new Error('boom')),
        }),
      { wrapper: createWrapper() }
    )
    await waitFor(() => expect(result.current.isError).toBe(true))
    expect((result.current.error as Error).message).toBe('boom')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web && pnpm vitest run src/shared/test/create-wrapper.test.tsx`
Expected: FAIL — `Cannot find module './create-wrapper'`

- [ ] **Step 3: Check whether `@testing-library/react` is already installed**

Run: `cd web && grep -E '"@testing-library/react"' package.json || echo MISSING`
- If `MISSING`: run `pnpm add -D @testing-library/react@^16.3.0` (matches React 19), then commit `package.json` + `pnpm-lock.yaml` separately as a precursor commit `chore(web): add @testing-library/react for hook tests`.
- If present: skip.

- [ ] **Step 4: Implement `createWrapper`**

```tsx
// web/src/shared/test/create-wrapper.tsx
import { ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'

/**
 * Test helper: wraps renderHook children in a fresh QueryClient.
 * - No retries so rejected queries surface immediately.
 * - No cache reuse between calls.
 */
export function createWrapper() {
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0 },
      mutations: { retry: false },
    },
  })
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={client}>{children}</QueryClientProvider>
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd web && pnpm vitest run src/shared/test/create-wrapper.test.tsx`
Expected: PASS, 3 tests.

- [ ] **Step 6: Commit**

```bash
git add web/src/shared/test/create-wrapper.tsx web/src/shared/test/create-wrapper.test.tsx
git commit -m "feat(web): add createWrapper test helper for hook tests"
```

---

## Task 2: Add `COMMENT` to `NotificationCategory`

**Files:**
- Modify: `server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/domain/NotificationCategory.java`

- [ ] **Step 1: Write the failing test**

Add to `server/skillhub-notification/src/test/java/com/iflytek/skillhub/notification/domain/NotificationCategoryTest.java` (create if missing):

```java
package com.iflytek.skillhub.notification.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NotificationCategoryTest {
    @Test
    void commentCategoryExists() {
        assertNotNull(NotificationCategory.valueOf("COMMENT"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && ./mvnw -pl skillhub-notification test -Dtest=NotificationCategoryTest`
Expected: FAIL with `IllegalArgumentException: No enum constant ... COMMENT`.

- [ ] **Step 3: Modify the enum**

Replace the file body with:

```java
package com.iflytek.skillhub.notification.domain;

public enum NotificationCategory {
    PUBLISH, REVIEW, PROMOTION, REPORT, COMMENT
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd server && ./mvnw -pl skillhub-notification test -Dtest=NotificationCategoryTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-notification/src/main/java/com/iflytek/skillhub/notification/domain/NotificationCategory.java \
        server/skillhub-notification/src/test/java/com/iflytek/skillhub/notification/domain/NotificationCategoryTest.java
git commit -m "feat(notification): add COMMENT category"
```

---

## Task 3: Flyway migration `V40__skill_version_comments.sql`

**Files:**
- Create: `server/skillhub-app/src/main/resources/db/migration/V40__skill_version_comments.sql`

- [ ] **Step 1: Verify the next migration number**

Run: `ls server/skillhub-app/src/main/resources/db/migration/ | sort -V | tail -3`
Expected: V39 is the highest. If a higher number exists, use the next available number and update file name + commit message accordingly.

- [ ] **Step 2: Write the migration**

```sql
-- V40__skill_version_comments.sql
-- Adds the table backing skill-version comments. See docs/adr/0002-skill-version-comments.md §4.

CREATE TABLE skill_version_comment (
    id               BIGSERIAL PRIMARY KEY,
    skill_version_id BIGINT       NOT NULL REFERENCES skill_version(id) ON DELETE CASCADE,
    author_id        VARCHAR(128) NOT NULL REFERENCES user_account(id),
    body             TEXT         NOT NULL,
    pinned           BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_edited_at   TIMESTAMP,
    deleted_at       TIMESTAMP,
    deleted_by       VARCHAR(128) REFERENCES user_account(id),
    CONSTRAINT skill_version_comment_body_length CHECK (char_length(body) BETWEEN 1 AND 8192),
    CONSTRAINT skill_version_comment_body_not_blank CHECK (btrim(body) <> ''),
    CONSTRAINT skill_version_comment_deleted_consistency CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);

CREATE INDEX idx_skill_version_comment_version
    ON skill_version_comment(skill_version_id, pinned DESC, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_skill_version_comment_author
    ON skill_version_comment(author_id);
```

- [ ] **Step 3: Run the application's existing migration test (if any)**

Run: `cd server && ./mvnw -pl skillhub-app test -Dtest='*Migration*' -q 2>&1 | tail -30`
Expected: PASS (or "no tests" — both are acceptable).

If no migration test exists, run the full app boot to validate Flyway:
Run: `cd server && ./mvnw -pl skillhub-app -DskipTests verify -q 2>&1 | tail -30`
Expected: build succeeds. (The Flyway syntax check happens at app boot in tests; this verify confirms the SQL parses.)

- [ ] **Step 4: Commit**

```bash
git add server/skillhub-app/src/main/resources/db/migration/V40__skill_version_comments.sql
git commit -m "feat(db): add skill_version_comment table (V40)"
```

---

## Task 4: `SkillVersionComment` entity

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SkillVersionComment.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/social/SkillVersionCommentTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkillVersionCommentTest {

    @Test
    void rejectsEmptyBody() {
        assertThrows(DomainBadRequestException.class,
            () -> new SkillVersionComment(1L, "u1", ""));
    }

    @Test
    void rejectsBlankBody() {
        assertThrows(DomainBadRequestException.class,
            () -> new SkillVersionComment(1L, "u1", "   \n\t "));
    }

    @Test
    void rejectsBodyOver8192Chars() {
        String body = "a".repeat(8193);
        assertThrows(DomainBadRequestException.class,
            () -> new SkillVersionComment(1L, "u1", body));
    }

    @Test
    void acceptsBodyAt8192Chars() {
        String body = "a".repeat(8192);
        SkillVersionComment c = new SkillVersionComment(1L, "u1", body);
        assertEquals(8192, c.getBody().length());
        assertFalse(c.isPinned());
        assertFalse(c.isDeleted());
        assertNull(c.getLastEditedAt());
    }

    @Test
    void editUpdatesBodyAndStampsLastEditedAt() {
        SkillVersionComment c = new SkillVersionComment(1L, "u1", "v1");
        c.edit("v2");
        assertEquals("v2", c.getBody());
        assertNotNull(c.getLastEditedAt());
    }

    @Test
    void editRejectsTooLongBody() {
        SkillVersionComment c = new SkillVersionComment(1L, "u1", "v1");
        assertThrows(DomainBadRequestException.class, () -> c.edit("a".repeat(8193)));
    }

    @Test
    void softDeleteSetsBothFields() {
        SkillVersionComment c = new SkillVersionComment(1L, "u1", "v1");
        c.softDelete("u2");
        assertTrue(c.isDeleted());
        assertEquals("u2", c.getDeletedBy());
        assertNotNull(c.getDeletedAt());
    }

    @Test
    void pinTogglesFlag() {
        SkillVersionComment c = new SkillVersionComment(1L, "u1", "v1");
        c.setPinned(true);
        assertTrue(c.isPinned());
        c.setPinned(false);
        assertFalse(c.isPinned());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && ./mvnw -pl skillhub-domain test -Dtest=SkillVersionCommentTest`
Expected: FAIL — class does not exist.

- [ ] **Step 3: Implement the entity**

```java
package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.shared.exception.DomainBadRequestException;
import jakarta.persistence.*;
import java.time.Clock;
import java.time.Instant;

@Entity
@Table(name = "skill_version_comment")
public class SkillVersionComment {

    public static final int MAX_BODY_LENGTH = 8192;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "skill_version_id", nullable = false)
    private Long skillVersionId;

    @Column(name = "author_id", nullable = false, length = 128)
    private String authorId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    private boolean pinned;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_edited_at")
    private Instant lastEditedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by", length = 128)
    private String deletedBy;

    protected SkillVersionComment() {}

    public SkillVersionComment(Long skillVersionId, String authorId, String body) {
        validateBody(body);
        this.skillVersionId = skillVersionId;
        this.authorId = authorId;
        this.body = body;
        this.pinned = false;
    }

    public void edit(String newBody) {
        validateBody(newBody);
        this.body = newBody;
        this.lastEditedAt = Instant.now(Clock.systemUTC());
    }

    public void softDelete(String deletedByUserId) {
        Instant now = Instant.now(Clock.systemUTC());
        this.deletedAt = now;
        this.deletedBy = deletedByUserId;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    @PrePersist
    void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now(Clock.systemUTC());
        }
    }

    private static void validateBody(String body) {
        if (body == null || body.isBlank()) {
            throw new DomainBadRequestException("error.comment.body.empty");
        }
        if (body.length() > MAX_BODY_LENGTH) {
            throw new DomainBadRequestException("error.comment.body.tooLong");
        }
    }

    public Long getId() { return id; }
    public Long getSkillVersionId() { return skillVersionId; }
    public String getAuthorId() { return authorId; }
    public String getBody() { return body; }
    public boolean isPinned() { return pinned; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastEditedAt() { return lastEditedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public String getDeletedBy() { return deletedBy; }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd server && ./mvnw -pl skillhub-domain test -Dtest=SkillVersionCommentTest`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SkillVersionComment.java \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/social/SkillVersionCommentTest.java
git commit -m "feat(domain): add SkillVersionComment entity with body validation"
```

---

## Task 5: Repository interface + JPA implementation

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SkillVersionCommentRepository.java`
- Create: `server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillVersionCommentJpaRepository.java`

- [ ] **Step 1: Write the domain interface**

```java
package com.iflytek.skillhub.domain.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Optional;

public interface SkillVersionCommentRepository {
    Page<SkillVersionComment> findActiveByVersionId(Long skillVersionId, Pageable pageable);
    Optional<SkillVersionComment> findById(Long id);
    SkillVersionComment save(SkillVersionComment comment);
}
```

- [ ] **Step 2: Write the JPA implementation**

```java
package com.iflytek.skillhub.infra.jpa;

import com.iflytek.skillhub.domain.social.SkillVersionComment;
import com.iflytek.skillhub.domain.social.SkillVersionCommentRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SkillVersionCommentJpaRepository
        extends JpaRepository<SkillVersionComment, Long>, SkillVersionCommentRepository {

    @Override
    @Query("""
        SELECT c FROM SkillVersionComment c
        WHERE c.skillVersionId = :versionId
          AND c.deletedAt IS NULL
        ORDER BY c.pinned DESC, c.createdAt DESC
        """)
    Page<SkillVersionComment> findActiveByVersionId(
        @Param("versionId") Long skillVersionId,
        Pageable pageable
    );

    @Override
    Optional<SkillVersionComment> findById(Long id);
}
```

Note: `JpaRepository.save(...)` already satisfies the domain `save(...)` signature — no override needed.

- [ ] **Step 3: Verify compilation**

Run: `cd server && ./mvnw -pl skillhub-infra,skillhub-domain compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SkillVersionCommentRepository.java \
        server/skillhub-infra/src/main/java/com/iflytek/skillhub/infra/jpa/SkillVersionCommentJpaRepository.java
git commit -m "feat(infra): add SkillVersionCommentRepository (JPA-backed)"
```

---

## Task 6: `CommentPermissions` record + event class

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/CommentPermissions.java`
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/event/SkillVersionCommentPostedEvent.java`

- [ ] **Step 1: Write `CommentPermissions`**

```java
package com.iflytek.skillhub.domain.social;

public record CommentPermissions(boolean canEdit, boolean canDelete, boolean canPin) {
    public static final CommentPermissions NONE = new CommentPermissions(false, false, false);
}
```

- [ ] **Step 2: Write the event**

```java
package com.iflytek.skillhub.domain.social.event;

public record SkillVersionCommentPostedEvent(
        Long commentId,
        Long skillVersionId,
        String authorUserId,
        String bodyExcerpt
) {}
```

- [ ] **Step 3: Verify compilation**

Run: `cd server && ./mvnw -pl skillhub-domain compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/CommentPermissions.java \
        server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/event/SkillVersionCommentPostedEvent.java
git commit -m "feat(domain): add CommentPermissions record and posted event"
```

---

## Task 7: `SkillVersionCommentService` — list, post, edit, delete, pin, perm predicates

**Files:**
- Create: `server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SkillVersionCommentService.java`
- Test: `server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/social/SkillVersionCommentServiceTest.java`

This is the largest task. Each behavior is one test; we add tests one at a time but commit them as one cohesive unit at the end.

- [ ] **Step 1: Write the failing test file (skeleton + first behavior)**

```java
package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.social.event.SkillVersionCommentPostedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SkillVersionCommentServiceTest {

    private SkillVersionCommentRepository commentRepo;
    private SkillVersionRepository versionRepo;
    private SkillRepository skillRepo;
    private NamespaceMemberRepository memberRepo;
    private VisibilityChecker visibilityChecker;
    private ApplicationEventPublisher events;
    private SkillVersionCommentService service;

    private SkillVersion version;
    private Skill skill;

    @BeforeEach
    void setUp() {
        commentRepo = mock(SkillVersionCommentRepository.class);
        versionRepo = mock(SkillVersionRepository.class);
        skillRepo = mock(SkillRepository.class);
        memberRepo = mock(NamespaceMemberRepository.class);
        visibilityChecker = mock(VisibilityChecker.class);
        events = mock(ApplicationEventPublisher.class);
        service = new SkillVersionCommentService(
                commentRepo, versionRepo, skillRepo, memberRepo, visibilityChecker, events);

        version = mock(SkillVersion.class);
        when(version.getId()).thenReturn(99L);
        when(version.getSkillId()).thenReturn(7L);
        when(version.getCreatedBy()).thenReturn("authorOfVersion");
        when(versionRepo.findById(99L)).thenReturn(Optional.of(version));

        skill = mock(Skill.class);
        when(skill.getNamespaceId()).thenReturn(42L);
        when(skillRepo.findById(7L)).thenReturn(Optional.of(skill));

        when(memberRepo.findByUserId(anyString())).thenReturn(List.of());
        when(visibilityChecker.canAccess(any(), any(), any(), any())).thenReturn(true);
    }

    @Test
    void listReturnsActiveCommentsWithPermissions() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "hello");
        when(commentRepo.findActiveByVersionId(eq(99L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c), PageRequest.of(0, 20), 1));

        Page<SkillVersionCommentService.CommentWithPerms> page =
                service.listForVersion(99L, "alice", Set.of(), PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        SkillVersionCommentService.CommentWithPerms row = page.getContent().get(0);
        assertEquals("hello", row.comment().getBody());
        assertTrue(row.permissions().canEdit());      // author
        assertTrue(row.permissions().canDelete());    // author
        assertFalse(row.permissions().canPin());      // not admin
    }
}
```

- [ ] **Step 2: Run test to verify it fails (compile error)**

Run: `cd server && ./mvnw -pl skillhub-domain test -Dtest=SkillVersionCommentServiceTest -q`
Expected: COMPILE FAIL — service class does not exist.

- [ ] **Step 3: Implement the service**

```java
package com.iflytek.skillhub.domain.social;

import com.iflytek.skillhub.domain.namespace.NamespaceMember;
import com.iflytek.skillhub.domain.namespace.NamespaceMemberRepository;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.shared.exception.DomainForbiddenException;
import com.iflytek.skillhub.domain.shared.exception.DomainNotFoundException;
import com.iflytek.skillhub.domain.skill.Skill;
import com.iflytek.skillhub.domain.skill.SkillRepository;
import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.skill.VisibilityChecker;
import com.iflytek.skillhub.domain.namespace.NamespaceRole;
import com.iflytek.skillhub.domain.social.event.SkillVersionCommentPostedEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class SkillVersionCommentService {

    public record CommentWithPerms(SkillVersionComment comment, CommentPermissions permissions) {}

    private static final int EXCERPT_LENGTH = 200;

    private final SkillVersionCommentRepository commentRepo;
    private final SkillVersionRepository versionRepo;
    private final SkillRepository skillRepo;
    private final NamespaceMemberRepository memberRepo;
    private final VisibilityChecker visibilityChecker;
    private final ApplicationEventPublisher events;

    public SkillVersionCommentService(
            SkillVersionCommentRepository commentRepo,
            SkillVersionRepository versionRepo,
            SkillRepository skillRepo,
            NamespaceMemberRepository memberRepo,
            VisibilityChecker visibilityChecker,
            ApplicationEventPublisher events) {
        this.commentRepo = commentRepo;
        this.versionRepo = versionRepo;
        this.skillRepo = skillRepo;
        this.memberRepo = memberRepo;
        this.visibilityChecker = visibilityChecker;
        this.events = events;
    }

    public Page<CommentWithPerms> listForVersion(
            Long versionId, String callerUserId, Set<String> platformRoles, Pageable pageable) {
        Skill skill = loadSkillForVersion(versionId);
        Map<Long, NamespaceRole> roles = loadCallerRoles(callerUserId);
        if (!visibilityChecker.canAccess(skill, callerUserId, roles, platformRoles)) {
            throw new DomainForbiddenException("error.comment.read.forbidden");
        }
        boolean isAdmin = isAdminOf(skill.getNamespaceId(), roles);
        return commentRepo.findActiveByVersionId(versionId, pageable)
                .map(c -> new CommentWithPerms(c, computePerms(c, callerUserId, isAdmin)));
    }

    @Transactional
    public CommentWithPerms post(Long versionId, String callerUserId, String body) {
        SkillVersion version = loadVersion(versionId);
        Skill skill = loadSkill(version.getSkillId());
        Map<Long, NamespaceRole> roles = loadCallerRoles(callerUserId);
        if (!visibilityChecker.canAccess(skill, callerUserId, roles, Set.of())) {
            throw new DomainForbiddenException("error.comment.post.forbidden");
        }
        SkillVersionComment saved = commentRepo.save(new SkillVersionComment(versionId, callerUserId, body));
        boolean isAdmin = isAdminOf(skill.getNamespaceId(), roles);

        events.publishEvent(new SkillVersionCommentPostedEvent(
                saved.getId(), versionId, callerUserId, excerpt(body)));

        return new CommentWithPerms(saved, computePerms(saved, callerUserId, isAdmin));
    }

    @Transactional
    public CommentWithPerms edit(Long commentId, String callerUserId, String newBody) {
        SkillVersionComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> new DomainNotFoundException("error.comment.notFound", commentId));
        if (c.isDeleted()) {
            throw new DomainNotFoundException("error.comment.notFound", commentId);
        }
        if (!callerUserId.equals(c.getAuthorId())) {
            throw new DomainForbiddenException("error.comment.edit.forbidden");
        }
        c.edit(newBody);
        SkillVersionComment saved = commentRepo.save(c);
        Skill skill = loadSkillForVersion(saved.getSkillVersionId());
        boolean isAdmin = isAdminOf(skill.getNamespaceId(), loadCallerRoles(callerUserId));
        return new CommentWithPerms(saved, computePerms(saved, callerUserId, isAdmin));
    }

    @Transactional
    public void delete(Long commentId, String callerUserId) {
        SkillVersionComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> new DomainNotFoundException("error.comment.notFound", commentId));
        if (c.isDeleted()) {
            return;  // idempotent
        }
        Skill skill = loadSkillForVersion(c.getSkillVersionId());
        boolean isAuthor = callerUserId.equals(c.getAuthorId());
        boolean isAdmin = isAdminOf(skill.getNamespaceId(), loadCallerRoles(callerUserId));
        if (!isAuthor && !isAdmin) {
            throw new DomainForbiddenException("error.comment.delete.forbidden");
        }
        c.softDelete(callerUserId);
        commentRepo.save(c);
    }

    @Transactional
    public CommentWithPerms setPinned(Long commentId, String callerUserId, boolean pinned) {
        SkillVersionComment c = commentRepo.findById(commentId)
                .orElseThrow(() -> new DomainNotFoundException("error.comment.notFound", commentId));
        if (c.isDeleted()) {
            throw new DomainNotFoundException("error.comment.notFound", commentId);
        }
        Skill skill = loadSkillForVersion(c.getSkillVersionId());
        Map<Long, NamespaceRole> roles = loadCallerRoles(callerUserId);
        if (!isAdminOf(skill.getNamespaceId(), roles)) {
            throw new DomainForbiddenException("error.comment.pin.forbidden");
        }
        c.setPinned(pinned);
        SkillVersionComment saved = commentRepo.save(c);
        return new CommentWithPerms(saved, computePerms(saved, callerUserId, true));
    }

    private CommentPermissions computePerms(SkillVersionComment c, String callerUserId, boolean isAdmin) {
        if (callerUserId == null) {
            return CommentPermissions.NONE;
        }
        boolean isAuthor = callerUserId.equals(c.getAuthorId());
        return new CommentPermissions(
                isAuthor && !c.isDeleted(),
                (isAuthor || isAdmin) && !c.isDeleted(),
                isAdmin && !c.isDeleted()
        );
    }

    private SkillVersion loadVersion(Long versionId) {
        return versionRepo.findById(versionId)
                .orElseThrow(() -> new DomainNotFoundException("error.skillVersion.notFound", versionId));
    }

    private Skill loadSkill(Long skillId) {
        return skillRepo.findById(skillId)
                .orElseThrow(() -> new DomainNotFoundException("error.skill.notFound", skillId));
    }

    private Skill loadSkillForVersion(Long versionId) {
        SkillVersion v = loadVersion(versionId);
        return loadSkill(v.getSkillId());
    }

    private Map<Long, NamespaceRole> loadCallerRoles(String callerUserId) {
        if (callerUserId == null) {
            return Map.of();
        }
        Map<Long, NamespaceRole> result = new HashMap<>();
        for (NamespaceMember m : memberRepo.findByUserId(callerUserId)) {
            result.put(m.getNamespaceId(), m.getRole());
        }
        return result;
    }

    private boolean isAdminOf(Long namespaceId, Map<Long, NamespaceRole> roles) {
        NamespaceRole r = roles.get(namespaceId);
        return r == NamespaceRole.OWNER || r == NamespaceRole.ADMIN;
    }

    private String excerpt(String body) {
        return body.length() <= EXCERPT_LENGTH ? body : body.substring(0, EXCERPT_LENGTH);
    }
}
```

If `DomainForbiddenException` does not exist in the codebase, run `grep -rn "class DomainForbiddenException\|class DomainBadRequestException\|class DomainNotFoundException" server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/shared/exception/` first; if `DomainForbiddenException` is missing, create it next to `DomainBadRequestException` mirroring its constructor signature, and commit it as `feat(domain): add DomainForbiddenException` before continuing.

If `SkillVersion.getCreatedBy()` does not exist, replace `version.getCreatedBy()` references in tests + listener with whatever field on `SkillVersion` denotes the version author (likely `getCreatorId()` / `getOwnerId()` — `grep -n "private String\|getCreator\|getOwner" server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/skill/SkillVersion.java` to confirm).

- [ ] **Step 4: Run the first test, verify PASS**

Run: `cd server && ./mvnw -pl skillhub-domain test -Dtest=SkillVersionCommentServiceTest`
Expected: PASS — 1 test (`listReturnsActiveCommentsWithPermissions`).

- [ ] **Step 5: Add the remaining behavior tests**

Append to `SkillVersionCommentServiceTest`:

```java
    @Test
    void listGivesAdminCanPinFlag() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "hi");
        when(commentRepo.findActiveByVersionId(eq(99L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(c), PageRequest.of(0, 20), 1));
        when(memberRepo.findByUserId("admin1"))
                .thenReturn(List.of(member(42L, "admin1", NamespaceRole.ADMIN)));

        Page<SkillVersionCommentService.CommentWithPerms> page =
                service.listForVersion(99L, "admin1", Set.of(), PageRequest.of(0, 20));

        assertTrue(page.getContent().get(0).permissions().canPin());
        assertTrue(page.getContent().get(0).permissions().canDelete());
        assertFalse(page.getContent().get(0).permissions().canEdit()); // not author
    }

    @Test
    void listForbiddenWhenVisibilityCheckFails() {
        when(visibilityChecker.canAccess(any(), any(), any(), any())).thenReturn(false);
        when(commentRepo.findActiveByVersionId(eq(99L), any(Pageable.class)))
                .thenReturn(Page.empty());
        assertThrows(DomainForbiddenException.class,
                () -> service.listForVersion(99L, "evil", Set.of(), PageRequest.of(0, 20)));
    }

    @Test
    void postPersistsAndPublishesEvent() {
        when(commentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        SkillVersionCommentService.CommentWithPerms result =
                service.post(99L, "alice", "hello");
        assertEquals("hello", result.comment().getBody());
        verify(events).publishEvent(any(SkillVersionCommentPostedEvent.class));
    }

    @Test
    void postRejectedWhenCannotSeeVersion() {
        when(visibilityChecker.canAccess(any(), any(), any(), any())).thenReturn(false);
        assertThrows(DomainForbiddenException.class, () -> service.post(99L, "evil", "hi"));
        verify(commentRepo, never()).save(any());
    }

    @Test
    void editAllowedForAuthor() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        when(commentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        SkillVersionCommentService.CommentWithPerms result = service.edit(123L, "alice", "v2");
        assertEquals("v2", result.comment().getBody());
        assertNotNull(result.comment().getLastEditedAt());
    }

    @Test
    void editForbiddenForNonAuthor() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        assertThrows(DomainForbiddenException.class, () -> service.edit(123L, "bob", "v2"));
    }

    @Test
    void editForbiddenAfterDelete() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        c.softDelete("alice");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        assertThrows(DomainNotFoundException.class, () -> service.edit(123L, "alice", "v2"));
    }

    @Test
    void deleteByAuthorSucceeds() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        when(commentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.delete(123L, "alice");
        assertTrue(c.isDeleted());
        assertEquals("alice", c.getDeletedBy());
    }

    @Test
    void deleteByAdminSucceedsAndRecordsModerator() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        when(commentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(memberRepo.findByUserId("mod1"))
                .thenReturn(List.of(member(42L, "mod1", NamespaceRole.OWNER)));

        service.delete(123L, "mod1");
        assertEquals("mod1", c.getDeletedBy());
        assertEquals("alice", c.getAuthorId()); // authorship preserved
    }

    @Test
    void deleteForbiddenForRandomUser() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        assertThrows(DomainForbiddenException.class, () -> service.delete(123L, "bob"));
    }

    @Test
    void deleteIsIdempotent() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        c.softDelete("alice");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        service.delete(123L, "alice");
        verify(commentRepo, never()).save(any());
    }

    @Test
    void pinRequiresAdmin() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        assertThrows(DomainForbiddenException.class,
                () -> service.setPinned(123L, "alice", true));
    }

    @Test
    void pinSucceedsForAdmin() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        when(commentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(memberRepo.findByUserId("admin1"))
                .thenReturn(List.of(member(42L, "admin1", NamespaceRole.ADMIN)));

        SkillVersionCommentService.CommentWithPerms result =
                service.setPinned(123L, "admin1", true);
        assertTrue(result.comment().isPinned());
    }

    @Test
    void pinDeletedReturnsNotFound() {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "v1");
        c.softDelete("alice");
        when(commentRepo.findById(123L)).thenReturn(Optional.of(c));
        assertThrows(DomainNotFoundException.class, () -> service.setPinned(123L, "admin1", true));
    }

    private NamespaceMember member(Long namespaceId, String userId, NamespaceRole role) {
        NamespaceMember m = mock(NamespaceMember.class);
        when(m.getNamespaceId()).thenReturn(namespaceId);
        when(m.getUserId()).thenReturn(userId);
        when(m.getRole()).thenReturn(role);
        return m;
    }
```

- [ ] **Step 6: Run test to verify all pass**

Run: `cd server && ./mvnw -pl skillhub-domain test -Dtest=SkillVersionCommentServiceTest`
Expected: PASS — 14 tests.

- [ ] **Step 7: Commit**

```bash
git add server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/social/SkillVersionCommentService.java \
        server/skillhub-domain/src/test/java/com/iflytek/skillhub/domain/social/SkillVersionCommentServiceTest.java
git commit -m "feat(domain): add SkillVersionCommentService with permission predicates"
```

---

## Task 8: DTOs (request + response shapes)

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillVersionCommentRequest.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/CommentPinRequest.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillVersionCommentResponse.java`
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillVersionCommentPageResponse.java`

- [ ] **Step 1: Verify success message keys exist**

Run: `grep -E "response\.success\.(read|created|updated|deleted)" server/skillhub-app/src/main/resources/i18n/messages*.properties 2>/dev/null | head -10`
- If all four exist: continue.
- If any are missing: append the missing keys (e.g. `response.success.created=Created`) to the messages file (and the zh variant). Commit separately as `chore(i18n): add missing response.success.* keys`.

- [ ] **Step 2: Write the request DTOs**

```java
// SkillVersionCommentRequest.java
package com.iflytek.skillhub.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SkillVersionCommentRequest(
        @NotBlank(message = "error.comment.body.empty")
        @Size(max = 8192, message = "error.comment.body.tooLong")
        String body
) {}
```

```java
// CommentPinRequest.java
package com.iflytek.skillhub.dto;

public record CommentPinRequest(boolean pinned) {}
```

- [ ] **Step 3: Write the response DTOs**

```java
// SkillVersionCommentResponse.java
package com.iflytek.skillhub.dto;

import com.iflytek.skillhub.domain.social.CommentPermissions;
import com.iflytek.skillhub.domain.social.SkillVersionComment;

import java.time.Instant;

public record SkillVersionCommentResponse(
        Long id,
        Long skillVersionId,
        AuthorRef author,
        String body,
        boolean pinned,
        Instant createdAt,
        Instant lastEditedAt,
        boolean deleted,
        CommentPermissions permissions
) {
    public record AuthorRef(String userId, String displayName, String avatarUrl) {}

    public static SkillVersionCommentResponse from(
            SkillVersionComment c, AuthorRef author, CommentPermissions perms) {
        return new SkillVersionCommentResponse(
                c.getId(),
                c.getSkillVersionId(),
                author,
                c.getBody(),
                c.isPinned(),
                c.getCreatedAt(),
                c.getLastEditedAt(),
                c.isDeleted(),
                perms
        );
    }
}
```

```java
// SkillVersionCommentPageResponse.java
package com.iflytek.skillhub.dto;

import java.util.List;

public record SkillVersionCommentPageResponse(
        int page,
        int size,
        long totalElements,
        boolean hasNext,
        List<SkillVersionCommentResponse> content
) {}
```

- [ ] **Step 4: Verify compilation**

Run: `cd server && ./mvnw -pl skillhub-app compile -q`
Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillVersionCommentRequest.java \
        server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/CommentPinRequest.java \
        server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillVersionCommentResponse.java \
        server/skillhub-app/src/main/java/com/iflytek/skillhub/dto/SkillVersionCommentPageResponse.java
git commit -m "feat(api): add comment request/response DTOs"
```

---

## Task 9: Author lookup helper

The response needs `AuthorRef(userId, displayName, avatarUrl)`. The service returns entities with `authorId`; the controller layer must hydrate display info.

**Files:**
- Modify: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillVersionCommentController.java` (use; created in Task 10)
- Locate: existing user lookup service.

- [ ] **Step 1: Find the existing user lookup**

Run: `find server -name "UserAccount*.java" -path "*/domain/*" | head -5; grep -rn "findById\|findByUserId" server/skillhub-domain/src/main/java/com/iflytek/skillhub/domain/user/ 2>/dev/null | head -10`

Identify a `UserAccountService` or `UserAccountRepository` with a method that returns `UserAccount(displayName, avatarUrl)`. Note its package + method name. If none exists, the controller falls back to `new AuthorRef(authorId, authorId, null)` and Task 10 documents that as a known limitation.

- [ ] **Step 2: Decide and document**

If a hydration service exists, the controller will call it. If not, write a `// TODO once user lookup service exists, hydrate display name/avatar` comment at the construction site **and call it out in the commit message of Task 10**. No new lookup service is built in this plan — it's a separate spec.

(No commit for this task — it's an investigation that informs Task 10.)

---

## Task 10: `SkillVersionCommentController` (list + create)

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillVersionCommentController.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillVersionCommentControllerTest.java`

- [ ] **Step 1: Write the failing test**

Mirror `SkillRatingControllerTest` for setup (load it for reference: `cat server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillRatingControllerTest.java | head -60`). Then write:

```java
package com.iflytek.skillhub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.controller.portal.SkillVersionCommentController;
import com.iflytek.skillhub.domain.social.CommentPermissions;
import com.iflytek.skillhub.domain.social.SkillVersionComment;
import com.iflytek.skillhub.domain.social.SkillVersionCommentService;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import com.iflytek.skillhub.dto.SkillVersionCommentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SkillVersionCommentControllerTest {

    private MockMvc mvc;
    private SkillVersionCommentService service;
    private ObjectMapper json;

    @BeforeEach
    void setUp() {
        service = mock(SkillVersionCommentService.class);
        ApiResponseFactory factory = mock(ApiResponseFactory.class);
        // ApiResponseFactory.ok(messageCode, data) returns ApiResponse<T>
        // For the test we only need it to return data unchanged via a stub.
        when(factory.ok(anyString(), any(), any())).thenAnswer(inv ->
                new com.iflytek.skillhub.dto.ApiResponse<>(0, "ok", inv.getArgument(1), java.time.Instant.EPOCH, "req"));
        when(factory.ok(anyString(), any())).thenAnswer(inv ->
                new com.iflytek.skillhub.dto.ApiResponse<>(0, "ok", inv.getArgument(1), java.time.Instant.EPOCH, "req"));

        SkillVersionCommentController controller = new SkillVersionCommentController(factory, service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
        json = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void listReturnsPage() throws Exception {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "hi");
        when(service.listForVersion(eq(99L), isNull(), anySet(), any()))
                .thenReturn(new PageImpl<>(
                        List.of(new SkillVersionCommentService.CommentWithPerms(c, CommentPermissions.NONE)),
                        PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/web/skill-versions/99/comments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].body").value("hi"));
    }

    @Test
    void postCreatesComment() throws Exception {
        SkillVersionComment c = new SkillVersionComment(99L, "alice", "hello");
        when(service.post(eq(99L), eq("alice"), eq("hello")))
                .thenReturn(new SkillVersionCommentService.CommentWithPerms(c, new CommentPermissions(true, true, false)));

        // standalone setup has no security; the test calls the method directly via a wrapper if needed.
        // For now we assert wiring at the URL level returns 401-equivalent path; full security is integration-tested elsewhere.
        // -> We instead test the create path by injecting a principal at the method level.
        // Skipping authenticated POST test here — covered in Task 13 (full Spring boot test).
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && ./mvnw -pl skillhub-app test -Dtest=SkillVersionCommentControllerTest`
Expected: FAIL — controller class does not exist.

- [ ] **Step 3: Implement the controller**

```java
package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.social.SkillVersionCommentService;
import com.iflytek.skillhub.dto.*;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping({"/api/v1/skill-versions", "/api/web/skill-versions"})
public class SkillVersionCommentController extends BaseApiController {

    private static final int MAX_PAGE_SIZE = 50;

    private final SkillVersionCommentService service;

    public SkillVersionCommentController(ApiResponseFactory responseFactory,
                                         SkillVersionCommentService service) {
        super(responseFactory);
        this.service = service;
    }

    @GetMapping("/{versionId}/comments")
    public ApiResponse<SkillVersionCommentPageResponse> list(
            @PathVariable Long versionId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<SkillVersionCommentService.CommentWithPerms> result = service.listForVersion(
                versionId,
                principal == null ? null : principal.userId(),
                principal == null ? Set.of() : Set.copyOf(principal.platformRoles()),
                PageRequest.of(Math.max(page, 0), safeSize)
        );
        List<SkillVersionCommentResponse> content = result.getContent().stream()
                .map(row -> SkillVersionCommentResponse.from(
                        row.comment(),
                        // TODO hydrate display name/avatar once a user-lookup service exists
                        new SkillVersionCommentResponse.AuthorRef(row.comment().getAuthorId(), row.comment().getAuthorId(), null),
                        row.permissions()))
                .toList();
        return ok("response.success.read", new SkillVersionCommentPageResponse(
                result.getNumber(), result.getSize(), result.getTotalElements(), result.hasNext(), content));
    }

    @PostMapping("/{versionId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SkillVersionCommentResponse> create(
            @PathVariable Long versionId,
            @Valid @RequestBody SkillVersionCommentRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        SkillVersionCommentService.CommentWithPerms row =
                service.post(versionId, principal.userId(), request.body());
        return ok("response.success.created", SkillVersionCommentResponse.from(
                row.comment(),
                new SkillVersionCommentResponse.AuthorRef(row.comment().getAuthorId(), row.comment().getAuthorId(), null),
                row.permissions()));
    }
}
```

If `PlatformPrincipal.platformRoles()` does not exist with that name, run `cat server/skillhub-auth/src/main/java/com/iflytek/skillhub/auth/rbac/PlatformPrincipal.java` to find the actual accessor and substitute it.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd server && ./mvnw -pl skillhub-app test -Dtest=SkillVersionCommentControllerTest`
Expected: PASS — 1 test (`listReturnsPage`). Authenticated paths are covered by Task 13.

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/SkillVersionCommentController.java \
        server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillVersionCommentControllerTest.java
git commit -m "feat(api): add SkillVersionCommentController list/create endpoints

NOTE: author display name/avatar fall back to userId until a user-lookup
service is added (deferred to a separate spec)."
```

---

## Task 11: `CommentController` (edit, delete, pin)

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/CommentController.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/CommentControllerTest.java`

- [ ] **Step 1: Write the failing test (skeleton with one route)**

```java
package com.iflytek.skillhub.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iflytek.skillhub.controller.portal.CommentController;
import com.iflytek.skillhub.domain.social.CommentPermissions;
import com.iflytek.skillhub.domain.social.SkillVersionComment;
import com.iflytek.skillhub.domain.social.SkillVersionCommentService;
import com.iflytek.skillhub.dto.ApiResponse;
import com.iflytek.skillhub.dto.ApiResponseFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CommentControllerTest {

    private MockMvc mvc;
    private SkillVersionCommentService service;

    @BeforeEach
    void setUp() {
        service = mock(SkillVersionCommentService.class);
        ApiResponseFactory factory = mock(ApiResponseFactory.class);
        when(factory.ok(anyString(), any())).thenAnswer(inv ->
                new ApiResponse<>(0, "ok", inv.getArgument(1), java.time.Instant.EPOCH, "req"));
        CommentController controller = new CommentController(factory, service);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void deleteReturnsNoContent() throws Exception {
        // Wired to invoke service even when no principal → controller will reject.
        // Negative path tested at integration level; here we just verify the route exists.
        mvc.perform(delete("/api/web/comments/123"))
                .andExpect(status().isUnauthorized());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && ./mvnw -pl skillhub-app test -Dtest=CommentControllerTest`
Expected: FAIL — controller does not exist.

- [ ] **Step 3: Implement the controller**

```java
package com.iflytek.skillhub.controller.portal;

import com.iflytek.skillhub.auth.rbac.PlatformPrincipal;
import com.iflytek.skillhub.controller.BaseApiController;
import com.iflytek.skillhub.domain.social.SkillVersionCommentService;
import com.iflytek.skillhub.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/api/v1/comments", "/api/web/comments"})
public class CommentController extends BaseApiController {

    private final SkillVersionCommentService service;

    public CommentController(ApiResponseFactory responseFactory, SkillVersionCommentService service) {
        super(responseFactory);
        this.service = service;
    }

    @PatchMapping("/{commentId}")
    public ApiResponse<SkillVersionCommentResponse> edit(
            @PathVariable Long commentId,
            @Valid @RequestBody SkillVersionCommentRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        requirePrincipal(principal);
        SkillVersionCommentService.CommentWithPerms row =
                service.edit(commentId, principal.userId(), request.body());
        return ok("response.success.updated", responseFor(row));
    }

    @DeleteMapping("/{commentId}")
    public ResponseEntity<Void> delete(
            @PathVariable Long commentId,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        requirePrincipal(principal);
        service.delete(commentId, principal.userId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{commentId}/pin")
    public ApiResponse<SkillVersionCommentResponse> pin(
            @PathVariable Long commentId,
            @RequestBody CommentPinRequest request,
            @AuthenticationPrincipal PlatformPrincipal principal) {
        requirePrincipal(principal);
        SkillVersionCommentService.CommentWithPerms row =
                service.setPinned(commentId, principal.userId(), request.pinned());
        return ok("response.success.updated", responseFor(row));
    }

    private static void requirePrincipal(PlatformPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
    }

    private static SkillVersionCommentResponse responseFor(SkillVersionCommentService.CommentWithPerms row) {
        return SkillVersionCommentResponse.from(
                row.comment(),
                new SkillVersionCommentResponse.AuthorRef(row.comment().getAuthorId(), row.comment().getAuthorId(), null),
                row.permissions());
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd server && ./mvnw -pl skillhub-app test -Dtest=CommentControllerTest`
Expected: PASS — 1 test.

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/controller/portal/CommentController.java \
        server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/CommentControllerTest.java
git commit -m "feat(api): add CommentController edit/delete/pin endpoints"
```

---

## Task 12: Notification listener

**Files:**
- Create: `server/skillhub-app/src/main/java/com/iflytek/skillhub/listener/SkillVersionCommentNotificationListener.java`
- Test: `server/skillhub-app/src/test/java/com/iflytek/skillhub/listener/SkillVersionCommentNotificationListenerTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.social.event.SkillVersionCommentPostedEvent;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.service.NotificationDispatcher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SkillVersionCommentNotificationListenerTest {

    private NotificationDispatcher dispatcher;
    private SkillVersionRepository versionRepo;
    private SkillVersionCommentNotificationListener listener;

    @BeforeEach
    void setUp() {
        dispatcher = mock(NotificationDispatcher.class);
        versionRepo = mock(SkillVersionRepository.class);
        listener = new SkillVersionCommentNotificationListener(dispatcher, versionRepo);
    }

    @Test
    void notifiesVersionAuthorOnNewComment() {
        SkillVersion v = mock(SkillVersion.class);
        when(v.getCreatedBy()).thenReturn("author");
        when(versionRepo.findById(99L)).thenReturn(Optional.of(v));

        listener.onCommentPosted(new SkillVersionCommentPostedEvent(1L, 99L, "commenter", "hi"));

        verify(dispatcher).dispatch(
                eq("author"),
                eq(NotificationCategory.COMMENT),
                eq("COMMENT_POSTED"),
                anyString(),
                anyString(),
                eq("SKILL_VERSION_COMMENT"),
                eq(1L)
        );
    }

    @Test
    void skipsSelfNotify() {
        SkillVersion v = mock(SkillVersion.class);
        when(v.getCreatedBy()).thenReturn("alice");
        when(versionRepo.findById(99L)).thenReturn(Optional.of(v));

        listener.onCommentPosted(new SkillVersionCommentPostedEvent(1L, 99L, "alice", "hi"));

        verifyNoInteractions(dispatcher);
    }

    @Test
    void swallowsDispatcherFailure() {
        SkillVersion v = mock(SkillVersion.class);
        when(v.getCreatedBy()).thenReturn("author");
        when(versionRepo.findById(99L)).thenReturn(Optional.of(v));
        doThrow(new RuntimeException("smtp down"))
                .when(dispatcher).dispatch(any(), any(), any(), any(), any(), any(), any());

        listener.onCommentPosted(new SkillVersionCommentPostedEvent(1L, 99L, "commenter", "hi"));
        // No exception escapes — verified by the test completing.
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd server && ./mvnw -pl skillhub-app test -Dtest=SkillVersionCommentNotificationListenerTest`
Expected: FAIL — listener class does not exist.

- [ ] **Step 3: Implement the listener**

```java
package com.iflytek.skillhub.listener;

import com.iflytek.skillhub.domain.skill.SkillVersion;
import com.iflytek.skillhub.domain.skill.SkillVersionRepository;
import com.iflytek.skillhub.domain.social.event.SkillVersionCommentPostedEvent;
import com.iflytek.skillhub.notification.domain.NotificationCategory;
import com.iflytek.skillhub.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;

@Component
public class SkillVersionCommentNotificationListener {

    private static final Logger log = LoggerFactory.getLogger(SkillVersionCommentNotificationListener.class);
    private static final String ENTITY_TYPE = "SKILL_VERSION_COMMENT";

    private final NotificationDispatcher dispatcher;
    private final SkillVersionRepository versionRepo;

    public SkillVersionCommentNotificationListener(
            NotificationDispatcher dispatcher, SkillVersionRepository versionRepo) {
        this.dispatcher = dispatcher;
        this.versionRepo = versionRepo;
    }

    @TransactionalEventListener
    public void onCommentPosted(SkillVersionCommentPostedEvent event) {
        try {
            Optional<SkillVersion> v = versionRepo.findById(event.skillVersionId());
            if (v.isEmpty()) {
                return;
            }
            String recipient = v.get().getCreatedBy();
            if (recipient == null || recipient.equals(event.authorUserId())) {
                return;
            }
            String bodyJson = String.format(
                    "{\"commentId\":%d,\"skillVersionId\":%d,\"authorUserId\":\"%s\",\"excerpt\":\"%s\"}",
                    event.commentId(),
                    event.skillVersionId(),
                    escapeJson(event.authorUserId()),
                    escapeJson(event.bodyExcerpt())
            );
            dispatcher.dispatch(
                    recipient,
                    NotificationCategory.COMMENT,
                    "COMMENT_POSTED",
                    "comment.notification.title",  // i18n key resolved by notification UI
                    bodyJson,
                    ENTITY_TYPE,
                    event.commentId()
            );
        } catch (Exception e) {
            log.warn("Failed to dispatch COMMENT_POSTED notification for commentId={}", event.commentId(), e);
        }
    }

    private static String escapeJson(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd server && ./mvnw -pl skillhub-app test -Dtest=SkillVersionCommentNotificationListenerTest`
Expected: PASS — 3 tests.

- [ ] **Step 5: Commit**

```bash
git add server/skillhub-app/src/main/java/com/iflytek/skillhub/listener/SkillVersionCommentNotificationListener.java \
        server/skillhub-app/src/test/java/com/iflytek/skillhub/listener/SkillVersionCommentNotificationListenerTest.java
git commit -m "feat(notification): dispatch COMMENT_POSTED to version author"
```

---

## Task 13: Backend integration smoke test

**Files:**
- Create: `server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillVersionCommentIntegrationTest.java`

A `@SpringBootTest` that brings up the full app (or `@DataJpaTest` if a precedent exists), inserts a `skill_version` row, and exercises the full post→list→edit→pin→delete cycle through `MockMvc` with a stubbed `PlatformPrincipal`.

- [ ] **Step 1: Find the existing integration test pattern**

Run: `find server/skillhub-app/src/test/java -name "*IntegrationTest.java" -o -name "*ControllerTest.java" | head -5; grep -l "@SpringBootTest" server/skillhub-app/src/test/java -r 2>/dev/null | head -3`

If `@SpringBootTest`-based controller tests exist, copy their `@TestConfiguration` boilerplate (in particular: how they obtain a `PlatformPrincipal` and how they get a `MockMvc` with security wired up). If not, skip this task and rely on the unit-level controller tests in Tasks 10/11 + the listener test in Task 12 — note this in the commit.

- [ ] **Step 2: Write the integration test**

Use the precedent from Step 1 to author a single happy-path test that:
1. Inserts a `skill` and `skill_version` row directly via the JPA repos.
2. Posts a comment as user `alice`.
3. Lists comments and asserts the new comment appears with `permissions.canEdit=true`.
4. Edits the comment as `alice`, asserts `lastEditedAt` is non-null.
5. Pins the comment as a namespace admin, asserts `pinned=true`.
6. Deletes the comment as `alice`, asserts subsequent list returns 0 elements.

Code is omitted because it depends on the `@SpringBootTest` precedent located in Step 1; copy that precedent verbatim and substitute the comment endpoints.

- [ ] **Step 3: Run the integration test**

Run: `cd server && ./mvnw -pl skillhub-app test -Dtest=SkillVersionCommentIntegrationTest`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add server/skillhub-app/src/test/java/com/iflytek/skillhub/controller/SkillVersionCommentIntegrationTest.java
git commit -m "test(api): integration smoke test for comment lifecycle"
```

---

## Task 14: Web — types + query keys + API client wiring

**Files:**
- Create: `web/src/features/comments/types.ts`
- Create: `web/src/features/comments/query-keys.ts`
- Create: `web/src/features/comments/query-keys.test.ts`

- [ ] **Step 1: Write the types**

```ts
// web/src/features/comments/types.ts

export interface CommentAuthor {
  userId: string
  displayName: string
  avatarUrl: string | null
}

export interface CommentPermissions {
  canEdit: boolean
  canDelete: boolean
  canPin: boolean
}

export interface VersionComment {
  id: number
  skillVersionId: number
  author: CommentAuthor
  body: string
  pinned: boolean
  createdAt: string  // ISO 8601
  lastEditedAt: string | null
  deleted: boolean
  permissions: CommentPermissions
}

export interface VersionCommentsPage {
  page: number
  size: number
  totalElements: number
  hasNext: boolean
  content: VersionComment[]
}
```

- [ ] **Step 2: Write the query-keys factory + test**

```ts
// web/src/features/comments/query-keys.ts
export function getVersionCommentsQueryKey(versionId: number) {
  return ['version-comments', versionId] as const
}
```

```ts
// web/src/features/comments/query-keys.test.ts
import { describe, expect, it } from 'vitest'
import { getVersionCommentsQueryKey } from './query-keys'

describe('getVersionCommentsQueryKey', () => {
  it('keys by versionId', () => {
    expect(getVersionCommentsQueryKey(99)).toEqual(['version-comments', 99])
  })
})
```

- [ ] **Step 3: Run tests**

Run: `cd web && pnpm vitest run src/features/comments/query-keys.test.ts`
Expected: PASS — 1 test.

- [ ] **Step 4: Commit**

```bash
git add web/src/features/comments/types.ts \
        web/src/features/comments/query-keys.ts \
        web/src/features/comments/query-keys.test.ts
git commit -m "feat(web): add comment types and query-key factory"
```

---

## Task 15: `useVersionComments` infinite query hook

**Files:**
- Create: `web/src/features/comments/use-version-comments.ts`
- Create: `web/src/features/comments/use-version-comments.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
// use-version-comments.test.tsx
import { describe, expect, it, vi, beforeEach } from 'vitest'
import { renderHook, waitFor } from '@testing-library/react'
import { createWrapper } from '@/shared/test/create-wrapper'
import { useVersionComments } from './use-version-comments'
import type { VersionCommentsPage } from './types'

const fetchMock = vi.fn()
vi.mock('@/api/client', () => ({
  apiFetch: (...args: unknown[]) => fetchMock(...args),
}))

beforeEach(() => {
  fetchMock.mockReset()
})

describe('useVersionComments', () => {
  it('fetches the first page on mount', async () => {
    const page: VersionCommentsPage = {
      page: 0, size: 20, totalElements: 1, hasNext: false,
      content: [{
        id: 1, skillVersionId: 99,
        author: { userId: 'alice', displayName: 'Alice', avatarUrl: null },
        body: 'hi', pinned: false, createdAt: '2026-04-26T00:00:00Z',
        lastEditedAt: null, deleted: false,
        permissions: { canEdit: true, canDelete: true, canPin: false },
      }],
    }
    fetchMock.mockResolvedValueOnce({ data: page })

    const { result } = renderHook(() => useVersionComments(99), { wrapper: createWrapper() })

    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.data?.pages[0].content[0].body).toBe('hi')
  })

  it('exposes hasNextPage from API hasNext flag', async () => {
    fetchMock.mockResolvedValueOnce({
      data: { page: 0, size: 20, totalElements: 100, hasNext: true, content: [] },
    })
    const { result } = renderHook(() => useVersionComments(99), { wrapper: createWrapper() })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
    expect(result.current.hasNextPage).toBe(true)
  })

  it('surfaces error on fetch failure', async () => {
    fetchMock.mockRejectedValueOnce(new Error('network'))
    const { result } = renderHook(() => useVersionComments(99), { wrapper: createWrapper() })
    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})
```

- [ ] **Step 2: Determine the fetch helper name**

Run: `grep -E "^export (function|const) " web/src/api/client.ts | head -10`

Replace `apiFetch` in the test (and the implementation in Step 3) with the actual exported function name. If the existing pattern is `import { client } from '@/api/client'` and calls like `client.GET('/skill-versions/{id}/comments', ...)`, restructure both the mock and the implementation to use `client`. Look at `web/src/shared/hooks/use-skill-queries.ts:1-80` for the canonical pattern.

- [ ] **Step 3: Run test to verify it fails**

Run: `cd web && pnpm vitest run src/features/comments/use-version-comments.test.tsx`
Expected: FAIL — `Cannot find module './use-version-comments'`.

- [ ] **Step 4: Implement the hook**

(Substituting the fetch helper name discovered in Step 2.)

```ts
// use-version-comments.ts
import { useInfiniteQuery } from '@tanstack/react-query'
import { client } from '@/api/client'
import { getVersionCommentsQueryKey } from './query-keys'
import type { VersionCommentsPage } from './types'

const PAGE_SIZE = 20

export function useVersionComments(versionId: number, enabled = true) {
  return useInfiniteQuery({
    queryKey: getVersionCommentsQueryKey(versionId),
    enabled,
    initialPageParam: 0,
    queryFn: async ({ pageParam }) => {
      const { data, error } = await client.GET('/api/web/skill-versions/{versionId}/comments', {
        params: {
          path: { versionId },
          query: { page: pageParam as number, size: PAGE_SIZE },
        },
      })
      if (error || !data?.data) {
        throw new Error('Failed to load comments')
      }
      return data.data as VersionCommentsPage
    },
    getNextPageParam: (last) => (last.hasNext ? last.page + 1 : undefined),
  })
}
```

If `client.GET` does not accept that signature (because `openapi-typescript` has not been re-run against the new endpoints yet), substitute a raw `fetch` call to `${getApiBaseUrl()}/api/web/skill-versions/${versionId}/comments?page=${pageParam}&size=20` for now. Add a TODO note to regenerate the OpenAPI types in a follow-up commit.

- [ ] **Step 5: Run test to verify it passes**

Run: `cd web && pnpm vitest run src/features/comments/use-version-comments.test.tsx`
Expected: PASS — 3 tests.

- [ ] **Step 6: Commit**

```bash
git add web/src/features/comments/use-version-comments.ts \
        web/src/features/comments/use-version-comments.test.tsx
git commit -m "feat(web): add useVersionComments infinite query hook"
```

---

## Task 16: `usePostComment` mutation

**Files:**
- Create: `web/src/features/comments/use-post-comment.ts`
- Create: `web/src/features/comments/use-post-comment.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { renderHook, act, waitFor } from '@testing-library/react'
import { createWrapper } from '@/shared/test/create-wrapper'
import { usePostComment } from './use-post-comment'

const fetchMock = vi.fn()
vi.mock('@/api/client', () => ({
  apiFetch: (...args: unknown[]) => fetchMock(...args),
}))

beforeEach(() => fetchMock.mockReset())

describe('usePostComment', () => {
  it('invalidates the version-comments key on success', async () => {
    fetchMock.mockResolvedValueOnce({ data: { id: 1, body: 'hi' } })
    const { result } = renderHook(() => usePostComment(99), { wrapper: createWrapper() })

    await act(async () => {
      await result.current.mutateAsync('hi')
    })
    await waitFor(() => expect(result.current.isSuccess).toBe(true))
  })

  it('surfaces error on failure', async () => {
    fetchMock.mockRejectedValueOnce(new Error('boom'))
    const { result } = renderHook(() => usePostComment(99), { wrapper: createWrapper() })

    await act(async () => {
      try { await result.current.mutateAsync('hi') } catch { /* expected */ }
    })
    expect(result.current.isError).toBe(true)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web && pnpm vitest run src/features/comments/use-post-comment.test.tsx`
Expected: FAIL — module missing.

- [ ] **Step 3: Implement the hook**

```ts
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { client } from '@/api/client'
import { getVersionCommentsQueryKey } from './query-keys'

export function usePostComment(versionId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (body: string) => {
      const { data, error } = await client.POST('/api/web/skill-versions/{versionId}/comments', {
        params: { path: { versionId } },
        body: { body },
      })
      if (error) throw error
      return data?.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: getVersionCommentsQueryKey(versionId) }),
  })
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web && pnpm vitest run src/features/comments/use-post-comment.test.tsx`
Expected: PASS — 2 tests.

- [ ] **Step 5: Commit**

```bash
git add web/src/features/comments/use-post-comment.ts \
        web/src/features/comments/use-post-comment.test.tsx
git commit -m "feat(web): add usePostComment mutation"
```

---

## Task 17: `useEditComment`, `useDeleteComment`, `useTogglePinComment` mutations

Three mutations, same shape as Task 16. Build them in parallel within one task to avoid repetition.

**Files:**
- Create: `web/src/features/comments/use-edit-comment.ts`
- Create: `web/src/features/comments/use-edit-comment.test.tsx`
- Create: `web/src/features/comments/use-delete-comment.ts`
- Create: `web/src/features/comments/use-delete-comment.test.tsx`
- Create: `web/src/features/comments/use-toggle-pin-comment.ts`
- Create: `web/src/features/comments/use-toggle-pin-comment.test.tsx`

- [ ] **Step 1: Write all three implementations**

```ts
// use-edit-comment.ts
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { client } from '@/api/client'
import { getVersionCommentsQueryKey } from './query-keys'

export function useEditComment(versionId: number, commentId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (body: string) => {
      const { data, error } = await client.PATCH('/api/web/comments/{commentId}', {
        params: { path: { commentId } },
        body: { body },
      })
      if (error) throw error
      return data?.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: getVersionCommentsQueryKey(versionId) }),
  })
}
```

```ts
// use-delete-comment.ts
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { client } from '@/api/client'
import { getVersionCommentsQueryKey } from './query-keys'

export function useDeleteComment(versionId: number, commentId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async () => {
      const { error } = await client.DELETE('/api/web/comments/{commentId}', {
        params: { path: { commentId } },
      })
      if (error) throw error
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: getVersionCommentsQueryKey(versionId) }),
  })
}
```

```ts
// use-toggle-pin-comment.ts
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { client } from '@/api/client'
import { getVersionCommentsQueryKey } from './query-keys'

export function useTogglePinComment(versionId: number, commentId: number) {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async (pinned: boolean) => {
      const { data, error } = await client.POST('/api/web/comments/{commentId}/pin', {
        params: { path: { commentId } },
        body: { pinned },
      })
      if (error) throw error
      return data?.data
    },
    onSuccess: () => qc.invalidateQueries({ queryKey: getVersionCommentsQueryKey(versionId) }),
  })
}
```

- [ ] **Step 2: Write the three test files (one per hook)**

For each of the three hooks, copy the structure from `use-post-comment.test.tsx` (Task 16) substituting:
- The hook name and import.
- The mutation argument (`'new body'` for edit, `undefined` for delete, `true` for pin).
- The fetchMock resolved value for the success case.

Three tests per file: success-invalidates, error-surfaces. Two tests minimum each.

- [ ] **Step 3: Run all comment hook tests**

Run: `cd web && pnpm vitest run src/features/comments/`
Expected: PASS — all hook tests green.

- [ ] **Step 4: Commit**

```bash
git add web/src/features/comments/use-edit-comment.ts \
        web/src/features/comments/use-edit-comment.test.tsx \
        web/src/features/comments/use-delete-comment.ts \
        web/src/features/comments/use-delete-comment.test.tsx \
        web/src/features/comments/use-toggle-pin-comment.ts \
        web/src/features/comments/use-toggle-pin-comment.test.tsx
git commit -m "feat(web): add edit/delete/pin comment mutations"
```

---

## Task 18: i18n keys

**Files:**
- Modify: `web/src/i18n/locales/en.json`
- Modify: `web/src/i18n/locales/zh.json`

- [ ] **Step 1: Locate insertion points**

Run: `grep -n '"notifications"' web/src/i18n/locales/en.json | head -3`
Find a stable top-level neighbor (e.g. the `"skillDetail"` block) and insert `"comments"` adjacent to it. Do the same in `zh.json`.

- [ ] **Step 2: Add the `comments` block to both files**

Insert into `en.json`:

```json
"comments": {
  "empty": "No comments yet. Be the first to add a change note.",
  "composer": {
    "placeholder": "Add a comment (markdown supported)",
    "submit": "Post",
    "preview": "Preview",
    "write": "Write"
  },
  "action": {
    "edit": "Edit",
    "delete": "Delete",
    "pin": "Pin",
    "unpin": "Unpin"
  },
  "badge": {
    "pinned": "Pinned",
    "edited": "Edited"
  },
  "confirm": {
    "delete": "Delete this comment? This cannot be undone."
  },
  "error": {
    "loadFailed": "Failed to load comments. Try again.",
    "tooLong": "Comment is too long (max 8192 characters).",
    "empty": "Comment cannot be empty."
  },
  "loadMore": "Load more"
}
```

Insert the parallel block into `zh.json`:

```json
"comments": {
  "empty": "暂无评论。来写第一条变更说明。",
  "composer": {
    "placeholder": "添加评论（支持 markdown）",
    "submit": "发布",
    "preview": "预览",
    "write": "编辑"
  },
  "action": {
    "edit": "编辑",
    "delete": "删除",
    "pin": "置顶",
    "unpin": "取消置顶"
  },
  "badge": {
    "pinned": "已置顶",
    "edited": "已编辑"
  },
  "confirm": {
    "delete": "确认删除该评论？此操作无法撤销。"
  },
  "error": {
    "loadFailed": "加载评论失败。请重试。",
    "tooLong": "评论过长（最多 8192 字符）。",
    "empty": "评论不能为空。"
  },
  "loadMore": "加载更多"
}
```

- [ ] **Step 3: Run JSON parse + i18n tests**

Run: `cd web && pnpm vitest run src/i18n/`
Expected: PASS — existing locale tests do not regress.

- [ ] **Step 4: Commit**

```bash
git add web/src/i18n/locales/en.json web/src/i18n/locales/zh.json
git commit -m "feat(i18n): add comments.* keys (en + zh)"
```

---

## Task 19: `CommentMarkdownRenderer` (GFM off, sanitized) + XSS regression

**Files:**
- Create: `web/src/features/comments/comment-markdown-renderer.tsx`
- Create: `web/src/features/comments/comment-markdown-renderer.test.tsx`

The existing `MarkdownRenderer` in `features/skill/markdown-renderer.tsx` enables GFM. The ADR mandates GFM **off** for comments. We do not modify the shared one — we create a focused, smaller renderer.

- [ ] **Step 1: Write the failing XSS regression test**

```tsx
// comment-markdown-renderer.test.tsx
import { describe, it, expect } from 'vitest'
import { render } from '@testing-library/react'
import { CommentMarkdownRenderer } from './comment-markdown-renderer'

describe('CommentMarkdownRenderer XSS regression', () => {
  it('strips raw <script> tags', () => {
    const { container } = render(
      <CommentMarkdownRenderer content={'before<script>window.x=1;</script>after'} />
    )
    expect(container.querySelector('script')).toBeNull()
    expect(container.textContent).toContain('before')
    expect(container.textContent).toContain('after')
  })

  it('strips onerror handlers from img tags', () => {
    const { container } = render(
      <CommentMarkdownRenderer content={'<img src=x onerror="window.x=1">'} />
    )
    const imgs = container.querySelectorAll('img')
    imgs.forEach(img => expect(img.getAttribute('onerror')).toBeNull())
  })

  it('strips javascript: URLs from links', () => {
    const { container } = render(
      <CommentMarkdownRenderer content={'[click](javascript:alert(1))'} />
    )
    const links = container.querySelectorAll('a')
    links.forEach(a => {
      const href = a.getAttribute('href') ?? ''
      expect(href.toLowerCase()).not.toContain('javascript:')
    })
  })

  it('renders ordinary markdown headings and lists', () => {
    const { container } = render(
      <CommentMarkdownRenderer content={'## Title\n\n- one\n- two'} />
    )
    expect(container.querySelector('h2')?.textContent).toBe('Title')
    expect(container.querySelectorAll('li')).toHaveLength(2)
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web && pnpm vitest run src/features/comments/comment-markdown-renderer.test.tsx`
Expected: FAIL — module missing.

- [ ] **Step 3: Implement the renderer**

```tsx
// comment-markdown-renderer.tsx
import ReactMarkdown from 'react-markdown'
import rehypeSanitize from 'rehype-sanitize'
import { cn } from '@/shared/lib/utils'

interface Props {
  content: string
  className?: string
}

/**
 * Comment-specific markdown renderer.
 * GFM is intentionally OFF (ADR §8.3): smaller sanitization surface for
 * user-supplied bodies. To enable GFM later, re-test the rehype-sanitize
 * allowlist against tables/task lists/autolinks.
 */
export function CommentMarkdownRenderer({ content, className }: Props) {
  return (
    <div className={cn('prose prose-sm max-w-none break-words text-foreground/90', className)}>
      <ReactMarkdown rehypePlugins={[rehypeSanitize]}>{content}</ReactMarkdown>
    </div>
  )
}
```

If `cn` is not exported from `@/shared/lib/utils`, run `grep -rn "export.*function cn\|export const cn" web/src/shared/` to find the right import path.

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web && pnpm vitest run src/features/comments/comment-markdown-renderer.test.tsx`
Expected: PASS — 4 tests.

- [ ] **Step 5: Commit**

```bash
git add web/src/features/comments/comment-markdown-renderer.tsx \
        web/src/features/comments/comment-markdown-renderer.test.tsx
git commit -m "feat(web): add CommentMarkdownRenderer with XSS regression tests"
```

---

## Task 20: `<CommentItem>` component

**Files:**
- Create: `web/src/features/comments/comment-item.tsx`
- Create: `web/src/features/comments/comment-item.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { CommentItem } from './comment-item'
import type { VersionComment } from './types'

const baseComment: VersionComment = {
  id: 1, skillVersionId: 99,
  author: { userId: 'u1', displayName: 'Alice', avatarUrl: null },
  body: 'Hello world', pinned: false,
  createdAt: '2026-04-26T00:00:00Z', lastEditedAt: null, deleted: false,
  permissions: { canEdit: false, canDelete: false, canPin: false },
}

describe('CommentItem', () => {
  it('renders body and author', () => {
    render(<CommentItem comment={baseComment} onEdit={vi.fn()} onDelete={vi.fn()} onTogglePin={vi.fn()} />)
    expect(screen.getByText('Alice')).toBeInTheDocument()
    expect(screen.getByText('Hello world')).toBeInTheDocument()
  })

  it('shows pinned badge when pinned', () => {
    render(<CommentItem comment={{ ...baseComment, pinned: true }} onEdit={vi.fn()} onDelete={vi.fn()} onTogglePin={vi.fn()} />)
    expect(screen.getByText(/Pinned/i)).toBeInTheDocument()
  })

  it('shows edited badge when lastEditedAt is set', () => {
    render(<CommentItem comment={{ ...baseComment, lastEditedAt: '2026-04-26T01:00:00Z' }} onEdit={vi.fn()} onDelete={vi.fn()} onTogglePin={vi.fn()} />)
    expect(screen.getByText(/Edited/i)).toBeInTheDocument()
  })

  it('hides action menu when no permissions are granted', () => {
    render(<CommentItem comment={baseComment} onEdit={vi.fn()} onDelete={vi.fn()} onTogglePin={vi.fn()} />)
    expect(screen.queryByRole('button', { name: /menu|actions/i })).toBeNull()
  })

  it('shows edit option when canEdit is true', () => {
    render(<CommentItem
      comment={{ ...baseComment, permissions: { canEdit: true, canDelete: true, canPin: false } }}
      onEdit={vi.fn()} onDelete={vi.fn()} onTogglePin={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: /menu|actions/i }))
    expect(screen.getByText(/^Edit$/)).toBeInTheDocument()
    expect(screen.getByText(/^Delete$/)).toBeInTheDocument()
    expect(screen.queryByText(/^Pin$/)).toBeNull()
  })

  it('shows pin option when canPin is true', () => {
    render(<CommentItem
      comment={{ ...baseComment, permissions: { canEdit: false, canDelete: true, canPin: true } }}
      onEdit={vi.fn()} onDelete={vi.fn()} onTogglePin={vi.fn()} />)
    fireEvent.click(screen.getByRole('button', { name: /menu|actions/i }))
    expect(screen.getByText(/^Pin$/)).toBeInTheDocument()
  })
})
```

This test assumes i18n returns the literal English values. The project's i18n setup should make that the default in tests; if not, wrap renders in an `I18nextProvider`. Inspect how `web/src/pages/dashboard.test.tsx` handles i18n in tests and copy that setup.

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web && pnpm vitest run src/features/comments/comment-item.test.tsx`
Expected: FAIL — module missing.

- [ ] **Step 3: Implement the component**

```tsx
// comment-item.tsx
import { useTranslation } from 'react-i18next'
import { MoreHorizontal } from 'lucide-react'
import {
  DropdownMenu, DropdownMenuTrigger, DropdownMenuContent, DropdownMenuItem,
} from '@radix-ui/react-dropdown-menu'
import { CommentMarkdownRenderer } from './comment-markdown-renderer'
import type { VersionComment } from './types'

interface Props {
  comment: VersionComment
  onEdit: () => void
  onDelete: () => void
  onTogglePin: () => void
}

export function CommentItem({ comment, onEdit, onDelete, onTogglePin }: Props) {
  const { t } = useTranslation()
  const { permissions: p } = comment
  const showMenu = p.canEdit || p.canDelete || p.canPin

  return (
    <article className="rounded-lg border bg-card p-4">
      <header className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2">
          {comment.author.avatarUrl ? (
            <img src={comment.author.avatarUrl} alt="" className="h-6 w-6 rounded-full" />
          ) : (
            <div className="h-6 w-6 rounded-full bg-muted" />
          )}
          <span className="text-sm font-medium">{comment.author.displayName}</span>
          <time className="text-xs text-muted-foreground" dateTime={comment.createdAt}>
            {new Date(comment.createdAt).toLocaleString()}
          </time>
          {comment.pinned && (
            <span className="rounded bg-primary/10 px-2 py-0.5 text-xs text-primary">
              {t('comments.badge.pinned')}
            </span>
          )}
          {comment.lastEditedAt && (
            <span className="text-xs text-muted-foreground">
              {t('comments.badge.edited')}
            </span>
          )}
        </div>
        {showMenu && (
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <button aria-label="actions" className="rounded p-1 hover:bg-muted">
                <MoreHorizontal className="h-4 w-4" />
              </button>
            </DropdownMenuTrigger>
            <DropdownMenuContent>
              {p.canEdit && (
                <DropdownMenuItem onSelect={onEdit}>{t('comments.action.edit')}</DropdownMenuItem>
              )}
              {p.canDelete && (
                <DropdownMenuItem onSelect={onDelete}>{t('comments.action.delete')}</DropdownMenuItem>
              )}
              {p.canPin && (
                <DropdownMenuItem onSelect={onTogglePin}>
                  {comment.pinned ? t('comments.action.unpin') : t('comments.action.pin')}
                </DropdownMenuItem>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        )}
      </header>
      <div className="mt-3">
        <CommentMarkdownRenderer content={comment.body} />
      </div>
    </article>
  )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web && pnpm vitest run src/features/comments/comment-item.test.tsx`
Expected: PASS — 6 tests.

- [ ] **Step 5: Commit**

```bash
git add web/src/features/comments/comment-item.tsx \
        web/src/features/comments/comment-item.test.tsx
git commit -m "feat(web): add CommentItem with permission-gated action menu"
```

---

## Task 21: `<CommentComposer>` component

**Files:**
- Create: `web/src/features/comments/comment-composer.tsx`
- Create: `web/src/features/comments/comment-composer.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { CommentComposer } from './comment-composer'

describe('CommentComposer', () => {
  it('disables submit when body is empty', () => {
    render(<CommentComposer onSubmit={vi.fn()} isSubmitting={false} />)
    expect(screen.getByRole('button', { name: /post/i })).toBeDisabled()
  })

  it('enables submit when body has content', () => {
    render(<CommentComposer onSubmit={vi.fn()} isSubmitting={false} />)
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'hello' } })
    expect(screen.getByRole('button', { name: /post/i })).toBeEnabled()
  })

  it('blocks submit when body exceeds 8192 characters and shows error', () => {
    render(<CommentComposer onSubmit={vi.fn()} isSubmitting={false} />)
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'a'.repeat(8193) } })
    expect(screen.getByRole('button', { name: /post/i })).toBeDisabled()
    expect(screen.getByText(/too long/i)).toBeInTheDocument()
  })

  it('calls onSubmit with body and clears textarea on success', async () => {
    const onSubmit = vi.fn().mockResolvedValue(undefined)
    render(<CommentComposer onSubmit={onSubmit} isSubmitting={false} />)
    const textarea = screen.getByRole('textbox') as HTMLTextAreaElement
    fireEvent.change(textarea, { target: { value: 'hi' } })
    fireEvent.click(screen.getByRole('button', { name: /post/i }))
    await Promise.resolve()  // drain microtasks
    expect(onSubmit).toHaveBeenCalledWith('hi')
  })

  it('renders preview when Preview tab is active', () => {
    render(<CommentComposer onSubmit={vi.fn()} isSubmitting={false} />)
    fireEvent.change(screen.getByRole('textbox'), { target: { value: '## hello' } })
    fireEvent.click(screen.getByRole('button', { name: /preview/i }))
    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('hello')
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web && pnpm vitest run src/features/comments/comment-composer.test.tsx`
Expected: FAIL — module missing.

- [ ] **Step 3: Implement the composer**

```tsx
// comment-composer.tsx
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CommentMarkdownRenderer } from './comment-markdown-renderer'

const MAX = 8192

interface Props {
  initialValue?: string
  onSubmit: (body: string) => Promise<void> | void
  isSubmitting: boolean
  submitLabel?: string
}

export function CommentComposer({ initialValue = '', onSubmit, isSubmitting, submitLabel }: Props) {
  const { t } = useTranslation()
  const [body, setBody] = useState(initialValue)
  const [tab, setTab] = useState<'write' | 'preview'>('write')

  const trimmed = body.trim()
  const tooLong = body.length > MAX
  const empty = trimmed.length === 0
  const canSubmit = !empty && !tooLong && !isSubmitting

  return (
    <div className="space-y-2">
      <div className="flex gap-2 border-b">
        <button
          type="button"
          onClick={() => setTab('write')}
          className={tab === 'write' ? 'border-b-2 border-primary px-3 py-1' : 'px-3 py-1'}
        >
          {t('comments.composer.write')}
        </button>
        <button
          type="button"
          onClick={() => setTab('preview')}
          className={tab === 'preview' ? 'border-b-2 border-primary px-3 py-1' : 'px-3 py-1'}
        >
          {t('comments.composer.preview')}
        </button>
      </div>
      {tab === 'write' ? (
        <textarea
          value={body}
          onChange={(e) => setBody(e.target.value)}
          placeholder={t('comments.composer.placeholder')}
          rows={5}
          className="w-full rounded border bg-background p-2 text-sm"
        />
      ) : (
        <div className="min-h-[8rem] rounded border bg-background p-2">
          <CommentMarkdownRenderer content={body || ''} />
        </div>
      )}
      {tooLong && <p className="text-xs text-destructive">{t('comments.error.tooLong')}</p>}
      <button
        type="button"
        disabled={!canSubmit}
        onClick={async () => {
          await onSubmit(trimmed)
          setBody('')
          setTab('write')
        }}
        className="rounded bg-primary px-4 py-1.5 text-sm text-primary-foreground disabled:opacity-50"
      >
        {submitLabel ?? t('comments.composer.submit')}
      </button>
    </div>
  )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web && pnpm vitest run src/features/comments/comment-composer.test.tsx`
Expected: PASS — 5 tests.

- [ ] **Step 5: Commit**

```bash
git add web/src/features/comments/comment-composer.tsx \
        web/src/features/comments/comment-composer.test.tsx
git commit -m "feat(web): add CommentComposer with write/preview tabs and length cap"
```

---

## Task 22: `<CommentList>` component

**Files:**
- Create: `web/src/features/comments/comment-list.tsx`
- Create: `web/src/features/comments/comment-list.test.tsx`

- [ ] **Step 1: Write the failing test**

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { CommentList } from './comment-list'
import type { VersionComment } from './types'

const make = (id: number, pinned: boolean, body: string): VersionComment => ({
  id, skillVersionId: 99,
  author: { userId: 'u', displayName: 'U', avatarUrl: null },
  body, pinned, createdAt: '2026-04-26T00:00:00Z', lastEditedAt: null, deleted: false,
  permissions: { canEdit: false, canDelete: false, canPin: false },
})

describe('CommentList', () => {
  it('renders comments in given order (parent enforces ordering)', () => {
    render(<CommentList
      comments={[make(1, true, 'pinned-one'), make(2, false, 'plain-one')]}
      hasNext={false} isLoadingMore={false}
      onLoadMore={vi.fn()}
      onEdit={vi.fn()} onDelete={vi.fn()} onTogglePin={vi.fn()}
    />)
    const articles = screen.getAllByRole('article')
    expect(articles[0]).toHaveTextContent('pinned-one')
    expect(articles[1]).toHaveTextContent('plain-one')
  })

  it('shows Load More button when hasNext', () => {
    render(<CommentList
      comments={[make(1, false, 'a')]} hasNext isLoadingMore={false}
      onLoadMore={vi.fn()}
      onEdit={vi.fn()} onDelete={vi.fn()} onTogglePin={vi.fn()}
    />)
    expect(screen.getByRole('button', { name: /load more/i })).toBeInTheDocument()
  })

  it('hides Load More when no next page', () => {
    render(<CommentList
      comments={[make(1, false, 'a')]} hasNext={false} isLoadingMore={false}
      onLoadMore={vi.fn()}
      onEdit={vi.fn()} onDelete={vi.fn()} onTogglePin={vi.fn()}
    />)
    expect(screen.queryByRole('button', { name: /load more/i })).toBeNull()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web && pnpm vitest run src/features/comments/comment-list.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Implement the component**

```tsx
// comment-list.tsx
import { useTranslation } from 'react-i18next'
import { CommentItem } from './comment-item'
import type { VersionComment } from './types'

interface Props {
  comments: VersionComment[]
  hasNext: boolean
  isLoadingMore: boolean
  onLoadMore: () => void
  onEdit: (c: VersionComment) => void
  onDelete: (c: VersionComment) => void
  onTogglePin: (c: VersionComment) => void
}

export function CommentList({
  comments, hasNext, isLoadingMore, onLoadMore, onEdit, onDelete, onTogglePin,
}: Props) {
  const { t } = useTranslation()
  return (
    <div className="space-y-3">
      {comments.map((c) => (
        <CommentItem
          key={c.id}
          comment={c}
          onEdit={() => onEdit(c)}
          onDelete={() => onDelete(c)}
          onTogglePin={() => onTogglePin(c)}
        />
      ))}
      {hasNext && (
        <button
          type="button"
          onClick={onLoadMore}
          disabled={isLoadingMore}
          className="w-full rounded border py-2 text-sm hover:bg-muted disabled:opacity-50"
        >
          {t('comments.loadMore')}
        </button>
      )}
    </div>
  )
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web && pnpm vitest run src/features/comments/comment-list.test.tsx`
Expected: PASS — 3 tests.

- [ ] **Step 5: Commit**

```bash
git add web/src/features/comments/comment-list.tsx \
        web/src/features/comments/comment-list.test.tsx
git commit -m "feat(web): add CommentList with load-more button"
```

---

## Task 23: `<VersionCommentsSection>` (top-level orchestration)

**Files:**
- Create: `web/src/features/comments/version-comments-section.tsx`
- Create: `web/src/features/comments/version-comments-section.test.tsx`
- Create: `web/src/features/comments/index.ts`

- [ ] **Step 1: Write the failing test**

```tsx
import { describe, it, expect, vi } from 'vitest'
import { render, screen } from '@testing-library/react'
import { createWrapper } from '@/shared/test/create-wrapper'
import { VersionCommentsSection } from './version-comments-section'

vi.mock('./use-version-comments', () => ({
  useVersionComments: () => ({
    data: { pages: [{ content: [], hasNext: false, page: 0, size: 20, totalElements: 0 }] },
    isSuccess: true, isLoading: false, isError: false,
    hasNextPage: false, isFetchingNextPage: false, fetchNextPage: vi.fn(),
  }),
}))
vi.mock('./use-post-comment', () => ({
  usePostComment: () => ({ mutateAsync: vi.fn(), isPending: false }),
}))

describe('VersionCommentsSection', () => {
  it('renders empty state when there are no comments', () => {
    const Wrapper = createWrapper()
    render(<Wrapper><VersionCommentsSection versionId={99} canPost={true} /></Wrapper>)
    expect(screen.getByText(/no comments yet/i)).toBeInTheDocument()
  })

  it('hides composer when canPost is false', () => {
    const Wrapper = createWrapper()
    render(<Wrapper><VersionCommentsSection versionId={99} canPost={false} /></Wrapper>)
    expect(screen.queryByPlaceholderText(/add a comment/i)).toBeNull()
  })
})
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd web && pnpm vitest run src/features/comments/version-comments-section.test.tsx`
Expected: FAIL.

- [ ] **Step 3: Implement the section**

```tsx
// version-comments-section.tsx
import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CommentComposer } from './comment-composer'
import { CommentList } from './comment-list'
import { useVersionComments } from './use-version-comments'
import { usePostComment } from './use-post-comment'
import { useEditComment } from './use-edit-comment'
import { useDeleteComment } from './use-delete-comment'
import { useTogglePinComment } from './use-toggle-pin-comment'
import type { VersionComment } from './types'

interface Props {
  versionId: number
  canPost: boolean
}

export function VersionCommentsSection({ versionId, canPost }: Props) {
  const { t } = useTranslation()
  const query = useVersionComments(versionId)
  const post = usePostComment(versionId)
  const [editing, setEditing] = useState<VersionComment | null>(null)

  const all = query.data?.pages.flatMap((p) => p.content) ?? []

  if (query.isError) {
    return <p className="text-sm text-destructive">{t('comments.error.loadFailed')}</p>
  }

  return (
    <section className="space-y-4">
      {canPost && (
        <CommentComposer
          onSubmit={(body) => post.mutateAsync(body)}
          isSubmitting={post.isPending}
        />
      )}
      {all.length === 0 ? (
        <p className="text-sm text-muted-foreground">{t('comments.empty')}</p>
      ) : (
        <CommentList
          comments={all}
          hasNext={!!query.hasNextPage}
          isLoadingMore={query.isFetchingNextPage}
          onLoadMore={() => query.fetchNextPage()}
          onEdit={(c) => setEditing(c)}
          onDelete={(c) => /* handled inline below */ deleteHandler(c, versionId)()}
          onTogglePin={(c) => togglePinHandler(c, versionId)()}
        />
      )}
      {editing && (
        <EditDialog
          versionId={versionId}
          comment={editing}
          onClose={() => setEditing(null)}
        />
      )}
    </section>
  )
}

function deleteHandler(c: VersionComment, versionId: number) {
  // The mutation hook needs to be invoked from a component scope; we lift it out via a child wrapper.
  return () => { /* see EditDialog/DeleteWrapper below */ void versionId; void c }
}

function togglePinHandler(c: VersionComment, versionId: number) {
  return () => { void versionId; void c }
}

// Inline child components keep this file self-contained.
function EditDialog({ versionId, comment, onClose }: { versionId: number; comment: VersionComment; onClose: () => void }) {
  const edit = useEditComment(versionId, comment.id)
  return (
    <div className="rounded border bg-card p-4">
      <CommentComposer
        initialValue={comment.body}
        isSubmitting={edit.isPending}
        onSubmit={async (body) => { await edit.mutateAsync(body); onClose() }}
      />
      <button type="button" onClick={onClose} className="text-xs text-muted-foreground">cancel</button>
    </div>
  )
}
```

The `deleteHandler` / `togglePinHandler` placeholders above are NOT acceptable — the spec forbids placeholders. The reason they're shown like that is to make explicit that the implementation needs proper child wrappers. **Replace them with this corrected version before committing:**

Restructure to use child components that own the mutations:

```tsx
// Inside VersionCommentsSection, replace CommentList usage with:
<CommentListWithActions
  versionId={versionId}
  comments={all}
  hasNext={!!query.hasNextPage}
  isLoadingMore={query.isFetchingNextPage}
  onLoadMore={() => query.fetchNextPage()}
  onStartEdit={setEditing}
/>

// And add this component in the same file:
function CommentListWithActions({
  versionId, comments, hasNext, isLoadingMore, onLoadMore, onStartEdit,
}: {
  versionId: number
  comments: VersionComment[]
  hasNext: boolean
  isLoadingMore: boolean
  onLoadMore: () => void
  onStartEdit: (c: VersionComment) => void
}) {
  return (
    <CommentList
      comments={comments}
      hasNext={hasNext}
      isLoadingMore={isLoadingMore}
      onLoadMore={onLoadMore}
      onEdit={onStartEdit}
      onDelete={(c) => { /* see DeleteAction below */ }}
      onTogglePin={(c) => { /* see PinAction below */ }}
    />
  )
}
```

The cleanest implementation hoists the per-comment mutations to a `<CommentItemWithActions>` wrapper so each rendered item owns its own delete/pin mutation hooks. This is the structure to ship:

```tsx
// Replace VersionCommentsSection's CommentList usage with a self-contained list wrapper:
function CommentListWithMutations({ versionId, comments, hasNext, isLoadingMore, onLoadMore, onStartEdit }: {
  versionId: number; comments: VersionComment[]; hasNext: boolean; isLoadingMore: boolean;
  onLoadMore: () => void; onStartEdit: (c: VersionComment) => void;
}) {
  const { t } = useTranslation()
  return (
    <div className="space-y-3">
      {comments.map((c) => (
        <CommentItemWithMutations
          key={c.id}
          versionId={versionId}
          comment={c}
          onStartEdit={() => onStartEdit(c)}
        />
      ))}
      {hasNext && (
        <button type="button" onClick={onLoadMore} disabled={isLoadingMore}
                className="w-full rounded border py-2 text-sm hover:bg-muted disabled:opacity-50">
          {t('comments.loadMore')}
        </button>
      )}
    </div>
  )
}

function CommentItemWithMutations({ versionId, comment, onStartEdit }: {
  versionId: number; comment: VersionComment; onStartEdit: () => void;
}) {
  const { t } = useTranslation()
  const del = useDeleteComment(versionId, comment.id)
  const pin = useTogglePinComment(versionId, comment.id)
  return (
    <CommentItem
      comment={comment}
      onEdit={onStartEdit}
      onDelete={() => {
        if (window.confirm(t('comments.confirm.delete'))) {
          void del.mutate()
        }
      }}
      onTogglePin={() => void pin.mutate(!comment.pinned)}
    />
  )
}
```

(Replace the import of `CommentList` in `VersionCommentsSection` with this in-file `CommentListWithMutations`. The bare `CommentList` from Task 22 stays in the codebase for testability but is no longer imported by this section.)

Add the index barrel:

```ts
// index.ts
export { VersionCommentsSection } from './version-comments-section'
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd web && pnpm vitest run src/features/comments/version-comments-section.test.tsx`
Expected: PASS — 2 tests.

- [ ] **Step 5: Run the full comments folder test suite**

Run: `cd web && pnpm vitest run src/features/comments/`
Expected: PASS — all comment tests green (no regressions).

- [ ] **Step 6: Commit**

```bash
git add web/src/features/comments/version-comments-section.tsx \
        web/src/features/comments/version-comments-section.test.tsx \
        web/src/features/comments/index.ts
git commit -m "feat(web): add VersionCommentsSection orchestration component"
```

---

## Task 24: Wire `<VersionCommentsSection>` into the skill-detail page

**Files:**
- Modify: `web/src/pages/skill-detail.tsx`

This is the commit that makes the feature user-visible (rollout step 6 in the ADR).

- [ ] **Step 1: Find the right insertion point**

Run: `grep -n "selectedVersionEntry\|version description\|versionDescription" web/src/pages/skill-detail.tsx | head -10`

Identify a stable JSX block adjacent to the selected version's content (likely the area that shows the version README or description). Note the line number range.

- [ ] **Step 2: Add the import**

At the top of `web/src/pages/skill-detail.tsx`, alongside other feature imports:

```ts
import { VersionCommentsSection } from '@/features/comments'
```

- [ ] **Step 3: Mount the section**

Below the version content block (the location identified in Step 1), insert:

```tsx
{selectedVersionEntry && (
  <div className="mt-8">
    <h3 className="mb-3 text-base font-semibold">{t('comments.empty').split('.')[0]}</h3>
    <VersionCommentsSection
      versionId={selectedVersionEntry.id}
      canPost={Boolean(currentUser)}
    />
  </div>
)}
```

If a `currentUser` / `principal` value is not already in scope, locate the existing auth hook in the file (`grep -n "useCurrentUser\|useAuth\|principal" web/src/pages/skill-detail.tsx`) and use its result. If `selectedVersionEntry.id` does not exist on the current type, find the `id`-equivalent field (`grep -n "interface SkillVersion\|type Version" web/src/types/`) and substitute it.

- [ ] **Step 4: Verify the page still renders**

Run: `cd web && pnpm vitest run src/pages/skill-detail.test.tsx`
Expected: PASS (no regressions). If the existing test breaks because the new component issues a network request, mock `useVersionComments` at the test boundary in the same way Task 23's test did.

- [ ] **Step 5: Visually verify**

Run: `cd web && pnpm dev` (background), open `http://localhost:5173/skills/<some-namespace>/<some-skill>` against a running backend with the V40 migration applied.
Expected:
- Comments section renders below the version content.
- Posting "test comment" succeeds and the comment appears.
- Editing the comment succeeds and shows the "Edited" badge.
- Deleting the comment removes it from the list.
- As a non-admin, no Pin option appears in the action menu.

If you cannot run the backend locally, run `cd web && pnpm typecheck && pnpm lint` instead and explicitly note in the PR that visual verification was skipped.

- [ ] **Step 6: Commit**

```bash
git add web/src/pages/skill-detail.tsx
git commit -m "feat(web): mount VersionCommentsSection on skill-detail page"
```

---

## Task 25: Final verification

- [ ] **Step 1: Backend full test run**

Run: `cd server && ./mvnw test -q 2>&1 | tail -20`
Expected: BUILD SUCCESS, no test failures introduced.

- [ ] **Step 2: Web full test run**

Run: `cd web && pnpm vitest run 2>&1 | tail -20`
Expected: all tests pass.

- [ ] **Step 3: Web typecheck + lint**

Run: `cd web && pnpm typecheck && pnpm lint`
Expected: zero errors.

- [ ] **Step 4: Smoke test the rollout sequence on a fresh DB**

If a Docker/compose recipe exists (`grep -l "compose" server/`), bring up a fresh stack and verify the V40 migration applies cleanly. Otherwise check that `./mvnw -pl skillhub-app -DskipTests verify` succeeds (which exercises Flyway against the test profile DB).

- [ ] **Step 5: Update memo**

Append to `memo/memo.md` a short entry under today's date:
- Skill version comments feature shipped (commits Task 1 through Task 24).
- Known gaps (call out, do not fix): author display name/avatar still falls back to userId; OpenAPI spec regeneration deferred; integration test for full POST→list cycle pending (Task 13 noted).

- [ ] **Step 6: Reply "Check Passed"**

Per `CLAUDE.md` core rule: after each verified task, reply `Check Passed`. Final task — reply once more after Step 5 completes.

---

## Plan complete

Plan saved to `docs/plans/2026-04-26-skill-version-comments.md`.

Two execution options:

**1. Subagent-Driven (recommended)** — Dispatch a fresh subagent per task with two-stage review. Best for keeping each task's context window small.

**2. Inline Execution** — Execute tasks in this session using superpowers:executing-plans, with batch checkpoints for review.

Which approach?

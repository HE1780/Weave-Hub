> **Recovered 2026-05-08** — 本文件曾在 commit `084cd9cc`(2026-04-29 文档精简)中被误删,从 `084cd9cc^:docs/adr/0004-agent-promotion.md` 还原。memo/plan 中所有指向本文件的链接重新生效。

# ADR 0004 — Agent Promotion

**Status:** Accepted
**Date:** 2026-04-28
**Context:** A9 — extend the existing skill-only promotion subsystem to also promote Agents from a team namespace into the global namespace.

## Decision

Use a **discriminator-column** schema (`source_type SKILL | AGENT`) on the existing `promotion_request` table plus a **strategy-pattern materializer** (`PromotionMaterializer` interface, registry keyed by `SourceType`) — instead of building parallel `agent_promotion_request` tables/services or a generic `source_entity_id` column.

## Consequences

- ~80% of `PromotionService` is reused unchanged — state machine, authorization, audit log, optimistic locking, events, notifications.
- Frontend reuses one unified review queue (`/dashboard/promotions`); per-row `SourceTypeBadge` differentiates skill vs agent visually.
- Each materializer (`SkillPromotionMaterializer`, `AgentPromotionMaterializer`) is independently testable and depends only on its own domain repositories.
- Schema gains a CHECK constraint enforcing the discriminator/id pairing (V49) so DB rejects ambiguous rows.
- FK integrity preserved on both sides (skill source FKs to skill/skill_version, agent source FKs to agent/agent_version) — vs the generic `source_entity_id` alternative which would lose it.
- `PromotionSubmittedEvent` field names stay skill-flavored (`skillId`/`versionId`) for backwards compatibility with 2 existing callers; agent ids reuse the same slots since the listener (`NotificationEventListener`) only reads `promotionId`.

## Alternatives considered

- **Parallel tables (`agent_promotion_request`)** — rejected: would duplicate the state machine, auth, and notification wiring; reviewer inbox would have to merge two streams.
- **Generic `source_entity_id` + `source_type` (no FK)** — rejected: loses referential integrity and complicates joins.
- **Switch in PromotionService instead of strategy pattern** — rejected: would force PromotionService to inject all skill + agent repositories (constructor with 11+ deps), defeating cohesion.

## Notable spec deviations

- **`AgentPublishedEvent` already existed.** Spec originally directed creating a new 3-arg record; the existing one is 5-arg `(agentId, agentVersionId, namespaceId, publisherId, publishedAt)` and is published by `AgentPublishService`/`AgentLifecycleService`/`AgentReviewService`. AgentPromotionMaterializer reuses it.
- **`LabelDefinition` has no namespace concept.** Spec originally proposed filtering label copy by namespace scope. Removed — all labels are platform-scope today; materializer copies all source labels verbatim.
- **`ReviewPermissionChecker.canSubmitPromotion` only had a `Skill` overload.** Added an `Agent` overload as a prereq inside Task 11.
- **List endpoint has no namespace dimension.** Spec proposed `findByTargetNamespaceIdAndStatusAndSourceType`; existing list code uses `findByStatus` (no namespace filter), so the new method mirrors that with just `findByStatusAndSourceType`.

## References

- Spec: [docs/superpowers/specs/2026-04-28-agent-promotion-design.md](../superpowers/specs/2026-04-28-agent-promotion-design.md)
- Plan: [docs/superpowers/plans/2026-04-28-agent-promotion.md](../superpowers/plans/2026-04-28-agent-promotion.md)
- Migration: `server/skillhub-app/src/main/resources/db/migration/V49__agent_promotion.sql`
- ADR 0003 clause: 1.1 (fork-led Agent management)

## Out of scope (explicit deferrals)

- Wiring `LandingHotSection` to read top-promoted agents.
- Updating the agent search index on promotion (existing P3-3 follow-up).
- Filter tabs on the review queue.
- Source-link traceability column on the promoted Agent entity.
- A public `/namespaces/global` endpoint to resolve the target namespace id without requiring viewer membership (current frontend uses `useMyNamespaces` and gracefully hides the button when the viewer isn't a global member).

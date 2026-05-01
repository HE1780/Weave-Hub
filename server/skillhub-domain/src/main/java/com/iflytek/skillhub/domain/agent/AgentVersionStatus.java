package com.iflytek.skillhub.domain.agent;

/**
 * Lifecycle states for an AgentVersion. Aligned with SkillVersionStatus.
 *
 * Transitions:
 *   DRAFT          -> SCANNING         (publish trigger; initial pre-scan placeholder)
 *   SCANNING       -> UPLOADED         (scan passed)
 *   SCANNING       -> SCAN_FAILED      (scan rejected / threw)
 *   SCAN_FAILED    -> SCANNING         (re-scan)
 *   UPLOADED       -> PENDING_REVIEW   (author submits public/namespace agent for review)
 *   UPLOADED       -> PUBLISHED        (author confirms private publish, or SUPER_ADMIN auto-publish)
 *   PENDING_REVIEW -> PUBLISHED        (reviewer approves)
 *   PENDING_REVIEW -> REJECTED         (reviewer rejects)
 *   PENDING_REVIEW -> UPLOADED         (author withdraws review)
 *   PUBLISHED      -> ARCHIVED         (owner archives)
 *   PUBLISHED      -> YANKED           (admin yanks a published version)
 *   REJECTED       -> DRAFT            (author resubmits with edits)
 */
public enum AgentVersionStatus {
    DRAFT,
    SCANNING,
    SCAN_FAILED,
    UPLOADED,
    PENDING_REVIEW,
    PUBLISHED,
    REJECTED,
    ARCHIVED,
    YANKED
}

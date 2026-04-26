package com.iflytek.skillhub.domain.agent;

/**
 * Lifecycle states for an AgentVersion.
 *
 * Transitions allowed in v1:
 *   DRAFT          -> PENDING_REVIEW   (author submits public/namespace agent)
 *   DRAFT          -> PUBLISHED        (private agent auto-publishes)
 *   PENDING_REVIEW -> PUBLISHED        (reviewer approves)
 *   PENDING_REVIEW -> REJECTED         (reviewer rejects)
 *   PUBLISHED      -> ARCHIVED         (owner archives)
 *   REJECTED       -> DRAFT            (author resubmits with edits)
 */
public enum AgentVersionStatus {
    DRAFT,
    PENDING_REVIEW,
    PUBLISHED,
    REJECTED,
    ARCHIVED
}

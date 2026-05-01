-- V50__agent_parity_state_machine.sql
-- Agent <-> Skill parity (commit b250461e on 2026-05-01) introduced new
-- AgentVersion fields and an extended status enum but never shipped a
-- Flyway migration. This file rebuilds the agent_* schema in its parity
-- shape so dev / test environments come up cleanly.
--
-- WARNING — DEV/TEST ONLY: this migration unconditionally drops every
-- agent_* table. Per the parity HANDOVER ("目前全部为测试数据可直接删除"),
-- there is no production agent data yet. If that changes, write a separate
-- alter-only migration before running this in any non-dev environment.
--
-- The reset is idempotent vs Flyway history: V41–V49 entries stay marked
-- success; V50 is the new source of truth for agent_* DDL.
--
-- Cleanup of FK leftovers on promotion_request happens via DROP CONSTRAINT
-- IF EXISTS so this script tolerates either pristine V41–V49 state OR a
-- mid-flight drop (e.g. drop-agent-tables.sh already ran).

ALTER TABLE promotion_request
    DROP CONSTRAINT IF EXISTS promotion_request_source_agent_id_fkey,
    DROP CONSTRAINT IF EXISTS promotion_request_source_agent_version_id_fkey,
    DROP CONSTRAINT IF EXISTS promotion_request_target_agent_id_fkey;

DROP TABLE IF EXISTS agent_review_task CASCADE;
DROP TABLE IF EXISTS agent_version_stats CASCADE;
DROP TABLE IF EXISTS agent_version_comment CASCADE;
DROP TABLE IF EXISTS agent_version CASCADE;
DROP TABLE IF EXISTS agent_tag CASCADE;
DROP TABLE IF EXISTS agent_label CASCADE;
DROP TABLE IF EXISTS agent_star CASCADE;
DROP TABLE IF EXISTS agent_rating CASCADE;
DROP TABLE IF EXISTS agent_report CASCADE;
DROP TABLE IF EXISTS agent CASCADE;

CREATE TABLE agent (
    id BIGSERIAL PRIMARY KEY,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    slug VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description TEXT,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    owner_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    star_count INT NOT NULL DEFAULT 0,
    rating_avg NUMERIC(3,2) NOT NULL DEFAULT 0,
    rating_count INT NOT NULL DEFAULT 0,
    download_count INT NOT NULL DEFAULT 0,
    latest_version_id BIGINT,
    hidden BOOLEAN NOT NULL DEFAULT FALSE,
    hidden_at TIMESTAMPTZ,
    hidden_by VARCHAR(128),
    hide_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (namespace_id, slug),
    CHECK (visibility IN ('PUBLIC', 'PRIVATE', 'NAMESPACE_ONLY')),
    CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_agent_namespace ON agent(namespace_id);
CREATE INDEX idx_agent_owner ON agent(owner_id);
CREATE INDEX idx_agent_visibility ON agent(visibility) WHERE status = 'ACTIVE';
CREATE INDEX idx_agent_hidden ON agent(hidden) WHERE hidden = TRUE;

CREATE TABLE agent_version (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'SCANNING',
    soul_md TEXT,
    workflow_yaml TEXT,
    manifest_yaml TEXT,
    package_object_key VARCHAR(256),
    package_size_bytes BIGINT,
    requested_visibility VARCHAR(16),
    bundle_ready BOOLEAN NOT NULL DEFAULT FALSE,
    download_ready BOOLEAN NOT NULL DEFAULT FALSE,
    yanked_at TIMESTAMPTZ,
    yanked_by VARCHAR(128),
    yank_reason TEXT,
    submitted_by VARCHAR(64) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    UNIQUE (agent_id, version),
    CHECK (status IN (
        'DRAFT',
        'SCANNING',
        'SCAN_FAILED',
        'UPLOADED',
        'PENDING_REVIEW',
        'PUBLISHED',
        'REJECTED',
        'ARCHIVED',
        'YANKED'
    )),
    CHECK (requested_visibility IS NULL
           OR requested_visibility IN ('PUBLIC', 'PRIVATE', 'NAMESPACE_ONLY'))
);

CREATE INDEX idx_agent_version_agent_status ON agent_version(agent_id, status);
CREATE INDEX idx_agent_version_published ON agent_version(agent_id) WHERE status = 'PUBLISHED';

ALTER TABLE agent
    ADD CONSTRAINT agent_latest_version_id_fkey
    FOREIGN KEY (latest_version_id) REFERENCES agent_version(id);

CREATE TABLE agent_review_task (
    id BIGSERIAL PRIMARY KEY,
    agent_version_id BIGINT NOT NULL REFERENCES agent_version(id) ON DELETE CASCADE,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    version INTEGER NOT NULL DEFAULT 1,
    submitted_by VARCHAR(64) NOT NULL,
    reviewed_by VARCHAR(64),
    review_comment TEXT,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    reviewed_at TIMESTAMPTZ,
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))
);

CREATE INDEX idx_agent_review_namespace_status ON agent_review_task(namespace_id, status);
CREATE INDEX idx_agent_review_version ON agent_review_task(agent_version_id);

CREATE TABLE agent_star (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agent(id),
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (agent_id, user_id)
);

CREATE INDEX idx_agent_star_user_id ON agent_star(user_id);
CREATE INDEX idx_agent_star_agent_id ON agent_star(agent_id);

CREATE TABLE agent_rating (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agent(id),
    user_id VARCHAR(128) NOT NULL REFERENCES user_account(id),
    score SMALLINT NOT NULL CHECK (score >= 1 AND score <= 5),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (agent_id, user_id)
);

CREATE INDEX idx_agent_rating_agent_id ON agent_rating(agent_id);

CREATE TABLE agent_version_comment (
    id                BIGSERIAL    PRIMARY KEY,
    agent_version_id  BIGINT       NOT NULL REFERENCES agent_version(id) ON DELETE CASCADE,
    author_id         VARCHAR(128) NOT NULL REFERENCES user_account(id),
    body              TEXT         NOT NULL,
    pinned            BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_edited_at    TIMESTAMP,
    deleted_at        TIMESTAMP,
    deleted_by        VARCHAR(128) REFERENCES user_account(id),
    CONSTRAINT agent_version_comment_body_length CHECK (char_length(body) BETWEEN 1 AND 8192),
    CONSTRAINT agent_version_comment_body_not_blank CHECK (btrim(body) <> ''),
    CONSTRAINT agent_version_comment_deleted_consistency CHECK ((deleted_at IS NULL) = (deleted_by IS NULL))
);

CREATE INDEX idx_agent_version_comment_version
    ON agent_version_comment(agent_version_id, pinned DESC, created_at DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_agent_version_comment_author
    ON agent_version_comment(author_id);

CREATE TABLE agent_report (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id) ON DELETE CASCADE,
    reporter_id VARCHAR(128) NOT NULL,
    reason VARCHAR(200) NOT NULL,
    details TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    handled_by VARCHAR(128),
    handle_comment TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    handled_at TIMESTAMPTZ
);

CREATE INDEX idx_agent_report_status_created_at ON agent_report(status, created_at DESC);
CREATE INDEX idx_agent_report_agent_id ON agent_report(agent_id);

CREATE TABLE agent_tag (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    tag_name VARCHAR(64) NOT NULL,
    version_id BIGINT NOT NULL REFERENCES agent_version(id),
    created_by VARCHAR(128) REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(agent_id, tag_name)
);

CREATE INDEX idx_agent_tag_agent_id ON agent_tag(agent_id);

CREATE TABLE agent_label (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    label_id BIGINT NOT NULL REFERENCES label_definition(id) ON DELETE CASCADE,
    created_by VARCHAR(128) REFERENCES user_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(agent_id, label_id)
);

CREATE INDEX idx_agent_label_label_id ON agent_label(label_id);
CREATE INDEX idx_agent_label_agent_id ON agent_label(agent_id);

CREATE TABLE agent_version_stats (
    agent_version_id BIGINT PRIMARY KEY REFERENCES agent_version(id) ON DELETE CASCADE,
    agent_id BIGINT NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    download_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_agent_version_stats_agent_id ON agent_version_stats(agent_id);

ALTER TABLE promotion_request
    ADD CONSTRAINT promotion_request_source_agent_id_fkey
        FOREIGN KEY (source_agent_id) REFERENCES agent(id),
    ADD CONSTRAINT promotion_request_source_agent_version_id_fkey
        FOREIGN KEY (source_agent_version_id) REFERENCES agent_version(id),
    ADD CONSTRAINT promotion_request_target_agent_id_fkey
        FOREIGN KEY (target_agent_id) REFERENCES agent(id);

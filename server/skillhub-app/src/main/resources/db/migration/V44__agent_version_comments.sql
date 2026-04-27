-- V44__agent_version_comments.sql
-- Adds the table backing agent-version comments.
-- Direct mirror of V40 (skill_version_comment). The Agent–Skill alignment
-- cluster (see docs/plans/2026-04-27-fork-backlog.md) deliberately uses a
-- sibling table rather than a polymorphic comment column so the two paths
-- evolve independently and neither side has to know about the other's
-- visibility / permission model.

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

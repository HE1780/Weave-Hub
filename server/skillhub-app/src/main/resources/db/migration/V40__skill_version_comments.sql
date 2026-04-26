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

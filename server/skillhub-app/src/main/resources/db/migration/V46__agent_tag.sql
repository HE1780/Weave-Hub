-- V46__agent_tag.sql
-- Agent named-tag table. Sibling of skill_tag (V2 + V20 timestamptz);
-- new table goes straight to TIMESTAMPTZ. Tags resolve to a specific
-- agent_version, only namespace ADMIN/OWNER can write, and the reserved
-- "latest" tag is enforced at the service layer.

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

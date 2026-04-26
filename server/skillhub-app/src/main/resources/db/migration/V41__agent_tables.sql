-- Agent registry: agent and agent_version tables.
-- Mirrors the skill / skill_version pair but stripped to the v1 needs:
-- no rating, no star, no download counters, no promotion. Add later.

CREATE TABLE agent (
    id BIGSERIAL PRIMARY KEY,
    namespace_id BIGINT NOT NULL REFERENCES namespace(id),
    slug VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    description TEXT,
    visibility VARCHAR(16) NOT NULL DEFAULT 'PRIVATE',
    owner_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (namespace_id, slug),
    CHECK (visibility IN ('PUBLIC', 'PRIVATE', 'NAMESPACE')),
    CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE INDEX idx_agent_namespace ON agent(namespace_id);
CREATE INDEX idx_agent_owner ON agent(owner_id);
CREATE INDEX idx_agent_visibility ON agent(visibility) WHERE status = 'ACTIVE';

CREATE TABLE agent_version (
    id BIGSERIAL PRIMARY KEY,
    agent_id BIGINT NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    version VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    soul_md TEXT,
    workflow_yaml TEXT,
    manifest_yaml TEXT,
    package_object_key VARCHAR(256),
    package_size_bytes BIGINT,
    submitted_by VARCHAR(64) NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    published_at TIMESTAMPTZ,
    UNIQUE (agent_id, version),
    CHECK (status IN ('DRAFT', 'PENDING_REVIEW', 'PUBLISHED', 'REJECTED', 'ARCHIVED'))
);

CREATE INDEX idx_agent_version_agent_status ON agent_version(agent_id, status);
CREATE INDEX idx_agent_version_published ON agent_version(agent_id) WHERE status = 'PUBLISHED';

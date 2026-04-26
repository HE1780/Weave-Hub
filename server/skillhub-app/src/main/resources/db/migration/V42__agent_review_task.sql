-- Agent review queue. Mirrors review_task for skills but kept separate so
-- the two queues can evolve independently and FKs stay clean.

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

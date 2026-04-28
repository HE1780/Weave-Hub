-- A9 Agent Promotion: extend promotion_request to support agent source.
-- Adds discriminator + nullable parallel agent columns, a CHECK constraint
-- enforcing the discriminator semantics, and reworks the partial unique
-- index that previously assumed skill-only.

ALTER TABLE promotion_request
  ADD COLUMN source_type VARCHAR(16) NOT NULL DEFAULT 'SKILL',
  ADD COLUMN source_agent_id BIGINT NULL REFERENCES agent(id),
  ADD COLUMN source_agent_version_id BIGINT NULL REFERENCES agent_version(id),
  ADD COLUMN target_agent_id BIGINT NULL REFERENCES agent(id);

ALTER TABLE promotion_request
  ALTER COLUMN source_skill_id DROP NOT NULL,
  ALTER COLUMN source_version_id DROP NOT NULL;

ALTER TABLE promotion_request
  ADD CONSTRAINT promotion_request_source_consistency CHECK (
    (source_type = 'SKILL' AND source_skill_id IS NOT NULL
                            AND source_version_id IS NOT NULL
                            AND source_agent_id IS NULL
                            AND source_agent_version_id IS NULL)
    OR
    (source_type = 'AGENT' AND source_agent_id IS NOT NULL
                            AND source_agent_version_id IS NOT NULL
                            AND source_skill_id IS NULL
                            AND source_version_id IS NULL)
  );

DROP INDEX IF EXISTS idx_promotion_request_version_pending;

CREATE UNIQUE INDEX promotion_request_pending_skill_version_uq
  ON promotion_request(source_version_id)
  WHERE status = 'PENDING' AND source_type = 'SKILL';

CREATE UNIQUE INDEX promotion_request_pending_agent_version_uq
  ON promotion_request(source_agent_version_id)
  WHERE status = 'PENDING' AND source_type = 'AGENT';

-- V48__agent_download_counters.sql
-- A10: per-agent and per-version download counters mirroring skill side.
-- skill side has download_count on skill + skill_version_stats table; we
-- do the same here.

ALTER TABLE agent
    ADD COLUMN IF NOT EXISTS download_count INTEGER NOT NULL DEFAULT 0;

CREATE TABLE agent_version_stats (
    agent_version_id BIGINT PRIMARY KEY REFERENCES agent_version(id) ON DELETE CASCADE,
    agent_id BIGINT NOT NULL REFERENCES agent(id) ON DELETE CASCADE,
    download_count BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_agent_version_stats_agent_id ON agent_version_stats(agent_id);

-- Backfill stats rows for every existing version so increments don't have
-- to UPSERT in hot paths.
INSERT INTO agent_version_stats (agent_version_id, agent_id, download_count, updated_at)
SELECT id, agent_id, 0, NOW() FROM agent_version
ON CONFLICT (agent_version_id) DO NOTHING;

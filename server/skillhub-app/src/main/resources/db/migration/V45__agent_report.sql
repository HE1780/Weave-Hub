-- V45__agent_report.sql
-- Agent abuse-report table. Mirrors V10 (skill_report) one-for-one; sibling
-- table per the Agent–Skill alignment cluster (fork-backlog A6).

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

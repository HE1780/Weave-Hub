-- V47__agent_label.sql
-- Agent–Skill parity (A4): attach existing label_definition rows to agents.
-- Mirrors V34's skill_label association table; LabelDefinition vocabulary is
-- shared so the same label slug can be applied to both Skills and Agents.

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

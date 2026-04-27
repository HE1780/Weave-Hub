-- Mirrors V3 skill_star / skill_rating onto Agent.
-- Adds denormalized social counters to agent so list/detail responses can
-- read aggregates without an N+1.

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

ALTER TABLE agent
    ADD COLUMN star_count INT NOT NULL DEFAULT 0,
    ADD COLUMN rating_avg NUMERIC(3,2) NOT NULL DEFAULT 0,
    ADD COLUMN rating_count INT NOT NULL DEFAULT 0;

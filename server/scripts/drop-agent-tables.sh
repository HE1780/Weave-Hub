#!/usr/bin/env bash

# Drops agent_* tables so JPA can recreate them with the parity-aligned schema
# (new columns on agent_version, hidden + latest_version_id on agent, etc).
#
# Per the agent-skill parity spec: existing agent rows are test-only data;
# blowing them away is the chosen migration path. Skill-side tables are
# untouched. Run against a local Postgres only.
#
# Usage:
#   PGPASSWORD=skillhub server/scripts/drop-agent-tables.sh
#
# Environment overrides:
#   PGHOST   default: localhost
#   PGPORT   default: 5432
#   PGUSER   default: skillhub
#   PGDATABASE default: skillhub

set -euo pipefail

PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGUSER="${PGUSER:-skillhub}"
PGDATABASE="${PGDATABASE:-skillhub}"

echo "Dropping agent_* tables on ${PGUSER}@${PGHOST}:${PGPORT}/${PGDATABASE}"

psql -h "$PGHOST" -p "$PGPORT" -U "$PGUSER" -d "$PGDATABASE" <<'SQL'
DROP TABLE IF EXISTS agent_review_task CASCADE;
DROP TABLE IF EXISTS agent_version_stats CASCADE;
DROP TABLE IF EXISTS agent_version_comment CASCADE;
DROP TABLE IF EXISTS agent_version CASCADE;
DROP TABLE IF EXISTS agent_tag CASCADE;
DROP TABLE IF EXISTS agent_label CASCADE;
DROP TABLE IF EXISTS agent_star CASCADE;
DROP TABLE IF EXISTS agent_rating CASCADE;
DROP TABLE IF EXISTS agent_report CASCADE;
DROP TABLE IF EXISTS agent CASCADE;
SQL

echo "Done. Restart the app so JPA recreates the tables with the new schema."

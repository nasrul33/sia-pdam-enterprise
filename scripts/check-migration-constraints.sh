#!/usr/bin/env sh
set -eu

if docker compose version >/dev/null 2>&1; then
  DOCKER_COMPOSE="${DOCKER_COMPOSE:-docker compose}"
elif command -v docker-compose >/dev/null 2>&1; then
  DOCKER_COMPOSE="${DOCKER_COMPOSE:-docker-compose}"
else
  echo "docker compose or docker-compose is required." >&2
  exit 1
fi

POSTGRES_DB="${POSTGRES_DB:-sia_pdam}"
POSTGRES_USER="${POSTGRES_USER:-sia}"
CHECK_SQL="${CHECK_SQL:-backend/src/test/resources/db/v22-v23-constraint-checks.sql}"

if [ ! -f "$CHECK_SQL" ]; then
  echo "Migration constraint check file was not found: $CHECK_SQL" >&2
  exit 1
fi

echo "Checking V22/V23 PostgreSQL migration constraints..."
$DOCKER_COMPOSE exec -T postgres psql \
  -U "$POSTGRES_USER" \
  -d "$POSTGRES_DB" \
  -X \
  -q \
  -v ON_ERROR_STOP=1 \
  < "$CHECK_SQL"

echo "Migration constraint verification complete."

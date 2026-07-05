#!/usr/bin/env sh
set -eu

if docker compose version >/dev/null 2>&1; then
  DOCKER_COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  DOCKER_COMPOSE="docker-compose"
else
  echo "docker compose or docker-compose is required." >&2
  exit 1
fi

echo "Checking Docker Compose..."
$DOCKER_COMPOSE config >/dev/null

echo "Checking backend..."
(cd backend && gradle clean test bootJar)

echo "Checking frontend..."
(cd frontend && npm ci && npm run lint && npm run typecheck && npm run build)

echo "Verification complete."

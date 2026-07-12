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

SMOKE_COMPOSE_PROJECT_NAME="${SMOKE_COMPOSE_PROJECT_NAME:-sia-pdam-compose-smoke}"
POSTGRES_PORT="${POSTGRES_PORT:-15632}"
REDIS_PORT="${REDIS_PORT:-16579}"
MINIO_PORT="${MINIO_PORT:-19200}"
MINIO_CONSOLE_PORT="${MINIO_CONSOLE_PORT:-19201}"
BACKEND_PORT="${BACKEND_PORT:-18280}"
FRONTEND_PORT="${FRONTEND_PORT:-13200}"
BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://localhost:$BACKEND_PORT}"
FRONTEND_BASE_URL="${FRONTEND_BASE_URL:-http://localhost:$FRONTEND_PORT}"
POSTGRES_DB="${POSTGRES_DB:-sia_pdam}"
POSTGRES_USER="${POSTGRES_USER:-sia}"
SMOKE_TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-180}"
SMOKE_KEEP_RUNNING="${SMOKE_KEEP_RUNNING:-0}"
SIA_BOOTSTRAP_ADMIN_USERNAME="${SIA_BOOTSTRAP_ADMIN_USERNAME:-smoke-admin}"
SIA_BOOTSTRAP_ADMIN_EMAIL="${SIA_BOOTSTRAP_ADMIN_EMAIL:-smoke-admin@example.test}"
SIA_BOOTSTRAP_ADMIN_PASSWORD="${SIA_BOOTSTRAP_ADMIN_PASSWORD:-SmokeOnly-Compose-2026!}"
DEV_BASIC_AUTH_USERNAME="${DEV_BASIC_AUTH_USERNAME:-$SIA_BOOTSTRAP_ADMIN_USERNAME}"
DEV_BASIC_AUTH_PASSWORD="${DEV_BASIC_AUTH_PASSWORD:-$SIA_BOOTSTRAP_ADMIN_PASSWORD}"
export POSTGRES_PORT REDIS_PORT MINIO_PORT MINIO_CONSOLE_PORT BACKEND_PORT FRONTEND_PORT
export SIA_BOOTSTRAP_ADMIN_USERNAME SIA_BOOTSTRAP_ADMIN_EMAIL SIA_BOOTSTRAP_ADMIN_PASSWORD
export DEV_BASIC_AUTH_USERNAME DEV_BASIC_AUTH_PASSWORD

compose() {
  $DOCKER_COMPOSE -p "$SMOKE_COMPOSE_PROJECT_NAME" -f docker-compose.yml "$@"
}

cleanup() {
  if [ "$SMOKE_KEEP_RUNNING" != "1" ]; then
    compose down --volumes --remove-orphans >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT INT TERM

wait_for_content() {
  name="$1"
  url="$2"
  expected="$3"
  deadline=$(( $(date +%s) + SMOKE_TIMEOUT_SECONDS ))

  while [ "$(date +%s)" -le "$deadline" ]; do
    body="$(curl -fsS "$url" 2>/dev/null || true)"
    if [ -n "$body" ] && printf '%s' "$body" | grep -q "$expected"; then
      echo "OK $name"
      return 0
    fi
    sleep 2
  done

  echo "Smoke failed: $name did not return expected content from $url" >&2
  compose logs --no-color backend frontend >&2 || true
  exit 1
}

wait_for_status() {
  name="$1"
  url="$2"
  expected_status="$3"
  deadline=$(( $(date +%s) + SMOKE_TIMEOUT_SECONDS ))

  while [ "$(date +%s)" -le "$deadline" ]; do
    status="$(curl -sS -o /dev/null -w '%{http_code}' "$url" 2>/dev/null || true)"
    if [ "$status" = "$expected_status" ]; then
      echo "OK $name"
      return 0
    fi
    sleep 2
  done

  echo "Smoke failed: $name returned HTTP ${status:-none}, expected $expected_status from $url" >&2
  compose logs --no-color backend frontend >&2 || true
  exit 1
}

echo "Validating Docker Compose config..."
compose config >/dev/null

echo "Building and starting Docker Compose stack..."
compose up -d --build

wait_for_content "backend health" "$BACKEND_BASE_URL/actuator/health" '"status":"UP"'
wait_for_content "anonymous auth state" "$BACKEND_BASE_URL/api/auth/me" '"authenticated":false'
wait_for_content "server-side Basic Auth BFF" "$FRONTEND_BASE_URL/api/backend/api/auth/me" '"authenticated":true'
wait_for_content "BFF principal" "$FRONTEND_BASE_URL/api/backend/api/auth/me" '"username":"smoke-admin"'

for route in \
  "/" \
  "/accounting" \
  "/accounting/assets" \
  "/accounting/payables" \
  "/admin/settings" \
  "/admin/users" \
  "/billing" \
  "/connections" \
  "/connections/requests" \
  "/customers" \
  "/metering" \
  "/payments" \
  "/payments/bank-mutations" \
  "/receivables/aging" \
  "/receivables/collection-actions" \
  "/receivables/installments" \
  "/reports/financial-statements" \
  "/reports/trial-balance" \
  "/tariffs"
do
  wait_for_status "frontend route $route" "$FRONTEND_BASE_URL$route" "200"
done

failed_migrations="$(compose exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "select count(*) from flyway_schema_history where success = false;")"
if [ "$failed_migrations" != "0" ]; then
  echo "Smoke failed: Flyway has $failed_migrations failed migration(s)." >&2
  compose logs --no-color backend postgres >&2 || true
  exit 1
fi
echo "OK Flyway migration history has no failed entries"

DOCKER_COMPOSE="$DOCKER_COMPOSE -p $SMOKE_COMPOSE_PROJECT_NAME -f docker-compose.yml" \
  sh scripts/check-migration-constraints.sh

echo "Docker Compose smoke verification complete."

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

BACKEND_BASE_URL="${BACKEND_BASE_URL:-http://localhost:18080}"
FRONTEND_BASE_URL="${FRONTEND_BASE_URL:-http://localhost:13000}"
POSTGRES_DB="${POSTGRES_DB:-sia_pdam}"
POSTGRES_USER="${POSTGRES_USER:-sia}"
SMOKE_TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-180}"
SMOKE_KEEP_RUNNING="${SMOKE_KEEP_RUNNING:-1}"

cleanup() {
  if [ "$SMOKE_KEEP_RUNNING" != "1" ]; then
    $DOCKER_COMPOSE down --remove-orphans >/dev/null 2>&1 || true
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
  $DOCKER_COMPOSE logs --no-color backend frontend >&2 || true
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
  $DOCKER_COMPOSE logs --no-color backend frontend >&2 || true
  exit 1
}

echo "Validating Docker Compose config..."
$DOCKER_COMPOSE config >/dev/null

echo "Building and starting Docker Compose stack..."
$DOCKER_COMPOSE up -d --build

wait_for_content "backend health" "$BACKEND_BASE_URL/actuator/health" '"status":"UP"'
wait_for_content "anonymous auth state" "$BACKEND_BASE_URL/api/auth/me" '"authenticated":false'

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

failed_migrations="$($DOCKER_COMPOSE exec -T postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc "select count(*) from flyway_schema_history where success = false;")"
if [ "$failed_migrations" != "0" ]; then
  echo "Smoke failed: Flyway has $failed_migrations failed migration(s)." >&2
  $DOCKER_COMPOSE logs --no-color backend postgres >&2 || true
  exit 1
fi
echo "OK Flyway migration history has no failed entries"

sh scripts/check-migration-constraints.sh

echo "Docker Compose smoke verification complete."

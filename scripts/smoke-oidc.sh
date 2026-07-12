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

OIDC_COMPOSE_PROJECT_NAME="${OIDC_COMPOSE_PROJECT_NAME:-sia-pdam-oidc-smoke}"
OIDC_SMOKE_KEEP_RUNNING="${OIDC_SMOKE_KEEP_RUNNING:-0}"
SMOKE_TIMEOUT_SECONDS="${SMOKE_TIMEOUT_SECONDS:-240}"
POSTGRES_PORT="${POSTGRES_PORT:-15532}"
REDIS_PORT="${REDIS_PORT:-16479}"
MINIO_PORT="${MINIO_PORT:-19100}"
MINIO_CONSOLE_PORT="${MINIO_CONSOLE_PORT:-19101}"
BACKEND_PORT="${BACKEND_PORT:-18180}"
FRONTEND_PORT="${FRONTEND_PORT:-13100}"
KEYCLOAK_PORT="${KEYCLOAK_PORT:-18181}"
export POSTGRES_PORT REDIS_PORT MINIO_PORT MINIO_CONSOLE_PORT BACKEND_PORT FRONTEND_PORT KEYCLOAK_PORT

compose() {
  $DOCKER_COMPOSE -p "$OIDC_COMPOSE_PROJECT_NAME" \
    -f docker-compose.yml \
    -f infra/keycloak/docker-compose.oidc.yml \
    "$@"
}

cleanup() {
  if [ "$OIDC_SMOKE_KEEP_RUNNING" != "1" ]; then
    compose --profile oidc down --volumes --remove-orphans >/dev/null 2>&1 || true
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

  echo "OIDC smoke failed: $name did not return expected content from $url" >&2
  compose --profile oidc logs --no-color keycloak backend frontend >&2 || true
  exit 1
}

wait_for_status() {
  name="$1"
  url="$2"
  expected_status="$3"
  authorization="${4:-}"
  deadline=$(( $(date +%s) + SMOKE_TIMEOUT_SECONDS ))

  while [ "$(date +%s)" -le "$deadline" ]; do
    if [ -n "$authorization" ]; then
      status="$(curl -sS -H "Authorization: Bearer $authorization" -o /dev/null -w '%{http_code}' "$url" 2>/dev/null || true)"
    else
      status="$(curl -sS -o /dev/null -w '%{http_code}' "$url" 2>/dev/null || true)"
    fi
    if [ "$status" = "$expected_status" ]; then
      echo "OK $name"
      return 0
    fi
    sleep 2
  done

  echo "OIDC smoke failed: $name returned HTTP ${status:-none}, expected $expected_status from $url" >&2
  compose --profile oidc logs --no-color keycloak backend frontend >&2 || true
  exit 1
}

echo "Validating isolated OIDC Compose configuration..."
compose --profile oidc config >/dev/null

echo "Starting Keycloak 26.7.0 and infrastructure..."
compose --profile oidc up -d postgres redis minio keycloak
wait_for_content \
  "Keycloak realm discovery" \
  "http://localhost:$KEYCLOAK_PORT/realms/sia-pdam-test/.well-known/openid-configuration" \
  '"issuer"'

echo "Building OIDC backend and frontend..."
compose --profile oidc up -d --build backend frontend
wait_for_content "OIDC backend health" "http://localhost:$BACKEND_PORT/actuator/health" '"status":"UP"'
wait_for_content "NextAuth Keycloak provider" "http://localhost:$FRONTEND_PORT/api/auth/providers" '"keycloak"'
wait_for_status "protected API rejects anonymous access" "http://localhost:$BACKEND_PORT/api/accounts" "401"

token_response="$(compose --profile oidc --profile oidc-tools run --rm --no-deps oidc-token-client \
  -fsS -X POST \
  -H 'Content-Type: application/x-www-form-urlencoded' \
  --data-urlencode 'grant_type=password' \
  --data-urlencode 'client_id=sia-pdam' \
  --data-urlencode 'client_secret=smoke-client-secret-not-for-production' \
  --data-urlencode 'username=oidc-smoke' \
  --data-urlencode 'password=SmokeOnly-2026!' \
  'http://keycloak:8080/realms/sia-pdam-test/protocol/openid-connect/token')"
access_token="$(printf '%s' "$token_response" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')"

if [ -z "$access_token" ]; then
  echo "OIDC smoke failed: Keycloak token response did not contain access_token." >&2
  exit 1
fi
echo "OK Keycloak issued access token"

current_user="$(curl -fsS -H "Authorization: Bearer $access_token" "http://localhost:$BACKEND_PORT/api/auth/me")"
printf '%s' "$current_user" | grep -q '"username":"oidc-smoke"'
printf '%s' "$current_user" | grep -q '"authenticated":true'
printf '%s' "$current_user" | grep -q '"account.manage"'
printf '%s' "$current_user" | grep -q '"ROLE_FINANCE_SUPERVISOR"'
printf '%s' "$current_user" | grep -q '"ROLE_BILLING_OFFICER"'
echo "OK JWT principal, realm role, client role, and permission mapping"

wait_for_status \
  "permission-protected account API" \
  "http://localhost:$BACKEND_PORT/api/accounts" \
  "200" \
  "$access_token"

echo "OIDC smoke verification complete."

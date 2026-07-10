# Quality Gates

## Backend

```bash
cd backend
gradle clean test
gradle bootJar
```

## Frontend

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm run test:permissions
npm run build
```

## Docker

```bash
docker compose config
docker compose build
docker compose up -d
docker compose ps
```

On local Docker Desktop installations that expose only the legacy plugin, use `docker-compose` with the same arguments.

## Docker Compose Smoke

CI runs the full route smoke after backend, frontend, and compose-config gates pass:

```bash
sh scripts/smoke-compose.sh
```

The smoke script builds and starts the stack, waits for `/actuator/health`, verifies anonymous `/api/auth/me`, checks all baseline frontend routes, and fails if Flyway has any failed migration entry. By default it keeps the local stack running after success. CI sets `SMOKE_KEEP_RUNNING=0` so containers are torn down after the job.

Current frontend route coverage:

```txt
/
/accounting
/accounting/assets
/accounting/payables
/admin/settings
/billing
/connections
/connections/requests
/customers
/metering
/payments
/payments/bank-mutations
/receivables/aging
/receivables/collection-actions
/receivables/installments
/reports/financial-statements
/reports/trial-balance
/tariffs
```

## Migration

```bash
cd backend
gradle flywayValidate
gradle flywayMigrate
```

Current smoke verification also starts backend with PostgreSQL and confirms Flyway applies migrations before Hibernate validation:

```bash
POSTGRES_PORT=15432 REDIS_PORT=16379 MINIO_PORT=19000 MINIO_CONSOLE_PORT=19001 BACKEND_PORT=18080 \
docker-compose up -d postgres redis minio backend
```

To verify first-admin provisioning without default credentials, pass explicit bootstrap values:

```bash
SIA_BOOTSTRAP_ADMIN_USERNAME=admin \
SIA_BOOTSTRAP_ADMIN_EMAIL=admin@example.test \
SIA_BOOTSTRAP_ADMIN_PASSWORD='<operator-supplied-strong-password>' \
docker-compose up -d postgres redis minio backend
```

## Strict Review Output

| Severity | Location | Issue | Risk | Required Fix |
|---|---|---|---|---|

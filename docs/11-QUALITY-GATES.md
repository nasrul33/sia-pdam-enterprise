# Quality Gates

## Backend

```bash
cd backend
gradle clean test integrationTest bootJar
```

`test` mengecualikan kelas `*IT`; task `integrationTest` menjalankan API high-risk terhadap PostgreSQL Testcontainers dengan seluruh migrasi Flyway dan fixture deterministik.

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

The smoke script builds and starts the stack, waits for `/actuator/health`, verifies anonymous `/api/auth/me`, checks all baseline frontend routes, fails if Flyway has any failed migration entry, and then runs the PostgreSQL V22/V23 constraint plus V24 performance-index checks. By default it keeps the local stack running after success. CI sets `SMOKE_KEEP_RUNNING=0` so containers are torn down after the job.

Current frontend route coverage:

```txt
/
/accounting
/accounting/assets
/accounting/payables
/admin/settings
/admin/users
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

## Keycloak OIDC Smoke

```bash
sh scripts/smoke-oidc.sh
```

Smoke OIDC memakai project, port, network, dan volume terisolasi. Gate mengimpor realm test Keycloak 26.7.0, membangun backend dengan profil `oidc-smoke`, membangun frontend OIDC, memverifikasi discovery/provider, memastikan API terproteksi menolak anonymous, mengambil token test, dan memeriksa principal, realm role, client role, permission claim, serta endpoint `account.manage`. Credential realm ini hanya untuk smoke dan tidak boleh digunakan di staging/produksi.

CI menjalankan `smoke-compose.sh` lalu `smoke-oidc.sh`. Keduanya membersihkan container setelah selesai.

## Migration

```bash
cd backend
gradle flywayValidate
gradle flywayMigrate
```

DB-backed migration constraint checks run against the PostgreSQL service from Docker Compose:

```bash
sh scripts/check-migration-constraints.sh
```

The SQL file `backend/src/test/resources/db/v22-v23-constraint-checks.sql` validates V22 blueprint tables, V23 metering import/lock tables, and V24 performance indexes inside one transaction and ends with `ROLLBACK`, so the check does not persist fixture data.

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

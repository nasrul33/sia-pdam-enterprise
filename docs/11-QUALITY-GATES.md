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

## Migration

```bash
cd backend
gradle flywayValidate
gradle flywayMigrate
```

Current smoke verification also starts backend with PostgreSQL and confirms Flyway applies V1/V2 before Hibernate validation:

```bash
POSTGRES_PORT=15432 REDIS_PORT=16379 MINIO_PORT=19000 MINIO_CONSOLE_PORT=19001 BACKEND_PORT=18080 \
docker-compose up -d postgres redis minio backend
```

## Strict Review Output

| Severity | Location | Issue | Risk | Required Fix |
|---|---|---|---|---|

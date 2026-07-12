# Runbook Deployment Produksi

## Prasyarat Wajib

- Docker Engine dan Docker Compose plugin terpasang pada host yang dipatch.
- PostgreSQL produksi menyediakan backup terenkripsi dan point-in-time recovery.
- Keycloak tersedia melalui issuer HTTPS yang dapat diakses browser dan container backend.
- TLS berakhir di reverse proxy/load balancer; hanya port 443 yang dibuka ke publik.
- Image backend/frontend sudah dibangun CI, dipindai, dan dipush dengan tag commit immutable.
- Operator memiliki akses terpisah untuk deploy, database, backup, dan Keycloak.

Compose root adalah baseline single-node. Produksi multi-node harus memakai orchestrator yang menyediakan rolling update, secret manager, persistent volume, dan network policy tanpa mengubah kontrak environment di bawah.

## Environment Produksi

Buat `.env.production` di secret store/host deployment, jangan commit file tersebut:

```env
SPRING_PROFILES_ACTIVE=prod
AUTH_MODE=oidc
NEXT_PUBLIC_DEV_AUTH_MODE=oidc
NEXT_PUBLIC_DEV_BASIC_AUTH_USERNAME=
NEXT_PUBLIC_DEV_BASIC_AUTH_PASSWORD=
NEXT_PUBLIC_API_BASE_URL=https://api.sia-pdam.example.id
BACKEND_INTERNAL_URL=http://backend:8080
NEXTAUTH_URL=https://sia-pdam.example.id
AUTH_SECRET=<minimum-32-byte-random-secret>
KEYCLOAK_ISSUER_URI=https://identity.example.id/realms/sia-pdam
KEYCLOAK_CLIENT_ID=sia-pdam
KEYCLOAK_CLIENT_SECRET=<secret-manager-reference>
SIA_PAYMENT_WEBHOOK_SECRET=<minimum-32-byte-random-secret>
ALLOWED_ORIGINS=https://sia-pdam.example.id
POSTGRES_DB=sia_pdam
POSTGRES_USER=sia_app
POSTGRES_PASSWORD=<secret-manager-reference>
MINIO_ROOT_USER=<secret-manager-reference>
MINIO_ROOT_PASSWORD=<secret-manager-reference>
BACKEND_IMAGE=registry.example.id/sia-pdam/backend:<git-sha>
FRONTEND_IMAGE=registry.example.id/sia-pdam/frontend:<git-sha>
```

Jangan mengisi `SIA_BOOTSTRAP_ADMIN_*` di produksi OIDC. User dan role produksi dikelola di Keycloak.

## Preflight Release

Jalankan dari commit/tag release yang sama dengan image:

```bash
cd backend
gradle clean test integrationTest bootJar
cd ../frontend
npm ci
npm run test:permissions
npm run typecheck
npm run lint
npm run build
cd ..
docker compose --env-file .env.production config --quiet
sh scripts/smoke-compose.sh
sh scripts/smoke-oidc.sh
```

Pastikan backup pre-deploy selesai sesuai [BACKUP-RESTORE.md](BACKUP-RESTORE.md) dan catat checksum, release SHA, image digest, serta versi migrasi Flyway pada tiket perubahan.

## Urutan Deployment

```bash
docker login registry.example.id
docker compose --env-file .env.production pull backend frontend
docker compose --env-file .env.production up -d postgres redis minio
docker compose --env-file .env.production ps
```

Jalankan satu instance backend lebih dulu agar Flyway tidak bersaing dengan rollout paralel:

```bash
docker compose --env-file .env.production up -d --no-deps backend
docker compose --env-file .env.production logs --since=5m backend
curl -fsS https://api.sia-pdam.example.id/actuator/health
```

Deploy frontend setelah backend `UP` dan Flyway tidak melaporkan kegagalan:

```bash
docker compose --env-file .env.production up -d --no-deps frontend
curl -fsS https://sia-pdam.example.id/api/auth/providers
curl -fsS https://sia-pdam.example.id/
```

## Verifikasi Pasca-Deploy

```bash
docker compose --env-file .env.production ps
docker compose --env-file .env.production exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -tAc \
  "select version, description, success from flyway_schema_history order by installed_rank desc limit 5;"
curl -fsS https://api.sia-pdam.example.id/actuator/health
curl -fsS https://identity.example.id/realms/sia-pdam/.well-known/openid-configuration
```

Lakukan login OIDC dengan user UAT non-admin, buka dashboard, dan lakukan satu transaksi read-only. Untuk release finansial, rekonsiliasi query kontrol pada [OBSERVABILITY.md](OBSERVABILITY.md) harus bernilai nol sebelum perubahan dinyatakan selesai.

## Stop Condition

Hentikan rollout dan jalankan [ROLLBACK.md](ROLLBACK.md) jika health gagal lebih dari dua menit, migrasi gagal, login OIDC gagal, error HTTP 5xx melebihi 5%, atau query keseimbangan jurnal/ledger menghasilkan nilai selain nol.

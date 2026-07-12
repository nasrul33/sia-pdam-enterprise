# Runbook Rollback

## Prinsip

- Jangan menghapus atau mengubah baris `flyway_schema_history` secara manual.
- Jangan menjalankan SQL down migration pada data finansial tanpa migration baru dan review DBA.
- Rollback aplikasi hanya aman bila migrasi release bersifat backward-compatible.
- Bila migrasi/data tidak backward-compatible, lakukan restore/cutover ke database baru dari backup pre-deploy.

## Keputusan Rollback

1. **Aplikasi gagal, migrasi belum berjalan:** deploy ulang image SHA sebelumnya.
2. **Migrasi additive dan kompatibel:** rollback image backend/frontend ke digest sebelumnya; biarkan schema additive.
3. **Migrasi gagal di tengah:** hentikan writer, simpan log, jangan repair otomatis, eskalasi DBA.
4. **Data berubah salah atau schema tidak kompatibel:** hentikan writer dan restore database pre-deploy sesuai `BACKUP-RESTORE.md`.

## Rollback Image

Ubah hanya tag image pada `.env.production` ke digest release terakhir yang disetujui:

```env
BACKEND_IMAGE=registry.example.id/sia-pdam/backend:<previous-git-sha>
FRONTEND_IMAGE=registry.example.id/sia-pdam/frontend:<previous-git-sha>
```

Lalu jalankan:

```bash
docker compose --env-file .env.production pull backend frontend
docker compose --env-file .env.production up -d --no-deps backend
curl -fsS https://api.sia-pdam.example.id/actuator/health
docker compose --env-file .env.production up -d --no-deps frontend
curl -fsS https://sia-pdam.example.id/
```

## Rollback Database melalui Restore/Cutover

```bash
docker compose --env-file .env.production stop frontend backend
```

Restore backup ke database baru mengikuti `BACKUP-RESTORE.md`. Setelah verifikasi, ubah `POSTGRES_DB`/secret koneksi ke database restore, lalu:

```bash
docker compose --env-file .env.production up -d backend
curl -fsS https://api.sia-pdam.example.id/actuator/health
docker compose --env-file .env.production up -d frontend
```

## Verifikasi Wajib

```bash
docker compose --env-file .env.production exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c \
  "select count(*) as unbalanced_posted_journals from (select je.id from journal_entries je join journal_lines jl on jl.journal_entry_id=je.id where je.status='POSTED' group by je.id having sum(jl.debit)<>sum(jl.credit)) q;"
docker compose --env-file .env.production exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -c \
  "select count(*) as failed_migrations from flyway_schema_history where success=false;"
```

Verifikasi login OIDC, dashboard, invoice read-only, payment register, trial balance, dan audit log. Buka kembali akses pengguna hanya setelah health `UP`, query kontrol bernilai nol, dan Finance Controller menyetujui hasil.

## Bukti Insiden

Simpan release SHA gagal, image digest, waktu kejadian, alasan rollback, log backend/frontend/Keycloak, versi migrasi sebelum/sesudah, checksum backup, hasil query kontrol, operator, approver, dan tindakan pencegahan ulang.

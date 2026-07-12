# Runbook Backup dan Restore

## Kebijakan Minimum

- PostgreSQL: backup penuh harian, WAL/PITR berkelanjutan dengan target RPO maksimum 15 menit.
- MinIO: versioning dan replikasi ke lokasi berbeda.
- Retensi: 35 backup harian, 12 backup bulanan, dan sesuai kebijakan arsip PDAM.
- Enkripsi: in transit dan at rest; kunci tidak disimpan bersama backup.
- Restore drill: minimal triwulanan ke environment terisolasi.

Compose lokal tidak mengaktifkan WAL archive. Produksi wajib memakai PostgreSQL terkelola dengan PITR atau konfigurasi WAL archive yang diawasi sebelum go-live.

## Backup PostgreSQL Pre-Deploy

```bash
export BACKUP_DIR=/srv/backup/sia-pdam/$(date -u +%Y%m%dT%H%M%SZ)
install -d -m 0700 "$BACKUP_DIR"
docker compose --env-file .env.production exec -T postgres \
  pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" \
  --format=custom --compress=9 --no-owner --no-acl \
  > "$BACKUP_DIR/sia-pdam.dump"
sha256sum "$BACKUP_DIR/sia-pdam.dump" > "$BACKUP_DIR/sia-pdam.dump.sha256"
sha256sum -c "$BACKUP_DIR/sia-pdam.dump.sha256"
docker run --rm -v "$BACKUP_DIR:/backup:ro" postgres:16-alpine \
  pg_restore --list /backup/sia-pdam.dump >/dev/null
```

Simpan metadata release:

```bash
docker compose --env-file .env.production exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -Atc \
  "select version || '|' || description from flyway_schema_history where success order by installed_rank;" \
  > "$BACKUP_DIR/flyway-versions.txt"
git rev-parse HEAD > "$BACKUP_DIR/release-sha.txt"
```

Upload direktori backup ke object storage immutable dan verifikasi checksum setelah upload.

## Backup MinIO

Gunakan alias `production` dan `backup` yang credential-nya berasal dari secret manager:

```bash
mc version enable production/sia-pdam
mc mirror --preserve --overwrite production/sia-pdam backup/sia-pdam/$(date -u +%Y%m%dT%H%M%SZ)
mc du production/sia-pdam
```

Redis bukan system of record. Pulihkan dari PostgreSQL/aplikasi; jangan menjadikan snapshot Redis sebagai backup transaksi finansial.

## Restore PostgreSQL Terisolasi

Jangan restore menimpa database aktif. Buat database baru dan hentikan seluruh writer selama cutover:

```bash
export RESTORE_DB=sia_pdam_restore_$(date -u +%Y%m%d%H%M%S)
sha256sum -c "$BACKUP_DIR/sia-pdam.dump.sha256"
docker compose --env-file .env.production exec -T postgres \
  createdb -U "$POSTGRES_USER" "$RESTORE_DB"
docker compose --env-file .env.production exec -T postgres \
  pg_restore -U "$POSTGRES_USER" -d "$RESTORE_DB" \
  --clean --if-exists --no-owner --no-acl \
  < "$BACKUP_DIR/sia-pdam.dump"
```

Verifikasi hasil restore:

```bash
docker compose --env-file .env.production exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$RESTORE_DB" -v ON_ERROR_STOP=1 -c \
  "select count(*) as failed_migrations from flyway_schema_history where success = false;"
docker compose --env-file .env.production exec -T postgres \
  psql -U "$POSTGRES_USER" -d "$RESTORE_DB" -v ON_ERROR_STOP=1 -c \
  "select count(*) as unbalanced_posted_journals from (select je.id from journal_entries je join journal_lines jl on jl.journal_entry_id=je.id where je.status='POSTED' group by je.id having sum(jl.debit)<>sum(jl.credit)) q;"
```

Setelah aplikasi staging menunjuk ke database restore, jalankan health, login OIDC, laporan trial balance, dan rekonsiliasi jumlah invoice/payment/journal terhadap backup metadata. Cutover hanya setelah persetujuan DBA dan Finance Controller.

## Bukti Operasional

Catat waktu mulai/selesai, operator, ukuran backup, checksum, lokasi terenkripsi, versi Flyway, release SHA, hasil restore drill, RPO aktual, dan RTO aktual pada tiket perubahan.

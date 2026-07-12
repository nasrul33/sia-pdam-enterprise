# Runbook Observability

## Endpoint

| Endpoint | Tujuan | Akses |
|---|---|---|
| `/actuator/health` | readiness aplikasi dan dependency | publik melalui jaringan internal/probe |
| `/actuator/info` | metadata build | jaringan internal |
| `/actuator/metrics` | katalog metrik Micrometer | akun monitoring terautentikasi |
| `/actuator/prometheus` | scrape Prometheus | akun monitoring terautentikasi dan network allowlist |

Jangan mengekspos `metrics` atau `prometheus` langsung ke internet. Pada profil `prod`, gunakan client/service account Keycloak khusus monitoring dengan hak minimum atau management network terpisah.

## Pemeriksaan Cepat

```bash
curl -fsS https://api.sia-pdam.example.id/actuator/health
curl -fsS -H "Authorization: Bearer $MONITORING_TOKEN" \
  https://api.sia-pdam.example.id/actuator/prometheus | grep '^jvm_'
docker compose --env-file .env.production logs --since=15m --timestamps backend frontend
```

Kumpulkan stdout/stderr container ke log platform terpusat. Redact header `Authorization`, cookie session, password, client secret, webhook signature, dan isi payload yang mengandung PII. Audit finansial tetap bersumber dari `audit_logs` dan `audit_chain_entries`, bukan log container.

## Dashboard Minimum

- HTTP request rate, 4xx/5xx ratio, dan latency p50/p95/p99 per route.
- JVM heap/non-heap, GC pause, thread, dan process CPU.
- Hikari active/idle/pending/max connection.
- PostgreSQL connection, lock wait, deadlock, replication lag, disk, dan backup age.
- Container restart, CPU, memory, filesystem, dan network error.
- Keycloak login failure, token issuance failure, dan latency issuer/JWKS.
- Jumlah payment webhook gagal, idempotency conflict, reconciliation belum selesai, draft journal, dan period pre-close blocker.

## Alert Minimum

| Kondisi | Window | Severity |
|---|---:|---|
| Health bukan `UP` | 2 menit | Critical |
| HTTP 5xx > 2% | 5 menit | Warning |
| HTTP 5xx > 5% | 5 menit | Critical |
| API p95 > 2 detik | 10 menit | Warning |
| Hikari pending > 0 | 5 menit | Warning |
| PostgreSQL connection > 80% | 10 menit | Warning |
| PostgreSQL/volume disk > 80% | 15 menit | Warning |
| PostgreSQL/volume disk > 90% | 5 menit | Critical |
| Backup sukses terakhir > 24 jam | segera | Critical |
| Replication/WAL lag > RPO 15 menit | 5 menit | Critical |
| Container restart >= 3 | 10 menit | Warning |
| Flyway migration gagal | segera | Critical |
| Jurnal posted tidak seimbang | sekali terdeteksi | Critical |
| Ledger line hilang/duplikat | sekali terdeteksi | Critical |

## Query Kontrol Finansial

Jalankan minimal setiap 15 menit dan setelah deploy/restore:

```sql
SELECT count(*) AS unbalanced_posted_journals
FROM (
    SELECT je.id
    FROM journal_entries je
    JOIN journal_lines jl ON jl.journal_entry_id = je.id
    WHERE je.status = 'POSTED'
    GROUP BY je.id
    HAVING sum(jl.debit) <> sum(jl.credit)
) q;

SELECT count(*) AS posted_lines_without_ledger
FROM journal_entries je
JOIN journal_lines jl ON jl.journal_entry_id = je.id
LEFT JOIN ledger_entries le ON le.journal_line_id = jl.id
WHERE je.status = 'POSTED' AND le.id IS NULL;

SELECT count(*) AS duplicate_ledger_lines
FROM (
    SELECT journal_line_id
    FROM ledger_entries
    GROUP BY journal_line_id
    HAVING count(*) <> 1
) q;
```

Semua hasil harus `0`. Jika tidak, hentikan posting finansial, jangan mengubah jurnal posted, simpan bukti query, dan eskalasi ke Finance Controller serta DBA.

## Triage

1. Catat waktu, release SHA, route, status, request/correlation ID bila tersedia, dan user teknis tanpa token.
2. Periksa health, restart count, 5xx, Hikari, PostgreSQL lock/connection, lalu Keycloak.
3. Korelasikan aksi finansial melalui `audit_logs`, source module/record journal, dan audit chain.
4. Jangan restart berulang bila ada migrasi atau query finansial gagal; ikuti `ROLLBACK.md`.

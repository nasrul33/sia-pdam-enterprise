# SIA-PDAM Enterprise

Rebuild **Sistem Informasi Akuntansi dan Pelayanan PDAM** di repo baru dengan stack Java/Spring Boot + Next.js. Repo lama hanya menjadi referensi domain dan tidak boleh disentuh.

## Stack

- Backend: Java 26, Spring Boot 4.1.0, Spring Web, Spring Security, Spring Data JPA, Flyway, PostgreSQL, Redis, MinIO.
- Frontend: Next.js 16.2.10, React 19.2.7, TypeScript 6.0.3 strict, Tailwind CSS 4.3.2, TanStack Query 5.101.2, Zod 4.4.3.
- Database: PostgreSQL 16.
- DevOps: Docker Compose, GitHub Actions.


## Platform Version Policy

- Backend baseline menggunakan **Java 26** dan **Spring Boot 4.1.0**.
- Gradle baseline menggunakan **Gradle 9.6.1** karena Java 26 membutuhkan Gradle 9.4+ untuk dukungan toolchain/run yang aman.
- Jika target produksi membutuhkan LTS konservatif, evaluasi ulang Java 25 LTS sebelum go-live tanpa mengubah prinsip domain dan audit.

## Prinsip Utama

1. Tidak boleh memakai `float` atau `double` untuk uang.
2. Semua nilai uang memakai `BigDecimal` melalui primitive `Money`.
3. Jurnal `POSTED` immutable.
4. Koreksi transaksi dilakukan melalui reversal/adjustment.
5. Debit harus sama dengan kredit sebelum posting.
6. Period lock wajib mencegah posting dan mutasi transaksi finansial.
7. Payment idempotency wajib dengan unique constraint database.
8. Semua aksi sensitif wajib masuk audit trail.

## Quick Start

```bash
docker compose config # or: docker-compose config
docker compose up --build # or: docker-compose up --build
```

Backend:

```bash
cd backend
gradle clean test
gradle bootJar
```

Frontend:

```bash
cd frontend
npm ci
npm run lint
npm run typecheck
npm run build
```

Dashboard awal membaca `GET /api/dashboard/overview` dari backend dan sudah memiliki loading, error, empty, dan table state.

## Struktur

```txt
backend/                 Spring Boot backend
frontend/                Next.js dashboard
docs/                    planning, agents, artifacts, primitives
infra/                   infrastructure notes/configs
scripts/                 helper scripts
.github/workflows/       CI pipeline
CODEX.md                 Codex execution contract
AGENTS.md                Agent role contract
```

## Catatan

Scaffold ini sengaja memulai dari foundation, primitive uang, audit trail persisted, idempotency payment, schema domain utama, dan accounting core. UI finansial tidak boleh mendahului backend domain dan API contract.

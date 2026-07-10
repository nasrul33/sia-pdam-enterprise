# Blueprint Gap Closure Design

## Tujuan

Menutup seluruh gap yang tersisa antara SIA-PDAM Enterprise dan blueprint `nasrul33/SIA-PDAM` tanpa menurunkan kontrol finansial, keamanan, atau kualitas operasional. Implementasi dilakukan sebagai modular monolith Java/Spring Boot dan Next.js dengan migrasi PostgreSQL non-destruktif, otorisasi server-side, audit trail, dan pengujian berlapis.

## Ruang Lingkup

Program ini mencakup tujuh increment yang masing-masing harus dapat diuji dan direview secara mandiri:

1. permission parity untuk customer, connection, metering, tariff, dan receivable aging;
2. administrasi pengguna/role serta Keycloak OIDC untuk produksi;
3. pre-close checklist periode akuntansi;
4. komponen tarif non-air dan denda;
5. hardening konfigurasi dan runbook produksi;
6. seeded API integration tests untuk workflow berisiko tinggi;
7. penggantian input UUID mentah dengan entity selector pada UI operator.

Perubahan tidak mencakup penyalinan source Laravel/Livewire dari blueprint, migrasi data produksi, atau perubahan destruktif terhadap tabel yang sudah ada.

## Keputusan Arsitektur

### Strategi Implementasi

Setiap increment menggunakan vertical slice: migration, domain/application service, API, frontend jika relevan, test, dokumentasi, lalu quality gate. Increment berikutnya baru dimulai setelah test terfokus increment sebelumnya lulus. Commit dipisahkan per increment untuk menjaga auditability dan rollback.

### Autentikasi

- Profil produksi menggunakan Spring Security OAuth2 Resource Server dengan JWT dari Keycloak.
- Frontend menggunakan OIDC Authorization Code Flow dengan PKCE dan session server-side; access token tidak disimpan di `localStorage`.
- Authority aplikasi berasal dari claim role/permission Keycloak dan dipetakan ke format permission yang sudah digunakan, misalnya `journal.post`.
- Database tetap menjadi katalog permission aplikasi dan relasi role untuk administrasi serta audit. Sinkronisasi ke Keycloak dilakukan melalui adapter yang eksplisit, bukan dari domain service.
- Basic Auth hanya aktif pada profil `local` dan `test`. Aplikasi gagal startup bila Basic Auth aktif pada profil produksi.
- Endpoint health tetap publik. Endpoint webhook pembayaran tetap tanpa login pengguna, tetapi wajib lolos verifikasi HMAC.

### Otorisasi

Permission baru yang wajib tersedia:

- `customer.read`, `customer.manage`;
- `connection.read`, `connection.manage`;
- `meter-route.read`, `meter-route.manage`;
- `meter-reading.read`, `meter-reading.create`, `meter-reading.verify`, `meter-reading.lock`;
- `tariff.read`, `tariff.manage`, `tariff.calculate`;
- `receivable-aging.read`, `receivable-aging.generate`;
- `user.read`, `user.manage`, `role.manage`.

Semua endpoint mutasi memakai `@PreAuthorize` granular. Endpoint baca juga tidak boleh hanya memakai `isAuthenticated()` bila kontrak API menetapkan permission khusus. UI menyembunyikan atau menonaktifkan aksi berdasarkan authority, tetapi backend tetap menjadi pengendali utama.

### Administrasi Pengguna dan Role

Backend menyediakan API berpaginasi untuk membaca pengguna, membaca katalog role/permission, mengaktifkan atau menonaktifkan pengguna, dan mengubah assignment role. Mutasi wajib mencatat actor, alasan, timestamp, keadaan sebelum, dan keadaan sesudah. Pengguna tidak boleh menonaktifkan akun sendiri atau menghapus role super-admin terakhir.

Halaman `/admin/users` menyediakan pencarian, filter status/role, detail authority efektif, assignment role, dan status loading/error/empty/success. Sinkronisasi identity provider ditampilkan sebagai status operasional tanpa mengekspos secret Keycloak.

## Kontrol Akuntansi

### Pre-Close Checklist

Periode hanya boleh berpindah dari `OPEN` ke `CLOSING_REVIEW` bila checklist tidak memiliki blocker. Checklist membaca data secara konsisten dalam transaksi read-only dan mengembalikan kode, jumlah, tingkat keparahan, dan tautan tindak lanjut untuk setiap temuan.

Blocker minimum:

- jurnal draft pada periode;
- transaksi depresiasi aset yang belum selesai untuk aset aktif yang wajib disusutkan;
- rekonsiliasi pembayaran/bank pada periode yang belum selesai atau belum sign-off;
- allowance/provision piutang yang belum diposting bila snapshot aging periode tersedia;
- billing batch periode yang belum selesai atau invoice draft yang belum diputuskan.

Endpoint preview checklist dapat dipanggil dengan permission `period.manage`. Transisi `startClosingReview` menjalankan ulang checklist di backend dan gagal atomik bila blocker masih ada. Lock periode tetap membutuhkan `period.close` dan hanya diizinkan dari `CLOSING_REVIEW`.

## Billing dan Tarif

### Model Tarif

`tariff_versions` memperoleh kolom decimal non-null dengan default nol untuk:

- `fixed_charge`;
- `levy_charge`;
- `admin_charge`;
- `waste_charge`;
- `penalty_rate`.

Constraint memastikan seluruh komponen bernilai non-negatif dan `penalty_rate` berada pada rentang yang disepakati model, yaitu `0..100` persen. Migrasi bersifat additive sehingga data lama tetap valid dengan nilai nol.

### Kalkulasi dan Invoice

Tariff engine menghasilkan rincian blok pemakaian dan rincian biaya non-air secara terpisah. `subtotal` adalah jumlah seluruh komponen sebelum denda. Denda dihitung server-side dari basis yang dikunci oleh kontrak: persentase terhadap subtotal tagihan yang masih terutang, dibulatkan dua desimal dengan `HALF_UP`.

Invoice menyimpan snapshot setiap komponen agar perubahan tarif berikutnya tidak mengubah invoice historis. Issue invoice memposting total piutang secara debit dan memecah kredit ke akun pendapatan air serta akun pendapatan non-air yang dikonfigurasi. Posting ditolak bila akun komponen wajib belum tersedia, periode terkunci, atau debit dan kredit tidak seimbang. Void memakai jurnal reversal, bukan mutasi jurnal terposting.

## UX Operator

Input UUID mentah diganti dengan reusable async entity selector untuk customer, connection, route, invoice, payment, dan account. Selector menyediakan pencarian server-side, debounce, bounded result, keyboard navigation, loading, error, empty, dan selected-state yang stabil. UUID tetap menjadi value internal, sedangkan operator melihat nomor bisnis, nama, status, dan konteks singkat.

Endpoint lookup menggunakan pagination dan permission baca domain terkait. Form tetap dapat menampilkan entity terpilih ketika hasil tersebut tidak berada pada halaman pencarian saat ini.

## Hardening Produksi

- Startup produksi gagal bila webhook secret kosong atau masih bernilai default development.
- Environment produksi tidak boleh menerima variabel frontend Basic Auth.
- Konfigurasi Keycloak mencakup issuer URI, audience, client ID, redirect URI, logout URI, dan claim mapping tanpa menyimpan client secret di repo.
- `infra/` memuat runbook deployment, backup, restore drill, rollback aplikasi, rollback migration additive, health check, log terstruktur, metrics, alert minimum, dan incident verification.
- Docker Compose lokal tetap dapat dijalankan tanpa Keycloak melalui profil local; compose production reference mencantumkan dependency identity provider secara eksplisit.

## Pengujian

### Backend

- Unit test untuk permission expression, tariff calculation, penalty rounding, pre-close blocker, role safety rule, dan secret validation.
- Controller security tests untuk anonymous `401`, authenticated-without-permission `403`, dan authority yang tepat menghasilkan respons domain.
- Test migration memastikan permission/role grant, kolom tarif, constraint, dan index terpasang.
- Seeded integration profile menjalankan PostgreSQL nyata dan memverifikasi workflow issue invoice, settlement/reversal, bank reconciliation, allowance, depreciation, dan period close.

### Frontend

- Test model/permission untuk setiap aksi sensitif.
- Component test entity selector untuk loading, error, empty, search, keyboard, dan selected entity.
- Build route mencakup `/admin/users` serta seluruh route baseline.

### End-to-End dan Smoke

- Local Basic Auth smoke tetap menguji health, auth, Flyway, dan route utama.
- OIDC smoke memakai realm test Keycloak dan memverifikasi login, authority mapping, forbidden state, serta logout.
- Seluruh gate wajib lulus: backend test dan bootJar, frontend test/lint/typecheck/build, migration checks, Docker Compose smoke, lalu audit blueprint ulang.

## Migrasi dan Kompatibilitas

- Semua migrasi schema bersifat additive; tidak ada drop, rename, atau rewrite data.
- Default nol pada komponen tarif mempertahankan hasil invoice lama.
- Permission baru di-seed idempotent dan diberikan ke super-admin; role operasional menerima grant sesuai prinsip least privilege.
- Basic Auth lokal dipertahankan agar developer workflow dan smoke yang ada tidak terputus.
- API response lama hanya boleh ditambah field; field yang sudah ada tidak dihapus atau diubah maknanya.

## Observability dan Audit

Mutasi user/role, transisi periode, kalkulasi/issue/void invoice, dan perubahan konfigurasi security menghasilkan audit event terstruktur dengan correlation ID. Metric minimum meliputi kegagalan login, forbidden response, webhook signature failure, billing batch failure, unbalanced journal rejection, dan pre-close blocker count.

## Urutan Delivery

1. Permission parity dan test otorisasi.
2. Pre-close checklist dan kontrol transisi periode.
3. Tariff non-air, denda, snapshot invoice, dan posting seimbang.
4. Admin user/role API dan UI.
5. Keycloak/OIDC production profile dan local Basic Auth isolation.
6. Entity selector dan penghapusan input UUID mentah.
7. Runbook, seeded integration profile, OIDC smoke, full gate, dan blueprint re-audit.

## Kriteria Selesai

Program dinyatakan selesai hanya bila:

- tidak ada controller domain yang masih memakai `isAuthenticated()` untuk operasi dengan permission granular;
- periode tidak dapat masuk closing review ketika checklist memiliki blocker;
- tariff engine dan invoice mencakup seluruh komponen blueprint dan jurnal issue/void selalu seimbang;
- admin dapat mengelola user/role tanpa melanggar self-disable dan last-super-admin guard;
- produksi memakai OIDC dan menolak Basic Auth/default webhook secret;
- UI operator tidak lagi meminta UUID mentah pada workflow utama;
- seeded API integration tests dan seluruh CI/smoke gate lulus;
- dokumentasi context pack, API contract, blueprint mapping, deployment, backup, rollback, dan observability sudah diperbarui;
- audit akhir tidak menemukan gap functional, security, accounting control, frontend coverage, atau production readiness yang berada dalam ruang lingkup spesifikasi ini.

# Blueprint Mapping - SIA-PDAM Enterprise

## Source Blueprint

- Blueprint repository: `https://github.com/nasrul33/SIA-PDAM.git`
- Local reference path: `.blueprint/SIA-PDAM`
- Blueprint stack: Laravel 12, PHP 8.3, Livewire 3, Alpine, Tailwind 3, PostgreSQL 16, Redis, MinIO.
- Target stack tetap: Java 26, Spring Boot 4.1, PostgreSQL, Flyway, Next.js 16, React 19, TypeScript, Tailwind.

## Adoption Rule

Repo blueprint dipakai sebagai referensi domain, acceptance criteria, backlog, dan kontrol audit. Implementasi tidak dicopy mentah karena arsitektur target berbeda. Setiap fitur dari blueprint harus diterjemahkan ke modular monolith Java/Spring dan frontend Next.js dengan kontrak API, migration, test, RBAC, audit trail, dan UI state lengkap.

## Module Mapping

| Blueprint Area | Target Module | Adoption Focus |
|---|---|---|
| Accounting services, CoA, period, journal, closing, payables, fixed assets | `backend/accounting`, `frontend/accounting`, `reporting` | Posting governance, reversal, opening/closing entries, AP, asset depreciation/disposal, posted-only reporting |
| Billing tariff, invoice generation, correction, invoice document | `backend/billing`, `frontend/billing` | Tariff versioning, batch issue readiness, invoice issue, correction workflow, invoice document/PDF |
| Payment webhook, counter payment, allocation, bank mutation import, daily reconciliation, receipt | `backend/payment`, `backend/reporting`, `frontend/payments` | Idempotency, settlement, reversal, bank import, reconciliation jobs, stale packet acknowledgement, receipt surface |
| Customer, customer history, connection requests | `backend/customer`, `backend/connection`, `frontend/customers`, `frontend/connections` | Master data lifecycle, request workflow, customer history, ownership validation |
| Metering route, reading intake, verification, locking | `backend/metering`, `frontend/metering` | Route build, reading import/offline sync, anomaly validation, verify/lock workflow |
| Receivable aging, dunning, installment, allowance | `backend/receivable`, `frontend/receivables` | Aging snapshot, collection worklist, installment plan, allowance/provision |
| Reporting financial statements, tax recap, exports | `backend/reporting`, `frontend/reports` | SAK EP-style statements, tax recap, posted-ledger source, bounded exports |
| RBAC, SoD, audit chain | `shared/security`, `shared/audit`, all frontend command panels | Granular authorities, separation of duties, tamper-evident audit chain, permission-aware UI |
| Docker, scheduler, queue jobs, UAT docs | `infra`, `scripts`, `.github`, docs | Worker/scheduler readiness, health checks, reproducible local smoke path |

## Blueprint Gap Closure Register

| Gap ID | Status | Evidence Implementasi | Evidence Verifikasi |
|---|---|---|---|
| BP-BIL-001 | Closed | V20 invoice void trace, issue/void API, exact source-journal reversal, guarded billing UI | Billing/accounting service and permission tests; seeded issue-settle-reverse-void IT |
| BP-BIL-002 | Closed | persisted invoice document endpoint and preview/print surface | InvoiceDocumentApplicationServiceTest, billing model/frontend gates |
| BP-PAY-001 | Closed | V22 bank mutation persistence, source adapters, import/reconcile API/UI | PaymentBankMutationApplicationServiceTest, frontend import model tests |
| BP-PAY-002 | Closed | daily reconciliation command, receipt/detail API, review/evidence/sign-off surfaces | payment reconciliation/reporting tests and frontend model tests |
| BP-REC-001 | Closed | V22 installment plan/item lifecycle and UI | ReceivableBlueprintApplicationServiceTest, frontend gates |
| BP-REC-002 | Closed | dunning worklist, collection actions, allowance posting | collection/receivable/accounting tests, frontend gates |
| BP-ACC-001 | Closed | supplier/AP record and settlement through accounting posting | AccountingBlueprintApplicationServiceTest, V22 constraint checks |
| BP-ACC-002 | Closed | fixed-asset registration, depreciation, disposal, and pre-close blocker | accounting tests and seeded period-close IT |
| BP-ACC-003 | Closed | opening balance, closing entry, reversal, period pre-close/lock | accounting tests and seeded period-close IT |
| BP-REP-001 | Closed | posted-ledger financial statements, trial balance, and tax recap | reporting tests, ledger balance IT, frontend gates |
| BP-SEC-001 | Closed | tamper-evident audit-chain writer and verification endpoint | AuditChainApplicationServiceTest and permission tests |
| BP-OPS-001 | Closed | application settings plus guarded user/role admin workspaces | admin/settings backend tests, 64-test frontend suite, route smoke |
| BP-MTR-001 | Closed | V23 offline import batches, row results, explicit reading lock, locked-only billing | metering/billing tests, V23 constraint smoke, frontend gates |
| BP-DB-001 | Closed | V24 additive performance indexes for operational/reporting queries | rollback-only PostgreSQL constraint/index smoke |

## Adoption Status - 2026-07-10

Baseline parity sudah ditambahkan untuk gap besar blueprint:

- `BP-PAY-001` dan `BP-PAY-002`: tabel `bank_mutations`, import backend, daily reconciliation command, dan receipt endpoint eksplisit.
- `BP-REC-001` dan `BP-REC-002`: installment plan/items, dunning runner, dan allowance posting ke accounting.
- `BP-ACC-001`, `BP-ACC-002`, dan `BP-ACC-003`: suppliers, AP payables, fixed assets, depreciation/disposal, journal reversal, opening balance, dan closing entry.
- `BP-REP-001`: financial statements dan tax recap dari posted ledger.
- `BP-SEC-001`: audit-chain table, writer, dan verification endpoint.
- `BP-OPS-001`: app settings backend dan frontend.
- `BP-MTR-001`: offline meter reading import batch, row-level imported/skipped/invalid result, source device trace, explicit `LOCKED` reading workflow, dan billing generation dari locked readings only.
- `BP-DB-001`: blueprint performance index review translated into additive PostgreSQL indexes for list filters, date-range reporting, open receivables, metering, payments, and reconciliation workloads.
- Customer/Connection blueprint gap: connection request workflow dan customer history read surface.
- DevOps CI parity: GitHub Actions now runs frontend permission/workspace model tests and Docker Compose route smoke for backend health, anonymous auth state, Flyway no-failure history, and all baseline frontend routes.
- Testing parity: Docker Compose smoke now runs DB-backed V22/V23 PostgreSQL constraint checks plus V24 performance-index checks for blueprint financial/operational tables, metering import/lock tables, and dashboard/report query paths.
- Release integration parity: dedicated `integrationTest` runs API-level invoice issue, payment settlement/reversal, invoice void, posted-journal/ledger balance, and period pre-close controls against fresh PostgreSQL migrations and deterministic seed data.
- Production security parity: isolated Keycloak 26.7.0 smoke validates OIDC token issuance, NextAuth provider wiring, JWT principal, realm/client roles, permission claims, anonymous rejection, and a permission-protected API without weakening the production HTTPS issuer validator.
- Operations parity: executable deployment, backup/restore, rollback, and observability runbooks define immutable images, PITR/backup evidence, Flyway controls, health/metrics/logs, alert thresholds, and financial reconciliation queries.
- Focused backend regression tests sudah ditambahkan untuk AP posting/settlement, asset depreciation, journal reversal, closing entry, opening balance duplicate guard, bank mutation import/reconcile, installment plan, dunning, allowance delegation, dan audit-chain verification.

Frontend parity route yang ditambahkan:

- `/accounting/payables`
- `/accounting/assets`
- `/payments/bank-mutations`
- `/receivables/installments`
- `/reports/financial-statements`
- `/admin/settings`
- `/connections/requests`
- `/metering` now includes offline import and lock workflow controls

## Adoption Protocol

1. Inspect the relevant blueprint files before implementing a gap.
2. Convert the business rule into target Java/Next API contracts, not Laravel/Livewire code.
3. Add or update Flyway migration with constraints and indexes for critical invariants.
4. Add backend tests for domain/application behavior and frontend tests for permission/state logic.
5. Update `docs/00-CONTEXT-PACK.md` and backlog with traceability after each adopted feature.

## Post-Baseline Validation Targets

Blueprint implementation gaps listed above are closed at baseline level. The next phase is controlled UAT with official tariff/numbering data, infrastructure-specific secret manager/TLS/PITR wiring, load testing, and operator sign-off; these require environment and business inputs rather than additional inferred domain behavior.

## Final Audit Evidence - 2026-07-12

| Control | Command/Evidence | Result |
|---|---|---|
| Generic authentication scan | `rg -n 'isAuthenticated\(\)' backend/src/main/java/id/pdam/sia` | Only `AuthController` response-state reporting; no endpoint authorization contract uses generic authentication |
| Production secret exposure | `rg -n 'dev-only-change-me\|NEXT_PUBLIC_DEV_BASIC_AUTH_(USERNAME\|PASSWORD)' backend/src/main/resources/application-prod.yml frontend/src` | No match |
| Raw UUID operator input | `rg -n 'placeholder=.*UUID' frontend/src` | No match |
| Backend clean gate | `gradle clean test integrationTest bootJar` | Passed in 5m47s |
| Frontend clean gate | `npm ci && npm run test:permissions && npm run typecheck && npm run lint && npm run build` | Passed; 64 tests, 0 vulnerabilities, 21 routes |
| Local runtime gate | `sh scripts/smoke-compose.sh` | Passed on isolated fresh project/volumes, including authenticated server-side Basic BFF |
| OIDC runtime gate | `sh scripts/smoke-oidc.sh` | Passed on isolated Keycloak 26.7.0 project/volumes |
| Git hygiene | `git diff --check`, scope/status/secret review | Required before final commit and push; `.superpowers/` excluded |

No approved blueprint implementation gap remains open. Official tariff values, numbering policy, production TLS/secret-manager/PITR endpoints, load targets, and UAT approval remain external business/infrastructure inputs and are not inferred in code.

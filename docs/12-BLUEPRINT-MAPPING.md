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

## Extracted Blueprint Gap Backlog

| Gap ID | Gap | Target Agent | Priority |
|---|---|---|---|
| BP-BIL-001 | Invoice correction workflow with reversal/adjustment governance | BillingAgent + AccountingAgent | High |
| BP-BIL-002 | Invoice document/PDF generation and customer-facing print view | BillingAgent + FrontendAgent | High |
| BP-PAY-001 | Bank mutation import adapter beyond frontend-only UAT normalization | PaymentAgent | High |
| BP-PAY-002 | Daily reconciliation job and receipt view data | PaymentAgent + DevOpsAgent | Medium |
| BP-REC-001 | Installment plan and installment item lifecycle | ReceivableAgent | High |
| BP-REC-002 | Dunning worklist, allowance, and provision workflow | ReceivableAgent + AccountingAgent | Medium |
| BP-ACC-001 | Payable/AP recording and settlement | AccountingAgent | High |
| BP-ACC-002 | Fixed asset registration, depreciation, and disposal | AccountingAgent | Medium |
| BP-ACC-003 | Opening balance, closing entry, and journal reversal services | AccountingAgent | High |
| BP-REP-001 | Financial statements and tax recap from posted ledger | ReportingAgent | High |
| BP-SEC-001 | Tamper-evident audit chain verification | SecurityAgent | Medium |
| BP-OPS-001 | Application settings/admin configuration surface | FrontendAgent + BackendFoundationAgent | Medium |
| BP-DB-001 | Blueprint performance indexes reviewed against current PostgreSQL schema | DatabaseAgent | Medium |

## Adoption Status - 2026-07-10

Baseline parity sudah ditambahkan untuk gap besar blueprint:

- `BP-PAY-001` dan `BP-PAY-002`: tabel `bank_mutations`, import backend, daily reconciliation command, dan receipt endpoint eksplisit.
- `BP-REC-001` dan `BP-REC-002`: installment plan/items, dunning runner, dan allowance posting ke accounting.
- `BP-ACC-001`, `BP-ACC-002`, dan `BP-ACC-003`: suppliers, AP payables, fixed assets, depreciation/disposal, journal reversal, opening balance, dan closing entry.
- `BP-REP-001`: financial statements dan tax recap dari posted ledger.
- `BP-SEC-001`: audit-chain table, writer, dan verification endpoint.
- `BP-OPS-001`: app settings backend dan frontend.
- Customer/Connection blueprint gap: connection request workflow dan customer history read surface.

Frontend parity route yang ditambahkan:

- `/accounting/payables`
- `/accounting/assets`
- `/payments/bank-mutations`
- `/receivables/installments`
- `/reports/financial-statements`
- `/admin/settings`
- `/connections/requests`

## Adoption Protocol

1. Inspect the relevant blueprint files before implementing a gap.
2. Convert the business rule into target Java/Next API contracts, not Laravel/Livewire code.
3. Add or update Flyway migration with constraints and indexes for critical invariants.
4. Add backend tests for domain/application behavior and frontend tests for permission/state logic.
5. Update `docs/00-CONTEXT-PACK.md` and backlog with traceability after each adopted feature.

## Immediate Next Adoption Targets

1. TestingAgent: add focused tests for V22 workflows: AP posting, asset depreciation/disposal, bank mutation reconcile, installment plan, allowance, audit-chain verify.
2. MeteringAgent: deepen blueprint parity for offline reading import and explicit reading lock semantics.
3. DevOpsAgent: add CI coverage for blueprint baseline routes and Docker Compose smoke targets.

# Context Pack - SIA-PDAM Enterprise

## Project Identity

- Project name: SIA-PDAM Enterprise
- Repository: sia-pdam-enterprise
- Objective: Rebuild sistem PDAM berbasis Java/Spring Boot dan Next.js
- Current phase: Bootstrap
- Last updated: 2026-07-10

## Immutable Context

- Repo baru, tidak mengubah repo lama.
- Backend: Java 26 + Spring Boot 4.1.0.
- Frontend: Next.js 16.2.10 + React 19.2.7 + TypeScript 6.0.3 + Tailwind CSS 4.3.2.
- Database: PostgreSQL + Flyway.
- Architecture: Modular monolith.
- Financial rules: no monetary primitive outside BigDecimal/Money, posted journal immutable, period lock mandatory.

## Blueprint Source

- Source repository: `https://github.com/nasrul33/SIA-PDAM.git`
- Local reference path: `.blueprint/SIA-PDAM` (ignored from git)
- Usage rule: blueprint is domain/backlog/acceptance reference only. Laravel/Livewire implementation is not copied into the Java/Spring and Next.js target stack.
- Mapping document: `docs/12-BLUEPRINT-MAPPING.md`

## Business Rules

| ID | Rule | Module | Status |
|---|---|---|---|
| BR-ACC-001 | Debit must equal credit before posting | Accounting | confirmed |
| BR-ACC-002 | Posted journal is immutable | Accounting | confirmed |
| BR-ACC-003 | Locked period blocks posting | Accounting | confirmed |
| BR-ACC-004 | Posted journal must materialize one immutable ledger entry per journal line | Accounting | confirmed |
| BR-CUS-001 | Customer number is unique | Customer | confirmed |
| BR-CON-001 | Connection number is unique and lifecycle-controlled | Connection | confirmed |
| BR-MTR-001 | Meter route code is unique | Metering | confirmed |
| BR-MTR-002 | Meter reading is unique per connection and period | Metering | confirmed |
| BR-MTR-003 | Offline meter reading import records imported, skipped, and invalid rows with source device trace and audit reason | Metering | confirmed |
| BR-MTR-004 | Verified meter reading must be explicitly locked before it can feed billing generation | Metering/Billing | confirmed |
| BR-BIL-001 | Progressive tariff calculation uses active effective version | Billing | confirmed |
| BR-BIL-002 | Tariff blocks must be sequential, contiguous, and end with an unbounded block | Billing | confirmed |
| BR-BIL-003 | Billing batch generation is idempotent and creates draft invoices from locked readings only | Billing | confirmed |
| BR-BIL-004 | Invoice issue posts receivable debit and revenue credit through accounting service before status becomes issued | Billing | confirmed |
| BR-BIL-005 | Billing batch issue readiness is read-only and flags draft amount, blocked invoices, and missing journal trace before operator issue action | Billing/Reporting | confirmed |
| BR-BIL-006 | Invoice document preview is read-only and must use stored invoice, line, connection, and customer data without recalculating tariff amounts | Billing | confirmed |
| BR-BIL-007 | Invoice void is allowed only for issued unpaid invoices, reverses the original posted billing journal exactly through accounting service, stores void journal trace, and keeps the original invoice record | Billing/Accounting | confirmed |
| BR-PAY-001 | Payment idempotency is enforced in DB | Payment | confirmed |
| BR-PAY-002 | Payment webhook signature must be validated before persistence | Payment | confirmed |
| BR-PAY-003 | Counter payment allocation total must equal payment amount and duplicate retry must be no-op | Payment | confirmed |
| BR-PAY-004 | Counter payment settlement posts debit cash/bank and credit receivable through accounting service | Payment | confirmed |
| BR-PAY-005 | Payment reversal restores invoice outstanding and posts debit receivable credit cash/bank through accounting service | Payment | confirmed |
| BR-PAY-006 | Payment reconciliation sessions are operational non-posting records and can be completed only after all items are no longer OPEN | Payment | confirmed |
| BR-PAY-007 | Accepted reconciliation exceptions require an explicit source-traceable adjustment journal through accounting service; matching results never auto-post journals | Payment/Accounting | confirmed |
| BR-PAY-008 | Bank reconciliation evidence report is read-only and exportable only after reconciliation session completion | Payment/Reporting | confirmed |
| BR-PAY-009 | Completed bank reconciliation evidence can be signed off once with reason by an actor different from creator and completer | Payment/Reporting | confirmed |
| BR-PAY-010 | Bank reconciliation review register is read-only, completed-session-only, and exposes sign-off SLA and exception workload without ledger mutation | Payment/Reporting | confirmed |
| BR-PAY-011 | Bank reconciliation handoff export is read-only and includes reviewer handoff columns without creating notes or approval mutations | Payment/Reporting | confirmed |
| BR-PAY-012 | Bank reconciliation reviewer handoff notes are controlled metadata with revision history and must not alter signed-off evidence, reconciliation items, payments, journals, or ledger rows | Payment/Reporting | confirmed |
| BR-PAY-013 | Bank reconciliation handoff workload is read-only, filterable by owner/status/due date, and exposes overdue follow-up SLA without mutating evidence or ledger records | Payment/Reporting | confirmed |
| BR-PAY-014 | Bank reconciliation handoff owner escalation is read-only, grouped by owner/status including unassigned queues, and must not mutate evidence, notes, journals, payments, or ledger records | Payment/Reporting | confirmed |
| BR-PAY-015 | Bank reconciliation active handoff aging buckets are read-only, exclude cleared notes, and group due-today and stale overdue workload by owner without mutating evidence or ledger records | Payment/Reporting | confirmed |
| BR-PAY-016 | Bank reconciliation stale evidence packet export is read-only, includes active overdue handoff note detail grouped by owner and aging bucket, and must not send notifications or mutate reconciliation evidence | Payment/Reporting | confirmed |
| BR-PAY-017 | Supervisor stale handoff packet acknowledgement stores reason, actor, timestamp, filter snapshot, and packet scope hash without mutating handoff notes, evidence, payments, journals, or ledger rows | Payment/Reporting | confirmed |
| BR-REC-001 | Receivable aging uses open invoice outstanding balance with current, 30, 60, 90, and over-90 day buckets | Receivable | confirmed |
| BR-REC-002 | Collection and dunning action requires active customer context, overdue open invoice for invoice-level action, and no duplicate active action by invoice/type | Receivable | confirmed |
| BR-REC-003 | Invoice-level collection action must verify invoice connection belongs to requested customer | Receivable | confirmed |
| BR-REP-001 | Financial reports must read posted ledger entries only and exclude draft operational records | Reporting | confirmed |
| BR-AUD-001 | Sensitive actions are audit logged | Shared | confirmed |
| BR-SEC-001 | Collection action endpoints require granular backend permissions for read, create, execute, and cancel | Security/Receivable | confirmed |
| BR-SEC-002 | Backend authentication loads enabled users and authorities from RBAC tables | Security | confirmed |
| BR-SEC-003 | RBAC role and permission catalog is seeded without default user credentials | Security | confirmed |
| BR-SEC-004 | Bootstrap admin user is provisioned only from explicit operator-supplied credentials | Security | confirmed |
| BR-SEC-005 | Payment settlement, reversal, and webhook event read endpoints require granular backend permissions while provider webhook remains HMAC-authenticated | Security/Payment | confirmed |
| BR-SEC-006 | Accounting and billing financial command endpoints require granular backend permissions | Security/Accounting/Billing | confirmed |
| BR-SEC-007 | Bank reconciliation sign-off requires `payment.reconciliation.signoff` plus reconciliation read authority; auditor read access cannot mutate approval | Security/Payment | confirmed |
| BR-SEC-009 | Supervisor stale handoff acknowledgement requires `payment.reconciliation.stale-acknowledge` plus reconciliation read authority and is not granted to auditor or cashier roles | Security/Payment | confirmed |
| BR-SEC-008 | `/api/auth/me` is a public session-state endpoint that returns anonymous state without credentials and full authorities only for authenticated users | Security/Auth | confirmed |
| BR-SEC-010 | Invoice document read requires `invoice.view`, while invoice void/correction requires `invoice.correct.approve` and is not granted to billing officer or auditor roles | Security/Billing | confirmed |
| BR-UI-002 | Collection action frontend visibility follows backend-provided authorities | Frontend/Security | confirmed |
| BR-UI-003 | Financial command frontend visibility follows backend-provided accounting, billing, and payment authorities | Frontend/Security | confirmed |
| BR-UI-004 | Accounting workspace must present CoA, period, and journal control states without bypassing backend posting governance | Frontend/Accounting | confirmed |
| BR-UI-005 | Accounting workspace mutations must require matching command permission, audit reason, confirmation for high-risk workflows, and backend revalidation | Frontend/Accounting | confirmed |
| BR-UI-006 | Billing workspace must use idempotency for batch generation and require controlled invoice issue with receivable/revenue accounts | Frontend/Billing | confirmed |
| BR-UI-007 | Accounting journal detail must expose debit-credit lines, source traceability, posting metadata, and balance status without adding edit paths | Frontend/Accounting | confirmed |
| BR-UI-008 | Payment workspace must use backend payment command contracts, idempotency, account validation, audit reason, and permission-aware visibility | Frontend/Payment | confirmed |
| BR-UI-009 | Billing workspace batch drill-down must use backend batch invoice contract and preserve invoice issue controls | Frontend/Billing | confirmed |
| BR-UI-010 | Customer and connection backend endpoints must have frontend surfaces for list, detail, create, and lifecycle workflow actions | Frontend/Customer/Connection | confirmed |
| BR-UI-011 | Metering backend endpoints must have frontend surfaces for route management, meter reading input, and submit/verify/reject workflow | Frontend/Metering | confirmed |
| BR-UI-012 | Tariff backend endpoints must have frontend surfaces for tariff version, block, activation/archive, and calculation simulation | Frontend/Billing | confirmed |
| BR-UI-013 | Receivable aging and trial balance reporting endpoints must have frontend report surfaces with loading/error/empty states | Frontend/Receivable/Reporting | confirmed |
| BR-UI-014 | Frontend shell and shared states must use a modern high-contrast enterprise dashboard visual system, not plain template styling | Frontend | confirmed |
| BR-UI-001 | Operational frontend pages expose loading, error, empty, filter, mutation pending, and mutation error states | Frontend | confirmed |

## Requirements Traceability

| Req ID | Requirement | Module | Design Decision | Backlog | Test |
|---|---|---|---|---|---|
| REQ-ACC-001 | CoA exists | Accounting | accounts table unique code + `/api/accounts` | T-020 | AccountingApplicationServiceTest |
| REQ-ACC-002 | Journal must balance | Accounting | PostingService validation + `/api/journals/{id}/post` | T-023 | JournalEntryTest |
| REQ-ACC-003 | Posted journal immutable | Accounting | Domain guard + DB trigger | T-024 | PostedJournalImmutableTest |
| REQ-ACC-004 | Ledger terbentuk dari journal posted | Accounting | PostingService calls LedgerEntryMaterializationService after successful post | T-081 | LedgerMaterializationTest |
| REQ-CUS-001 | Nomor pelanggan unique | Customer | customers table unique customer_number + `/api/customers` | T-030 | CustomerApplicationServiceTest |
| REQ-CON-001 | Nomor sambungan unique | Connection | connections table unique connection_number + lifecycle endpoints | T-031 | ConnectionApplicationServiceTest |
| REQ-MTR-001 | Rute baca meter valid | Metering | meter_routes unique route_code + `/api/meter-routes` | T-040 | MeteringApplicationServiceTest |
| REQ-MTR-002 | Baca meter unique per sambungan dan periode | Metering | meter_readings unique connection_id+period + lifecycle endpoints | T-041 | MeteringApplicationServiceTest |
| REQ-MTR-003 | Import baca meter offline | Metering | `POST /api/meter-readings/offline-import` creates an import batch, row-level imported/skipped/invalid audit items, source device trace, and draft readings for valid rows | T-041A | MeteringApplicationServiceTest |
| REQ-MTR-004 | Lock baca meter sebelum billing | Metering/Billing | `POST /api/meter-readings/{readingId}/lock` moves verified readings to `LOCKED`; billing batch queries locked readings only | T-041B,T-054 | MeteringApplicationServiceTest, BillingBatchApplicationServiceTest |
| REQ-BIL-001 | Kalkulasi tarif progresif valid | Billing | tariff_versions effective active lookup + tariff_blocks progressive calculation | T-050 | TariffEngineApplicationServiceTest |
| REQ-BIL-002 | Generate tagihan idempotent | Billing | billing_batches idempotency_key + period/area unique + draft invoices | T-054 | BillingBatchApplicationServiceTest |
| REQ-BIL-003 | Issue invoice menghasilkan jurnal piutang/pendapatan | Billing/Accounting | `/api/invoices/{id}/issue` calls AccountingApplicationService, posts source journal, links invoice to issue journal | T-082 | BillingBatchApplicationServiceTest, AccountingApplicationServiceTest |
| REQ-BIL-004 | Readiness issue batch billing | Billing | `/api/billing-batches/{batchId}/issue-readiness` summarizes total/draft/blocked invoices, draft amount, outstanding amount, and missing journal trace without mutating invoice or journal state | T-115A | BillingBatchApplicationServiceTest, billing-workspace-model.test.ts |
| REQ-BIL-005 | Dokumen rekening invoice | Billing | `GET /api/invoices/{invoiceId}/document` builds a read-only invoice document from persisted invoice, invoice lines, connection, and customer data; amounts are not recalculated from tariff rules | T-122 | InvoiceDocumentApplicationServiceTest, BillingControllerPermissionTest |
| REQ-BIL-006 | Void/koreksi invoice terkendali | Billing/Accounting | `POST /api/invoices/{invoiceId}/void` allows only issued unpaid invoices with issue journal trace, calls `AccountingApplicationService.postBillingInvoiceVoid`, reverses original posted billing journal lines, stores `void_journal_entry_id` and `voided_at`, and audits reason | T-122 | BillingBatchApplicationServiceTest, AccountingApplicationServiceTest, BillingInvoiceControlMigrationTest |
| REQ-PAY-001 | Payment idempotent | Payment | `idempotency_keys` + `payments.idempotency_key` + duplicate no-op result hydration | T-062 | PaymentIdempotencyTest |
| REQ-PAY-002 | Webhook pembayaran tervalidasi signature | Payment | HMAC SHA-256 `X-Payment-Signature` + `payment_webhook_events` | T-060 | PaymentWebhookApplicationServiceTest |
| REQ-PAY-003 | Settlement pembayaran posting kas/bank dan piutang | Payment/Accounting | `/api/payments/counter` calls AccountingApplicationService, posts source journal, links payment to settlement journal | T-083 | PaymentIdempotencyTest, AccountingApplicationServiceTest |
| REQ-PAY-004 | Reversal pembayaran memulihkan piutang dan posting jurnal balik | Payment/Accounting | `/api/payments/{id}/reverse` restores invoice allocation, posts source reversal journal, links payment to reversal journal | T-084 | PaymentIdempotencyTest, AccountingApplicationServiceTest |
| REQ-PAY-005 | Session rekonsiliasi pembayaran persisten | Payment | `/api/payment-reconciliation/sessions` persists match results, tracks item resolution status, requires reason/actor for resolution and completion, and never posts journals automatically | T-103 | PaymentReconciliationApplicationServiceTest, PaymentReconciliationSessionMigrationTest |
| REQ-PAY-006 | Adjustment exception rekonsiliasi terkendali | Payment/Accounting | `/api/payment-reconciliation/sessions/{sessionId}/items/{itemId}/adjust` accepts only OPEN-session items with `ACCEPTED` exception status, calls `AccountingApplicationService.postPaymentReconciliationAdjustment`, posts source module `PAYMENT_RECONCILIATION_ADJUSTMENT`, and stores adjustment journal trace on the item | T-105 | PaymentReconciliationApplicationServiceTest, AccountingApplicationServiceTest, PaymentReconciliationAdjustmentMigrationTest |
| REQ-PAY-007 | Evidence report rekonsiliasi bank | Payment/Reporting | `/api/reports/payment-reconciliation-evidence/{sessionId}` and CSV export read only `COMPLETED` sessions, include item resolution, matched payment, settlement/reversal/adjustment journal trace, and completion audit evidence | T-106 | BankReconciliationEvidenceReportApplicationServiceTest, ReportingControllerPermissionTest |
| REQ-PAY-008 | Sign-off evidence rekonsiliasi bank | Payment/Reporting | `/api/payment-reconciliation/sessions/{sessionId}/sign-off` stores one-time signed-off actor, timestamp, and reason only for completed sessions, rejects creator/completer SoD conflicts, and writes `SIGN_OFF_RECONCILIATION_SESSION` audit trail | T-107 | PaymentReconciliationApplicationServiceTest, PaymentReconciliationSignOffMigrationTest |
| REQ-PAY-009 | Register review rekonsiliasi bank | Payment/Reporting | `/api/reports/payment-reconciliation-review-register` returns paginated completed reconciliation evidence with sign-off status, exception count, adjustment count, total variance, and pending sign-off age; filters support sign-off status and completed date range | T-108 | BankReconciliationReviewRegisterApplicationServiceTest, ReportingControllerPermissionTest |
| REQ-PAY-010 | Export handoff register review rekonsiliasi | Payment/Reporting | `/api/reports/payment-reconciliation-review-register/export` returns bounded CSV for the same sign-off/date filters with pending sign-off SLA, exception/adjustment counts, variance, generated timestamp, and latest reviewer handoff columns when controlled notes exist | T-109,T-110 | BankReconciliationReviewRegisterApplicationServiceTest, ReportingControllerPermissionTest |
| REQ-PAY-011 | Controlled handoff notes rekonsiliasi | Payment/Reporting | `/api/reports/payment-reconciliation-review-register/{sessionId}/handoff-notes` reads notes and revision history, while create/revise endpoints persist current note metadata plus immutable revisions for completed sessions only | T-110 | BankReconciliationHandoffNoteApplicationServiceTest, PaymentReconciliationHandoffNoteMigrationTest |
| REQ-PAY-012 | Workload SLA handoff note rekonsiliasi | Payment/Reporting | `/api/reports/payment-reconciliation-handoff-notes` and `/export` list/export controlled handoff notes by status, owner, and due date with session trace, revision count, and overdue-day calculation while keeping evidence immutable | T-111 | BankReconciliationHandoffWorkloadApplicationServiceTest, ReportingControllerPermissionTest |
| REQ-PAY-013 | Escalation owner SLA handoff rekonsiliasi | Payment/Reporting | `/api/reports/payment-reconciliation-handoff-notes/owner-sla` and `/owner-sla/export` group workload by owner/status, include unassigned queue support, escalation priority, truncation flag, and bounded CSV export while keeping evidence immutable | T-112 | BankReconciliationHandoffWorkloadApplicationServiceTest, ReportingControllerPermissionTest |
| REQ-PAY-014 | Aging bucket handoff aktif rekonsiliasi | Payment/Reporting | `/api/reports/payment-reconciliation-handoff-notes/aging-buckets` and `/aging-buckets/export` group active OPEN/IN_PROGRESS notes by owner into due-today, overdue 1-3, 4-7, and over-7 day buckets with stale-only CSV export | T-113 | BankReconciliationHandoffWorkloadApplicationServiceTest, ReportingControllerPermissionTest |
| REQ-PAY-015 | Stale evidence packet handoff rekonsiliasi | Payment/Reporting | `/api/reports/payment-reconciliation-handoff-notes/aging-buckets/evidence-packet/export` exports active overdue handoff note details grouped by owner and aging bucket with session trace and revision count without notification or evidence mutation | T-114 | BankReconciliationHandoffWorkloadApplicationServiceTest, ReportingControllerPermissionTest |
| REQ-PAY-016 | Acknowledgement stale evidence packet | Payment/Reporting | `/api/reports/payment-reconciliation-handoff-notes/aging-buckets/evidence-packet/summary` computes current packet scope hash and POST `/acknowledgements` stores one idempotent acknowledgement per hash with reason, actor, timestamp, counts, and filter snapshot without mutating handoff evidence or ledger rows | T-115 | BankReconciliationHandoffAcknowledgementApplicationServiceTest, BankReconciliationHandoffWorkloadApplicationServiceTest, ReportingControllerPermissionTest |
| REQ-REC-001 | Aging piutang valid | Receivable | `receivable_aging_snapshots` generated from open `ISSUED/PARTIAL_PAID` invoices | T-070 | AgingServiceTest |
| REQ-REC-002 | Aksi penagihan piutang terkendali | Receivable | `/api/collection-actions` creates overdue invoice dunning actions, blocks duplicate active action, and controls start/complete/cancel transitions | T-085 | CollectionActionApplicationServiceTest |
| REQ-REC-003 | Aksi penagihan invoice hanya untuk customer pemilik sambungan | Receivable/Connection | Collection action validates `invoice.connectionId -> connection.customerId` before saving or auditing | T-087 | CollectionActionApplicationServiceTest |
| REQ-REP-001 | Report keuangan posted-only | Reporting | trial balance generated from `ledger_entries` only | T-080 | ReportPostedOnlyTest |
| REQ-SEC-001 | Permission granular untuk aksi penagihan | Security/Receivable | Collection action controller uses `hasAuthority` for `collection-action.read/create/execute/cancel` | T-088 | CollectionActionControllerPermissionTest |
| REQ-SEC-002 | Auth source database-backed | Security | Spring Security loads users, role authorities, and permission authorities from RBAC tables | T-089 | DatabaseUserDetailsServiceTest |
| REQ-SEC-003 | Katalog RBAC awal tersedia | Security | Flyway V7 seeds operational roles, collection-action permissions, and grants without seeding default passwords | T-090A | RbacSeedMigrationTest |
| REQ-SEC-004 | Bootstrap admin aman | Security | Startup bootstrap creates or grants `super-admin` only when `SIA_BOOTSTRAP_ADMIN_*` values are complete and valid | T-090B | BootstrapAdminUserServiceTest |
| REQ-SEC-005 | Permission granular pembayaran | Security/Payment | Payment settlement, reversal, and webhook event read use `payment.counter`, `payment.reverse`, and `payment.webhook.read`; provider webhook remains HMAC-validated without user Basic auth | T-092 | PaymentControllerPermissionTest, PaymentPermissionSeedMigrationTest, SecurityConfigTest |
| REQ-SEC-006 | Permission granular command akuntansi dan billing | Security/Accounting/Billing | Account manage, period manage/close, journal create/post, billing generate, and invoice issue use `account.manage`, `period.manage`, `period.close`, `journal.create`, `journal.post`, `billing.generate`, and `invoice.issue` authorities | T-093 | AccountingControllerPermissionTest, BillingControllerPermissionTest, AccountingBillingPermissionSeedMigrationTest |
| REQ-SEC-007 | Permission baca register pembayaran | Security/Payment | Payment list/detail endpoints require `payment.read`; Flyway V10 seeds the permission for `super-admin`, `finance-supervisor`, and `auditor-internal` without granting cashier broad read access | T-101 | PaymentControllerPermissionTest, PaymentReadPermissionSeedMigrationTest |
| REQ-SEC-008 | Permission rekonsiliasi pembayaran | Security/Payment | Payment reconciliation export and bank statement matching require `payment.reconcile`; Flyway V11 grants only supervisory/auditor roles and leaves cashier settlement-only | T-102 | PaymentControllerPermissionTest, PaymentReconciliationPermissionSeedMigrationTest |
| REQ-SEC-009 | Permission adjustment rekonsiliasi | Security/Payment/Accounting | Reconciliation adjustment endpoint requires both `payment.reconcile` and `journal.post` because it creates a posted accounting journal from a payment review workflow | T-105 | PaymentControllerPermissionTest |
| REQ-SEC-010 | Permission evidence report rekonsiliasi | Security/Payment/Reporting | Bank reconciliation evidence report endpoints require `payment.reconcile` because they expose payment, bank statement, resolution, and journal traceability evidence | T-106 | ReportingControllerPermissionTest |
| REQ-SEC-011 | Permission sign-off rekonsiliasi | Security/Payment | Reconciliation sign-off endpoint requires `payment.reconcile` and `payment.reconciliation.signoff`; Flyway V15 grants sign-off only to `super-admin` and `finance-supervisor`, leaving `auditor-internal` read-only through `payment.reconcile` | T-107 | PaymentControllerPermissionTest, PaymentReconciliationSignOffPermissionSeedMigrationTest |
| REQ-SEC-012 | Permission register review rekonsiliasi | Security/Payment/Reporting | Reconciliation review register requires `payment.reconcile` and remains read-only, so auditor can prioritize evidence packs without sign-off authority | T-108 | ReportingControllerPermissionTest |
| REQ-SEC-013 | Permission export handoff review rekonsiliasi | Security/Payment/Reporting | Reconciliation handoff export requires `payment.reconcile` and remains read-only; no reviewer note mutation is introduced by the CSV workflow | T-109 | ReportingControllerPermissionTest |
| REQ-SEC-014 | Permission mutasi handoff note rekonsiliasi | Security/Payment/Reporting | Reconciliation handoff note create/revise requires `payment.reconcile` plus `payment.reconciliation.handoff-note`; read remains available through `payment.reconcile` | T-110 | ReportingControllerPermissionTest, PaymentReconciliationHandoffNoteMigrationTest |
| REQ-SEC-015 | Permission workload handoff note rekonsiliasi | Security/Payment/Reporting | Handoff note workload list/export requires `payment.reconcile` and remains read-only; mutation still requires `payment.reconciliation.handoff-note` through the controlled note endpoints | T-111 | ReportingControllerPermissionTest |
| REQ-SEC-016 | Permission escalation owner handoff note | Security/Payment/Reporting | Handoff owner SLA list/export requires `payment.reconcile` and remains read-only; note mutation is still isolated behind `payment.reconciliation.handoff-note` | T-112 | ReportingControllerPermissionTest |
| REQ-SEC-017 | Permission aging bucket handoff aktif | Security/Payment/Reporting | Handoff aging bucket list/export requires `payment.reconcile` and remains read-only; stale export does not grant note mutation rights | T-113 | ReportingControllerPermissionTest |
| REQ-SEC-018 | Permission evidence packet handoff stale | Security/Payment/Reporting | Handoff stale evidence packet export requires `payment.reconcile` and remains read-only; packet export does not send notifications, create escalation tasks, or grant note mutation rights | T-114 | ReportingControllerPermissionTest |
| REQ-SEC-020 | Permission acknowledgement packet handoff stale | Security/Payment/Reporting | Stale packet acknowledgement requires `payment.reconcile` plus `payment.reconciliation.stale-acknowledge`; Flyway V19 grants only `super-admin` and `finance-supervisor`, not auditor or cashier roles | T-115 | ReportingControllerPermissionTest, PaymentReconciliationHandoffAcknowledgementMigrationTest, financial-command-permissions.test.ts |
| REQ-SEC-019 | Auth-state endpoint aman untuk browser lokal | Security/Auth | `GET /api/auth/me` is permit-all, returns `{authenticated:false, authorities:[]}` for anonymous requests, and returns DB-backed authorities when Basic Auth is supplied; sensitive command endpoints remain permission-enforced | HOTFIX-LOCAL-001 | AuthControllerTest, SecurityConfigTest, browser smoke |
| REQ-SEC-021 | Permission dokumen dan koreksi invoice | Security/Billing | Invoice document endpoint requires `invoice.view`; invoice void endpoint requires `invoice.correct.approve`; Flyway V21 grants correction only to `super-admin`, `finance-supervisor`, and `billing-supervisor` | T-122 | BillingControllerPermissionTest, BillingInvoiceControlMigrationTest, financial-command-permissions.test.ts |
| REQ-UI-001 | Workspace penagihan piutang siap operasional | Frontend | `/receivables/collection-actions` provides typed API integration, filters, create form, workflow actions, loading/error/empty states, and mutation feedback | T-086 | npm lint, typecheck, build |
| REQ-UI-002 | Visibility aksi penagihan permission-aware | Frontend/Security | Frontend reads `/api/auth/me`, gates list/create/execute/cancel controls by `collection-action.*` authorities, and keeps backend enforcement authoritative | T-091 | AuthControllerTest, collection-action-permissions.test.ts |
| REQ-UI-003 | Visibility command finansial permission-aware | Frontend/Security | Dashboard reads `/api/auth/me` and shows accounting/billing/payment command access from `account.manage`, `period.manage`, `period.close`, `journal.create`, `journal.post`, `billing.generate`, `invoice.view`, `invoice.issue`, `invoice.correct.approve`, `payment.counter`, `payment.read`, `payment.reconcile`, `payment.reconciliation.handoff-note`, `payment.reconciliation.signoff`, `payment.reconciliation.stale-acknowledge`, `payment.reverse`, and `payment.webhook.read` authorities | T-094,T-099,T-101,T-102,T-107,T-110,T-115,T-122 | financial-command-permissions.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-004 | Workspace akuntansi fondasi | Frontend/Accounting | `/accounting` lists CoA, accounting periods, and journals with typed API schemas, summary cards, status badges, journal filter, loading/error/empty states, and permission-aware command availability | T-095 | accounting-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-005 | Workflow command akuntansi | Frontend/Accounting | `/accounting` supports permission-gated CoA creation, accounting period creation, period closing-review/lock confirmation, manual journal draft creation with debit-credit validation, and journal posting confirmation with audit reason | T-096 | accounting-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-006 | Workspace billing fondasi | Frontend/Billing | `/billing` lists billing batches and invoices with typed schemas/hooks, period/status filters, summary cards, idempotent batch generation, draft invoice issue confirmation, account validation, loading/error/empty states, and permission-aware commands | T-097 | billing-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-007 | Detail baca jurnal akuntansi | Frontend/Accounting | `/accounting` opens a read-only journal detail drawer from the journal table, fetches `/api/journals/{journalId}`, shows source traceability, posted metadata, totals, balanced status, and debit-credit lines with account labels | T-098 | accounting-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-008 | Workspace pembayaran settlement | Frontend/Payment | `/payments` reads payment webhook events when authorized, submits counter settlement with idempotency key, submits payment reversal with audit reason, validates asset cash/receivable accounts and allocation totals locally, and keeps backend permissions authoritative | T-099 | payment-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-009 | Drill-down invoice batch billing | Frontend/Billing | `/billing` can select a billing batch, fetch `/api/billing-batches/{batchId}/invoices`, filter selected-batch invoices by invoice status locally, clear scope back to global invoices, and keep invoice issue permission/account validation unchanged | T-100 | billing-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-010 | Payment register dan detail rekonsiliasi | Frontend/Payment | `/payments` lists payment records with status/channel filters, reads payment detail by ID, shows receipt/allocation/journal traceability, and summarizes settled, reversed, and net cash impact for the active page without adding alternate reversal controls | T-101 | PaymentQueryApplicationServiceTest, payment-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-011 | Export dan matching rekonsiliasi pembayaran | Frontend/Payment | `/payments` exports current reconciliation register slices to CSV and matches pasted/imported bank statement rows against settled/reversed payments, showing exact/probable/variance/reversed/multiple/unmatched status without mutating accounting journals | T-102 | PaymentReconciliationApplicationServiceTest, payment-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-012 | Review session rekonsiliasi pembayaran | Frontend/Payment | `/payments` can save a matched bank statement as a session, list recent sessions, review item match/resolution status, resolve open items with reason, and complete the session only after all items are closed | T-103 | PaymentReconciliationApplicationServiceTest, payment-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-013 | Adapter import statement bank untuk UAT | Frontend/Payment | `/payments` validates source-specific bank statement templates, normalizes supported profiles into the existing match payload, shows row-level import errors, and provides downloadable templates before matching | T-104 | payment-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-014 | Adjustment journal dari review rekonsiliasi | Frontend/Payment | `/payments` shows adjustment journal trace per reconciliation item and exposes a permission-gated adjustment form for accepted exceptions with period, amount, debit account, credit account, and audit reason validation | T-105 | payment-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-015 | Export evidence report rekonsiliasi | Frontend/Payment | `/payments` loads evidence report for selected completed reconciliation session, shows completion audit and item evidence preview, and downloads CSV evidence with loading/error/empty states | T-106 | payment-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-016 | Sign-off evidence rekonsiliasi | Frontend/Payment | `/payments` shows completed-session sign-off status, loads evidence before approval, gates sign-off by `payment.reconciliation.signoff`, validates reason and SoD actor locally, and renders approved trace read-only after sign-off | T-107 | payment-workspace-model.test.ts, financial-command-permissions.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-017 | Register review rekonsiliasi | Frontend/Payment | `/payments` shows a paginated reconciliation review register with sign-off/date filters, summary metrics, pending SLA badge, exception/adjustment totals, loading/error/empty states, and permission denied state | T-108 | payment-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-018 | Export handoff register review | Frontend/Payment | `/payments` exports the active review register filter to CSV with export pending/error states and deterministic filename segments for sign-off status and completed date range | T-109 | payment-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-019 | Dashboard SLA handoff note rekonsiliasi | Frontend/Payment | `/payments` shows a permission-gated handoff note workload panel with status/owner/due-date filters, overdue summary, loading/error/empty states, pagination, and deterministic CSV export filename | T-111 | payment-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-020 | Drill-down owner escalation handoff | Frontend/Payment | `/payments` shows owner/status escalation grouping, supports unassigned owner drill-down into workload filters, and exports deterministic owner SLA CSV with loading/error/truncated states | T-112 | payment-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-021 | Aging bucket stale handoff | Frontend/Payment | `/payments` shows active handoff aging buckets by owner, due-today and stale overdue metrics, retry/truncated states, owner drill-down, and deterministic stale CSV export | T-113 | payment-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-022 | Evidence packet handoff stale | Frontend/Payment | `/payments` exports the active handoff aging filter as stale evidence packet CSV with pending/error state and deterministic filename for supervisor review | T-114 | payment-workspace-model.test.ts, npm test:permissions, npm typecheck/lint/build |
| REQ-UI-023 | Frontend master pelanggan dan sambungan | Frontend/Customer/Connection | `/customers` and `/connections` expose customer list/detail/create, tariff group create/list, connection list/detail/create, and activate/suspend/terminate workflow with audit reason | T-116 | npm typecheck/lint/build |
| REQ-UI-024 | Frontend baca meter | Frontend/Metering | `/metering` exposes meter route list/create, meter reading list/detail/create, anomaly flag, and submit/verify/reject workflow with audit reason | T-117 | npm typecheck/lint/build |
| REQ-UI-025 | Frontend master tarif | Frontend/Billing | `/tariffs` exposes tariff version list/detail/create, tariff block list/add, activate/archive workflow, and tariff calculation simulation | T-118 | npm typecheck/lint/build |
| REQ-UI-026 | Frontend aging piutang | Frontend/Receivable | `/receivables/aging` exposes snapshot list/detail/by-period lookup and generate workflow with audit reason | T-119 | npm typecheck/lint/build |
| REQ-UI-027 | Frontend laporan neraca saldo | Frontend/Reporting | `/reports/trial-balance` exposes posted-ledger trial balance filter, balance status, account lines, and empty/error/loading states | T-120 | npm typecheck/lint/build |
| REQ-UI-028 | Premium enterprise UI shell | Frontend | App shell, dashboard, page header, status badge, loading, empty, and error states use a high-contrast command-center style with active navigation, responsive mobile nav, clearer hierarchy, and no letter-spacing overrides | T-121 | npm typecheck/lint/build, route smoke |
| REQ-UI-029 | Supervisor acknowledgement stale packet | Frontend/Payment | `/payments` shows current stale packet summary/hash in the handoff workload panel and gates acknowledgement by `payment.reconciliation.stale-acknowledge`, requiring reason and using backend scope hash validation | T-115 | payment-workspace-model.test.ts, financial-command-permissions.test.ts, npm test:permissions/typecheck/lint/build |
| REQ-UI-030 | Dokumen dan void invoice billing | Frontend/Billing | `/billing` exposes permission-gated invoice document preview, high-risk void confirmation, local guard for issued unpaid traced invoices, mutation loading/error/success states, and query invalidation after void | T-122 | billing-workspace-model.test.ts, financial-command-permissions.test.ts, npm test:permissions/typecheck/lint/build |

## Decision Log

| Date | Decision | Reason | Impact |
|---|---|---|---|
| 2026-07-09 | Use `nasrul33/SIA-PDAM` as blueprint reference | User selected the legacy Laravel SIA-PDAM repo as the canonical domain/backlog source | New work must inspect blueprint behavior first, translate it into Java/Spring + Next.js contracts, and update traceability without copy-pasting framework code |
| 2026-07-05 | Use new repo | Avoid disturbing legacy Laravel repo | Clean rewrite |
| 2026-07-05 | Use modular monolith | Financial consistency and simpler deployment | Modules stay in one backend |
| 2026-07-05 | Use Java 26 | User requested latest Java | Latest Java SE release baseline |
| 2026-07-05 | Use Spring Boot 4.1.0 | User requested latest Spring Boot | Latest official Spring Boot stable baseline |
| 2026-07-05 | Use Gradle 9.6.1 | Java 26 requires modern Gradle support | Build tooling aligned with Java 26 |
| 2026-07-05 | Use latest locked frontend dependencies | User requested newest dependency baseline | `package-lock.json` and CI must use `npm ci` |
| 2026-07-07 | Keep `payment.read` supervisory by default | Payment register exposes settlement, reversal, allocation, and journal traceability beyond cashier settlement duties | Cashier keeps `payment.counter`; supervisors and auditors can inspect payment records |
| 2026-07-07 | Keep payment reconciliation non-posting | Bank matching identifies variance and exceptions but must not create or change journals automatically | Reconciliation export/match is audited and permission-gated; journal correction remains explicit accounting workflow |
| 2026-07-07 | Persist reconciliation sessions as operational audit records | Finance needs replayable review evidence for bank exceptions without mixing drafts into ledgers | Session/item tables store match and resolution state; completion is blocked while any item is OPEN |
| 2026-07-07 | Normalize bank statement imports in frontend before match | UAT bank files vary by source but backend contract should remain stable and bounded | Import profiles `STANDARD`, `BANK_MUTATION`, and `PAYMENT_GATEWAY` produce the existing `/match` rows payload with row-level validation feedback |
| 2026-07-07 | Keep reconciliation adjustments explicit and period-lock-aware | Accepted bank exceptions affect the ledger only after finance supplies period, amount, debit/credit accounts, and audit reason | Adjustment endpoint requires `payment.reconcile` plus `journal.post`, posts through AccountingApplicationService, and links the journal back to the reconciliation item |
| 2026-07-08 | Evidence report is completed-session-only | Month-end evidence must be stable and not mix ongoing review rows into audit exports | Reporting endpoint rejects non-completed reconciliation sessions and CSV includes resolution, payment, journal, and audit trace columns |
| 2026-07-08 | Keep reconciliation sign-off separate from evidence read | Auditor must inspect evidence without becoming an approver, and approver cannot be the same actor that created or completed the session | New `payment.reconciliation.signoff` authority, SoD domain guard, signed-off trace columns, and read-only approved state in `/payments` |
| 2026-07-08 | Add reconciliation review register before more mutation workflows | Finance needs a prioritization surface for pending sign-off and exception-heavy evidence packs | Reporting exposes a paginated read-only register; `/payments` shows status/date filters, SLA, exception, adjustment, and variance columns |
| 2026-07-08 | Keep reviewer handoff as CSV export before note persistence | Handoff notes may vary by UAT process and should not create uncontrolled mutable review records yet | Export includes blank reviewer notes, owner, due date, and status columns while backend remains read-only |
| 2026-07-08 | Add read-only handoff workload before escalation workflow | Finance needs owner-level SLA visibility without creating another mutation path | Reporting exposes status/owner/due-date workload and bounded CSV export; `/payments` shows overdue summary and export controls gated by `payment.reconcile` |
| 2026-07-08 | Add owner SLA escalation as read-only drill-down | Finance needs to prioritize owner queues, including unassigned notes, before adding any escalation mutation workflow | Reporting groups bounded handoff workload by owner/status with priority and CSV export; `/payments` can drill owner queues into the detailed workload filters |
| 2026-07-08 | Add active aging bucket report before supervisor packets | Finance needs stale queues separated by urgency before producing owner evidence packets | Reporting buckets active handoff notes by owner into due-today and overdue windows; `/payments` exports stale bucket CSV without sending notifications or mutating notes |
| 2026-07-08 | Add stale evidence packet as read-only export | Supervisors need detail packets after aging buckets, but notification/escalation mutation rules are not yet finalized | Reporting exports active overdue note details grouped by owner and aging bucket; `/payments` exposes a packet export without changing notes or evidence |
| 2026-07-08 | Treat `/api/auth/me` as public auth-state, not a protected command | Local in-app browser may not show Basic Auth prompt, and anonymous session state exposes no financial or personal data | Direct browser checks return HTTP 200 anonymous; authenticated calls still return RBAC authorities; all sensitive endpoints remain backend permission-enforced |
| 2026-07-08 | Align local Compose CORS and frontend API base to `localhost` ports | Frontend runs on `localhost:13000` while backend runs on `localhost:18080`; default `localhost:3000` CORS caused browser `Failed to fetch` | Compose sets `ALLOWED_ORIGINS` for local ports and builds frontend with `NEXT_PUBLIC_API_BASE_URL=http://localhost:18080` |
| 2026-07-09 | Close backend-to-frontend coverage gaps for operational endpoints | Audit showed customer, connection, metering, tariff, receivable aging, and trial balance endpoints had no frontend route | Added Indonesian enterprise dashboard surfaces for those endpoints and updated navigation away from placeholder dashboard links |
| 2026-07-09 | Refresh frontend visual system to premium enterprise dashboard | The shell and shared UI states looked flat and dated despite backend coverage | Added dark command sidebar, active navigation, responsive module nav, stronger page header, premium status/loading/empty/error components, dashboard card hierarchy, and removed legacy letter-spacing classes |
| 2026-07-09 | Close premium frontend consistency gaps | Follow-up audit found desktop sidebar internal scrolling, inconsistent button/input focus styles, and older sky-accent controls across workspaces | Compact sidebar now scrolls as one surface, route count is corrected, global table/form/button affordances are added, and accounting/billing/payment/operations/collection controls use aligned teal enterprise accents |
| 2026-07-09 | Normalize remaining module accent drift | Module audit still found sky-accent selected rows, panel icons, adjustment forms, and slate hover rows after the premium shell refresh | Replaced remaining sky/slate interactive accents with teal enterprise treatment across accounting, billing, payments, receivables, operations, dashboard, and financial access panel |
| 2026-07-05 | Add persisted audit and idempotency foundation | Audit/payment duplicate controls are mandatory | Enables safe payment and posting workflow implementation |
| 2026-07-05 | Add V2 domain foundation migration | Customer through reporting tables need DB constraints before feature services | Domain modules can be implemented incrementally without ad hoc schema |
| 2026-07-05 | Add Spring Boot Flyway starter | Smoke test showed JPA validation can run before migrations without Boot 4 starter | Flyway V1/V2 now apply before Hibernate validation |
| 2026-07-05 | Remove static Docker container names | Local smoke test conflicted with existing containers | Compose is project-scoped and CI-friendly |
| 2026-07-05 | Add repository-backed Accounting API | Accounting core needs API boundary before billing/payment integration | CoA, accounting period, draft journal, and journal posting are transactional and audited |
| 2026-07-05 | Add Customer and Connection API foundation | Metering and billing need controlled customer and connection master data | Customer, address, tariff group, and connection lifecycle are transactional and audited |
| 2026-07-05 | Add Metering API foundation | Billing needs controlled usage per connection and period | Meter routes and meter reading lifecycle are transactional, validated, and audited |
| 2026-07-05 | Add Tariff Engine foundation | Billing batch must calculate server-side from effective tariff versions | Progressive tariff blocks are versioned, audited, and calculated from active effective version |
| 2026-07-05 | Add Billing Batch foundation | Revenue generation must be repeat-safe and based on finalized meter readings | Batch generation is idempotent and creates draft invoices without direct journal writes; V23 later restricts the source to locked readings only |
| 2026-07-05 | Add Payment Webhook foundation | External payment callbacks must be authenticated before settlement | HMAC-validated webhook events are stored as `RECEIVED` without settlement journal writes |
| 2026-07-05 | Add Payment Idempotency foundation | Counter settlement must not duplicate cash receipt or invoice allocation on retry | Payment settlement reserves idempotency, creates receipt/allocation, updates invoice balance, and skips journal posting until controlled accounting workflow |
| 2026-07-05 | Add Receivable Aging foundation | Collection workflow needs consistent outstanding buckets before reporting and action follow-up | Aging snapshots are generated from open invoice outstanding balances without deriving final financial reports from draft data |
| 2026-07-05 | Add Posted Reporting foundation | Final financial reports must not read draft invoices or operational payments | Trial balance reads `ledger_entries` only and exposes balanced debit/credit controls |
| 2026-07-05 | Add Ledger Materialization from posted journals | Trial balance needs controlled ledger rows created only after journal posting succeeds | PostingService now writes one `ledger_entries` row per posted journal line in the same transaction |
| 2026-07-06 | Add Controlled Invoice Issue posting | Revenue recognition must be posted through accounting, not by billing writing journals directly | Invoice issue creates a source-traceable posted journal: debit receivable, credit revenue, then marks invoice `ISSUED` with issue journal link |
| 2026-07-06 | Add Controlled Payment Settlement posting | Cash/bank recognition must be posted through accounting, not by payment writing journals directly | Counter settlement creates a source-traceable posted journal: debit cash/bank, credit receivable, then links payment to settlement journal |
| 2026-07-06 | Add Controlled Payment Reversal posting | Payment cancellation must restore receivable and reverse cash/bank through accounting | Payment reversal restores invoice outstanding, posts debit receivable and credit cash/bank, then links payment to reversal journal |
| 2026-07-06 | Add Receivable Collection Action controls | Penagihan needs auditable operational workflow after aging identifies overdue receivables | Collection actions now require valid customer context, invoice-level dunning only for overdue open invoices, duplicate active actions are blocked, and state transitions are audited |
| 2026-07-06 | Add Receivable Collection Workspace | Receivable officers need a usable dashboard surface for collection action execution | Frontend now exposes typed collection-action list, filters, create form, workflow modal, mutation feedback, and full loading/error/empty states |
| 2026-07-06 | Add Collection Invoice-Customer Ownership validation | Collection action could otherwise attach another customer's overdue invoice to the wrong customer workflow | Invoice-level collection actions now load the invoice connection and reject mismatched customer ownership before persistence and audit |
| 2026-07-06 | Add Collection Action granular permissions | Collection actions are sensitive operational commands and must not rely on broad authenticated access | Controller method security now requires `collection-action.read`, `collection-action.create`, `collection-action.execute`, and `collection-action.cancel` authorities |
| 2026-07-06 | Add Database-backed RBAC authentication | Generated development users cannot support auditable production permissions | HTTP Basic now authenticates against `users.password_hash` and derives authorities from `roles`, `permissions`, `user_roles`, and `role_permissions` |
| 2026-07-06 | Add RBAC catalog seed migration | Empty RBAC tables make permission checks unusable after migration | Flyway V7 seeds operational roles, collection-action permissions, and role grants while avoiding default user credentials |
| 2026-07-06 | Add secure bootstrap admin provisioning | Production cannot depend on manual SQL or default credentials for first administrator access | Startup creates or grants an initial `super-admin` only when operator-supplied env values are complete, strong enough, and explicit |
| 2026-07-06 | Add permission-aware collection action frontend | Sensitive collection actions should not be visible to users without matching authorities | Frontend now loads current user authorities from `/api/auth/me` and gates read/create/execute/cancel controls while backend permissions remain authoritative |
| 2026-07-06 | Add Payment granular permissions | Cash settlement, reversal, and webhook monitoring are sensitive payment operations | Backend now requires `payment.counter`, `payment.reverse`, and `payment.webhook.read`; Flyway V8 seeds payment grants; provider webhook stays public at filter chain but HMAC-validated in application service |
| 2026-07-06 | Add Accounting and Billing command permissions | Posting, period lock, billing generation, and invoice issue create financial impact and must not rely on broad authentication | Backend now requires granular authorities for account manage, period manage/close, journal create/post, billing generate, and invoice issue; Flyway V9 seeds grants without default credentials |
| 2026-07-06 | Add Financial Command Access dashboard panel | Users need visible command availability before full accounting/billing workspaces are built | Dashboard now maps backend-provided accounting/billing authorities into active/locked command states with loading, error, and empty handling |
| 2026-07-06 | Add Accounting workspace foundation | Finance users need a single operational surface for CoA, periods, and journal control before mutation forms are expanded | `/accounting` now reads typed backend accounting endpoints, summarizes control state, shows command availability from authorities, and keeps posting/period actions non-mutating until workflow forms are implemented |
| 2026-07-06 | Add Accounting command workflows | Finance users need controlled write workflows without bypassing backend posting governance | `/accounting` now submits CoA, period, manual journal, period close, period lock, and journal post commands through typed TanStack mutations with audit reason, local journal balance validation, permission gates, and query invalidation |
| 2026-07-06 | Add Billing workspace foundation | Billing users need a controlled surface for batch generation and invoice issue before receivable settlement expansion | `/billing` now reads typed billing batch and invoice endpoints, generates batches with idempotency keys, issues draft invoices with receivable/revenue account selection, and gates commands by backend authorities |
| 2026-07-07 | Add Accounting journal detail drawer | Finance users need source-to-ledger traceability without leaving the accounting workspace | `/accounting` now fetches full journal details on demand, presents source document, posting metadata, backend totals, line-level debit-credit values, and local line integrity summary in a read-only drawer |
| 2026-07-07 | Add Payment settlement workspace visibility | Payment operators need a controlled surface for loket settlement, reversal, and webhook monitoring without inventing unsupported payment list contracts | `/payments` now uses existing backend payment contracts, idempotency key submission, asset account validation, allocation-total checks, permission-aware event visibility, and mutation feedback |
| 2026-07-07 | Add Billing batch invoice drill-down | Billing users need to inspect invoices created by a specific batch without losing issue controls or global invoice filters | `/billing` now uses the backend batch invoice endpoint, highlights the selected batch, scopes the invoice surface to the batch, and allows clearing back to global invoices |

## Assumptions Register

| ID | Assumption | Risk | Validation |
|---|---|---|---|
| A-001 | Single PDAM initially | Multi-unit scope may change data model | Confirm before production |
| A-002 | Partial payment is allowed | Allocation logic may change | Confirm before payment module |
| A-003 | Legacy data migration is separate | UAT uses seed data first | Confirm ETL strategy |

## Open Questions

| ID | Question | Impact | Needed Before |
|---|---|---|---|
| OQ-001 | Auth: session, JWT, or SSO? | Security architecture | Auth implementation |
| OQ-002 | Numbering format? | Unique generator | Customer/Billing |
| OQ-003 | Official tariff block values? | Billing amount setup | Billing batch |
| OQ-004 | Bank reconciliation channel? | Integration design | Payment R2 |

## Module Map

| Module | Purpose | Owns Data | Depends On |
|---|---|---|---|
| Shared | primitives, audit, exceptions | audit_logs | none |
| Accounting | CoA, period, journal | accounts, periods, journals | shared |
| Customer | pelanggan | customers | shared |
| Connection | sambungan and tariff group assignment | connections, tariff_groups | customer, shared |
| Metering | rute and baca meter | meter_routes, meter_readings | connection, shared |
| Billing | invoice and tariff | invoices, tariff_versions, tariff_blocks | metering, accounting |
| Payment | webhook intake and settlement | payment_webhook_events, payments, payment_allocations, payment_receipts | billing, accounting |
| Receivable | aging and collection | receivable_aging_snapshots, collection_actions | billing, payment |
| Reporting | posted reports | ledger_entries | accounting |

## Current Implementation State

- Completed: repository scaffold, docs baseline, backend skeleton, frontend dashboard shell, premium enterprise frontend visual system refresh, Money primitive, accounting domain skeleton, persisted audit primitive, idempotency primitive, V2 domain foundation migration, quality gate verification, initial GitHub push, repository-backed Accounting API, customer/connection API foundation, metering API foundation, billing batch foundation, payment webhook foundation, payment idempotency foundation, receivable aging foundation, posted reporting foundation, ledger materialization from posted journals, controlled invoice issue with receivable/revenue posting, controlled counter payment settlement with cash/bank receivable posting, controlled payment reversal with receivable restoration and reversal journal, receivable collection action workflow with dunning controls, frontend receivable collection workspace, collection invoice-customer ownership validation, collection action granular permission enforcement, database-backed user/role/permission authentication, RBAC role/permission seed catalog, secure bootstrap admin provisioning, permission-aware collection action frontend visibility, payment granular permission enforcement and seed catalog, accounting and billing command permission enforcement and seed catalog, permission-aware financial command dashboard visibility, accounting workspace foundation, accounting command workflows, billing workspace foundation, accounting journal detail drawer, payment settlement workspace visibility, billing batch invoice drill-down, billing batch issue readiness, invoice document preview, controlled invoice void with exact original-journal reversal, payment read permission, payment list/detail API, payment register reconciliation visibility, payment reconciliation export, bank statement matching, persisted reconciliation sessions, exception resolution workflow, bank statement import adapter/template validation, controlled reconciliation adjustment workflow, bank reconciliation evidence report, month-end bank reconciliation sign-off controls, reconciliation review register with exception SLA visibility, reconciliation reviewer handoff export, controlled reviewer handoff notes, handoff note SLA dashboard/workload export, handoff note owner escalation drill-down, handoff active aging bucket stale export, stale handoff evidence packet export, supervisor stale handoff acknowledgement workflow, blueprint baseline AP/supplier/fixed-asset/opening/closing/reversal workflows, bank mutation persistence/import/daily reconciliation, receivable installment/dunning/allowance workflows, financial statements/tax recap, app settings, tamper-evident audit-chain verification, connection request workflow, customer history read surface, frontend routes for all newly adopted blueprint baseline modules, focused backend regression tests for V22 accounting/payment/receivable/audit-chain workflows, metering offline import with row-level audit result, explicit meter reading lock workflow, billing batch generation restricted to locked readings, and CI Docker Compose smoke coverage for backend health, anonymous auth, Flyway history, and all blueprint baseline frontend routes.
- In progress: none.
- Blocked: official tariff values, numbering format, and final production auth mechanism decision beyond Basic auth.
- Next actions: add DB-backed migration/constraint tests for V22/V23 tables when a dedicated integration-test database gate is introduced, then review blueprint performance indexes against the current PostgreSQL schema.

## Latest Verification Snapshot

| Command | Result |
|---|---|
| `sh scripts/smoke-compose.sh` via Git for Windows `sh.exe` | passed on 2026-07-10; checked backend health, `/api/auth/me`, 18 frontend routes, and Flyway no-failure history |
| `docker compose config` | passed on 2026-07-10 after CI smoke workflow update |
| `docker run --rm -v "D:\sia-pdam-enterprise\backend:/workspace" -w /workspace gradle:9.6.1-jdk26 gradle test --no-daemon` | passed on 2026-07-10 after CI smoke workflow update |
| `npm.cmd run lint` | passed on 2026-07-10 after CI smoke workflow update |
| `npm.cmd run typecheck` | passed on 2026-07-10 after CI smoke workflow update; rerun serially after `next build` to avoid `.next/types` race |
| `npm.cmd run test:permissions` | passed on 2026-07-10 after CI smoke workflow update; 47 tests |
| `npm.cmd run build` | passed on 2026-07-10 after CI smoke workflow update |
| Targeted Metering/Billing backend tests: `MeteringApplicationServiceTest`, `BillingBatchApplicationServiceTest` | passed on 2026-07-10 after metering offline import and lock workflow |
| `docker run --rm -v "D:\sia-pdam-enterprise\backend:/workspace" -w /workspace gradle:9.6.1-jdk26 gradle test --no-daemon` | passed on 2026-07-10 after metering offline import and lock workflow |
| `npm.cmd run typecheck` | passed on 2026-07-10 after metering offline import UI |
| `npm.cmd run lint` | passed on 2026-07-10 after metering offline import UI |
| `npm.cmd run test:permissions` | passed on 2026-07-10 after metering offline import UI; 47 tests |
| `npm.cmd run build` | passed on 2026-07-10 after metering offline import UI |
| `docker compose up -d --build` | passed on 2026-07-10 after V23 metering migration; backend and frontend rebuilt |
| Flyway schema history query | passed on 2026-07-10; V23 `metering offline import lock` success=true |
| Smoke `GET /actuator/health`, `/api/auth/me`, `/metering` | passed on 2026-07-10; HTTP 200 and backend status UP |
| Targeted V22 backend tests: `AccountingBlueprintApplicationServiceTest`, `PaymentBankMutationApplicationServiceTest`, `ReceivableBlueprintApplicationServiceTest`, `AuditChainApplicationServiceTest` | passed on 2026-07-10 |
| `docker run --rm -v "D:\sia-pdam-enterprise\backend:/workspace" -w /workspace gradle:9.6.1-jdk26 gradle test --no-daemon` | passed on 2026-07-10 after focused V22 tests |
| `npm.cmd run typecheck` | passed on 2026-07-10 after focused V22 tests |
| `npm.cmd run lint` | passed on 2026-07-10 after focused V22 tests |
| `npm.cmd run test:permissions` | passed on 2026-07-10 after focused V22 tests; 47 tests |
| `npm.cmd run build` | passed on 2026-07-10 after focused V22 tests |
| `npm.cmd run typecheck` | passed on 2026-07-10 after blueprint frontend routes |
| `npm.cmd run lint` | passed on 2026-07-10 after blueprint frontend routes |
| `npm.cmd run build` | passed on 2026-07-10; new routes prerendered: AP, assets, bank mutations, installments, financial statements, settings, connection requests |
| `docker build -t sia-pdam-backend-check ./backend` | passed on 2026-07-10; backend compiled and `bootJar` succeeded |
| `docker run --rm -v "D:\sia-pdam-enterprise\backend:/workspace" -w /workspace gradle:9.6.1-jdk26 gradle test --no-daemon` | passed on 2026-07-10 |
| `docker compose up -d --build` | passed on 2026-07-10; backend, frontend, PostgreSQL, Redis, and MinIO started |
| Smoke `GET /actuator/health`, `/api/auth/me`, `/`, `/accounting/payables`, `/payments/bank-mutations`, `/reports/financial-statements` | all returned HTTP 200 on 2026-07-10 |
| `npm ci` | passed, 0 vulnerabilities |
| `npm run lint` | passed |
| `npm run typecheck` | passed |
| `npm run build` | passed with Next.js 16.2.10 |
| `docker run gradle clean test bootJar` | passed on `gradle:9.6.1-jdk26` |
| `docker-compose build backend` | passed |
| `docker-compose build frontend` | passed |
| `docker-compose config` | passed |
| Backend smoke test | passed: Flyway applied V1/V2, health endpoint UP, dashboard API returned valid JSON |
| Accounting API increment | passed: `gradle clean test bootJar`, backend Docker build, smoke health with PostgreSQL |
| Customer/Connection API increment | passed: `gradle clean test bootJar`, backend Docker build, smoke health with PostgreSQL |
| Metering API increment | passed: `gradle clean test bootJar`, backend Docker build, smoke health with PostgreSQL |
| Tariff Engine increment | passed: TDD target test, `gradle clean test bootJar`, backend Docker build, smoke health with PostgreSQL |
| Billing Batch increment | passed: TDD target test, `gradle clean test bootJar`, backend Docker build, smoke health with PostgreSQL |
| Payment Webhook increment | passed: TDD target test, `gradle clean test bootJar`, backend Docker build, smoke health with PostgreSQL |
| Payment Idempotency increment | passed: TDD target test, `gradle clean test bootJar`, backend Docker build, smoke health with PostgreSQL |
| Receivable Aging increment | passed: TDD target test, `gradle clean test bootJar`, backend Docker build, smoke health with PostgreSQL |
| Posted Reporting increment | passed: TDD target test, `gradle clean test bootJar`, backend Docker build, smoke health with PostgreSQL |
| Ledger Materialization increment | passed: TDD target test, `gradle clean test bootJar`, backend Docker build, smoke health with PostgreSQL |
| Controlled Invoice Issue increment | passed: TDD target tests, `gradle clean test bootJar`, backend Docker build, smoke health with PostgreSQL and Flyway V3 |
| Controlled Payment Posting increment | passed: TDD target tests, `gradle clean test bootJar`, backend Docker build, smoke health with PostgreSQL and Flyway V4 |
| Controlled Payment Reversal increment | passed: TDD target tests, `gradle clean test bootJar`, backend Docker build, smoke health with PostgreSQL and Flyway V5 |
| Receivable Collection Action increment | passed: TDD target test, `gradle clean test bootJar`, backend Docker build, smoke health with PostgreSQL and Flyway V6 |
| Receivable Collection Workspace increment | passed: `npm run typecheck`, `npm run lint`, `npm run build` |
| Collection Invoice-Customer Ownership increment | passed: TDD target test, `gradle clean test bootJar`, backend Docker build, smoke health with PostgreSQL and Flyway V6 |
| Collection Action Permission increment | passed: TDD target tests, `gradle clean test bootJar`, backend Docker build, smoke health with PostgreSQL and Flyway V6 |
| Database-backed RBAC Authentication increment | passed: RED/GREEN target test, `gradle clean test bootJar`, backend Docker build, smoke health, DB-backed Basic auth `200`, anonymous `401` |
| RBAC Catalog Seed increment | passed: RED/GREEN target seed test, `gradle clean test bootJar`, backend Docker build, smoke health, Flyway V7, 14 roles, 4 permissions, 11 grants, 0 default users |
| Secure Bootstrap Admin increment | passed: RED/GREEN target service test, `gradle clean test bootJar`, backend Docker build, smoke health, Flyway V7, bootstrap admin created with bcrypt hash, super-admin grant, protected endpoint `200`, anonymous `401` |
| Permission-aware Collection Frontend increment | passed: RED/GREEN AuthController test, RED/GREEN frontend permission helper test, `gradle clean test bootJar`, `npm run typecheck`, `npm run lint`, `npm run build`, backend/frontend Docker build, `/api/auth/me` smoke with authorities, protected endpoint `200`, anonymous `401` |
| Payment Permission increment | passed: RED/GREEN controller/security/migration tests, `gradle clean test bootJar`, backend Docker build, smoke health, Flyway version 8, 3 payment permissions, 7 payment grants, `GET /api/payment-webhook-events` authenticated `200` and anonymous `401`, provider webhook HMAC callback without Basic auth `202` |
| Accounting Billing Permission increment | passed: RED/GREEN controller/migration tests, `gradle clean test bootJar`, backend Docker build, smoke health, Flyway version 9, 7 accounting/billing command permissions, 16 command grants, `/api/auth/me` exposes new super-admin authorities, anonymous journal command `401`, authenticated invalid journal command `422` |
| Permission-aware Financial Command Frontend increment | passed: RED/GREEN financial permission helper test, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build` |
| Accounting Workspace Foundation increment | passed: RED/GREEN accounting workspace model test, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build` |
| Accounting Command Workflow increment | passed: RED/GREEN accounting workspace model tests, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build` |
| Billing Workspace Foundation increment | passed: RED/GREEN billing workspace model tests, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build` |
| Accounting Journal Detail Drawer increment | passed: RED/GREEN accounting detail line summary test, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build` |
| Payment Settlement Workspace increment | passed: RED/GREEN payment workspace model tests, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build`, local `/payments` smoke `200` |
| Billing Batch Invoice Drill-down increment | passed: RED/GREEN billing workspace model tests, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build`, local `/billing` smoke `200` |
| Payment Register Detail increment | passed: RED/GREEN backend payment query/controller/migration tests, full `gradle clean test bootJar` via `gradle:9.6.1-jdk26`, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build` |
| Payment Reconciliation Export Match increment | passed: targeted backend reconciliation/controller/migration tests, full `gradle clean test bootJar` via `gradle:9.6.1-jdk26`, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build` |
| Payment Reconciliation Session increment | passed: targeted backend reconciliation session/controller/migration tests, full `gradle clean test bootJar` via `gradle:9.6.1-jdk26`, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build` |
| Payment Reconciliation Import Adapter increment | passed: RED/GREEN payment workspace model import tests, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build` |
| Payment Reconciliation Sign-off increment | passed: targeted backend sign-off/controller/reporting/migration tests, full `gradle test bootJar` via `gradle:9.6.1-jdk26`, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build` |
| Payment Reconciliation Review Register increment | passed: targeted backend register/controller tests, full `gradle test bootJar` via `gradle:9.6.1-jdk26`, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build` |
| Payment Reconciliation Handoff Export increment | passed: targeted backend CSV/controller tests, full `gradle test bootJar` via `gradle:9.6.1-jdk26`, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build` |
| Payment Reconciliation Handoff Notes increment | passed: targeted backend note/register/controller/migration tests, full `gradle test bootJar` via `gradle:9.6.1-jdk26`, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build` |
| Payment Reconciliation Handoff Workload increment | passed: targeted backend workload/controller tests, full `gradle test bootJar` via `gradle:9.6.1-jdk26`, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build` |
| Payment Reconciliation Handoff Owner SLA increment | passed: targeted backend owner SLA/controller tests, full `gradle test bootJar` via `gradle:9.6.1-jdk26`, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build` |
| Payment Reconciliation Handoff Aging Bucket increment | passed: targeted backend aging bucket/controller tests, full `gradle test bootJar` via `gradle:9.6.1-jdk26`, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build` |
| Payment Reconciliation Handoff Evidence Packet increment | passed: targeted backend stale packet/controller tests, full `gradle test bootJar` via `gradle:9.6.1-jdk26`, `npm run test:permissions`, `npm run typecheck`, `npm run lint`, `npm run build` |
| Premium Frontend Visual System increment | passed: `npm run typecheck`, `npm run lint`, `npm run build`, `npm run test:permissions`, Docker frontend rebuild, route smoke for 11 frontend routes |
| Premium Frontend Gap Closure increment | passed: `npm run typecheck`, `npm run lint`, `npm run build`, `npm run test:permissions`, Docker frontend rebuild, route smoke for 11 frontend routes |
| Premium Module Accent Normalization increment | passed: `npm run typecheck`, `npm run lint`, `npm run build`, `npm run test:permissions`, Docker frontend rebuild, route smoke for 11 frontend routes |

## Handoff Instructions

- Inspect `CODEX.md`, `AGENTS.md`, `docs/10-ROADMAP-BACKLOG.md` first.
- Do not modify legacy repo.
- Do not build UI financial actions before backend permissions and API contracts exist.
- Run tests before claiming completion.

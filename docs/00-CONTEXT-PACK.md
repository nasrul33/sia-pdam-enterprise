# Context Pack - SIA-PDAM Enterprise

## Project Identity

- Project name: SIA-PDAM Enterprise
- Repository: sia-pdam-enterprise
- Objective: Rebuild sistem PDAM berbasis Java/Spring Boot dan Next.js
- Current phase: Bootstrap
- Last updated: 2026-07-06

## Immutable Context

- Repo baru, tidak mengubah repo lama.
- Backend: Java 26 + Spring Boot 4.1.0.
- Frontend: Next.js 16.2.10 + React 19.2.7 + TypeScript 6.0.3 + Tailwind CSS 4.3.2.
- Database: PostgreSQL + Flyway.
- Architecture: Modular monolith.
- Financial rules: no monetary primitive outside BigDecimal/Money, posted journal immutable, period lock mandatory.

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
| BR-BIL-001 | Progressive tariff calculation uses active effective version | Billing | confirmed |
| BR-BIL-002 | Tariff blocks must be sequential, contiguous, and end with an unbounded block | Billing | confirmed |
| BR-BIL-003 | Billing batch generation is idempotent and creates draft invoices from verified readings | Billing | confirmed |
| BR-BIL-004 | Invoice issue posts receivable debit and revenue credit through accounting service before status becomes issued | Billing | confirmed |
| BR-PAY-001 | Payment idempotency is enforced in DB | Payment | confirmed |
| BR-PAY-002 | Payment webhook signature must be validated before persistence | Payment | confirmed |
| BR-PAY-003 | Counter payment allocation total must equal payment amount and duplicate retry must be no-op | Payment | confirmed |
| BR-PAY-004 | Counter payment settlement posts debit cash/bank and credit receivable through accounting service | Payment | confirmed |
| BR-PAY-005 | Payment reversal restores invoice outstanding and posts debit receivable credit cash/bank through accounting service | Payment | confirmed |
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
| BR-UI-002 | Collection action frontend visibility follows backend-provided authorities | Frontend/Security | confirmed |
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
| REQ-BIL-001 | Kalkulasi tarif progresif valid | Billing | tariff_versions effective active lookup + tariff_blocks progressive calculation | T-050 | TariffEngineApplicationServiceTest |
| REQ-BIL-002 | Generate tagihan idempotent | Billing | billing_batches idempotency_key + period/area unique + draft invoices | T-054 | BillingBatchApplicationServiceTest |
| REQ-BIL-003 | Issue invoice menghasilkan jurnal piutang/pendapatan | Billing/Accounting | `/api/invoices/{id}/issue` calls AccountingApplicationService, posts source journal, links invoice to issue journal | T-082 | BillingBatchApplicationServiceTest, AccountingApplicationServiceTest |
| REQ-PAY-001 | Payment idempotent | Payment | `idempotency_keys` + `payments.idempotency_key` + duplicate no-op result hydration | T-062 | PaymentIdempotencyTest |
| REQ-PAY-002 | Webhook pembayaran tervalidasi signature | Payment | HMAC SHA-256 `X-Payment-Signature` + `payment_webhook_events` | T-060 | PaymentWebhookApplicationServiceTest |
| REQ-PAY-003 | Settlement pembayaran posting kas/bank dan piutang | Payment/Accounting | `/api/payments/counter` calls AccountingApplicationService, posts source journal, links payment to settlement journal | T-083 | PaymentIdempotencyTest, AccountingApplicationServiceTest |
| REQ-PAY-004 | Reversal pembayaran memulihkan piutang dan posting jurnal balik | Payment/Accounting | `/api/payments/{id}/reverse` restores invoice allocation, posts source reversal journal, links payment to reversal journal | T-084 | PaymentIdempotencyTest, AccountingApplicationServiceTest |
| REQ-REC-001 | Aging piutang valid | Receivable | `receivable_aging_snapshots` generated from open `ISSUED/PARTIAL_PAID` invoices | T-070 | AgingServiceTest |
| REQ-REC-002 | Aksi penagihan piutang terkendali | Receivable | `/api/collection-actions` creates overdue invoice dunning actions, blocks duplicate active action, and controls start/complete/cancel transitions | T-085 | CollectionActionApplicationServiceTest |
| REQ-REC-003 | Aksi penagihan invoice hanya untuk customer pemilik sambungan | Receivable/Connection | Collection action validates `invoice.connectionId -> connection.customerId` before saving or auditing | T-087 | CollectionActionApplicationServiceTest |
| REQ-REP-001 | Report keuangan posted-only | Reporting | trial balance generated from `ledger_entries` only | T-080 | ReportPostedOnlyTest |
| REQ-SEC-001 | Permission granular untuk aksi penagihan | Security/Receivable | Collection action controller uses `hasAuthority` for `collection-action.read/create/execute/cancel` | T-088 | CollectionActionControllerPermissionTest |
| REQ-SEC-002 | Auth source database-backed | Security | Spring Security loads users, role authorities, and permission authorities from RBAC tables | T-089 | DatabaseUserDetailsServiceTest |
| REQ-SEC-003 | Katalog RBAC awal tersedia | Security | Flyway V7 seeds operational roles, collection-action permissions, and grants without seeding default passwords | T-090A | RbacSeedMigrationTest |
| REQ-SEC-004 | Bootstrap admin aman | Security | Startup bootstrap creates or grants `super-admin` only when `SIA_BOOTSTRAP_ADMIN_*` values are complete and valid | T-090B | BootstrapAdminUserServiceTest |
| REQ-SEC-005 | Permission granular pembayaran | Security/Payment | Payment settlement, reversal, and webhook event read use `payment.counter`, `payment.reverse`, and `payment.webhook.read`; provider webhook remains HMAC-validated without user Basic auth | T-092 | PaymentControllerPermissionTest, PaymentPermissionSeedMigrationTest, SecurityConfigTest |
| REQ-UI-001 | Workspace penagihan piutang siap operasional | Frontend | `/receivables/collection-actions` provides typed API integration, filters, create form, workflow actions, loading/error/empty states, and mutation feedback | T-086 | npm lint, typecheck, build |
| REQ-UI-002 | Visibility aksi penagihan permission-aware | Frontend/Security | Frontend reads `/api/auth/me`, gates list/create/execute/cancel controls by `collection-action.*` authorities, and keeps backend enforcement authoritative | T-091 | AuthControllerTest, collection-action-permissions.test.ts |

## Decision Log

| Date | Decision | Reason | Impact |
|---|---|---|---|
| 2026-07-05 | Use new repo | Avoid disturbing legacy Laravel repo | Clean rewrite |
| 2026-07-05 | Use modular monolith | Financial consistency and simpler deployment | Modules stay in one backend |
| 2026-07-05 | Use Java 26 | User requested latest Java | Latest Java SE release baseline |
| 2026-07-05 | Use Spring Boot 4.1.0 | User requested latest Spring Boot | Latest official Spring Boot stable baseline |
| 2026-07-05 | Use Gradle 9.6.1 | Java 26 requires modern Gradle support | Build tooling aligned with Java 26 |
| 2026-07-05 | Use latest locked frontend dependencies | User requested newest dependency baseline | `package-lock.json` and CI must use `npm ci` |
| 2026-07-05 | Add persisted audit and idempotency foundation | Audit/payment duplicate controls are mandatory | Enables safe payment and posting workflow implementation |
| 2026-07-05 | Add V2 domain foundation migration | Customer through reporting tables need DB constraints before feature services | Domain modules can be implemented incrementally without ad hoc schema |
| 2026-07-05 | Add Spring Boot Flyway starter | Smoke test showed JPA validation can run before migrations without Boot 4 starter | Flyway V1/V2 now apply before Hibernate validation |
| 2026-07-05 | Remove static Docker container names | Local smoke test conflicted with existing containers | Compose is project-scoped and CI-friendly |
| 2026-07-05 | Add repository-backed Accounting API | Accounting core needs API boundary before billing/payment integration | CoA, accounting period, draft journal, and journal posting are transactional and audited |
| 2026-07-05 | Add Customer and Connection API foundation | Metering and billing need controlled customer and connection master data | Customer, address, tariff group, and connection lifecycle are transactional and audited |
| 2026-07-05 | Add Metering API foundation | Billing needs verified usage per connection and period | Meter routes and meter reading lifecycle are transactional, validated, and audited |
| 2026-07-05 | Add Tariff Engine foundation | Billing batch must calculate server-side from effective tariff versions | Progressive tariff blocks are versioned, audited, and calculated from active effective version |
| 2026-07-05 | Add Billing Batch foundation | Revenue generation must be repeat-safe and based on verified readings | Batch generation is idempotent and creates draft invoices without direct journal writes |
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

- Completed: repository scaffold, docs baseline, backend skeleton, frontend dashboard shell, Money primitive, accounting domain skeleton, persisted audit primitive, idempotency primitive, V2 domain foundation migration, quality gate verification, initial GitHub push, repository-backed Accounting API, customer/connection API foundation, metering API foundation, tariff engine foundation, billing batch foundation, payment webhook foundation, payment idempotency foundation, receivable aging foundation, posted reporting foundation, ledger materialization from posted journals, controlled invoice issue with receivable/revenue posting, controlled counter payment settlement with cash/bank receivable posting, controlled payment reversal with receivable restoration and reversal journal, receivable collection action workflow with dunning controls, frontend receivable collection workspace, collection invoice-customer ownership validation, collection action granular permission enforcement, database-backed user/role/permission authentication, RBAC role/permission seed catalog, secure bootstrap admin provisioning, permission-aware collection action frontend visibility, payment granular permission enforcement and seed catalog.
- In progress: none.
- Blocked: official tariff values, numbering format, final production auth mechanism decision beyond Basic auth.
- Next actions: expand granular permissions to accounting posting/period close and billing issue/generate modules.

## Latest Verification Snapshot

| Command | Result |
|---|---|
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

## Handoff Instructions

- Inspect `CODEX.md`, `AGENTS.md`, `docs/10-ROADMAP-BACKLOG.md` first.
- Do not modify legacy repo.
- Do not build UI financial actions before backend permissions and API contracts exist.
- Run tests before claiming completion.

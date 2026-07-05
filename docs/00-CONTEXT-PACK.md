# Context Pack - SIA-PDAM Enterprise

## Project Identity

- Project name: SIA-PDAM Enterprise
- Repository: sia-pdam-enterprise
- Objective: Rebuild sistem PDAM berbasis Java/Spring Boot dan Next.js
- Current phase: Bootstrap
- Last updated: 2026-07-05

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
| BR-PAY-001 | Payment idempotency is enforced in DB | Payment | confirmed |
| BR-AUD-001 | Sensitive actions are audit logged | Shared | confirmed |

## Requirements Traceability

| Req ID | Requirement | Module | Design Decision | Backlog | Test |
|---|---|---|---|---|---|
| REQ-ACC-001 | CoA exists | Accounting | accounts table unique code + `/api/accounts` | T-020 | AccountingApplicationServiceTest |
| REQ-ACC-002 | Journal must balance | Accounting | PostingService validation + `/api/journals/{id}/post` | T-023 | JournalEntryTest |
| REQ-ACC-003 | Posted journal immutable | Accounting | Domain guard + DB trigger | T-024 | PostedJournalImmutableTest |
| REQ-PAY-001 | Payment idempotent | Payment | unique idempotency key | T-062 | PaymentIdempotencyTest |

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
| OQ-003 | Tariff official blocks? | Tariff engine | Billing |
| OQ-004 | Bank reconciliation channel? | Integration design | Payment R2 |

## Module Map

| Module | Purpose | Owns Data | Depends On |
|---|---|---|---|
| Shared | primitives, audit, exceptions | audit_logs | none |
| Accounting | CoA, period, journal | accounts, periods, journals | shared |
| Customer | pelanggan | customers | shared |
| Billing | invoice and tariff | invoices, tariff_versions | metering, accounting |
| Payment | settlement | payments, receipts | billing, accounting |

## Current Implementation State

- Completed: repository scaffold, docs baseline, backend skeleton, frontend dashboard shell, Money primitive, accounting domain skeleton, persisted audit primitive, idempotency primitive, V2 domain foundation migration, quality gate verification, initial GitHub push, repository-backed Accounting API.
- In progress: none.
- Blocked: final auth decision, tariff formula, numbering format.
- Next actions: implement customer/connection services.

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

## Handoff Instructions

- Inspect `CODEX.md`, `AGENTS.md`, `docs/10-ROADMAP-BACKLOG.md` first.
- Do not modify legacy repo.
- Do not build UI financial actions before backend permissions and API contracts exist.
- Run tests before claiming completion.

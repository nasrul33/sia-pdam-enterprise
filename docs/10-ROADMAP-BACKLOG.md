# Roadmap and Backlog

| Task ID | Agent Role | Scope | Files/Area | Dependency | Acceptance Criteria | Quality Gate |
|---|---|---|---|---|---|---|
| T-000 | RepoArchitectAgent | Bootstrap repo | root, docs, docker | none | structure complete | docker compose config |
| T-001 | BackendFoundationAgent | Spring Boot skeleton | backend | T-000 | context loads | gradle test |
| T-002 | FrontendAgent | Next.js skeleton | frontend | T-000 | app builds | npm run build |
| T-003 | DevOpsAgent | Docker compose | root/infra | T-001,T-002 | services healthy | docker compose up -d |
| T-010 | BackendFoundationAgent | Money VO | shared/money | T-001 | no primitive money outside Money | MoneyTest |
| T-011 | BackendFoundationAgent | Exception model | shared/exception | T-001 | error response consistent | ExceptionHandlerTest |
| T-012 | DatabaseAgent | Flyway baseline | db/migration | T-001 | migration runs from empty DB | flywayMigrate |
| T-013 | SecurityAgent | Audit trail | shared/audit | T-012 | sensitive action logged | AuditTrailTest |
| T-014 | SecurityAgent | RBAC skeleton | auth | T-012 | permission enforced | PermissionTest |
| T-020 | AccountingAgent | CoA | accounting | T-010 | account code unique | AccountTest |
| T-021 | AccountingAgent | Accounting period | accounting | T-020 | period lifecycle valid | AccountingPeriodTest |
| T-022 | AccountingAgent | Journal draft | accounting | T-021 | draft valid | JournalDraftTest |
| T-023 | AccountingAgent | Posting service | accounting | T-022 | debit=credit enforced | JournalPostingTest |
| T-024 | DatabaseAgent | Posted immutable | accounting/db | T-023 | posted journal cannot mutate | PostedJournalImmutableTest |
| T-025 | AccountingAgent | Repository-backed Accounting API | accounting/web, accounting/application | T-024 | CoA, period, journal draft, and posting endpoints are validated, transactional, audited | AccountingApplicationServiceTest |
| T-030 | CustomerAgent | Customer master | customer | T-014 | customer number unique, address captured, API audited | CustomerApplicationServiceTest |
| T-031 | CustomerAgent | Connection | connection | T-030 | connection number unique, tariff group valid, lifecycle audited | ConnectionApplicationServiceTest |
| T-040 | MeteringAgent | Meter route | metering | T-031 | route code unique and audited | MeteringApplicationServiceTest |
| T-041 | MeteringAgent | Meter reading | metering | T-040 | active connection, valid period, unique connection+period, audited lifecycle | MeteringApplicationServiceTest |
| T-050 | BillingAgent | Tariff engine | billing | T-041 | active effective version, contiguous progressive blocks, tariff calculation valid | TariffEngineApplicationServiceTest |
| T-054 | BillingAgent | Billing batch | billing | T-050,T-023 | verified readings only, active connection, no duplicate invoice, idempotent draft generation | BillingBatchApplicationServiceTest |
| T-060 | PaymentAgent | Payment webhook | payment | T-054 | HMAC signature validated before event persistence | PaymentWebhookApplicationServiceTest |
| T-062 | PaymentAgent | Payment idempotency | payment | T-060 | counter payment duplicate no-op with receipt and invoice allocation | PaymentIdempotencyTest |
| T-070 | ReceivableAgent | Aging | receivable | T-062 | current, 30, 60, 90, and over-90 aging buckets valid | AgingServiceTest |
| T-080 | ReportingAgent | Posted reports | reporting | T-023,T-054,T-062 | trial balance reads posted ledger entries only and excludes draft operational records | ReportPostedOnlyTest |
| T-081 | AccountingAgent | Ledger materialization | accounting, reporting | T-080 | posted journal writes one ledger row per journal line | LedgerMaterializationTest |

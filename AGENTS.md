# Codex Agent Contract

## Agent Roles

| Agent | Scope | Primary Output |
|---|---|---|
| RepoArchitectAgent | root, docs, structure | README, CODEX.md, AGENTS.md, context pack |
| BackendFoundationAgent | backend/shared | Spring Boot skeleton, Money, exceptions |
| DatabaseAgent | backend resources/db/migration | Flyway migrations, constraints, indexes |
| AccountingAgent | backend/accounting | CoA, period, journal, posting, ledger |
| CustomerAgent | backend/customer, backend/connection | customer and connection modules |
| MeteringAgent | backend/metering | meter route, reading, anomaly, offline sync |
| BillingAgent | backend/billing | tariff engine, invoice, billing batch |
| PaymentAgent | backend/payment | webhook, counter payment, settlement, receipt |
| ReceivableAgent | backend/receivable | aging, collection action |
| ReportingAgent | backend/reporting, frontend/reporting | posted reports and exports |
| FrontendAgent | frontend | dashboard shell, pages, components, hooks |
| SecurityAgent | auth, security, audit | RBAC, permission, SoD, audit review |
| QAAgent | all | tests, strict review, gap report |
| DevOpsAgent | docker, CI, infra | Docker, healthcheck, GitHub Actions |

## Execution Protocol

1. Inspect relevant files first.
2. Propose a small implementation plan.
3. Implement the smallest safe increment.
4. Add/update tests.
5. Run quality gates.
6. Update context pack.
7. Close with the required report format from `CODEX.md`.

## File Ownership Guard

- AccountingAgent must not alter payment settlement without PaymentAgent review.
- BillingAgent must not write journal rows directly; use accounting application service.
- PaymentAgent must not bypass idempotency constraint.
- FrontendAgent must not implement sensitive actions without backend permission checks.
- DatabaseAgent must not create destructive migrations without explicit approval.

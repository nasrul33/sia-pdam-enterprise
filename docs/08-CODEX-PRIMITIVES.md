# Codex Primitives

## Backend Primitives

| Primitive | Location | Acceptance |
|---|---|---|
| Money | shared/money | BigDecimal only, allocation preserves total |
| BaseEntity | shared/persistence | UUID, createdAt, updatedAt, version |
| BusinessException | shared/exception | stable error code |
| ApiErrorResponse | shared/web | consistent response |
| AuditTrailService | shared/audit | persists sensitive actions to audit_logs |
| IdempotencyService | shared/idempotency | reserves duplicate-safe command keys |
| AccountingPeriod | accounting/domain | OPEN/CLOSING_REVIEW/LOCKED/REOPENED |
| Account | accounting/domain | unique code, normal balance |
| JournalEntry | accounting/domain | DRAFT/POSTED/REVERSED/VOID |
| JournalLine | accounting/domain | one-sided debit/credit |
| PostingService | accounting/application | debit=credit and period lock |
| AccountingApplicationService | accounting/application | repository-backed CoA, period, journal draft, posting workflow |
| AccountingController | accounting/web | validated REST endpoints with audit reason and server-side authentication |
| CustomerApplicationService | customer/application | repository-backed customer creation/list/detail with unique number and audit trail |
| ConnectionApplicationService | connection/application | tariff group, draft connection, and lifecycle control with audit trail |
| MeteringApplicationService | metering/application | meter route and reading lifecycle with active connection, period uniqueness, usage validation, and audit trail |
| TariffEngineApplicationService | billing/application | versioned progressive tariff calculation from active effective tariff blocks with audit trail |
| BillingBatchApplicationService | billing/application | idempotent draft invoice generation from verified meter readings without direct journal writes |
| PageResponse | shared/web | shared pagination contract for API lists |

## Frontend Primitives

| Primitive | Location | Acceptance |
|---|---|---|
| AppShell | components/layout | sidebar and content shell |
| Providers | app/providers | TanStack Query provider |
| PageHeader | components/common | consistent title/description |
| StatusBadge | components/status | clear status indicator |
| MoneyText | components/format | IDR formatting |
| EmptyState | components/state | empty state display |
| ErrorState | components/state | error display |
| LoadingSkeleton | components/state | loading display |
| PermissionGate | components/auth | permission-denied state |
| apiClient | lib/api | typed API call foundation with structured errors |
| queryKeys | lib/query | stable query keys |
| dashboard schema/hook | features/dashboard | validates backend dashboard response with Zod |

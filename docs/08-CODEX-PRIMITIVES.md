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
| BillingInvoicePostingCommand | accounting/application | source command for invoice receivable/revenue journal posting |
| PaymentSettlementPostingCommand | accounting/application | source command for payment cash/bank and receivable journal posting |
| PaymentReversalPostingCommand | accounting/application | source command for payment reversal debit receivable and credit cash/bank posting |
| PostingService | accounting/application | debit=credit and period lock |
| LedgerEntryMaterializationService | reporting/application | creates one posted ledger row per journal line after successful journal posting |
| AccountingApplicationService | accounting/application | repository-backed CoA, period, journal draft, posting workflow |
| AccountingController | accounting/web | validated REST endpoints with audit reason and server-side authentication |
| CustomerApplicationService | customer/application | repository-backed customer creation/list/detail with unique number and audit trail |
| ConnectionApplicationService | connection/application | tariff group, draft connection, and lifecycle control with audit trail |
| MeteringApplicationService | metering/application | meter route and reading lifecycle with active connection, period uniqueness, usage validation, and audit trail |
| TariffEngineApplicationService | billing/application | versioned progressive tariff calculation from active effective tariff blocks with audit trail |
| BillingBatchApplicationService | billing/application | idempotent draft invoice generation and controlled invoice issue through accounting service |
| PaymentWebhookApplicationService | payment/application | HMAC-validated provider callback persistence without settlement writes |
| PaymentSettlementApplicationService | payment/application | idempotent counter settlement, controlled reversal, invoice allocation restoration, accounting journal links, and audit trail |
| PaymentQueryApplicationService | payment/application | read-only payment list/detail hydration with receipt, allocation, and journal traceability |
| PaymentQueryController | payment/web | `payment.read` guarded payment register and detail endpoints |
| CollectionAction | receivable/domain | controlled receivable collection workflow with explicit status and action type |
| ReceivableAgingApplicationService | receivable/application | open-invoice aging snapshot with current, 30, 60, 90, and over-90 buckets |
| CollectionActionApplicationService | receivable/application | overdue invoice dunning validation, invoice-customer ownership validation, duplicate active action guard, audited start/complete/cancel workflow |
| Permissions | shared/security | central `@PreAuthorize` expressions for granular backend permissions |
| DatabaseUserDetailsService | shared/security | loads enabled DB users plus role and permission authorities for Spring Security |
| RBAC Seed Migration | db/migration | idempotent operational role, permission, and grant catalog without default credentials |
| Payment Permission Seed Migration | db/migration | idempotent payment permission and role grant catalog without default credentials |
| Payment Read Permission Seed Migration | db/migration | idempotent `payment.read` grant for supervisory and auditor roles without default credentials |
| Accounting Billing Permission Seed Migration | db/migration | idempotent accounting and billing command permission grants without default credentials |
| BootstrapAdminUserService | shared/security | opt-in initial admin provisioning from explicit env vars with encoded password and idempotent super-admin grant |
| AuthController | auth | exposes authenticated username and authorities for frontend permission visibility |
| PostedLedgerReportApplicationService | reporting/application | trial balance from posted ledger entries only, excluding draft operational records |
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
| collection action schema/hooks/workspace | features/receivables/collection-actions | typed receivable collection list, create, start, complete, cancel workflows with loading/error/empty states |
| current user hook and collection action permissions | features/auth, features/receivables/collection-actions | `/api/auth/me` authority lookup and permission-gated collection action controls |
| financial command access panel and permissions | features/security, app dashboard | `/api/auth/me` authority lookup and accounting/billing/payment command access visibility with loading/error/empty states |
| accounting schema/hooks/workspace | features/accounting, app/accounting | typed CoA, period, and journal lists with summary cards, status badges, filters, and permission-aware command availability |
| accounting mutation hooks and command validation | features/accounting | typed CoA, period, manual journal, period workflow, and journal posting mutations with audit reason handling and local debit-credit validation |
| accounting journal detail drawer | features/accounting | typed journal detail fetch with source traceability, posting metadata, debit-credit line display, and read-only balance validation |
| billing schema/hooks/workspace | features/billing, app/billing | typed billing batch and invoice lists with period/status filters, selected-batch invoice drill-down, idempotent batch generation, invoice issue confirmation, and receivable/revenue account validation |
| payment schema/hooks/workspace | features/payments, app/payments | typed payment register/detail reads, webhook event monitoring, counter settlement mutation with idempotency key, payment reversal mutation, receipt/allocation/journal traceability, asset account validation, allocation-total validation, permission gating, and loading/error/empty states |

# Database Plan

## Principles

1. Use UUID primary keys.
2. Use `NUMERIC(19,2)` for monetary columns.
3. Use unique constraints for business keys.
4. Use indexes for list filters and reports.
5. Use database guards for critical financial invariants.
6. Do not use destructive migrations without explicit approval.

## Implemented Tables

- audit_logs
- idempotency_keys
- users, roles, permissions, role_permissions, user_roles
- accounts
- accounting_periods
- journal_entries
- journal_lines
- customers
- customer_addresses
- tariff_groups
- connections
- meter_routes
- meter_readings
- tariff_versions
- tariff_blocks
- billing_batches
- invoices
- invoice_lines
- payments
- payment_allocations
- payment_receipts
- payment_webhook_events
- payment_reconciliation_sessions
- payment_reconciliation_items
- receivable_aging_snapshots
- collection_actions
- ledger_entries

## Migration Baseline

| Migration | Scope | Integrity Control |
|---|---|---|
| V1__baseline.sql | auth, audit, accounting core | unique account/journal keys, period format check, posted journal immutability trigger |
| V2__domain_foundation.sql | idempotency, customer, connection, metering, billing, payment, receivable, ledger | unique idempotency/payment keys, unique invoice and meter period keys, status checks, monetary non-negative checks, reporting indexes |
| V3__invoice_issue_accounting_trace.sql | invoice issue accounting traceability | source metadata on journal entries, unique source journal index, invoice-to-issue-journal link, unique issue journal reference |
| V4__payment_settlement_accounting_trace.sql | payment settlement accounting traceability | payment-to-settlement-journal link and unique settlement journal reference |
| V5__payment_reversal_accounting_trace.sql | payment reversal accounting traceability | payment-to-reversal-journal link and unique reversal journal reference |
| V6__collection_action_controls.sql | receivable collection workflow controls | action type check, completed state check, active duplicate prevention by invoice/type or customer/type, scheduled status index |
| V7__rbac_seed.sql | RBAC role and permission catalog | idempotent role, permission, and role-permission grants without default user credentials |
| V8__payment_permission_seed.sql | payment permission catalog | idempotent payment permission and role-permission grants without default user credentials |
| V9__accounting_billing_permission_seed.sql | accounting and billing command permission catalog | idempotent accounting/billing command permission grants without default user credentials |
| V10__payment_read_permission_seed.sql | payment read permission catalog | idempotent `payment.read` supervisory/auditor grants without default user credentials |
| V11__payment_reconciliation_permission_seed.sql | payment reconciliation permission catalog | idempotent `payment.reconcile` supervisory/auditor grants without default user credentials |
| V12__payment_reconciliation_sessions.sql | payment reconciliation session review | non-posting session/item tables, status checks, resolution indexes, and no cascade into financial history |

Smoke test on PostgreSQL 16 confirms migrations apply from an empty schema and Hibernate validation passes afterward.

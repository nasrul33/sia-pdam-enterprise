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

Smoke test on PostgreSQL 16 confirms migrations apply from an empty schema and Hibernate validation passes afterward.

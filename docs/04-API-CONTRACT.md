# API Contract Baseline

## Shared

| Method | Endpoint | Purpose | Permission |
|---|---|---|---|
| GET | /api/status | service status | public/dev |
| GET | /api/dashboard/overview | bootstrap dashboard overview | public/dev read-only |
| GET | /api/auth/me | current authenticated user and authorities for frontend permission visibility | authenticated |

`/api/dashboard/overview` returns:

```json
{
  "generatedAt": "2026-07-05T00:00:00Z",
  "metrics": [],
  "modules": [],
  "qualityGates": [],
  "risks": []
}
```

`/api/auth/me` returns:

```json
{
  "username": "bootstrap.admin",
  "authenticated": true,
  "authorities": ["ROLE_SUPER_ADMIN", "collection-action.read"]
}
```

This endpoint is for UI visibility only. Backend permission checks remain authoritative on command endpoints.

## Accounting

| Method | Endpoint | Purpose | Permission |
|---|---|---|---|
| GET | /api/accounts | list accounts | account.read |
| POST | /api/accounts | create account | account.manage |
| GET | /api/accounting-periods | list periods | period.read |
| POST | /api/accounting-periods | create period | period.manage |
| POST | /api/accounting-periods/{id}/start-closing-review | move period to closing review | period.close |
| POST | /api/accounting-periods/{id}/lock | lock period | period.close |
| GET | /api/journals | list journals | journal.read |
| GET | /api/journals/{id} | journal detail with lines | journal.read |
| POST | /api/journals | create draft journal | journal.create |
| POST | /api/journals/{id}/post | post journal | journal.post |
| POST | /api/journals/{id}/reverse | reverse journal | journal.reverse |

Mutation payloads require `reason` so audit trail has justification. Journal posting is blocked when debit/credit is not balanced or period is not `OPEN`/`REOPENED`. Successful journal posting materializes one `ledger_entries` row per journal line in the same transaction; downstream financial reports read those ledger rows only.

Accounting command endpoints are enforced server-side through method security for `account.manage`, `period.manage`, `period.close`, `journal.create`, and `journal.post`. Read permission enforcement remains a separate rollout.

## Customer and Connection

| Method | Endpoint | Purpose | Permission |
|---|---|---|---|
| GET | /api/customers | list customers with `status`, `search`, pagination | customer.read |
| GET | /api/customers/{id} | customer detail with addresses | customer.read |
| POST | /api/customers | create customer and primary address | customer.create |
| GET | /api/tariff-groups | list tariff groups | tariff.read |
| POST | /api/tariff-groups | create tariff group | tariff.manage |
| GET | /api/connections | list connections with `customerId`, `status`, pagination | connection.read |
| GET | /api/connections/{id} | connection detail | connection.read |
| POST | /api/connections | create draft connection | connection.create |
| POST | /api/connections/{id}/activate | activate draft/suspended connection | connection.lifecycle |
| POST | /api/connections/{id}/suspend | suspend active connection | connection.lifecycle |
| POST | /api/connections/{id}/terminate | terminate active/suspended connection | connection.lifecycle |

Connection creation requires active customer, existing tariff group, unique connection number, and `reason` for audit trail.

## Metering

| Method | Endpoint | Purpose | Permission |
|---|---|---|---|
| GET | /api/meter-routes | list meter routes with `areaCode`, pagination | meter-route.read |
| GET | /api/meter-routes/{id} | meter route detail | meter-route.read |
| POST | /api/meter-routes | create meter route | meter-route.manage |
| GET | /api/meter-readings | list readings with `routeId`, `period`, `status`, pagination | meter-reading.read |
| GET | /api/meter-readings/{id} | meter reading detail | meter-reading.read |
| POST | /api/meter-readings | create draft meter reading | meter-reading.create |
| POST | /api/meter-readings/{id}/submit | submit draft/rejected reading | meter-reading.submit |
| POST | /api/meter-readings/{id}/verify | verify submitted reading | meter-reading.verify |
| POST | /api/meter-readings/{id}/reject | reject submitted reading | meter-reading.verify |

Meter reading creation requires active connection, existing route, valid `yyyy-MM` period, `currentReading >= previousReading`, unique connection-period, and `reason` for audit trail.

## Billing Tariff

| Method | Endpoint | Purpose | Permission |
|---|---|---|---|
| GET | /api/tariff-versions | list tariff versions with `tariffGroupId`, `status`, pagination | tariff.read |
| GET | /api/tariff-versions/{id} | tariff version detail | tariff.read |
| POST | /api/tariff-versions | create draft tariff version | tariff.manage |
| GET | /api/tariff-versions/{id}/blocks | list blocks for a tariff version | tariff.read |
| POST | /api/tariff-versions/{id}/blocks | add block to draft tariff version | tariff.manage |
| POST | /api/tariff-versions/{id}/activate | activate a valid draft tariff version | tariff.manage |
| POST | /api/tariff-versions/{id}/archive | archive tariff version | tariff.manage |
| POST | /api/tariff-calculations | calculate progressive tariff from active effective version | tariff.calculate |

Tariff activation requires sequential contiguous blocks starting at `0.000` m3 and ending with one unbounded final block. Calculation uses the latest `ACTIVE` tariff version with `effectiveDate <= billingDate`.

## Billing Batch

| Method | Endpoint | Purpose | Permission |
|---|---|---|---|
| GET | /api/billing-batches | list billing batches with `period`, `status`, pagination | billing.read |
| GET | /api/billing-batches/{id} | billing batch detail | billing.read |
| GET | /api/billing-batches/{id}/invoices | list invoices generated by batch | billing.read |
| POST | /api/billing-batches/generate | generate draft invoices from verified readings | billing.generate |
| GET | /api/invoices | list invoices with `period`, `status`, pagination | invoice.read |
| POST | /api/invoices/{invoiceId}/issue | issue draft invoice and post receivable/revenue journal | invoice.issue |

`POST /api/billing-batches/generate` requires `billing.generate` authority and `Idempotency-Key` header. Generation requires verified meter readings for the requested `period` and `areaCode`, active connections, active effective tariff versions, no existing invoice for connection-period, and `reason` for audit trail. This endpoint creates `DRAFT` invoices only.

`POST /api/invoices/{invoiceId}/issue` requires `invoice.issue` authority and payload:

```json
{
  "receivableAccountId": "00000000-0000-0000-0000-000000000000",
  "revenueAccountId": "00000000-0000-0000-0000-000000000000",
  "reason": "issue invoice"
}
```

Invoice issue is allowed only from `DRAFT`, and `reason` is mandatory for audit trail. The accounting period matching invoice `period` must exist and allow posting. Accounting validates receivable account type `ASSET`, revenue account type `REVENUE`, creates a source-traceable journal (`sourceModule=BILLING`, `sourceRecordId=invoiceId`, `sourceDocumentNumber=invoiceNumber`), blocks duplicate source journals, posts through `PostingService`, materializes ledger entries, then links `invoices.issue_journal_entry_id` and changes invoice status to `ISSUED`.

## Payment Webhook

| Method | Endpoint | Purpose | Permission |
|---|---|---|---|
| POST | /api/payments/webhook | receive HMAC-validated payment provider webhook | HMAC signature |
| GET | /api/payment-webhook-events | list webhook events with `provider`, `status`, pagination | payment.webhook.read |

`POST /api/payments/webhook` requires `X-Payment-Signature`. The signature is HMAC-SHA256 over canonical payload `provider\nexternalReference\nidempotencyKey\npayload` using `sia.payment.webhook.secret`. This endpoint is not protected by user Basic auth because provider callbacks are authenticated by signature. It stores `RECEIVED` events only; settlement allocation, receipt creation, and journal posting are handled by later controlled payment workflow tasks.

`GET /api/payment-webhook-events` requires `payment.webhook.read` authority.

## Payment Settlement

All payment settlement mutation endpoints must send an `Idempotency-Key` header and reserve it through `idempotency_keys` before settlement writes.

| Method | Endpoint | Purpose | Permission |
|---|---|---|---|
| POST | /api/payments/counter | counter payment settlement | payment.counter |

`POST /api/payments/counter` requires `payment.counter` authority, `Idempotency-Key`, `amount`, `paidAt`, `allocations[]`, `cashAccountId`, `receivableAccountId`, and `reason`. The payment amount must equal the allocation total. Each invoice allocation must target an `ISSUED` or `PARTIAL_PAID` invoice and cannot exceed invoice outstanding amount. Accounting period is derived from `paidAt` using UTC `yyyy-MM`; the period must exist and allow posting. Accounting validates cash/bank and receivable accounts as `ASSET`, blocks duplicate source journals, posts debit cash/bank and credit receivable through `PostingService`, materializes ledger entries, then links `payments.settlement_journal_entry_id`. A completed retry with the same idempotency key and payload returns the original payment, receipt, and allocations without duplicate writes or duplicate journal posting.

## Payment Register

| Method | Endpoint | Purpose | Permission |
|---|---|---|---|
| GET | /api/payments | list payments with `status`, `channel`, pagination | payment.read |
| GET | /api/payments/{id} | payment detail with receipt, allocation, and journal trace | payment.read |

The payment register is read-only. It exposes settlement/reversal traceability but does not provide alternate correction paths; reversal remains the controlled command endpoint below.

## Payment Reversal

| Method | Endpoint | Purpose | Permission |
|---|---|---|---|
| POST | /api/payments/{id}/reverse | reverse settled payment | payment.reverse |

`POST /api/payments/{id}/reverse` requires `payment.reverse` authority, `cashAccountId`, `receivableAccountId`, and mandatory `reason`. Reversal is allowed only for `SETTLED` payments with an existing settlement journal. It restores each payment allocation back to invoice outstanding, marks the payment `REVERSED`, posts a source-traceable reversal journal (`sourceModule=PAYMENT_REVERSAL`) through `PostingService`, materializes ledger entries, and links `payments.reversal_journal_entry_id`. The reversal journal debits receivable and credits cash/bank.

## Payment Reconciliation

| Method | Endpoint | Purpose | Permission |
|---|---|---|---|
| GET | /api/payment-reconciliation/export | export settled/reversed payment slice as CSV with `status`, `channel`, `paidAtFrom`, `paidAtTo` filters | payment.reconcile |
| POST | /api/payment-reconciliation/match | match imported bank statement rows to settled/reversed payments | payment.reconcile |

`GET /api/payment-reconciliation/export` returns `text/csv`, limits export to 10,000 rows, and only allows `SETTLED` or `REVERSED` payment status. The action is audited because it exports sensitive kas-bank reconciliation data.

`POST /api/payment-reconciliation/match` accepts up to 200 rows:

```json
{
  "rows": [
    {
      "statementReference": "BANK-20260731-0001",
      "amount": 100000,
      "transactedAt": "2026-07-31T12:00:00Z",
      "channel": "COUNTER"
    }
  ]
}
```

The response classifies rows as `EXACT_MATCH`, `PROBABLE_MATCH`, `AMOUNT_VARIANCE`, `REVERSED_PAYMENT`, `MULTIPLE_CANDIDATES`, or `UNMATCHED`, including variance amount and matched payment/journal references when available. This workflow does not create, post, reverse, or mutate journals; accounting corrections remain explicit accounting/payment reversal workflows.

## Receivable Aging

| Method | Endpoint | Purpose | Permission |
|---|---|---|---|
| GET | /api/receivable-aging-snapshots | list aging snapshots with pagination | receivable-aging.read |
| GET | /api/receivable-aging-snapshots/{snapshotId} | aging snapshot detail | receivable-aging.read |
| GET | /api/receivable-aging-snapshots/by-period/{period} | aging snapshot by `yyyy-MM` period | receivable-aging.read |
| POST | /api/receivable-aging-snapshots/generate | generate or refresh period aging snapshot | receivable-aging.generate |

`POST /api/receivable-aging-snapshots/generate` requires authentication, `period`, `asOfDate`, and `reason`. Aging uses only open invoices with status `ISSUED` or `PARTIAL_PAID` and positive outstanding amount. Bucket rules are: `current` for not-yet-due or due today, `bucket30` for 1-30 days overdue, `bucket60` for 31-60 days, `bucket90` for 61-90 days, and `bucketOver90` for more than 90 days. This is an operational receivable snapshot; final financial reporting must still use posted ledger entries.

## Receivable Collection

| Method | Endpoint | Purpose | Permission |
|---|---|---|---|
| GET | /api/collection-actions | list collection actions with `status`, `customerId`, `invoiceId`, pagination | collection-action.read |
| GET | /api/collection-actions/{actionId} | collection action detail | collection-action.read |
| POST | /api/collection-actions | create collection or dunning action | collection-action.create |
| POST | /api/collection-actions/{actionId}/start | mark action in progress | collection-action.execute |
| POST | /api/collection-actions/{actionId}/complete | complete action | collection-action.execute |
| POST | /api/collection-actions/{actionId}/cancel | cancel action | collection-action.cancel |

`POST /api/collection-actions` requires authentication, `customerId`, `actionType`, `scheduledAt`, and mandatory `reason`. Invoice-level dunning types `REMINDER`, `WARNING_LETTER`, and `DISCONNECTION_NOTICE` require `invoiceId`; the invoice must be `ISSUED` or `PARTIAL_PAID`, have positive outstanding amount, be overdue on the scheduled date, and belong to the requested customer through `invoice.connectionId -> connection.customerId`. The backend blocks duplicate active `OPEN` or `IN_PROGRESS` actions for the same invoice/type and for customer-level actions without invoice. Workflow transitions are audited and limited to `OPEN -> IN_PROGRESS`, `OPEN/IN_PROGRESS -> COMPLETED`, and `OPEN/IN_PROGRESS -> CANCELLED`.

The permission values in the table are enforced server-side through method security using `hasAuthority(...)`.

## Reporting

| Method | Endpoint | Purpose | Permission |
|---|---|---|---|
| GET | /api/reports/trial-balance | trial balance from posted ledger entries for `fromDate` to `toDate` | report.financial.read |

`GET /api/reports/trial-balance` reads `ledger_entries` only, groups balances by account, and returns debit/credit balance totals plus a `balanced` flag. It must not read draft invoices, draft billing batches, pending payments, or operational snapshots as final financial report sources.

## Error Contract

All errors should return:

```json
{
  "timestamp": "2026-07-05T00:00:00Z",
  "code": "BUSINESS_ERROR",
  "message": "Human readable message",
  "details": []
}
```

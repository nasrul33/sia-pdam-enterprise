# API Contract Baseline

## Shared

| Method | Endpoint | Purpose | Permission |
|---|---|---|---|
| GET | /api/status | service status | public/dev |
| GET | /api/dashboard/overview | bootstrap dashboard overview | public/dev read-only |

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

## Accounting Planned

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

Mutation payloads require `reason` so audit trail has justification. Journal posting is blocked when debit/credit is not balanced or period is not `OPEN`/`REOPENED`.

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

`POST /api/billing-batches/generate` requires `Idempotency-Key` header. Generation requires verified meter readings for the requested `period` and `areaCode`, active connections, active effective tariff versions, no existing invoice for connection-period, and `reason` for audit trail. This endpoint creates `DRAFT` invoices only; receivable/revenue posting must be handled by a controlled accounting workflow.

## Payment Planned

All payment mutation endpoints must send an `Idempotency-Key` header and reserve it through `idempotency_keys` before settlement writes.

| Method | Endpoint | Purpose | Permission |
|---|---|---|---|
| POST | /api/payments/counter | counter payment settlement | payment.counter |
| POST | /api/payments/webhook | payment provider webhook | signature validation |
| POST | /api/payments/{id}/reverse | reverse settled payment | payment.reverse |

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

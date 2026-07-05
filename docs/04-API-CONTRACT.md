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

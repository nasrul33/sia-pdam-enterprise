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

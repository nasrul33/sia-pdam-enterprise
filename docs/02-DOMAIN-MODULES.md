# Domain Modules

| Module | Responsibility | Critical Controls |
|---|---|---|
| Shared | Money, audit, exceptions | no primitive money outside Money |
| Auth | RBAC and SoD | server-side authorization |
| Accounting | CoA, period, journal, ledger | debit=credit, posted immutable, period lock |
| Customer | master pelanggan | unique customer number |
| Connection | sambungan | lifecycle status |
| Metering | baca meter | unique connection+period |
| Billing | tarif and tagihan | idempotent generation, journal posting |
| Payment | settlement | unique idempotency key |
| Receivable | aging and collection | posted invoice/payment source |
| Reporting | reports | posted-ledger-only financial reports |

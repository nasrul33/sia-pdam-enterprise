# Domain Modules

| Module | Responsibility | Critical Controls |
|---|---|---|
| Shared | Money, audit, exceptions | no primitive money outside Money |
| Auth | RBAC and SoD | server-side authorization |
| Accounting | CoA, period, journal, ledger | debit=credit, posted immutable, period lock |
| Customer | master pelanggan and address | unique customer number, active/inactive/blacklisted status |
| Connection | sambungan and tariff group assignment | unique connection number, active customer validation, lifecycle status |
| Metering | rute and baca meter | unique route code, active connection only, unique connection+period, audited reading lifecycle |
| Billing | tarif and tagihan | active effective tariff version, contiguous progressive blocks, idempotent draft invoice generation, journal posting only through accounting workflow |
| Payment | settlement | unique idempotency key |
| Receivable | aging and collection | posted invoice/payment source |
| Reporting | reports | posted-ledger-only financial reports |

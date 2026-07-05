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
| Payment | webhook intake and settlement | validated webhook signature, unique idempotency key, allocation total equals payment amount, settlement reconciliation |
| Receivable | aging and collection | open invoice outstanding aging buckets, collection source control |
| Reporting | reports | posted-ledger-only financial reports |

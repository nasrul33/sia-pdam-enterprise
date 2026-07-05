# Security, Audit, and Governance

## Initial Roles

- Super Admin
- Admin Sistem
- Petugas Pelanggan
- Petugas Meter
- Supervisor Meter
- Billing Officer
- Billing Supervisor
- Kasir
- Finance Staff
- Finance Supervisor
- Auditor Internal
- Direksi/Manajemen

## Approval Matrix

| Action | Maker | Checker | Reason Required |
|---|---|---|---|
| Manual journal | Finance Staff | Finance Supervisor | yes |
| Post journal | Finance Supervisor | System validation | yes |
| Reverse journal | Finance Supervisor | Finance Manager | yes |
| Close period | Finance Manager | Authorized Role | yes |
| Correct invoice | Billing Officer | Billing Supervisor | yes |
| Reverse payment | Kasir/Supervisor | Finance Supervisor | yes |
| Change permission | Admin Sistem | Super Admin | yes |

## Audit Trail Fields

- actor
- module
- action
- record_id
- before_value
- after_value
- reason
- request_id
- ip_address
- created_at

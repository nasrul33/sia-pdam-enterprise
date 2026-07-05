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

## Permission Matrix

| Permission | Endpoint/Action | Intended Role |
|---|---|---|
| collection-action.read | `GET /api/collection-actions`, `GET /api/collection-actions/{id}` | Petugas Piutang, Supervisor Piutang, Auditor Internal |
| collection-action.create | `POST /api/collection-actions` | Petugas Piutang, Supervisor Piutang |
| collection-action.execute | `POST /api/collection-actions/{id}/start`, `POST /api/collection-actions/{id}/complete` | Petugas Piutang |
| collection-action.cancel | `POST /api/collection-actions/{id}/cancel` | Supervisor Piutang |

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

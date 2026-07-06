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
- Petugas Piutang
- Supervisor Piutang

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

## Seeded RBAC Catalog

- Flyway V7 seeds role and permission catalog only.
- No default user or default password is seeded by migration.
- Initial user provisioning must use a controlled operational/admin process with a prefixed password hash, preferably `{bcrypt}`.

| Role | Seeded Permissions |
|---|---|
| super-admin | collection-action.read, collection-action.create, collection-action.execute, collection-action.cancel |
| petugas-piutang | collection-action.read, collection-action.create, collection-action.execute |
| supervisor-piutang | collection-action.read, collection-action.create, collection-action.cancel |
| auditor-internal | collection-action.read |

## Authentication Source

- HTTP Basic is backed by `users.password_hash`.
- Stored password values use Spring Security password encoder prefixes such as `{bcrypt}` for production hashes.
- User authorities are derived from assigned role permissions in `role_permissions`.
- Role authorities are exposed as `ROLE_<ROLE_CODE>` with uppercase and hyphen-to-underscore normalization.
- Disabled users are rejected by Spring Security.

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

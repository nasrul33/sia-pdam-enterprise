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
| payment.counter | `POST /api/payments/counter` | Kasir |
| payment.reverse | `POST /api/payments/{id}/reverse` | Finance Supervisor |
| payment.webhook.read | `GET /api/payment-webhook-events` | Finance Supervisor, Auditor Internal |

## Seeded RBAC Catalog

- Flyway V7 seeds operational roles and collection action permissions.
- Flyway V8 seeds payment permissions and role grants.
- No default user or default password is seeded by migration.
- Initial user provisioning must use a controlled operational/admin process with a prefixed password hash, preferably `{bcrypt}`.

| Role | Seeded Permissions |
|---|---|
| super-admin | collection-action.read, collection-action.create, collection-action.execute, collection-action.cancel, payment.counter, payment.reverse, payment.webhook.read |
| petugas-piutang | collection-action.read, collection-action.create, collection-action.execute |
| supervisor-piutang | collection-action.read, collection-action.create, collection-action.cancel |
| kasir | payment.counter |
| finance-supervisor | payment.reverse, payment.webhook.read |
| auditor-internal | collection-action.read, payment.webhook.read |

## Authentication Source

- HTTP Basic is backed by `users.password_hash`.
- Stored password values use Spring Security password encoder prefixes such as `{bcrypt}` for production hashes.
- User authorities are derived from assigned role permissions in `role_permissions`.
- Role authorities are exposed as `ROLE_<ROLE_CODE>` with uppercase and hyphen-to-underscore normalization.
- Disabled users are rejected by Spring Security.

## Bootstrap Admin Provisioning

- No bootstrap user is created unless all three env vars are provided: `SIA_BOOTSTRAP_ADMIN_USERNAME`, `SIA_BOOTSTRAP_ADMIN_EMAIL`, and `SIA_BOOTSTRAP_ADMIN_PASSWORD`.
- Bootstrap password must contain at least 12 characters and is encoded with the configured Spring Security `PasswordEncoder`.
- Bootstrap requires the seeded `super-admin` role.
- If the username already exists, startup only grants the `super-admin` role and does not overwrite the existing password.
- Partial bootstrap configuration fails startup so insecure or incomplete first-access setup is visible immediately.

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

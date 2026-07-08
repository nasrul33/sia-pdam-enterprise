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
| payment.read | `GET /api/payments`, `GET /api/payments/{id}` | Finance Supervisor, Auditor Internal |
| payment.reconcile | Payment reconciliation, evidence, review register, handoff-note read, handoff workload, and owner SLA export endpoints | Finance Supervisor, Auditor Internal |
| payment.reconciliation.handoff-note | Create/revise reconciliation reviewer handoff notes | Finance Supervisor, Auditor Internal |
| payment.reconciliation.signoff | Sign off completed reconciliation evidence | Finance Supervisor |
| payment.reverse | `POST /api/payments/{id}/reverse` | Finance Supervisor |
| payment.webhook.read | `GET /api/payment-webhook-events` | Finance Supervisor, Auditor Internal |
| account.manage | `POST /api/accounts` | Finance Supervisor |
| period.manage | `POST /api/accounting-periods` | Finance Supervisor |
| period.close | `POST /api/accounting-periods/{id}/start-closing-review`, `POST /api/accounting-periods/{id}/lock` | Finance Supervisor |
| journal.create | `POST /api/journals` | Finance Staff, Finance Supervisor |
| journal.post | `POST /api/journals/{id}/post` | Finance Supervisor |
| billing.generate | `POST /api/billing-batches/generate` | Billing Officer, Billing Supervisor |
| invoice.issue | `POST /api/invoices/{id}/issue` | Billing Supervisor |

## Seeded RBAC Catalog

- Flyway V7 seeds operational roles and collection action permissions.
- Flyway V8 seeds payment permissions and role grants.
- Flyway V9 seeds accounting and billing command permissions and role grants.
- Flyway V10-V17 extend payment read, reconciliation, sign-off, and handoff-note permissions without default user credentials.
- No default user or default password is seeded by migration.
- Initial user provisioning must use a controlled operational/admin process with a prefixed password hash, preferably `{bcrypt}`.

| Role | Seeded Permissions |
|---|---|
| super-admin | collection-action.read, collection-action.create, collection-action.execute, collection-action.cancel, payment.counter, payment.read, payment.reconcile, payment.reconciliation.handoff-note, payment.reconciliation.signoff, payment.reverse, payment.webhook.read, account.manage, period.manage, period.close, journal.create, journal.post, billing.generate, invoice.issue |
| petugas-piutang | collection-action.read, collection-action.create, collection-action.execute |
| supervisor-piutang | collection-action.read, collection-action.create, collection-action.cancel |
| kasir | payment.counter |
| finance-staff | journal.create |
| finance-supervisor | payment.read, payment.reconcile, payment.reconciliation.handoff-note, payment.reconciliation.signoff, payment.reverse, payment.webhook.read, account.manage, period.manage, period.close, journal.create, journal.post |
| billing-officer | billing.generate |
| billing-supervisor | billing.generate, invoice.issue |
| auditor-internal | collection-action.read, payment.read, payment.reconcile, payment.reconciliation.handoff-note, payment.webhook.read |

## Authentication Source

- HTTP Basic is backed by `users.password_hash`.
- Stored password values use Spring Security password encoder prefixes such as `{bcrypt}` for production hashes.
- User authorities are derived from assigned role permissions in `role_permissions`.
- Role authorities are exposed as `ROLE_<ROLE_CODE>` with uppercase and hyphen-to-underscore normalization.
- Disabled users are rejected by Spring Security.

## Frontend Permission Visibility

- Frontend reads `/api/auth/me` for visibility and disabled/locked states only.
- Collection action UI is gated by `collection-action.*` authorities.
- Dashboard financial command panel is gated by accounting and billing command authorities.
- Payment handoff workload and owner SLA list/export visibility is gated by `payment.reconcile`; note create/revise controls remain separately gated by `payment.reconciliation.handoff-note`.
- Backend method security remains authoritative for every financial command.

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

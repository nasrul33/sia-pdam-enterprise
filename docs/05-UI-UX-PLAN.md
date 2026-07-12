# UI/UX Plan

## Dashboard Modules

- Ringkasan
- Pelanggan
- Sambungan
- Baca Meter
- Billing
- Pembayaran
- Piutang
- Akuntansi
- Laporan
- Admin

## Required UI States

Every page must include:

1. loading state;
2. empty state;
3. error state;
4. permission denied state;
5. confirmation dialog for sensitive actions;
6. validation messages;
7. status badges;
8. table filtering and pagination.

## Implemented Bootstrap UI

- `AppShell` uses enterprise sidebar navigation for all planned modules.
- Home dashboard uses TanStack Query against `GET /api/dashboard/overview`.
- Dashboard includes loading skeleton, API error state, empty fallback, status badges, quality gate list, module readiness table, and risk queue.
- Financial actions remain informational only until backend authorization and workflow APIs exist.

## Entity Selector Standard

- Workflow operator must select pelanggan, sambungan, rute, pembacaan, golongan tarif, invoice, payment, akun, dan periode by business label; raw database UUID input is prohibited.
- `EntitySelector` uses a controlled identifier value, 300 ms debounced lookup, abortable requests, minimum two-character query, stale-response protection, and selected-option retention.
- Combobox interaction supports Arrow Up/Down, Enter, Escape, clear action, `aria-expanded`, `aria-controls`, and `role="listbox"`/`role="option"` semantics.
- Lookup results are bounded to 20 rows and show business number/name, supporting description, and status. Loading, error, empty, disabled, invalid, and selected states use a fixed-height overlay so form layout does not shift.
- Backend payloads continue to carry UUID values and retain UUID validation; only the operator-facing input method changes.

## Authentication UX Standard

- All browser API traffic is same-origin through `/api/backend`; frontend components never construct Basic/Bearer authorization values.
- Local Basic credentials exist only in the Next.js server environment. Production access token remains inside the encrypted NextAuth server session and is never stored in local/session storage.
- `SessionProvider` is rendered only for OIDC builds to avoid unnecessary session fetches and hydration errors in local Basic mode.
- A backend `401` in OIDC mode redirects to `/api/auth/signin` with the current route as callback. Local server misconfiguration and backend unavailability render the existing query error state without exposing credential details.
- Permission-aware controls remain usability hints only; backend method security is authoritative for every sensitive mutation.
- User administration renders `EXTERNAL_MANAGED` accounts as "Dikelola IdP" and disables local status/role commands; local database mutation is available only under local Basic profile.

## Implemented Enterprise Workspaces

The 21 built routes cover dashboard, accounting/period/journal/AP/assets, billing/tariff, customer/connection/request, metering/offline import/lock, payment/bank mutation/reconciliation, receivable aging/collection/installment, financial reporting, settings, and guarded user/role administration. Each operational workspace uses typed API schemas, loading/error/empty states, permission gates, audit reason validation, and stable responsive layouts.

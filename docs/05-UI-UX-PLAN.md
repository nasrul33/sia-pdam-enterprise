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

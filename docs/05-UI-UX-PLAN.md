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

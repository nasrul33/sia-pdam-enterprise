-- Blueprint performance alignment for production list, reporting, and reconciliation workloads.
-- All indexes are additive and target query patterns already present in repositories/services.

CREATE INDEX idx_suppliers_status_code
    ON suppliers(status, code);

CREATE INDEX idx_payables_period_status_recorded
    ON payables(period, status, recorded_at DESC);

CREATE INDEX idx_payables_status_recorded
    ON payables(status, recorded_at DESC);

CREATE INDEX idx_fixed_assets_status_code
    ON fixed_assets(status, asset_code);

CREATE INDEX idx_installment_plans_status_created
    ON installment_plans(status, created_at DESC);

CREATE INDEX idx_billing_batches_period_status_created
    ON billing_batches(period, status, created_at DESC);

CREATE INDEX idx_invoices_period_status_created
    ON invoices(period, status, created_at DESC);

CREATE INDEX idx_invoices_open_receivables
    ON invoices(status, outstanding_amount)
    WHERE outstanding_amount > 0;

CREATE INDEX idx_payments_channel_paid_at
    ON payments(channel, paid_at DESC, created_at DESC);

CREATE INDEX idx_payments_status_channel_paid_at
    ON payments(status, channel, paid_at DESC, created_at DESC);

CREATE INDEX idx_connection_requests_status_requested
    ON connection_requests(status, requested_at DESC);

CREATE INDEX idx_customers_status_number
    ON customers(status, customer_number);

CREATE INDEX idx_connections_status_number
    ON connections(status, connection_number);

CREATE INDEX idx_connections_customer_status_number
    ON connections(customer_id, status, connection_number);

CREATE INDEX idx_meter_routes_area_code
    ON meter_routes(area_code, route_code);

CREATE INDEX idx_meter_readings_route_period_status_read
    ON meter_readings(route_id, period, status, read_at DESC);

CREATE INDEX idx_meter_readings_route_status_read
    ON meter_readings(route_id, status, read_at DESC);

CREATE INDEX idx_meter_readings_period_status_read
    ON meter_readings(period, status, read_at DESC);

CREATE INDEX idx_tariff_versions_group_status_effective
    ON tariff_versions(tariff_group_id, status, effective_date DESC);

CREATE INDEX idx_tariff_versions_status_effective
    ON tariff_versions(status, effective_date DESC);

CREATE INDEX idx_ledger_entries_posting_date_account
    ON ledger_entries(posting_date, account_id);

CREATE INDEX idx_reconciliation_sessions_completed_review
    ON payment_reconciliation_sessions(status, completed_at DESC, started_at DESC, created_at DESC);

CREATE INDEX idx_handoff_notes_active_due_status_updated
    ON payment_reconciliation_handoff_notes(handoff_due_date, handoff_status, updated_at DESC)
    WHERE handoff_status IN ('OPEN', 'IN_PROGRESS');

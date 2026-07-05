ALTER TABLE audit_logs
    ADD COLUMN before_value TEXT,
    ADD COLUMN after_value TEXT,
    ADD COLUMN correlation_id VARCHAR(128),
    ADD COLUMN ip_address VARCHAR(64),
    ADD COLUMN user_agent VARCHAR(255);

CREATE INDEX idx_audit_logs_correlation ON audit_logs(correlation_id);

ALTER TABLE accounts
    ADD COLUMN parent_id UUID REFERENCES accounts(id);

CREATE INDEX idx_accounts_parent ON accounts(parent_id);
CREATE INDEX idx_accounts_type ON accounts(type);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    module VARCHAR(64) NOT NULL,
    request_hash VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    response_reference VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_idempotency_status CHECK (status IN ('PENDING','COMPLETED','FAILED')),
    CONSTRAINT chk_idempotency_expiry CHECK (expires_at > created_at)
);

CREATE INDEX idx_idempotency_module_status ON idempotency_keys(module, status);
CREATE INDEX idx_idempotency_expires ON idempotency_keys(expires_at);

CREATE TABLE customers (
    id UUID PRIMARY KEY,
    customer_number VARCHAR(64) NOT NULL UNIQUE,
    full_name VARCHAR(255) NOT NULL,
    identity_number VARCHAR(64),
    phone_number VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_customers_status CHECK (status IN ('ACTIVE','INACTIVE','BLACKLISTED'))
);

CREATE INDEX idx_customers_name ON customers(full_name);
CREATE INDEX idx_customers_status ON customers(status);

CREATE TABLE customer_addresses (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    address_line TEXT NOT NULL,
    area_code VARCHAR(64) NOT NULL,
    latitude NUMERIC(10,7),
    longitude NUMERIC(10,7),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_customer_addresses_customer ON customer_addresses(customer_id);
CREATE INDEX idx_customer_addresses_area ON customer_addresses(area_code);

CREATE TABLE tariff_groups (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE connections (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    tariff_group_id UUID NOT NULL REFERENCES tariff_groups(id),
    connection_number VARCHAR(64) NOT NULL UNIQUE,
    meter_number VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    installed_at DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_connections_status CHECK (status IN ('DRAFT','ACTIVE','SUSPENDED','TERMINATED'))
);

CREATE INDEX idx_connections_customer ON connections(customer_id);
CREATE INDEX idx_connections_tariff_group ON connections(tariff_group_id);
CREATE INDEX idx_connections_status ON connections(status);

CREATE TABLE meter_routes (
    id UUID PRIMARY KEY,
    route_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    area_code VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_meter_routes_area ON meter_routes(area_code);

CREATE TABLE meter_readings (
    id UUID PRIMARY KEY,
    connection_id UUID NOT NULL REFERENCES connections(id),
    route_id UUID REFERENCES meter_routes(id),
    period VARCHAR(7) NOT NULL,
    previous_reading NUMERIC(14,3) NOT NULL,
    current_reading NUMERIC(14,3) NOT NULL,
    usage_m3 NUMERIC(14,3) NOT NULL,
    status VARCHAR(32) NOT NULL,
    read_at TIMESTAMPTZ NOT NULL,
    reader_id UUID,
    anomaly_flag BOOLEAN NOT NULL DEFAULT false,
    anomaly_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_meter_readings_connection_period UNIQUE (connection_id, period),
    CONSTRAINT chk_meter_readings_period CHECK (period ~ '^\d{4}-\d{2}$'),
    CONSTRAINT chk_meter_readings_usage CHECK (usage_m3 >= 0),
    CONSTRAINT chk_meter_readings_status CHECK (status IN ('DRAFT','SUBMITTED','VERIFIED','REJECTED'))
);

CREATE INDEX idx_meter_readings_period_status ON meter_readings(period, status);
CREATE INDEX idx_meter_readings_route_period ON meter_readings(route_id, period);

CREATE TABLE tariff_versions (
    id UUID PRIMARY KEY,
    tariff_group_id UUID NOT NULL REFERENCES tariff_groups(id),
    effective_date DATE NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tariff_versions_group_effective UNIQUE (tariff_group_id, effective_date),
    CONSTRAINT chk_tariff_versions_status CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED'))
);

CREATE INDEX idx_tariff_versions_status ON tariff_versions(status);

CREATE TABLE tariff_blocks (
    id UUID PRIMARY KEY,
    tariff_version_id UUID NOT NULL REFERENCES tariff_versions(id),
    block_order INTEGER NOT NULL,
    min_m3 NUMERIC(14,3) NOT NULL,
    max_m3 NUMERIC(14,3),
    price_per_m3 NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_tariff_blocks_order UNIQUE (tariff_version_id, block_order),
    CONSTRAINT chk_tariff_blocks_range CHECK (min_m3 >= 0 AND (max_m3 IS NULL OR max_m3 > min_m3)),
    CONSTRAINT chk_tariff_blocks_price CHECK (price_per_m3 >= 0)
);

CREATE TABLE billing_batches (
    id UUID PRIMARY KEY,
    batch_number VARCHAR(64) NOT NULL UNIQUE,
    period VARCHAR(7) NOT NULL,
    area_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_billing_batches_period_area UNIQUE (period, area_code),
    CONSTRAINT chk_billing_batches_period CHECK (period ~ '^\d{4}-\d{2}$'),
    CONSTRAINT chk_billing_batches_status CHECK (status IN ('DRAFT','RUNNING','COMPLETED','FAILED','VOID'))
);

CREATE INDEX idx_billing_batches_period_status ON billing_batches(period, status);

CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    billing_batch_id UUID REFERENCES billing_batches(id),
    connection_id UUID NOT NULL REFERENCES connections(id),
    invoice_number VARCHAR(64) NOT NULL UNIQUE,
    period VARCHAR(7) NOT NULL,
    status VARCHAR(32) NOT NULL,
    subtotal NUMERIC(19,2) NOT NULL,
    penalty_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    paid_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    outstanding_amount NUMERIC(19,2) NOT NULL,
    issued_at TIMESTAMPTZ,
    due_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_invoices_connection_period UNIQUE (connection_id, period),
    CONSTRAINT chk_invoices_period CHECK (period ~ '^\d{4}-\d{2}$'),
    CONSTRAINT chk_invoices_amount CHECK (
        subtotal >= 0 AND penalty_amount >= 0 AND paid_amount >= 0 AND outstanding_amount >= 0
    ),
    CONSTRAINT chk_invoices_status CHECK (status IN ('DRAFT','ISSUED','PARTIAL_PAID','PAID','CORRECTED','VOID'))
);

CREATE INDEX idx_invoices_period_status ON invoices(period, status);
CREATE INDEX idx_invoices_connection_status ON invoices(connection_id, status);
CREATE INDEX idx_invoices_due_date ON invoices(due_date);

CREATE TABLE invoice_lines (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    line_type VARCHAR(64) NOT NULL,
    description VARCHAR(255) NOT NULL,
    quantity NUMERIC(14,3) NOT NULL,
    unit_price NUMERIC(19,2) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_invoice_lines_amount CHECK (quantity >= 0 AND unit_price >= 0 AND amount >= 0)
);

CREATE INDEX idx_invoice_lines_invoice ON invoice_lines(invoice_id);

CREATE TABLE payments (
    id UUID PRIMARY KEY,
    payment_number VARCHAR(64) NOT NULL UNIQUE,
    idempotency_key VARCHAR(128) NOT NULL UNIQUE,
    channel VARCHAR(64) NOT NULL,
    external_reference VARCHAR(128),
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    paid_at TIMESTAMPTZ NOT NULL,
    settled_at TIMESTAMPTZ,
    reversed_at TIMESTAMPTZ,
    reversal_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_payments_amount CHECK (amount > 0),
    CONSTRAINT chk_payments_status CHECK (status IN ('PENDING','SETTLED','REVERSED','FAILED'))
);

CREATE UNIQUE INDEX uq_payments_external_reference
    ON payments(external_reference)
    WHERE external_reference IS NOT NULL;
CREATE INDEX idx_payments_status_paid_at ON payments(status, paid_at);

CREATE TABLE payment_allocations (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL REFERENCES payments(id),
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    amount NUMERIC(19,2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_allocations_payment_invoice UNIQUE (payment_id, invoice_id),
    CONSTRAINT chk_payment_allocations_amount CHECK (amount > 0)
);

CREATE INDEX idx_payment_allocations_invoice ON payment_allocations(invoice_id);

CREATE TABLE payment_receipts (
    id UUID PRIMARY KEY,
    payment_id UUID NOT NULL UNIQUE REFERENCES payments(id),
    receipt_number VARCHAR(64) NOT NULL UNIQUE,
    issued_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE payment_webhook_events (
    id UUID PRIMARY KEY,
    provider VARCHAR(64) NOT NULL,
    external_reference VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(32) NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_payment_webhook_events_reference UNIQUE (provider, external_reference),
    CONSTRAINT chk_payment_webhook_events_status CHECK (status IN ('RECEIVED','PROCESSED','FAILED','IGNORED'))
);

CREATE INDEX idx_payment_webhook_events_status ON payment_webhook_events(status, received_at);

CREATE TABLE receivable_aging_snapshots (
    id UUID PRIMARY KEY,
    period VARCHAR(7) NOT NULL UNIQUE,
    current_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    bucket_30_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    bucket_60_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    bucket_90_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    bucket_over_90_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    generated_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_receivable_aging_period CHECK (period ~ '^\d{4}-\d{2}$')
);

CREATE TABLE collection_actions (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    invoice_id UUID REFERENCES invoices(id),
    status VARCHAR(32) NOT NULL,
    action_type VARCHAR(64) NOT NULL,
    notes TEXT,
    scheduled_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_collection_actions_status CHECK (status IN ('OPEN','IN_PROGRESS','COMPLETED','CANCELLED'))
);

CREATE INDEX idx_collection_actions_customer_status ON collection_actions(customer_id, status);
CREATE INDEX idx_collection_actions_invoice ON collection_actions(invoice_id);

CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id),
    journal_line_id UUID NOT NULL UNIQUE REFERENCES journal_lines(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    posting_date DATE NOT NULL,
    debit NUMERIC(19,2) NOT NULL DEFAULT 0,
    credit NUMERIC(19,2) NOT NULL DEFAULT 0,
    source_module VARCHAR(64) NOT NULL,
    source_record_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_ledger_entries_amount CHECK (debit >= 0 AND credit >= 0),
    CONSTRAINT chk_ledger_entries_single_side CHECK ((debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0))
);

CREATE INDEX idx_ledger_entries_account_date ON ledger_entries(account_id, posting_date);
CREATE INDEX idx_ledger_entries_source ON ledger_entries(source_module, source_record_id);

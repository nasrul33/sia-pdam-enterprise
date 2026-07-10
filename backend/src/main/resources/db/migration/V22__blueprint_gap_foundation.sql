CREATE TABLE suppliers (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    contact_name VARCHAR(128),
    phone_number VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_suppliers_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE INDEX idx_suppliers_status ON suppliers(status);
CREATE INDEX idx_suppliers_name ON suppliers(name);

CREATE TABLE payables (
    id UUID PRIMARY KEY,
    supplier_id UUID NOT NULL REFERENCES suppliers(id),
    payable_number VARCHAR(64) NOT NULL UNIQUE,
    supplier_reference VARCHAR(128),
    period VARCHAR(7) NOT NULL,
    status VARCHAR(32) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    description VARCHAR(255) NOT NULL,
    recorded_at TIMESTAMPTZ NOT NULL,
    recorded_by VARCHAR(128) NOT NULL,
    paid_at TIMESTAMPTZ,
    paid_by VARCHAR(128),
    recorded_journal_entry_id UUID NOT NULL UNIQUE REFERENCES journal_entries(id),
    settlement_journal_entry_id UUID UNIQUE REFERENCES journal_entries(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_payables_status CHECK (status IN ('OPEN','PAID','VOID')),
    CONSTRAINT chk_payables_period CHECK (period ~ '^\d{4}-\d{2}$'),
    CONSTRAINT chk_payables_amount CHECK (amount > 0)
);

CREATE INDEX idx_payables_supplier_status ON payables(supplier_id, status);
CREATE INDEX idx_payables_period_status ON payables(period, status);

CREATE TABLE fixed_assets (
    id UUID PRIMARY KEY,
    asset_code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    acquisition_date DATE NOT NULL,
    acquisition_cost NUMERIC(19,2) NOT NULL,
    salvage_value NUMERIC(19,2) NOT NULL DEFAULT 0,
    useful_life_months INTEGER NOT NULL,
    depreciation_method VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    asset_account_id UUID NOT NULL REFERENCES accounts(id),
    accumulated_depreciation_account_id UUID NOT NULL REFERENCES accounts(id),
    depreciation_expense_account_id UUID NOT NULL REFERENCES accounts(id),
    accumulated_depreciation NUMERIC(19,2) NOT NULL DEFAULT 0,
    registered_journal_entry_id UUID NOT NULL UNIQUE REFERENCES journal_entries(id),
    disposed_at TIMESTAMPTZ,
    disposal_reason TEXT,
    disposal_journal_entry_id UUID UNIQUE REFERENCES journal_entries(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_fixed_assets_status CHECK (status IN ('ACTIVE','DISPOSED')),
    CONSTRAINT chk_fixed_assets_method CHECK (depreciation_method IN ('STRAIGHT_LINE','DECLINING_BALANCE')),
    CONSTRAINT chk_fixed_assets_cost CHECK (acquisition_cost > 0),
    CONSTRAINT chk_fixed_assets_salvage CHECK (salvage_value >= 0 AND salvage_value < acquisition_cost),
    CONSTRAINT chk_fixed_assets_life CHECK (useful_life_months > 0),
    CONSTRAINT chk_fixed_assets_accumulated CHECK (accumulated_depreciation >= 0 AND accumulated_depreciation <= acquisition_cost)
);

CREATE INDEX idx_fixed_assets_status ON fixed_assets(status);
CREATE INDEX idx_fixed_assets_accounts ON fixed_assets(asset_account_id, accumulated_depreciation_account_id);

CREATE TABLE fixed_asset_depreciations (
    id UUID PRIMARY KEY,
    asset_id UUID NOT NULL REFERENCES fixed_assets(id),
    period VARCHAR(7) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    journal_entry_id UUID NOT NULL UNIQUE REFERENCES journal_entries(id),
    posted_at TIMESTAMPTZ NOT NULL,
    posted_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_fixed_asset_depreciations_asset_period UNIQUE (asset_id, period),
    CONSTRAINT chk_fixed_asset_depreciations_period CHECK (period ~ '^\d{4}-\d{2}$'),
    CONSTRAINT chk_fixed_asset_depreciations_amount CHECK (amount > 0)
);

CREATE INDEX idx_fixed_asset_depreciations_period ON fixed_asset_depreciations(period);

CREATE TABLE installment_plans (
    id UUID PRIMARY KEY,
    invoice_id UUID NOT NULL REFERENCES invoices(id),
    plan_number VARCHAR(64) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    total_amount NUMERIC(19,2) NOT NULL,
    installment_count INTEGER NOT NULL,
    created_by VARCHAR(128) NOT NULL,
    approved_by VARCHAR(128),
    approved_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_installment_plans_status CHECK (status IN ('ACTIVE','COMPLETED','DEFAULTED','CANCELLED')),
    CONSTRAINT chk_installment_plans_total CHECK (total_amount > 0),
    CONSTRAINT chk_installment_plans_count CHECK (installment_count > 0)
);

CREATE INDEX idx_installment_plans_invoice_status ON installment_plans(invoice_id, status);

CREATE TABLE installment_items (
    id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES installment_plans(id),
    installment_number INTEGER NOT NULL,
    due_date DATE NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    paid_amount NUMERIC(19,2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_installment_items_plan_number UNIQUE (plan_id, installment_number),
    CONSTRAINT chk_installment_items_status CHECK (status IN ('OPEN','PAID','OVERDUE','CANCELLED')),
    CONSTRAINT chk_installment_items_amount CHECK (amount > 0 AND paid_amount >= 0 AND paid_amount <= amount)
);

CREATE INDEX idx_installment_items_due_status ON installment_items(due_date, status);

CREATE TABLE bank_mutations (
    id UUID PRIMARY KEY,
    external_reference VARCHAR(128) NOT NULL UNIQUE,
    source_filename VARCHAR(255) NOT NULL,
    bank_account_reference VARCHAR(128) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    transacted_at TIMESTAMPTZ NOT NULL,
    channel VARCHAR(64),
    description VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    reconciliation_session_id UUID REFERENCES payment_reconciliation_sessions(id),
    matched_payment_id UUID REFERENCES payments(id),
    matched_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ,
    resolved_by VARCHAR(128),
    resolution_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_bank_mutations_status CHECK (status IN ('UNMATCHED','MATCHED','RESOLVED')),
    CONSTRAINT chk_bank_mutations_amount CHECK (amount > 0)
);

CREATE INDEX idx_bank_mutations_status_date ON bank_mutations(status, transacted_at);
CREATE INDEX idx_bank_mutations_session ON bank_mutations(reconciliation_session_id);

CREATE TABLE connection_requests (
    id UUID PRIMARY KEY,
    request_number VARCHAR(64) NOT NULL UNIQUE,
    customer_id UUID REFERENCES customers(id),
    applicant_name VARCHAR(255) NOT NULL,
    phone_number VARCHAR(64),
    address_line TEXT NOT NULL,
    area_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    tariff_group_id UUID REFERENCES tariff_groups(id),
    survey_notes TEXT,
    decision_reason TEXT,
    requested_at TIMESTAMPTZ NOT NULL,
    decided_at TIMESTAMPTZ,
    decided_by VARCHAR(128),
    activated_connection_id UUID REFERENCES connections(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_connection_requests_status CHECK (status IN ('SUBMITTED','SURVEYED','APPROVED','REJECTED','ACTIVATED'))
);

CREATE INDEX idx_connection_requests_status_area ON connection_requests(status, area_code);
CREATE INDEX idx_connection_requests_customer ON connection_requests(customer_id);

CREATE TABLE customer_histories (
    id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES customers(id),
    change_type VARCHAR(64) NOT NULL,
    before_value TEXT,
    after_value TEXT,
    reason TEXT NOT NULL,
    changed_by VARCHAR(128) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_customer_histories_customer_changed ON customer_histories(customer_id, changed_at);

CREATE TABLE app_settings (
    id UUID PRIMARY KEY,
    setting_key VARCHAR(128) NOT NULL UNIQUE,
    setting_value TEXT NOT NULL,
    value_type VARCHAR(32) NOT NULL,
    description VARCHAR(255),
    updated_by VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_app_settings_type CHECK (value_type IN ('STRING','NUMBER','BOOLEAN','JSON'))
);

CREATE TABLE audit_chain_entries (
    id UUID PRIMARY KEY,
    sequence_no BIGSERIAL UNIQUE,
    audit_log_id UUID NOT NULL UNIQUE REFERENCES audit_logs(id),
    previous_hash VARCHAR(128),
    entry_hash VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_audit_chain_entries_hash ON audit_chain_entries(entry_hash);

INSERT INTO permissions (id, code, name)
VALUES
    ('00000000-0000-0000-0000-000000002201', 'supplier.manage', 'Manage supplier master data'),
    ('00000000-0000-0000-0000-000000002202', 'payable.record', 'Record AP payable and posting journal'),
    ('00000000-0000-0000-0000-000000002203', 'payable.settle', 'Settle AP payable and posting journal'),
    ('00000000-0000-0000-0000-000000002204', 'asset.manage', 'Register and manage fixed assets'),
    ('00000000-0000-0000-0000-000000002205', 'asset.depreciate', 'Post fixed asset depreciation'),
    ('00000000-0000-0000-0000-000000002206', 'journal.reverse', 'Post controlled journal reversal'),
    ('00000000-0000-0000-0000-000000002207', 'opening-balance.post', 'Post opening balance journal'),
    ('00000000-0000-0000-0000-000000002208', 'closing-entry.post', 'Post closing entry journal'),
    ('00000000-0000-0000-0000-000000002209', 'bank-mutation.import', 'Import bank mutation rows'),
    ('00000000-0000-0000-0000-000000002210', 'bank-mutation.reconcile', 'Run bank mutation reconciliation'),
    ('00000000-0000-0000-0000-000000002211', 'installment.manage', 'Manage receivable installment plans'),
    ('00000000-0000-0000-0000-000000002212', 'allowance.post', 'Post receivable allowance provision'),
    ('00000000-0000-0000-0000-000000002213', 'report.financial.read', 'Read financial statements and tax recap'),
    ('00000000-0000-0000-0000-000000002214', 'setting.manage', 'Manage application settings'),
    ('00000000-0000-0000-0000-000000002215', 'audit-chain.verify', 'Verify tamper-evident audit chain'),
    ('00000000-0000-0000-0000-000000002216', 'connection-request.manage', 'Manage connection requests'),
    ('00000000-0000-0000-0000-000000002217', 'customer-history.read', 'Read customer change histories')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    updated_at = now();

WITH blueprint_grants(role_code, permission_code) AS (
    VALUES
        ('super-admin', 'supplier.manage'),
        ('super-admin', 'payable.record'),
        ('super-admin', 'payable.settle'),
        ('super-admin', 'asset.manage'),
        ('super-admin', 'asset.depreciate'),
        ('super-admin', 'journal.reverse'),
        ('super-admin', 'opening-balance.post'),
        ('super-admin', 'closing-entry.post'),
        ('super-admin', 'bank-mutation.import'),
        ('super-admin', 'bank-mutation.reconcile'),
        ('super-admin', 'installment.manage'),
        ('super-admin', 'allowance.post'),
        ('super-admin', 'report.financial.read'),
        ('super-admin', 'setting.manage'),
        ('super-admin', 'audit-chain.verify'),
        ('super-admin', 'connection-request.manage'),
        ('super-admin', 'customer-history.read'),
        ('finance-staff', 'payable.record'),
        ('finance-staff', 'asset.manage'),
        ('finance-staff', 'opening-balance.post'),
        ('finance-supervisor', 'supplier.manage'),
        ('finance-supervisor', 'payable.record'),
        ('finance-supervisor', 'payable.settle'),
        ('finance-supervisor', 'asset.manage'),
        ('finance-supervisor', 'asset.depreciate'),
        ('finance-supervisor', 'journal.reverse'),
        ('finance-supervisor', 'opening-balance.post'),
        ('finance-supervisor', 'closing-entry.post'),
        ('finance-supervisor', 'bank-mutation.import'),
        ('finance-supervisor', 'bank-mutation.reconcile'),
        ('finance-supervisor', 'allowance.post'),
        ('finance-supervisor', 'report.financial.read'),
        ('auditor-internal', 'report.financial.read'),
        ('auditor-internal', 'audit-chain.verify'),
        ('auditor-internal', 'customer-history.read'),
        ('direksi-manajemen', 'report.financial.read'),
        ('petugas-piutang', 'installment.manage'),
        ('supervisor-piutang', 'installment.manage'),
        ('supervisor-piutang', 'allowance.post'),
        ('petugas-pelanggan', 'connection-request.manage'),
        ('admin-sistem', 'setting.manage')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM blueprint_grants
JOIN roles ON roles.code = blueprint_grants.role_code
JOIN permissions ON permissions.code = blueprint_grants.permission_code
ON CONFLICT DO NOTHING;

WITH invoice_read_grants(role_code, permission_code) AS (
    VALUES
        ('auditor-internal', 'invoice.view')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM invoice_read_grants
JOIN roles ON roles.code = invoice_read_grants.role_code
JOIN permissions ON permissions.code = invoice_read_grants.permission_code
ON CONFLICT DO NOTHING;

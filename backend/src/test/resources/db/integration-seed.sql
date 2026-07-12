INSERT INTO accounts (id, code, name, type) VALUES
    ('00000000-0000-0000-0000-000000000201', 'INT-1101', 'Integration accounts receivable', 'ASSET'),
    ('00000000-0000-0000-0000-000000000202', 'INT-1102', 'Integration cash', 'ASSET'),
    ('00000000-0000-0000-0000-000000000203', 'INT-4101', 'Integration water revenue', 'REVENUE'),
    ('00000000-0000-0000-0000-000000000204', 'INT-1201', 'Integration fixed asset', 'ASSET'),
    ('00000000-0000-0000-0000-000000000205', 'INT-1202', 'Integration accumulated depreciation', 'ASSET'),
    ('00000000-0000-0000-0000-000000000206', 'INT-5101', 'Integration depreciation expense', 'EXPENSE');

INSERT INTO accounting_periods (id, period, status)
VALUES ('00000000-0000-0000-0000-000000000101', '2026-07', 'OPEN');

INSERT INTO journal_entries (
    id, journal_number, accounting_period_id, description, status, source_module,
    source_record_id, source_document_number
) VALUES (
    '00000000-0000-0000-0000-000000000301', 'INT-ASSET-REG-001',
    '00000000-0000-0000-0000-000000000101', 'Integration asset registration', 'DRAFT',
    'FIXED_ASSET_REGISTRATION', '00000000-0000-0000-0000-000000000a01', 'INT-ASSET-001'
);

INSERT INTO customers (id, customer_number, full_name, status)
VALUES ('00000000-0000-0000-0000-000000000401', 'INT-CUST-001', 'Integration Customer', 'ACTIVE');

INSERT INTO tariff_groups (id, code, name)
VALUES ('00000000-0000-0000-0000-000000000501', 'INT-TARIFF', 'Integration Tariff');

INSERT INTO connections (
    id, customer_id, tariff_group_id, connection_number, meter_number, status, installed_at
) VALUES (
    '00000000-0000-0000-0000-000000000601',
    '00000000-0000-0000-0000-000000000401',
    '00000000-0000-0000-0000-000000000501',
    'INT-CONN-001', 'INT-METER-001', 'ACTIVE', '2025-01-01'
);

INSERT INTO billing_batches (id, batch_number, period, area_code, status, idempotency_key)
VALUES (
    '00000000-0000-0000-0000-000000000701', 'INT-BATCH-2026-07', '2026-07',
    'INT-AREA', 'COMPLETED', 'integration-batch-2026-07'
);

INSERT INTO invoices (
    id, billing_batch_id, connection_id, invoice_number, period, status, subtotal,
    penalty_amount, paid_amount, outstanding_amount, due_date, usage_charge,
    fixed_charge, levy_charge, admin_charge, waste_charge
) VALUES (
    '00000000-0000-0000-0000-000000000801',
    '00000000-0000-0000-0000-000000000701',
    '00000000-0000-0000-0000-000000000601',
    'INT-INV-2026-07-001', '2026-07', 'DRAFT', 100.00, 0.00, 0.00, 100.00,
    '2026-07-25', 100.00, 0.00, 0.00, 0.00, 0.00
);

INSERT INTO payment_reconciliation_sessions (
    id, session_number, status, source_filename, bank_account_reference, created_by, started_at
) VALUES (
    '00000000-0000-0000-0000-000000000901', 'INT-REC-2026-07', 'OPEN',
    'integration-bank.csv', 'INT-BANK-001', 'integration-treasury', '2026-07-10T03:00:00Z'
);

INSERT INTO fixed_assets (
    id, asset_code, name, acquisition_date, acquisition_cost, salvage_value,
    useful_life_months, depreciation_method, status, asset_account_id,
    accumulated_depreciation_account_id, depreciation_expense_account_id,
    accumulated_depreciation, registered_journal_entry_id
) VALUES (
    '00000000-0000-0000-0000-000000000a01', 'INT-ASSET-001', 'Integration Pump',
    '2025-01-01', 1200.00, 0.00, 120, 'STRAIGHT_LINE', 'ACTIVE',
    '00000000-0000-0000-0000-000000000204',
    '00000000-0000-0000-0000-000000000205',
    '00000000-0000-0000-0000-000000000206',
    0.00, '00000000-0000-0000-0000-000000000301'
);

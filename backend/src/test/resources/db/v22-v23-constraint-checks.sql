\echo Checking V22/V23 constraints and V24 performance indexes

\o /dev/null

BEGIN;

CREATE OR REPLACE FUNCTION pg_temp.expect_sqlstate(test_name TEXT, expected_sqlstate TEXT, statement TEXT)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    EXECUTE statement;
    RAISE EXCEPTION 'expected SQLSTATE % for %, but statement succeeded', expected_sqlstate, test_name;
EXCEPTION
    WHEN OTHERS THEN
        IF SQLSTATE <> expected_sqlstate THEN
            RAISE EXCEPTION 'expected SQLSTATE %, got % for %: %',
                expected_sqlstate,
                SQLSTATE,
                test_name,
                SQLERRM;
        END IF;
END;
$$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM permissions
        WHERE code IN (
            'supplier.manage',
            'payable.record',
            'asset.depreciate',
            'bank-mutation.import',
            'installment.manage',
            'audit-chain.verify',
            'connection-request.manage'
        )
        GROUP BY 1
        HAVING count(*) = 7
    ) THEN
        RAISE EXCEPTION 'V22 blueprint permission seed is incomplete';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM role_permissions
        JOIN roles ON roles.id = role_permissions.role_id
        JOIN permissions ON permissions.id = role_permissions.permission_id
        WHERE roles.code = 'super-admin'
          AND permissions.code = 'audit-chain.verify'
    ) THEN
        RAISE EXCEPTION 'super-admin audit-chain.verify grant is missing';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM role_permissions
        JOIN roles ON roles.id = role_permissions.role_id
        JOIN permissions ON permissions.id = role_permissions.permission_id
        WHERE roles.code = 'finance-supervisor'
          AND permissions.code = 'bank-mutation.reconcile'
    ) THEN
        RAISE EXCEPTION 'finance-supervisor bank-mutation.reconcile grant is missing';
    END IF;
END;
$$;

INSERT INTO accounts (id, code, name, type)
VALUES
    ('10000000-0000-0000-0000-000000000001', 'TEST-ASSET', 'Test Asset', 'ASSET'),
    ('10000000-0000-0000-0000-000000000002', 'TEST-ACCDEP', 'Test Accumulated Depreciation', 'ASSET'),
    ('10000000-0000-0000-0000-000000000003', 'TEST-DEP-EXP', 'Test Depreciation Expense', 'EXPENSE'),
    ('10000000-0000-0000-0000-000000000004', 'TEST-AP', 'Test AP', 'LIABILITY');

INSERT INTO accounting_periods (id, period, status)
VALUES ('10000000-0000-0000-0000-000000000010', '2099-01', 'OPEN');

INSERT INTO journal_entries (id, journal_number, accounting_period_id, description, status, posted_at, posted_by)
VALUES
    ('10000000-0000-0000-0000-000000000101', 'TEST-JE-001', '10000000-0000-0000-0000-000000000010', 'Seed journal 1', 'POSTED', now(), 'constraint.test'),
    ('10000000-0000-0000-0000-000000000102', 'TEST-JE-002', '10000000-0000-0000-0000-000000000010', 'Seed journal 2', 'POSTED', now(), 'constraint.test'),
    ('10000000-0000-0000-0000-000000000103', 'TEST-JE-003', '10000000-0000-0000-0000-000000000010', 'Seed journal 3', 'POSTED', now(), 'constraint.test'),
    ('10000000-0000-0000-0000-000000000104', 'TEST-JE-004', '10000000-0000-0000-0000-000000000010', 'Seed journal 4', 'POSTED', now(), 'constraint.test'),
    ('10000000-0000-0000-0000-000000000105', 'TEST-JE-005', '10000000-0000-0000-0000-000000000010', 'Seed journal 5', 'POSTED', now(), 'constraint.test'),
    ('10000000-0000-0000-0000-000000000106', 'TEST-JE-006', '10000000-0000-0000-0000-000000000010', 'Seed journal 6', 'POSTED', now(), 'constraint.test'),
    ('10000000-0000-0000-0000-000000000107', 'TEST-JE-007', '10000000-0000-0000-0000-000000000010', 'Seed journal 7', 'POSTED', now(), 'constraint.test'),
    ('10000000-0000-0000-0000-000000000108', 'TEST-JE-008', '10000000-0000-0000-0000-000000000010', 'Seed journal 8', 'POSTED', now(), 'constraint.test');

INSERT INTO suppliers (id, code, name, status)
VALUES ('20000000-0000-0000-0000-000000000001', 'SUP-TEST-001', 'Supplier Constraint Test', 'ACTIVE');

INSERT INTO payables (
    id,
    supplier_id,
    payable_number,
    period,
    status,
    amount,
    description,
    recorded_at,
    recorded_by,
    recorded_journal_entry_id
)
VALUES (
    '20000000-0000-0000-0000-000000000010',
    '20000000-0000-0000-0000-000000000001',
    'AP-TEST-001',
    '2099-01',
    'OPEN',
    100.00,
    'Constraint seed payable',
    now(),
    'constraint.test',
    '10000000-0000-0000-0000-000000000101'
);

SELECT pg_temp.expect_sqlstate(
    'payables amount must be positive',
    '23514',
    $sql$
    INSERT INTO payables (id, supplier_id, payable_number, period, status, amount, description, recorded_at, recorded_by, recorded_journal_entry_id)
    VALUES ('20000000-0000-0000-0000-000000000011', '20000000-0000-0000-0000-000000000001', 'AP-TEST-ZERO', '2099-01', 'OPEN', 0, 'Invalid payable', now(), 'constraint.test', '10000000-0000-0000-0000-000000000102')
    $sql$
);

SELECT pg_temp.expect_sqlstate(
    'payables period must use yyyy-MM',
    '23514',
    $sql$
    INSERT INTO payables (id, supplier_id, payable_number, period, status, amount, description, recorded_at, recorded_by, recorded_journal_entry_id)
    VALUES ('20000000-0000-0000-0000-000000000012', '20000000-0000-0000-0000-000000000001', 'AP-TEST-BAD-PERIOD', '209901', 'OPEN', 1, 'Invalid period', now(), 'constraint.test', '10000000-0000-0000-0000-000000000102')
    $sql$
);

SELECT pg_temp.expect_sqlstate(
    'payables recorded journal trace must be unique',
    '23505',
    $sql$
    INSERT INTO payables (id, supplier_id, payable_number, period, status, amount, description, recorded_at, recorded_by, recorded_journal_entry_id)
    VALUES ('20000000-0000-0000-0000-000000000013', '20000000-0000-0000-0000-000000000001', 'AP-TEST-DUP-JOURNAL', '2099-01', 'OPEN', 1, 'Duplicate journal', now(), 'constraint.test', '10000000-0000-0000-0000-000000000101')
    $sql$
);

INSERT INTO fixed_assets (
    id,
    asset_code,
    name,
    acquisition_date,
    acquisition_cost,
    salvage_value,
    useful_life_months,
    depreciation_method,
    status,
    asset_account_id,
    accumulated_depreciation_account_id,
    depreciation_expense_account_id,
    accumulated_depreciation,
    registered_journal_entry_id
)
VALUES (
    '30000000-0000-0000-0000-000000000001',
    'FA-TEST-001',
    'Constraint Test Asset',
    DATE '2099-01-01',
    1200.00,
    100.00,
    12,
    'STRAIGHT_LINE',
    'ACTIVE',
    '10000000-0000-0000-0000-000000000001',
    '10000000-0000-0000-0000-000000000002',
    '10000000-0000-0000-0000-000000000003',
    0.00,
    '10000000-0000-0000-0000-000000000103'
);

SELECT pg_temp.expect_sqlstate(
    'fixed asset salvage must be below acquisition cost',
    '23514',
    $sql$
    INSERT INTO fixed_assets (id, asset_code, name, acquisition_date, acquisition_cost, salvage_value, useful_life_months, depreciation_method, status, asset_account_id, accumulated_depreciation_account_id, depreciation_expense_account_id, accumulated_depreciation, registered_journal_entry_id)
    VALUES ('30000000-0000-0000-0000-000000000002', 'FA-TEST-BAD-SALVAGE', 'Invalid Asset', DATE '2099-01-01', 100.00, 100.00, 12, 'STRAIGHT_LINE', 'ACTIVE', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000003', 0.00, '10000000-0000-0000-0000-000000000104')
    $sql$
);

SELECT pg_temp.expect_sqlstate(
    'fixed asset accumulated depreciation cannot exceed acquisition cost',
    '23514',
    $sql$
    INSERT INTO fixed_assets (id, asset_code, name, acquisition_date, acquisition_cost, salvage_value, useful_life_months, depreciation_method, status, asset_account_id, accumulated_depreciation_account_id, depreciation_expense_account_id, accumulated_depreciation, registered_journal_entry_id)
    VALUES ('30000000-0000-0000-0000-000000000003', 'FA-TEST-BAD-ACCUM', 'Invalid Asset', DATE '2099-01-01', 100.00, 1.00, 12, 'STRAIGHT_LINE', 'ACTIVE', '10000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000003', 101.00, '10000000-0000-0000-0000-000000000104')
    $sql$
);

INSERT INTO fixed_asset_depreciations (id, asset_id, period, amount, journal_entry_id, posted_at, posted_by)
VALUES ('30000000-0000-0000-0000-000000000010', '30000000-0000-0000-0000-000000000001', '2099-01', 100.00, '10000000-0000-0000-0000-000000000104', now(), 'constraint.test');

SELECT pg_temp.expect_sqlstate(
    'fixed asset depreciation is unique per asset and period',
    '23505',
    $sql$
    INSERT INTO fixed_asset_depreciations (id, asset_id, period, amount, journal_entry_id, posted_at, posted_by)
    VALUES ('30000000-0000-0000-0000-000000000011', '30000000-0000-0000-0000-000000000001', '2099-01', 100.00, '10000000-0000-0000-0000-000000000105', now(), 'constraint.test')
    $sql$
);

SELECT pg_temp.expect_sqlstate(
    'fixed asset depreciation amount must be positive',
    '23514',
    $sql$
    INSERT INTO fixed_asset_depreciations (id, asset_id, period, amount, journal_entry_id, posted_at, posted_by)
    VALUES ('30000000-0000-0000-0000-000000000012', '30000000-0000-0000-0000-000000000001', '2099-02', 0.00, '10000000-0000-0000-0000-000000000105', now(), 'constraint.test')
    $sql$
);

INSERT INTO customers (id, customer_number, full_name, status)
VALUES ('40000000-0000-0000-0000-000000000001', 'CUST-CONSTRAINT-001', 'Constraint Customer', 'ACTIVE');

INSERT INTO tariff_groups (id, code, name)
VALUES ('40000000-0000-0000-0000-000000000002', 'TG-CONSTRAINT-001', 'Constraint Tariff Group');

INSERT INTO connections (id, customer_id, tariff_group_id, connection_number, meter_number, status, installed_at)
VALUES (
    '40000000-0000-0000-0000-000000000003',
    '40000000-0000-0000-0000-000000000001',
    '40000000-0000-0000-0000-000000000002',
    'SR-CONSTRAINT-001',
    'MTR-CONSTRAINT-001',
    'ACTIVE',
    DATE '2099-01-01'
);

INSERT INTO invoices (id, connection_id, invoice_number, period, status, subtotal, penalty_amount, paid_amount, outstanding_amount, due_date)
VALUES ('40000000-0000-0000-0000-000000000004', '40000000-0000-0000-0000-000000000003', 'INV-CONSTRAINT-001', '2099-01', 'ISSUED', 300.00, 0.00, 0.00, 300.00, DATE '2099-02-10');

INSERT INTO installment_plans (id, invoice_id, plan_number, status, total_amount, installment_count, created_by)
VALUES ('50000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000004', 'IP-CONSTRAINT-001', 'ACTIVE', 300.00, 3, 'constraint.test');

INSERT INTO installment_items (id, plan_id, installment_number, due_date, amount, paid_amount, status)
VALUES ('50000000-0000-0000-0000-000000000010', '50000000-0000-0000-0000-000000000001', 1, DATE '2099-02-10', 100.00, 0.00, 'OPEN');

SELECT pg_temp.expect_sqlstate(
    'installment item number is unique per plan',
    '23505',
    $sql$
    INSERT INTO installment_items (id, plan_id, installment_number, due_date, amount, paid_amount, status)
    VALUES ('50000000-0000-0000-0000-000000000011', '50000000-0000-0000-0000-000000000001', 1, DATE '2099-03-10', 100.00, 0.00, 'OPEN')
    $sql$
);

SELECT pg_temp.expect_sqlstate(
    'installment paid amount cannot exceed item amount',
    '23514',
    $sql$
    INSERT INTO installment_items (id, plan_id, installment_number, due_date, amount, paid_amount, status)
    VALUES ('50000000-0000-0000-0000-000000000012', '50000000-0000-0000-0000-000000000001', 2, DATE '2099-03-10', 100.00, 101.00, 'OPEN')
    $sql$
);

INSERT INTO bank_mutations (id, external_reference, source_filename, bank_account_reference, amount, transacted_at, status)
VALUES ('60000000-0000-0000-0000-000000000001', 'BM-CONSTRAINT-001', 'constraint.csv', 'BANK-001', 100.00, now(), 'UNMATCHED');

SELECT pg_temp.expect_sqlstate(
    'bank mutation external reference must be unique',
    '23505',
    $sql$
    INSERT INTO bank_mutations (id, external_reference, source_filename, bank_account_reference, amount, transacted_at, status)
    VALUES ('60000000-0000-0000-0000-000000000002', 'BM-CONSTRAINT-001', 'constraint.csv', 'BANK-001', 100.00, now(), 'UNMATCHED')
    $sql$
);

SELECT pg_temp.expect_sqlstate(
    'bank mutation amount must be positive',
    '23514',
    $sql$
    INSERT INTO bank_mutations (id, external_reference, source_filename, bank_account_reference, amount, transacted_at, status)
    VALUES ('60000000-0000-0000-0000-000000000003', 'BM-CONSTRAINT-ZERO', 'constraint.csv', 'BANK-001', 0.00, now(), 'UNMATCHED')
    $sql$
);

SELECT pg_temp.expect_sqlstate(
    'connection request status must be enumerated',
    '23514',
    $sql$
    INSERT INTO connection_requests (id, request_number, applicant_name, address_line, area_code, status, requested_at)
    VALUES ('70000000-0000-0000-0000-000000000001', 'CR-CONSTRAINT-BAD', 'Applicant', 'Address', 'AREA-01', 'BAD_STATUS', now())
    $sql$
);

SELECT pg_temp.expect_sqlstate(
    'app setting type must be enumerated',
    '23514',
    $sql$
    INSERT INTO app_settings (id, setting_key, setting_value, value_type, updated_by)
    VALUES ('70000000-0000-0000-0000-000000000002', 'constraint.bad.type', 'x', 'XML', 'constraint.test')
    $sql$
);

INSERT INTO audit_logs (id, actor, module, action, record_id, reason)
VALUES ('80000000-0000-0000-0000-000000000001', 'constraint.test', 'TEST', 'CREATE', 'record-1', 'constraint seed');

INSERT INTO audit_chain_entries (id, audit_log_id, previous_hash, entry_hash)
VALUES ('80000000-0000-0000-0000-000000000010', '80000000-0000-0000-0000-000000000001', NULL, 'hash-constraint-1');

SELECT pg_temp.expect_sqlstate(
    'audit chain entry is one-to-one with audit log',
    '23505',
    $sql$
    INSERT INTO audit_chain_entries (id, audit_log_id, previous_hash, entry_hash)
    VALUES ('80000000-0000-0000-0000-000000000011', '80000000-0000-0000-0000-000000000001', 'hash-constraint-1', 'hash-constraint-2')
    $sql$
);

INSERT INTO meter_routes (id, route_code, name, area_code)
VALUES ('90000000-0000-0000-0000-000000000001', 'MR-CONSTRAINT-001', 'Constraint Route', 'AREA-CONSTRAINT');

INSERT INTO meter_reading_import_batches (
    id,
    source_device_id,
    source_batch_reference,
    route_id,
    period,
    total_rows,
    imported_rows,
    skipped_rows,
    invalid_rows,
    imported_by,
    imported_at
)
VALUES (
    '90000000-0000-0000-0000-000000000010',
    'DEVICE-CONSTRAINT-001',
    'BATCH-CONSTRAINT-001',
    '90000000-0000-0000-0000-000000000001',
    '2099-01',
    3,
    1,
    1,
    1,
    'constraint.test',
    now()
);

SELECT pg_temp.expect_sqlstate(
    'meter import batch source reference must be unique',
    '23505',
    $sql$
    INSERT INTO meter_reading_import_batches (id, source_device_id, source_batch_reference, route_id, period, total_rows, imported_rows, skipped_rows, invalid_rows, imported_by, imported_at)
    VALUES ('90000000-0000-0000-0000-000000000011', 'DEVICE-CONSTRAINT-002', 'BATCH-CONSTRAINT-001', '90000000-0000-0000-0000-000000000001', '2099-01', 1, 1, 0, 0, 'constraint.test', now())
    $sql$
);

SELECT pg_temp.expect_sqlstate(
    'meter import batch counts cannot exceed total rows',
    '23514',
    $sql$
    INSERT INTO meter_reading_import_batches (id, source_device_id, source_batch_reference, route_id, period, total_rows, imported_rows, skipped_rows, invalid_rows, imported_by, imported_at)
    VALUES ('90000000-0000-0000-0000-000000000012', 'DEVICE-CONSTRAINT-003', 'BATCH-CONSTRAINT-BAD-COUNT', '90000000-0000-0000-0000-000000000001', '2099-01', 1, 1, 1, 0, 'constraint.test', now())
    $sql$
);

SELECT pg_temp.expect_sqlstate(
    'meter import batch period must use yyyy-MM',
    '23514',
    $sql$
    INSERT INTO meter_reading_import_batches (id, source_device_id, source_batch_reference, route_id, period, total_rows, imported_rows, skipped_rows, invalid_rows, imported_by, imported_at)
    VALUES ('90000000-0000-0000-0000-000000000013', 'DEVICE-CONSTRAINT-004', 'BATCH-CONSTRAINT-BAD-PERIOD', '90000000-0000-0000-0000-000000000001', '209901', 1, 1, 0, 0, 'constraint.test', now())
    $sql$
);

INSERT INTO meter_readings (
    id,
    connection_id,
    route_id,
    period,
    previous_reading,
    current_reading,
    usage_m3,
    status,
    read_at,
    anomaly_flag,
    import_batch_id,
    source_device_id,
    source_row_number,
    locked_at,
    locked_by
)
VALUES (
    '90000000-0000-0000-0000-000000000020',
    '40000000-0000-0000-0000-000000000003',
    '90000000-0000-0000-0000-000000000001',
    '2099-02',
    10.000,
    15.000,
    5.000,
    'LOCKED',
    now(),
    false,
    '90000000-0000-0000-0000-000000000010',
    'DEVICE-CONSTRAINT-001',
    1,
    now(),
    'constraint.test'
);

SELECT pg_temp.expect_sqlstate(
    'locked meter reading must carry lock trace',
    '23514',
    $sql$
    INSERT INTO meter_readings (id, connection_id, route_id, period, previous_reading, current_reading, usage_m3, status, read_at, anomaly_flag)
    VALUES ('90000000-0000-0000-0000-000000000021', '40000000-0000-0000-0000-000000000003', '90000000-0000-0000-0000-000000000001', '2099-03', 1.000, 2.000, 1.000, 'LOCKED', now(), false)
    $sql$
);

SELECT pg_temp.expect_sqlstate(
    'meter reading import row number must be positive',
    '23514',
    $sql$
    INSERT INTO meter_readings (id, connection_id, route_id, period, previous_reading, current_reading, usage_m3, status, read_at, anomaly_flag, source_row_number)
    VALUES ('90000000-0000-0000-0000-000000000022', '40000000-0000-0000-0000-000000000003', '90000000-0000-0000-0000-000000000001', '2099-04', 1.000, 2.000, 1.000, 'DRAFT', now(), false, 0)
    $sql$
);

INSERT INTO meter_reading_import_items (id, batch_id, row_number, connection_id, reading_id, status, code, message)
VALUES (
    '90000000-0000-0000-0000-000000000030',
    '90000000-0000-0000-0000-000000000010',
    1,
    '40000000-0000-0000-0000-000000000003',
    '90000000-0000-0000-0000-000000000020',
    'IMPORTED',
    'IMPORTED',
    'Imported.'
);

INSERT INTO meter_reading_import_items (id, batch_id, row_number, connection_id, reading_id, status, code, message)
VALUES (
    '90000000-0000-0000-0000-000000000031',
    '90000000-0000-0000-0000-000000000010',
    2,
    '90000000-0000-0000-0000-000000000099',
    NULL,
    'INVALID',
    'CONNECTION_NOT_FOUND',
    'Unknown offline source connection can be audited without FK failure.'
);

SELECT pg_temp.expect_sqlstate(
    'imported meter reading import item requires reading trace',
    '23514',
    $sql$
    INSERT INTO meter_reading_import_items (id, batch_id, row_number, connection_id, reading_id, status, code, message)
    VALUES ('90000000-0000-0000-0000-000000000032', '90000000-0000-0000-0000-000000000010', 3, '40000000-0000-0000-0000-000000000003', NULL, 'IMPORTED', 'IMPORTED', 'Missing reading trace')
    $sql$
);

SELECT pg_temp.expect_sqlstate(
    'non-imported meter reading import item cannot reference reading',
    '23514',
    $sql$
    INSERT INTO meter_reading_import_items (id, batch_id, row_number, connection_id, reading_id, status, code, message)
    VALUES ('90000000-0000-0000-0000-000000000033', '90000000-0000-0000-0000-000000000010', 3, '40000000-0000-0000-0000-000000000003', '90000000-0000-0000-0000-000000000020', 'SKIPPED', 'DUPLICATE', 'Skipped should not reference reading')
    $sql$
);

SELECT pg_temp.expect_sqlstate(
    'meter import item row is unique per batch',
    '23505',
    $sql$
    INSERT INTO meter_reading_import_items (id, batch_id, row_number, connection_id, reading_id, status, code, message)
    VALUES ('90000000-0000-0000-0000-000000000034', '90000000-0000-0000-0000-000000000010', 1, '40000000-0000-0000-0000-000000000003', NULL, 'SKIPPED', 'DUPLICATE', 'Duplicate row')
    $sql$
);

DO $$
DECLARE
    index_name TEXT;
BEGIN
    IF to_regclass('public.idx_meter_readings_locked_period') IS NULL THEN
        RAISE EXCEPTION 'V23 locked reading index is missing';
    END IF;

    IF to_regclass('public.idx_meter_import_items_batch_status') IS NULL THEN
        RAISE EXCEPTION 'V23 import item batch/status index is missing';
    END IF;

    FOREACH index_name IN ARRAY ARRAY[
        'idx_suppliers_status_code',
        'idx_payables_period_status_recorded',
        'idx_payables_status_recorded',
        'idx_fixed_assets_status_code',
        'idx_installment_plans_status_created',
        'idx_billing_batches_period_status_created',
        'idx_invoices_period_status_created',
        'idx_invoices_open_receivables',
        'idx_payments_channel_paid_at',
        'idx_payments_status_channel_paid_at',
        'idx_connection_requests_status_requested',
        'idx_customers_status_number',
        'idx_connections_status_number',
        'idx_connections_customer_status_number',
        'idx_meter_routes_area_code',
        'idx_meter_readings_route_period_status_read',
        'idx_meter_readings_route_status_read',
        'idx_meter_readings_period_status_read',
        'idx_tariff_versions_group_status_effective',
        'idx_tariff_versions_status_effective',
        'idx_ledger_entries_posting_date_account',
        'idx_reconciliation_sessions_completed_review',
        'idx_handoff_notes_active_due_status_updated'
    ]::TEXT[] LOOP
        IF to_regclass('public.' || index_name) IS NULL THEN
            RAISE EXCEPTION 'V24 performance index is missing: %', index_name;
        END IF;
    END LOOP;
END;
$$;

ROLLBACK;

\o

\echo V22/V23 constraints and V24 performance indexes passed

INSERT INTO permissions (id, code, name)
VALUES
    ('00000000-0000-0000-0000-000000002501', 'customer.read', 'Read customers'),
    ('00000000-0000-0000-0000-000000002502', 'customer.manage', 'Manage customers'),
    ('00000000-0000-0000-0000-000000002503', 'connection.read', 'Read connections'),
    ('00000000-0000-0000-0000-000000002504', 'connection.manage', 'Manage connections'),
    ('00000000-0000-0000-0000-000000002505', 'meter-route.read', 'Read meter routes'),
    ('00000000-0000-0000-0000-000000002506', 'meter-route.manage', 'Manage meter routes'),
    ('00000000-0000-0000-0000-000000002507', 'meter-reading.read', 'Read meter readings'),
    ('00000000-0000-0000-0000-000000002508', 'meter-reading.create', 'Create and import meter readings'),
    ('00000000-0000-0000-0000-000000002509', 'meter-reading.verify', 'Submit, verify, and reject meter readings'),
    ('00000000-0000-0000-0000-000000002510', 'meter-reading.lock', 'Lock verified meter readings'),
    ('00000000-0000-0000-0000-000000002511', 'tariff.read', 'Read tariffs'),
    ('00000000-0000-0000-0000-000000002512', 'tariff.manage', 'Manage tariffs'),
    ('00000000-0000-0000-0000-000000002513', 'tariff.calculate', 'Calculate tariffs'),
    ('00000000-0000-0000-0000-000000002514', 'receivable-aging.read', 'Read receivable aging'),
    ('00000000-0000-0000-0000-000000002515', 'receivable-aging.generate', 'Generate receivable aging')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    updated_at = now();

WITH operational_permission_grants(role_code, permission_code) AS (
    VALUES
        ('super-admin', 'customer.read'),
        ('super-admin', 'customer.manage'),
        ('super-admin', 'connection.read'),
        ('super-admin', 'connection.manage'),
        ('super-admin', 'meter-route.read'),
        ('super-admin', 'meter-route.manage'),
        ('super-admin', 'meter-reading.read'),
        ('super-admin', 'meter-reading.create'),
        ('super-admin', 'meter-reading.verify'),
        ('super-admin', 'meter-reading.lock'),
        ('super-admin', 'tariff.read'),
        ('super-admin', 'tariff.manage'),
        ('super-admin', 'tariff.calculate'),
        ('super-admin', 'receivable-aging.read'),
        ('super-admin', 'receivable-aging.generate'),
        ('auditor-internal', 'customer.read'),
        ('auditor-internal', 'connection.read'),
        ('auditor-internal', 'meter-route.read'),
        ('auditor-internal', 'meter-reading.read'),
        ('auditor-internal', 'tariff.read'),
        ('auditor-internal', 'receivable-aging.read'),
        ('direksi-manajemen', 'customer.read'),
        ('direksi-manajemen', 'connection.read'),
        ('direksi-manajemen', 'meter-route.read'),
        ('direksi-manajemen', 'meter-reading.read'),
        ('direksi-manajemen', 'tariff.read'),
        ('direksi-manajemen', 'receivable-aging.read'),
        ('petugas-pelanggan', 'customer.read'),
        ('petugas-pelanggan', 'customer.manage'),
        ('petugas-pelanggan', 'connection.read'),
        ('petugas-pelanggan', 'connection.manage'),
        ('petugas-pelanggan', 'tariff.read'),
        ('petugas-meter', 'meter-route.read'),
        ('petugas-meter', 'meter-reading.read'),
        ('petugas-meter', 'meter-reading.create'),
        ('petugas-meter', 'meter-reading.verify'),
        ('supervisor-meter', 'meter-route.read'),
        ('supervisor-meter', 'meter-route.manage'),
        ('supervisor-meter', 'meter-reading.read'),
        ('supervisor-meter', 'meter-reading.verify'),
        ('supervisor-meter', 'meter-reading.lock'),
        ('billing-officer', 'tariff.read'),
        ('billing-officer', 'tariff.calculate'),
        ('billing-supervisor', 'tariff.read'),
        ('billing-supervisor', 'tariff.manage'),
        ('billing-supervisor', 'tariff.calculate'),
        ('petugas-piutang', 'receivable-aging.read'),
        ('petugas-piutang', 'receivable-aging.generate'),
        ('supervisor-piutang', 'receivable-aging.read'),
        ('supervisor-piutang', 'receivable-aging.generate')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM operational_permission_grants
JOIN roles ON roles.code = operational_permission_grants.role_code
JOIN permissions ON permissions.code = operational_permission_grants.permission_code
ON CONFLICT DO NOTHING;

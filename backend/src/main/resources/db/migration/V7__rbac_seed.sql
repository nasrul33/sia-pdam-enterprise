INSERT INTO roles (id, code, name)
VALUES
    ('00000000-0000-0000-0000-000000000701', 'super-admin', 'Super Admin'),
    ('00000000-0000-0000-0000-000000000702', 'admin-sistem', 'Admin Sistem'),
    ('00000000-0000-0000-0000-000000000703', 'petugas-pelanggan', 'Petugas Pelanggan'),
    ('00000000-0000-0000-0000-000000000704', 'petugas-meter', 'Petugas Meter'),
    ('00000000-0000-0000-0000-000000000705', 'supervisor-meter', 'Supervisor Meter'),
    ('00000000-0000-0000-0000-000000000706', 'billing-officer', 'Billing Officer'),
    ('00000000-0000-0000-0000-000000000707', 'billing-supervisor', 'Billing Supervisor'),
    ('00000000-0000-0000-0000-000000000708', 'kasir', 'Kasir'),
    ('00000000-0000-0000-0000-000000000709', 'finance-staff', 'Finance Staff'),
    ('00000000-0000-0000-0000-000000000710', 'finance-supervisor', 'Finance Supervisor'),
    ('00000000-0000-0000-0000-000000000711', 'auditor-internal', 'Auditor Internal'),
    ('00000000-0000-0000-0000-000000000712', 'direksi-manajemen', 'Direksi/Manajemen'),
    ('00000000-0000-0000-0000-000000000713', 'petugas-piutang', 'Petugas Piutang'),
    ('00000000-0000-0000-0000-000000000714', 'supervisor-piutang', 'Supervisor Piutang')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    updated_at = now();

INSERT INTO permissions (id, code, name)
VALUES
    ('00000000-0000-0000-0000-000000000721', 'collection-action.read', 'Read collection actions'),
    ('00000000-0000-0000-0000-000000000722', 'collection-action.create', 'Create collection actions'),
    ('00000000-0000-0000-0000-000000000723', 'collection-action.execute', 'Start and complete collection actions'),
    ('00000000-0000-0000-0000-000000000724', 'collection-action.cancel', 'Cancel collection actions')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    updated_at = now();

WITH collection_action_grants(role_code, permission_code) AS (
    VALUES
        ('super-admin', 'collection-action.read'),
        ('super-admin', 'collection-action.create'),
        ('super-admin', 'collection-action.execute'),
        ('super-admin', 'collection-action.cancel'),
        ('petugas-piutang', 'collection-action.read'),
        ('petugas-piutang', 'collection-action.create'),
        ('petugas-piutang', 'collection-action.execute'),
        ('supervisor-piutang', 'collection-action.read'),
        ('supervisor-piutang', 'collection-action.create'),
        ('supervisor-piutang', 'collection-action.cancel'),
        ('auditor-internal', 'collection-action.read')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM collection_action_grants
JOIN roles ON roles.code = collection_action_grants.role_code
JOIN permissions ON permissions.code = collection_action_grants.permission_code
ON CONFLICT DO NOTHING;

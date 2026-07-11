INSERT INTO permissions (id, code, name)
VALUES
    ('00000000-0000-0000-0000-000000002701', 'user.read', 'Read users and role catalog'),
    ('00000000-0000-0000-0000-000000002702', 'user.manage', 'Enable and disable user accounts'),
    ('00000000-0000-0000-0000-000000002703', 'role.manage', 'Replace user role assignments')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    updated_at = now();

WITH admin_grants(role_code, permission_code) AS (
    VALUES
        ('super-admin', 'user.read'),
        ('super-admin', 'user.manage'),
        ('super-admin', 'role.manage'),
        ('admin-sistem', 'user.read'),
        ('admin-sistem', 'user.manage'),
        ('admin-sistem', 'role.manage'),
        ('auditor-internal', 'user.read')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM admin_grants
JOIN roles ON roles.code = admin_grants.role_code
JOIN permissions ON permissions.code = admin_grants.permission_code
ON CONFLICT DO NOTHING;

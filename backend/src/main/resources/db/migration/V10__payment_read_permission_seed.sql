INSERT INTO permissions (id, code, name)
VALUES ('00000000-0000-0000-0000-000000001001', 'payment.read', 'Read payment settlement records')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    updated_at = now();

WITH payment_read_grants(role_code, permission_code) AS (
    VALUES
        ('super-admin', 'payment.read'),
        ('finance-supervisor', 'payment.read'),
        ('auditor-internal', 'payment.read')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM payment_read_grants
JOIN roles ON roles.code = payment_read_grants.role_code
JOIN permissions ON permissions.code = payment_read_grants.permission_code
ON CONFLICT DO NOTHING;

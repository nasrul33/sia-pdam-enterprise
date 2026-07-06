INSERT INTO permissions (id, code, name)
VALUES
    ('00000000-0000-0000-0000-000000000801', 'payment.counter', 'Settle counter payments'),
    ('00000000-0000-0000-0000-000000000802', 'payment.reverse', 'Reverse settled payments'),
    ('00000000-0000-0000-0000-000000000803', 'payment.webhook.read', 'Read payment webhook events')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    updated_at = now();

WITH payment_grants(role_code, permission_code) AS (
    VALUES
        ('super-admin', 'payment.counter'),
        ('super-admin', 'payment.reverse'),
        ('super-admin', 'payment.webhook.read'),
        ('kasir', 'payment.counter'),
        ('finance-supervisor', 'payment.reverse'),
        ('finance-supervisor', 'payment.webhook.read'),
        ('auditor-internal', 'payment.webhook.read')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM payment_grants
JOIN roles ON roles.code = payment_grants.role_code
JOIN permissions ON permissions.code = payment_grants.permission_code
ON CONFLICT DO NOTHING;

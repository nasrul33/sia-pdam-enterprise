INSERT INTO permissions (id, code, name)
VALUES ('00000000-0000-0000-0000-000000001101', 'payment.reconcile', 'Export and match payment reconciliation data')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    updated_at = now();

WITH payment_reconciliation_grants(role_code, permission_code) AS (
    VALUES
        ('super-admin', 'payment.reconcile'),
        ('finance-supervisor', 'payment.reconcile'),
        ('auditor-internal', 'payment.reconcile')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM payment_reconciliation_grants
JOIN roles ON roles.code = payment_reconciliation_grants.role_code
JOIN permissions ON permissions.code = payment_reconciliation_grants.permission_code
ON CONFLICT DO NOTHING;

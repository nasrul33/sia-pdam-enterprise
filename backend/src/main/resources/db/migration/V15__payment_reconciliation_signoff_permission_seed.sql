INSERT INTO permissions (id, code, name)
VALUES ('00000000-0000-0000-0000-000000001501', 'payment.reconciliation.signoff', 'Sign off completed bank reconciliation evidence')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    updated_at = now();

WITH payment_reconciliation_signoff_grants(role_code, permission_code) AS (
    VALUES
        ('super-admin', 'payment.reconciliation.signoff'),
        ('finance-supervisor', 'payment.reconciliation.signoff')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM payment_reconciliation_signoff_grants
JOIN roles ON roles.code = payment_reconciliation_signoff_grants.role_code
JOIN permissions ON permissions.code = payment_reconciliation_signoff_grants.permission_code
ON CONFLICT DO NOTHING;

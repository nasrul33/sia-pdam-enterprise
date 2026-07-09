INSERT INTO permissions (id, code, name)
VALUES (
    '00000000-0000-0000-0000-000000001901',
    'payment.reconciliation.stale-acknowledge',
    'Acknowledge stale payment reconciliation handoff evidence packets'
)
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    updated_at = now();

WITH payment_reconciliation_stale_acknowledgement_grants(role_code, permission_code) AS (
    VALUES
        ('super-admin', 'payment.reconciliation.stale-acknowledge'),
        ('finance-supervisor', 'payment.reconciliation.stale-acknowledge')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM payment_reconciliation_stale_acknowledgement_grants
JOIN roles ON roles.code = payment_reconciliation_stale_acknowledgement_grants.role_code
JOIN permissions ON permissions.code = payment_reconciliation_stale_acknowledgement_grants.permission_code
ON CONFLICT DO NOTHING;

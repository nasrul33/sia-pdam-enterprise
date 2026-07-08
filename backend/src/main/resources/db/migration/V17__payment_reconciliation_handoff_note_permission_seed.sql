INSERT INTO permissions (id, code, name)
VALUES ('00000000-0000-0000-0000-000000001701', 'payment.reconciliation.handoff-note', 'Create and revise payment reconciliation handoff notes')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    updated_at = now();

WITH payment_reconciliation_handoff_note_grants(role_code, permission_code) AS (
    VALUES
        ('super-admin', 'payment.reconciliation.handoff-note'),
        ('finance-supervisor', 'payment.reconciliation.handoff-note'),
        ('auditor-internal', 'payment.reconciliation.handoff-note')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM payment_reconciliation_handoff_note_grants
JOIN roles ON roles.code = payment_reconciliation_handoff_note_grants.role_code
JOIN permissions ON permissions.code = payment_reconciliation_handoff_note_grants.permission_code
ON CONFLICT DO NOTHING;

INSERT INTO permissions (id, code, name)
VALUES
    ('00000000-0000-0000-0000-000000000908', 'invoice.view', 'View invoice documents and billing invoice detail'),
    ('00000000-0000-0000-0000-000000000909', 'invoice.correct.approve', 'Approve invoice correction or void with reversal journal')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    updated_at = now();

WITH invoice_control_grants(role_code, permission_code) AS (
    VALUES
        ('super-admin', 'invoice.view'),
        ('super-admin', 'invoice.correct.approve'),
        ('finance-supervisor', 'invoice.view'),
        ('finance-supervisor', 'invoice.correct.approve'),
        ('billing-officer', 'invoice.view'),
        ('billing-supervisor', 'invoice.view'),
        ('billing-supervisor', 'invoice.correct.approve'),
        ('auditor', 'invoice.view')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM invoice_control_grants
JOIN roles ON roles.code = invoice_control_grants.role_code
JOIN permissions ON permissions.code = invoice_control_grants.permission_code
ON CONFLICT DO NOTHING;

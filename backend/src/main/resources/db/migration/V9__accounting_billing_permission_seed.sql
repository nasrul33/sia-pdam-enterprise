INSERT INTO permissions (id, code, name)
VALUES
    ('00000000-0000-0000-0000-000000000901', 'account.manage', 'Manage chart of accounts'),
    ('00000000-0000-0000-0000-000000000902', 'period.manage', 'Manage accounting periods'),
    ('00000000-0000-0000-0000-000000000903', 'period.close', 'Start closing review and lock accounting periods'),
    ('00000000-0000-0000-0000-000000000904', 'journal.create', 'Create manual journals'),
    ('00000000-0000-0000-0000-000000000905', 'journal.post', 'Post journals to ledger'),
    ('00000000-0000-0000-0000-000000000906', 'billing.generate', 'Generate billing batches'),
    ('00000000-0000-0000-0000-000000000907', 'invoice.issue', 'Issue invoices and post receivable revenue journals')
ON CONFLICT (code) DO UPDATE
SET name = EXCLUDED.name,
    updated_at = now();

WITH accounting_billing_grants(role_code, permission_code) AS (
    VALUES
        ('super-admin', 'account.manage'),
        ('super-admin', 'period.manage'),
        ('super-admin', 'period.close'),
        ('super-admin', 'journal.create'),
        ('super-admin', 'journal.post'),
        ('super-admin', 'billing.generate'),
        ('super-admin', 'invoice.issue'),
        ('finance-staff', 'journal.create'),
        ('finance-supervisor', 'account.manage'),
        ('finance-supervisor', 'period.manage'),
        ('finance-supervisor', 'period.close'),
        ('finance-supervisor', 'journal.create'),
        ('finance-supervisor', 'journal.post'),
        ('billing-officer', 'billing.generate'),
        ('billing-supervisor', 'billing.generate'),
        ('billing-supervisor', 'invoice.issue')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT roles.id, permissions.id
FROM accounting_billing_grants
JOIN roles ON roles.code = accounting_billing_grants.role_code
JOIN permissions ON permissions.code = accounting_billing_grants.permission_code
ON CONFLICT DO NOTHING;

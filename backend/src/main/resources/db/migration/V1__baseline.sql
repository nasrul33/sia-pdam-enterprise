CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor VARCHAR(128) NOT NULL,
    module VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    record_id VARCHAR(128),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_module_record ON audit_logs(module, record_id);
CREATE INDEX idx_audit_logs_actor_created ON audit_logs(actor, created_at);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(128) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(128) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles(id),
    permission_id UUID NOT NULL REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id),
    role_id UUID NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_accounts_type CHECK (type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE'))
);

CREATE TABLE accounting_periods (
    id UUID PRIMARY KEY,
    period VARCHAR(7) NOT NULL UNIQUE,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_accounting_periods_status CHECK (status IN ('OPEN','CLOSING_REVIEW','LOCKED','REOPENED')),
    CONSTRAINT chk_accounting_periods_format CHECK (period ~ '^\d{4}-\d{2}$')
);

CREATE TABLE journal_entries (
    id UUID PRIMARY KEY,
    journal_number VARCHAR(64) NOT NULL UNIQUE,
    accounting_period_id UUID NOT NULL REFERENCES accounting_periods(id),
    description VARCHAR(255) NOT NULL,
    status VARCHAR(32) NOT NULL,
    posted_at TIMESTAMPTZ,
    posted_by VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_journal_entries_status CHECK (status IN ('DRAFT','POSTED','REVERSED','VOID'))
);

CREATE INDEX idx_journal_entries_period_status ON journal_entries(accounting_period_id, status);

CREATE TABLE journal_lines (
    id UUID PRIMARY KEY,
    journal_entry_id UUID NOT NULL REFERENCES journal_entries(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    debit NUMERIC(19,2) NOT NULL DEFAULT 0,
    credit NUMERIC(19,2) NOT NULL DEFAULT 0,
    description VARCHAR(255) NOT NULL DEFAULT '',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_journal_lines_non_negative CHECK (debit >= 0 AND credit >= 0),
    CONSTRAINT chk_journal_lines_single_side CHECK ((debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0))
);

CREATE INDEX idx_journal_lines_entry ON journal_lines(journal_entry_id);
CREATE INDEX idx_journal_lines_account ON journal_lines(account_id);

CREATE OR REPLACE FUNCTION prevent_posted_journal_mutation()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = 'POSTED' THEN
        RAISE EXCEPTION 'posted journal entry is immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_posted_journal_update
BEFORE UPDATE ON journal_entries
FOR EACH ROW
WHEN (OLD.status = 'POSTED')
EXECUTE FUNCTION prevent_posted_journal_mutation();

CREATE OR REPLACE FUNCTION prevent_posted_journal_delete()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.status = 'POSTED' THEN
        RAISE EXCEPTION 'posted journal entry cannot be deleted';
    END IF;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_prevent_posted_journal_delete
BEFORE DELETE ON journal_entries
FOR EACH ROW
EXECUTE FUNCTION prevent_posted_journal_delete();

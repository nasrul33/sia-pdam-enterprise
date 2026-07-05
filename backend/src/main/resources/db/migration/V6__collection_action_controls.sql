ALTER TABLE collection_actions
    ADD CONSTRAINT chk_collection_actions_action_type
        CHECK (action_type IN ('REMINDER','WARNING_LETTER','DISCONNECTION_NOTICE','FIELD_VISIT','PHONE_CALL','PAYMENT_PROMISE')),
    ADD CONSTRAINT chk_collection_actions_completed_state
        CHECK (
            (status = 'COMPLETED' AND completed_at IS NOT NULL)
            OR (status <> 'COMPLETED' AND completed_at IS NULL)
        );

CREATE UNIQUE INDEX uq_collection_actions_active_invoice_type
    ON collection_actions(invoice_id, action_type)
    WHERE invoice_id IS NOT NULL AND status IN ('OPEN','IN_PROGRESS');

CREATE UNIQUE INDEX uq_collection_actions_active_customer_type_without_invoice
    ON collection_actions(customer_id, action_type)
    WHERE invoice_id IS NULL AND status IN ('OPEN','IN_PROGRESS');

CREATE INDEX idx_collection_actions_status_scheduled
    ON collection_actions(status, scheduled_at);

ALTER TABLE tariff_versions
    ADD COLUMN fixed_charge NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN levy_charge NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN admin_charge NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN waste_charge NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN penalty_rate NUMERIC(9, 6) NOT NULL DEFAULT 0;

ALTER TABLE tariff_versions
    ADD CONSTRAINT ck_tariff_versions_non_negative_components
        CHECK (fixed_charge >= 0 AND levy_charge >= 0 AND admin_charge >= 0 AND waste_charge >= 0),
    ADD CONSTRAINT ck_tariff_versions_penalty_rate
        CHECK (penalty_rate >= 0 AND penalty_rate <= 1);

ALTER TABLE invoices
    ADD COLUMN usage_charge NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN fixed_charge NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN levy_charge NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN admin_charge NUMERIC(19, 2) NOT NULL DEFAULT 0,
    ADD COLUMN waste_charge NUMERIC(19, 2) NOT NULL DEFAULT 0;

UPDATE invoices
SET usage_charge = subtotal;

ALTER TABLE invoices
    ADD CONSTRAINT ck_invoices_non_negative_components
        CHECK (usage_charge >= 0 AND fixed_charge >= 0 AND levy_charge >= 0 AND admin_charge >= 0 AND waste_charge >= 0);

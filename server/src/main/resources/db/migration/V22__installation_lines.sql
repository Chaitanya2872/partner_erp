-- Installation section of estimates and invoices: priced lines (description, quantity, unit, rate, GST) kept apart
-- from the product lines. Installation lines have no product. Existing lines are product lines.
-- Applied migrations are not modified. The issued-invoice line guard compares whole rows, so it needs no change.

ALTER TABLE estimate_items
    ADD COLUMN kind varchar(20) NOT NULL DEFAULT 'PRODUCT',
    ADD CONSTRAINT ck_ei_kind CHECK (kind IN ('PRODUCT', 'INSTALLATION')),
    ADD CONSTRAINT ck_ei_kind_product CHECK (kind = 'PRODUCT' OR product_id IS NULL);

ALTER TABLE invoice_items
    ADD COLUMN kind varchar(20) NOT NULL DEFAULT 'PRODUCT',
    ADD CONSTRAINT ck_ii_kind CHECK (kind IN ('PRODUCT', 'INSTALLATION')),
    ADD CONSTRAINT ck_ii_kind_product CHECK (kind = 'PRODUCT' OR product_id IS NULL);

ALTER TABLE b2c_document_items
    ADD COLUMN kind varchar(20) NOT NULL DEFAULT 'PRODUCT',
    ADD CONSTRAINT ck_b2ci_kind CHECK (kind IN ('PRODUCT', 'INSTALLATION')),
    ADD CONSTRAINT ck_b2ci_kind_product CHECK (kind = 'PRODUCT' OR product_id IS NULL);

COMMENT ON COLUMN estimate_items.kind IS 'PRODUCT (price-sheet line) or INSTALLATION (free-text priced service line)';

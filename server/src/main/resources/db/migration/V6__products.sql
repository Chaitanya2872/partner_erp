-- Item & price sheet (source collection "items").
-- b2c_price / cost_price NULL means "not set" (the source shows "B2C price needed"); never fabricate them.

CREATE TABLE products (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sku           varchar(50)    NOT NULL,
    category      varchar(40)    NOT NULL CONSTRAINT ck_products_category CHECK (category IN (
                      'SMART_TOUCH_INTERFACES', 'SMART_RELAY_SENSING', 'SMART_GLIDE', 'LOCKS_ENTRY', 'SERVICES')),
    unit          varchar(20)    NOT NULL DEFAULT 'nos',
    hsn           varchar(20),
    b2c_name      varchar(300)   NOT NULL,
    partner_name  varchar(300)   NOT NULL,
    aliases       text[]         NOT NULL DEFAULT '{}',
    b2c_price     numeric(14, 2) CONSTRAINT ck_products_b2c_price CHECK (b2c_price >= 0),
    partner_price numeric(14, 2) NOT NULL CONSTRAINT ck_products_partner_price CHECK (partner_price >= 0),
    gst_percent   numeric(6, 3)  NOT NULL DEFAULT 18 CONSTRAINT ck_products_gst CHECK (gst_percent BETWEEN 0 AND 100),
    cost_price    numeric(14, 2) CONSTRAINT ck_products_cost CHECK (cost_price >= 0),
    active        boolean        NOT NULL DEFAULT true,   -- from ER; deactivate-vs-delete pending D-17
    created_at    timestamptz    NOT NULL DEFAULT now(),
    updated_at    timestamptz    NOT NULL DEFAULT now(),
    created_by    uuid REFERENCES users (id),
    updated_by    uuid REFERENCES users (id),
    version       bigint         NOT NULL DEFAULT 0
);
-- SKU is unique case-insensitively (source itemForm / importItems).
CREATE UNIQUE INDEX ux_products_sku ON products (lower(sku));
CREATE INDEX ix_products_category ON products (category, sku);
CREATE TRIGGER trg_products_updated_at BEFORE UPDATE ON products FOR EACH ROW EXECUTE FUNCTION set_updated_at();

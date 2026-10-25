-- Estimates, invoices, their line items, the internal B2C twins, and document numbering.
-- Line items are financial snapshots (ADR-10): product edits never change saved documents.
-- lead_id is nullable with ON DELETE SET NULL to keep the source's lead-delete behaviour
-- ("linked estimates and invoices stay") until D-26 is decided; the API still requires a lead on create.

CREATE TABLE document_sequences (
    prefix     varchar(10) NOT NULL,
    fy_tag     varchar(4)  NOT NULL,
    last_value integer     NOT NULL DEFAULT 0 CONSTRAINT ck_docseq_value CHECK (last_value >= 0),
    PRIMARY KEY (prefix, fy_tag)
);

CREATE TABLE estimates (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    estimate_number  varchar(30)    NOT NULL CONSTRAINT ux_estimates_number UNIQUE,
    partner_id       uuid           NOT NULL REFERENCES partners (id),
    lead_id          uuid REFERENCES leads (id) ON DELETE SET NULL,
    estimate_date    date           NOT NULL,
    valid_until      date,
    status           varchar(20)    NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT ck_estimates_status CHECK (status IN ('DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'INVOICED')),
    sent_on          date,
    discount_percent numeric(6, 3)  NOT NULL DEFAULT 0 CONSTRAINT ck_estimates_discount CHECK (discount_percent BETWEEN 0 AND 100),
    igst             boolean        NOT NULL DEFAULT false,
    bill_to          varchar(10)    NOT NULL DEFAULT 'CLIENT' CONSTRAINT ck_estimates_bill_to CHECK (bill_to IN ('CLIENT', 'PARTNER')),
    fee_percent      numeric(6, 3)  NOT NULL DEFAULT 0 CONSTRAINT ck_estimates_fee CHECK (fee_percent BETWEEN 0 AND 100),
    notes            text,
    subtotal         numeric(14, 2) NOT NULL DEFAULT 0,
    discount_amount  numeric(14, 2) NOT NULL DEFAULT 0,
    taxable_amount   numeric(14, 2) NOT NULL DEFAULT 0,
    gst_amount       numeric(14, 2) NOT NULL DEFAULT 0,
    grand_total      numeric(14, 2) NOT NULL DEFAULT 0,
    created_at       timestamptz    NOT NULL DEFAULT now(),
    updated_at       timestamptz    NOT NULL DEFAULT now(),
    created_by       uuid REFERENCES users (id),
    updated_by       uuid REFERENCES users (id),
    version          bigint         NOT NULL DEFAULT 0
);
CREATE INDEX ix_estimates_lead_date ON estimates (lead_id, estimate_date);
CREATE INDEX ix_estimates_partner ON estimates (partner_id);
CREATE INDEX ix_estimates_status ON estimates (status, valid_until);
CREATE TRIGGER trg_estimates_updated_at BEFORE UPDATE ON estimates FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE estimate_items (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    estimate_id    uuid           NOT NULL REFERENCES estimates (id) ON DELETE CASCADE,
    line_no        integer        NOT NULL CONSTRAINT ck_ei_line CHECK (line_no > 0),
    product_id     uuid REFERENCES products (id) ON DELETE SET NULL,
    description    varchar(300)   NOT NULL,   -- partner-facing name snapshot
    b2c_name       varchar(300),              -- internal name snapshot
    hsn            varchar(20),
    unit           varchar(20)    NOT NULL DEFAULT 'nos',
    quantity       numeric(12, 3) NOT NULL CONSTRAINT ck_ei_qty CHECK (quantity >= 0),
    unit_price     numeric(14, 2) NOT NULL CONSTRAINT ck_ei_price CHECK (unit_price >= 0),
    b2c_unit_price numeric(14, 2) NOT NULL DEFAULT 0 CONSTRAINT ck_ei_b2c_price CHECK (b2c_unit_price >= 0),
    gst_percent    numeric(6, 3)  NOT NULL CONSTRAINT ck_ei_gst CHECK (gst_percent BETWEEN 0 AND 100),
    line_total     numeric(14, 2) NOT NULL,   -- quantity x unit_price, before discount (as displayed)
    CONSTRAINT ux_estimate_items_line UNIQUE (estimate_id, line_no)
);
CREATE INDEX ix_ei_product ON estimate_items (product_id);

CREATE TABLE invoices (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_number   varchar(30)    NOT NULL CONSTRAINT ux_invoices_number UNIQUE,
    partner_id       uuid           NOT NULL REFERENCES partners (id),
    lead_id          uuid REFERENCES leads (id) ON DELETE SET NULL,
    estimate_id      uuid REFERENCES estimates (id) ON DELETE SET NULL,
    invoice_date     date           NOT NULL,
    due_date         date,
    status           varchar(20)    NOT NULL DEFAULT 'OPEN' CONSTRAINT ck_invoices_status CHECK (status IN ('OPEN', 'CANCELLED')),
    cancelled_at     timestamptz,
    cancelled_by     uuid REFERENCES users (id),
    discount_percent numeric(6, 3)  NOT NULL DEFAULT 0 CONSTRAINT ck_invoices_discount CHECK (discount_percent BETWEEN 0 AND 100),
    igst             boolean        NOT NULL DEFAULT false,
    bill_to          varchar(10)    NOT NULL DEFAULT 'CLIENT' CONSTRAINT ck_invoices_bill_to CHECK (bill_to IN ('CLIENT', 'PARTNER')),
    fee_percent      numeric(6, 3)  NOT NULL DEFAULT 0 CONSTRAINT ck_invoices_fee CHECK (fee_percent BETWEEN 0 AND 100),
    notes            text,
    subtotal         numeric(14, 2) NOT NULL DEFAULT 0,
    discount_amount  numeric(14, 2) NOT NULL DEFAULT 0,
    taxable_amount   numeric(14, 2) NOT NULL DEFAULT 0,
    gst_amount       numeric(14, 2) NOT NULL DEFAULT 0,
    grand_total      numeric(14, 2) NOT NULL DEFAULT 0,
    created_at       timestamptz    NOT NULL DEFAULT now(),
    updated_at       timestamptz    NOT NULL DEFAULT now(),
    created_by       uuid REFERENCES users (id),
    updated_by       uuid REFERENCES users (id),
    version          bigint         NOT NULL DEFAULT 0
);
CREATE INDEX ix_invoices_partner ON invoices (partner_id);
CREATE INDEX ix_invoices_lead ON invoices (lead_id);
CREATE INDEX ix_invoices_estimate ON invoices (estimate_id);
CREATE INDEX ix_invoices_status_due ON invoices (status, due_date);
CREATE INDEX ix_invoices_date ON invoices (invoice_date);
CREATE TRIGGER trg_invoices_updated_at BEFORE UPDATE ON invoices FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE invoice_items (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id     uuid           NOT NULL REFERENCES invoices (id) ON DELETE CASCADE,
    line_no        integer        NOT NULL CONSTRAINT ck_ii_line CHECK (line_no > 0),
    product_id     uuid REFERENCES products (id) ON DELETE SET NULL,
    description    varchar(300)   NOT NULL,
    b2c_name       varchar(300),
    hsn            varchar(20),
    unit           varchar(20)    NOT NULL DEFAULT 'nos',
    quantity       numeric(12, 3) NOT NULL CONSTRAINT ck_ii_qty CHECK (quantity >= 0),
    unit_price     numeric(14, 2) NOT NULL CONSTRAINT ck_ii_price CHECK (unit_price >= 0),
    b2c_unit_price numeric(14, 2) NOT NULL DEFAULT 0 CONSTRAINT ck_ii_b2c_price CHECK (b2c_unit_price >= 0),
    gst_percent    numeric(6, 3)  NOT NULL CONSTRAINT ck_ii_gst CHECK (gst_percent BETWEEN 0 AND 100),
    line_total     numeric(14, 2) NOT NULL,
    CONSTRAINT ux_invoice_items_line UNIQUE (invoice_id, line_no)
);
CREATE INDEX ix_ii_product ON invoice_items (product_id);

-- Internal B2C twin: exactly one per estimate or invoice (source saveDoc, id 'b2c_'+parentId).
CREATE TABLE b2c_documents (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    document_type            varchar(10)    NOT NULL CONSTRAINT ck_b2c_type CHECK (document_type IN ('ESTIMATE', 'INVOICE')),
    estimate_id              uuid CONSTRAINT ux_b2c_estimate UNIQUE REFERENCES estimates (id) ON DELETE CASCADE,
    invoice_id               uuid CONSTRAINT ux_b2c_invoice UNIQUE REFERENCES invoices (id) ON DELETE CASCADE,
    internal_number          varchar(40)    NOT NULL CONSTRAINT ux_b2c_internal_number UNIQUE,
    parent_number            varchar(30)    NOT NULL,
    partner_id               uuid           NOT NULL REFERENCES partners (id),
    lead_id                  uuid REFERENCES leads (id) ON DELETE SET NULL,
    document_date            date           NOT NULL,
    discount_percent         numeric(6, 3)  NOT NULL DEFAULT 0,
    igst                     boolean        NOT NULL DEFAULT false,
    partner_subtotal         numeric(14, 2) NOT NULL DEFAULT 0,
    partner_discount_amount  numeric(14, 2) NOT NULL DEFAULT 0,
    partner_taxable_amount   numeric(14, 2) NOT NULL DEFAULT 0,
    partner_gst_amount       numeric(14, 2) NOT NULL DEFAULT 0,
    partner_grand_total      numeric(14, 2) NOT NULL DEFAULT 0,
    b2c_subtotal             numeric(14, 2) NOT NULL DEFAULT 0,
    b2c_discount_amount      numeric(14, 2) NOT NULL DEFAULT 0,
    b2c_taxable_amount       numeric(14, 2) NOT NULL DEFAULT 0,
    b2c_gst_amount           numeric(14, 2) NOT NULL DEFAULT 0,
    b2c_grand_total          numeric(14, 2) NOT NULL DEFAULT 0,
    uplift                   numeric(14, 2) NOT NULL DEFAULT 0,   -- b2c_taxable - partner_taxable
    b2c_incomplete           boolean        NOT NULL DEFAULT false, -- some line had no B2C price (SRS FR-09)
    created_at               timestamptz    NOT NULL DEFAULT now(),
    updated_at               timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT ck_b2c_parent CHECK (
        (document_type = 'ESTIMATE' AND estimate_id IS NOT NULL AND invoice_id IS NULL) OR
        (document_type = 'INVOICE' AND invoice_id IS NOT NULL AND estimate_id IS NULL))
);
CREATE INDEX ix_b2c_partner ON b2c_documents (partner_id);
CREATE INDEX ix_b2c_type_date ON b2c_documents (document_type, document_date);
CREATE TRIGGER trg_b2c_documents_updated_at BEFORE UPDATE ON b2c_documents FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE b2c_document_items (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    b2c_document_id uuid           NOT NULL REFERENCES b2c_documents (id) ON DELETE CASCADE,
    line_no         integer        NOT NULL CONSTRAINT ck_b2ci_line CHECK (line_no > 0),
    product_id      uuid REFERENCES products (id) ON DELETE SET NULL,
    description     varchar(300),            -- B2C name
    partner_name    varchar(300),            -- partner copy name, shown on the internal view
    hsn             varchar(20),
    unit            varchar(20)    NOT NULL DEFAULT 'nos',
    quantity        numeric(12, 3) NOT NULL,
    unit_price      numeric(14, 2) NOT NULL,
    gst_percent     numeric(6, 3)  NOT NULL,
    line_total      numeric(14, 2) NOT NULL,
    CONSTRAINT ux_b2c_items_line UNIQUE (b2c_document_id, line_no)
);

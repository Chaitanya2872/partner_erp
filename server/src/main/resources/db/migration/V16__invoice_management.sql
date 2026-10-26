-- Phase 9: invoice management (DECISIONS.md §J).
--   * Supplier GST registration (state, GSTIN, address) in company_settings.
--   * Invoice lifecycle DRAFT → ISSUED → CANCELLED; numbers (INV-YYYY-NNNNNN) are assigned only on issue.
--   * Customer billing snapshot, place of supply and the tax treatment stored on the invoice.
--   * Per-line tax snapshot: taxable value, CGST, SGST/UTGST, IGST, total tax and line total.
--   * Issued and cancelled invoices are immutable at the database level as well as in the API.
-- Applied migrations are not modified; existing rows are carried over without changing their totals.

-- ---------------------------------------------------------------- supplier registration
ALTER TABLE company_settings
    ADD COLUMN state_code varchar(2) CONSTRAINT ck_settings_state_code CHECK (state_code ~ '^[0-9]{2}$'),
    ADD CONSTRAINT ck_settings_gstin CHECK (gstin IS NULL OR gstin ~ '^[0-9]{2}[A-Z0-9]{13}$'),
    ADD CONSTRAINT ck_settings_gstin_state CHECK (gstin IS NULL OR state_code IS NULL OR left(gstin, 2) = state_code);

COMMENT ON COLUMN company_settings.state_code IS 'GST state code of the supplier''s registration (place of business)';

-- ---------------------------------------------------------------- permissions
-- Approve on Invoices = confirm a tax treatment that can't be determined reliably, and change a line's GST rate.
-- Only the module's capability changes: nobody is granted the action by this migration.
UPDATE modules
   SET supports_approve = true,
       description = 'Partner-copy invoices; Delete = cancel or discard a draft; Approve = confirm tax treatment'
 WHERE code = 'INVOICES';

-- ---------------------------------------------------------------- invoices: lifecycle
ALTER TABLE invoices DROP CONSTRAINT ck_invoices_status;
UPDATE invoices SET status = 'ISSUED' WHERE status = 'OPEN';
ALTER TABLE invoices
    ALTER COLUMN status SET DEFAULT 'DRAFT',
    ADD CONSTRAINT ck_invoices_status CHECK (status IN ('DRAFT', 'ISSUED', 'CANCELLED')),
    ALTER COLUMN invoice_number DROP NOT NULL,
    ALTER COLUMN invoice_date DROP NOT NULL;

ALTER TABLE invoices
    ADD COLUMN issued_at              timestamptz,
    ADD COLUMN issued_by              uuid REFERENCES users (id),
    ADD COLUMN cancel_reason          varchar(500),
    ADD COLUMN deleted_at             timestamptz,
    ADD COLUMN deleted_by             uuid REFERENCES users (id),
    ADD COLUMN due_days               smallint CONSTRAINT ck_invoices_due_days CHECK (due_days BETWEEN 0 AND 365),
    -- Status of the source estimate before conversion, restored if the draft is discarded.
    ADD COLUMN source_estimate_status varchar(20);

UPDATE invoices SET issued_at = created_at, issued_by = created_by WHERE status <> 'DRAFT';
UPDATE invoices SET cancelled_at = coalesce(cancelled_at, updated_at) WHERE status = 'CANCELLED';
UPDATE invoices SET cancel_reason = 'Cancelled before Phase 9' WHERE status = 'CANCELLED' AND cancel_reason IS NULL;

-- ---------------------------------------------------------------- invoices: GST snapshot
ALTER TABLE invoices
    -- Supplier as registered when the invoice was issued.
    ADD COLUMN supplier_name             varchar(200),
    ADD COLUMN supplier_address          text,
    ADD COLUMN supplier_gstin            varchar(15),
    ADD COLUMN supplier_state_code       varchar(2),
    -- Customer billing details (recipient).
    ADD COLUMN billing_name              varchar(200),
    ADD COLUMN billing_address           text,
    ADD COLUMN billing_state_code        varchar(2),
    ADD COLUMN billing_gstin             varchar(15),
    -- Place of supply: entered explicitly with the basis it rests on, never derived from the billing state.
    ADD COLUMN place_of_supply_code      varchar(2),
    ADD COLUMN place_of_supply_basis     varchar(30)
        CONSTRAINT ck_invoices_pos_basis CHECK (place_of_supply_basis IN
            ('RECIPIENT_LOCATION', 'DELIVERY_LOCATION', 'INSTALLATION_SITE', 'OTHER')),
    -- INTRA = CGST + SGST/UTGST, INTER = IGST; computed from the supplier state and the place of supply.
    ADD COLUMN tax_type                  varchar(5) CONSTRAINT ck_invoices_tax_type CHECK (tax_type IN ('INTRA', 'INTER')),
    ADD COLUMN utgst                     boolean NOT NULL DEFAULT false,
    -- Explicit, authorized confirmation when the treatment can't be determined reliably.
    ADD COLUMN tax_confirmed_at          timestamptz,
    ADD COLUMN tax_confirmed_by          uuid REFERENCES users (id),
    ADD COLUMN tax_confirmation_note     varchar(500),
    -- The inputs the confirmation covered; a change to any of them needs a new confirmation.
    ADD COLUMN tax_confirmation_key      varchar(300),
    ADD COLUMN cgst_amount               numeric(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN sgst_amount               numeric(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN igst_amount               numeric(14, 2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_invoices_supplier_state CHECK (supplier_state_code ~ '^[0-9]{2}$'),
    ADD CONSTRAINT ck_invoices_billing_state CHECK (billing_state_code ~ '^[0-9]{2}$'),
    ADD CONSTRAINT ck_invoices_pos_code CHECK (place_of_supply_code ~ '^[0-9]{2}$'),
    ADD CONSTRAINT ck_invoices_billing_gstin CHECK (billing_gstin IS NULL OR billing_gstin ~ '^[0-9]{2}[A-Z0-9]{13}$'),
    ADD CONSTRAINT ck_invoices_tax_split CHECK (cgst_amount >= 0 AND sgst_amount >= 0 AND igst_amount >= 0);

-- Carry the stored tax type of existing rows into the new columns, without recomputing their totals.
UPDATE invoices
   SET tax_type    = CASE WHEN igst THEN 'INTER' ELSE 'INTRA' END,
       igst_amount = CASE WHEN igst THEN gst_amount ELSE 0 END,
       cgst_amount = CASE WHEN igst THEN 0 ELSE round(gst_amount / 2, 2) END,
       sgst_amount = CASE WHEN igst THEN 0 ELSE gst_amount - round(gst_amount / 2, 2) END
 WHERE status <> 'DRAFT';

ALTER TABLE invoices
    ADD CONSTRAINT ck_invoices_draft_number CHECK ((status = 'DRAFT') = (invoice_number IS NULL)),
    ADD CONSTRAINT ck_invoices_issued CHECK (status = 'DRAFT'
        OR (invoice_date IS NOT NULL AND issued_at IS NOT NULL AND tax_type IS NOT NULL)),
    ADD CONSTRAINT ck_invoices_cancelled CHECK ((status = 'CANCELLED') = (cancelled_at IS NOT NULL)
        AND (status <> 'CANCELLED' OR cancel_reason IS NOT NULL)),
    ADD CONSTRAINT ck_invoices_deleted_draft CHECK (deleted_at IS NULL OR status = 'DRAFT'),
    ADD CONSTRAINT ck_invoices_source_status CHECK (source_estimate_status IS NULL
        OR source_estimate_status IN ('DRAFT', 'SENT', 'ACCEPTED', 'REJECTED', 'INVOICED'));

-- One live (draft or issued) invoice per estimate: duplicate conversion is impossible even under concurrency.
CREATE UNIQUE INDEX ux_invoices_live_estimate ON invoices (estimate_id)
    WHERE estimate_id IS NOT NULL AND status <> 'CANCELLED' AND deleted_at IS NULL;
CREATE INDEX ix_invoices_created ON invoices (created_at);

-- ---------------------------------------------------------------- invoice lines: tax snapshot
ALTER TABLE invoice_items
    ADD COLUMN taxable_value numeric(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN cgst_amount   numeric(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN sgst_amount   numeric(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN igst_amount   numeric(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN tax_amount    numeric(14, 2) NOT NULL DEFAULT 0,
    ADD COLUMN total_amount  numeric(14, 2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT ck_ii_tax CHECK (taxable_value >= 0 AND cgst_amount >= 0 AND sgst_amount >= 0
        AND igst_amount >= 0 AND tax_amount = cgst_amount + sgst_amount + igst_amount
        AND total_amount = taxable_value + tax_amount);

COMMENT ON COLUMN invoice_items.line_total IS 'Quantity × unit price before discount (the "Amount" column)';
COMMENT ON COLUMN invoice_items.total_amount IS 'Taxable value + total tax';

-- Line snapshots for existing rows, from their own stored values (per-line rounding, BUSINESS_RULES §6.7).
WITH v AS (
    SELECT ii.id, i.igst,
           round(ii.quantity * ii.unit_price * (100 - i.discount_percent) / 100, 2) AS taxable,
           ii.gst_percent AS gst
      FROM invoice_items ii JOIN invoices i ON i.id = ii.invoice_id
)
UPDATE invoice_items ii
   SET taxable_value = v.taxable,
       igst_amount   = CASE WHEN v.igst THEN round(v.taxable * v.gst / 100, 2) ELSE 0 END,
       cgst_amount   = CASE WHEN v.igst THEN 0 ELSE round(v.taxable * v.gst / 200, 2) END,
       sgst_amount   = CASE WHEN v.igst THEN 0 ELSE round(v.taxable * v.gst / 200, 2) END
  FROM v
 WHERE ii.id = v.id;
UPDATE invoice_items SET tax_amount = cgst_amount + sgst_amount + igst_amount;
UPDATE invoice_items SET total_amount = taxable_value + tax_amount;

-- ---------------------------------------------------------------- immutability once issued
-- The API refuses these changes first; the triggers make sure no other path can make them either.
CREATE FUNCTION invoices_guard_issued() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.status <> 'DRAFT' THEN
            RAISE EXCEPTION 'Invoice % is %; invoices that were issued can''t be deleted', OLD.invoice_number, OLD.status
                USING ERRCODE = 'check_violation';
        END IF;
        RETURN OLD;
    END IF;
    IF OLD.status = 'DRAFT' THEN
        RETURN NEW;
    END IF;
    IF NEW.status = 'DRAFT' OR (OLD.status = 'CANCELLED' AND NEW.status <> 'CANCELLED') THEN
        RAISE EXCEPTION 'Invoice % can''t go back from % to %', OLD.invoice_number, OLD.status, NEW.status
            USING ERRCODE = 'check_violation';
    END IF;
    -- estimate_id and lead_id may only be detached by their foreign keys (ON DELETE SET NULL).
    IF (NEW.estimate_id IS DISTINCT FROM OLD.estimate_id AND NEW.estimate_id IS NOT NULL)
        OR (NEW.lead_id IS DISTINCT FROM OLD.lead_id AND NEW.lead_id IS NOT NULL)
        OR ROW (NEW.invoice_number, NEW.partner_id, NEW.invoice_date, NEW.due_date, NEW.discount_percent, NEW.igst,
                NEW.bill_to, NEW.fee_percent, NEW.notes, NEW.subtotal, NEW.discount_amount, NEW.taxable_amount,
                NEW.gst_amount, NEW.grand_total, NEW.issued_at, NEW.issued_by, NEW.supplier_name,
                NEW.supplier_address, NEW.supplier_gstin, NEW.supplier_state_code, NEW.billing_name,
                NEW.billing_address, NEW.billing_state_code, NEW.billing_gstin, NEW.place_of_supply_code,
                NEW.place_of_supply_basis, NEW.tax_type, NEW.utgst, NEW.tax_confirmed_at, NEW.tax_confirmed_by,
                NEW.tax_confirmation_note, NEW.tax_confirmation_key, NEW.cgst_amount, NEW.sgst_amount, NEW.igst_amount, NEW.due_days,
                NEW.created_at, NEW.created_by)
           IS DISTINCT FROM
           ROW (OLD.invoice_number, OLD.partner_id, OLD.invoice_date, OLD.due_date, OLD.discount_percent, OLD.igst,
                OLD.bill_to, OLD.fee_percent, OLD.notes, OLD.subtotal, OLD.discount_amount, OLD.taxable_amount,
                OLD.gst_amount, OLD.grand_total, OLD.issued_at, OLD.issued_by, OLD.supplier_name,
                OLD.supplier_address, OLD.supplier_gstin, OLD.supplier_state_code, OLD.billing_name,
                OLD.billing_address, OLD.billing_state_code, OLD.billing_gstin, OLD.place_of_supply_code,
                OLD.place_of_supply_basis, OLD.tax_type, OLD.utgst, OLD.tax_confirmed_at, OLD.tax_confirmed_by,
                OLD.tax_confirmation_note, OLD.tax_confirmation_key, OLD.cgst_amount, OLD.sgst_amount, OLD.igst_amount, OLD.due_days,
                OLD.created_at, OLD.created_by) THEN
        RAISE EXCEPTION 'Invoice % is %; its details can''t be changed', OLD.invoice_number, OLD.status
            USING ERRCODE = 'check_violation';
    END IF;
    IF OLD.status = 'CANCELLED' AND ROW (NEW.cancelled_at, NEW.cancelled_by, NEW.cancel_reason)
            IS DISTINCT FROM ROW (OLD.cancelled_at, OLD.cancelled_by, OLD.cancel_reason) THEN
        RAISE EXCEPTION 'Invoice % is cancelled; the cancellation can''t be changed', OLD.invoice_number
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_invoices_guard_issued BEFORE UPDATE OR DELETE ON invoices
    FOR EACH ROW EXECUTE FUNCTION invoices_guard_issued();

CREATE FUNCTION invoice_items_guard_issued() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    parent_status varchar(20);
BEGIN
    SELECT status INTO parent_status FROM invoices
     WHERE id = CASE WHEN TG_OP = 'DELETE' THEN OLD.invoice_id ELSE NEW.invoice_id END;
    -- No parent row: the invoice itself is being deleted (only drafts can be).
    IF parent_status IS NULL OR parent_status = 'DRAFT' THEN
        RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
    END IF;
    -- A product may only be detached by its foreign key; every other column is fixed once issued.
    IF TG_OP = 'UPDATE' AND NEW.product_id IS NULL
        AND (to_jsonb(NEW) - 'product_id') = (to_jsonb(OLD) - 'product_id') THEN
        RETURN NEW;
    END IF;
    RAISE EXCEPTION 'The lines of an issued or cancelled invoice can''t be changed'
        USING ERRCODE = 'check_violation';
END;
$$;

CREATE TRIGGER trg_invoice_items_guard_issued BEFORE INSERT OR UPDATE OR DELETE ON invoice_items
    FOR EACH ROW EXECUTE FUNCTION invoice_items_guard_issued();

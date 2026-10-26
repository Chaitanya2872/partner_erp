-- Phase 8: estimates. Forward-only; existing rows are kept and only filled in.
--   1. Products remember that a document line used them, permanently (decision 2, 2026-10-26).
--   2. Estimates are soft-deleted so numbers, history and the product marker stay meaningful.
--   3. Leads get LEAD-YYYY-NNNNNN numbers and an optional, validated client email (D-16 as decided 2026-10-26).
-- document_sequences.fy_tag holds the four-digit calendar year for the EST and LEAD prefixes.

-- ---------------------------------------------------------------- 1. product usage marker
ALTER TABLE products ADD COLUMN first_referenced_at timestamptz;

ALTER TABLE products DISABLE TRIGGER trg_products_updated_at;
UPDATE products p
   SET first_referenced_at = now()
 WHERE EXISTS (SELECT 1 FROM estimate_items WHERE product_id = p.id)
    OR EXISTS (SELECT 1 FROM invoice_items WHERE product_id = p.id)
    OR EXISTS (SELECT 1 FROM b2c_document_items WHERE product_id = p.id);
ALTER TABLE products ENABLE TRIGGER trg_products_updated_at;

-- Set by the database whenever a line points at a product, whatever code path wrote the line. Never cleared.
CREATE OR REPLACE FUNCTION mark_product_referenced() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF NEW.product_id IS NOT NULL THEN
        UPDATE products SET first_referenced_at = now()
         WHERE id = NEW.product_id AND first_referenced_at IS NULL;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_estimate_items_product_ref AFTER INSERT OR UPDATE OF product_id ON estimate_items
    FOR EACH ROW EXECUTE FUNCTION mark_product_referenced();
CREATE TRIGGER trg_invoice_items_product_ref AFTER INSERT OR UPDATE OF product_id ON invoice_items
    FOR EACH ROW EXECUTE FUNCTION mark_product_referenced();
CREATE TRIGGER trg_b2c_items_product_ref AFTER INSERT OR UPDATE OF product_id ON b2c_document_items
    FOR EACH ROW EXECUTE FUNCTION mark_product_referenced();

-- Backstop for the service check: a product that was ever used can't be deleted.
CREATE OR REPLACE FUNCTION refuse_referenced_product_delete() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF OLD.first_referenced_at IS NOT NULL THEN
        RAISE EXCEPTION 'product % has been used on a document and cannot be deleted', OLD.sku
            USING ERRCODE = 'restrict_violation';
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER trg_products_refuse_referenced_delete BEFORE DELETE ON products
    FOR EACH ROW EXECUTE FUNCTION refuse_referenced_product_delete();

-- ---------------------------------------------------------------- 2. estimate soft delete
ALTER TABLE estimates
    ADD COLUMN deleted_at timestamptz,
    ADD COLUMN deleted_by uuid REFERENCES users (id);

CREATE INDEX ix_estimates_live_date ON estimates (estimate_date DESC, estimate_number DESC) WHERE deleted_at IS NULL;

-- ---------------------------------------------------------------- 3. lead numbers and client email
ALTER TABLE leads DISABLE TRIGGER trg_leads_updated_at;

WITH numbered AS (
    SELECT id,
           EXTRACT(YEAR FROM created_at AT TIME ZONE 'Asia/Kolkata')::int AS yr,
           ROW_NUMBER() OVER (PARTITION BY EXTRACT(YEAR FROM created_at AT TIME ZONE 'Asia/Kolkata')
                              ORDER BY created_at, id) AS n
      FROM leads
     WHERE lead_number IS NULL)
UPDATE leads l
   SET lead_number = 'LEAD-' || numbered.yr || '-' || lpad(numbered.n::text, 6, '0')
  FROM numbered
 WHERE l.id = numbered.id;

ALTER TABLE leads ENABLE TRIGGER trg_leads_updated_at;

INSERT INTO document_sequences (prefix, fy_tag, last_value)
SELECT 'LEAD', substring(lead_number FROM 6 FOR 4), MAX(substring(lead_number FROM 11)::int)
  FROM leads
 WHERE lead_number ~ '^LEAD-[0-9]{4}-[0-9]{6}$'
 GROUP BY substring(lead_number FROM 6 FOR 4)
ON CONFLICT (prefix, fy_tag) DO UPDATE SET last_value = GREATEST(document_sequences.last_value, EXCLUDED.last_value);

-- The application allocates the number (DocumentNumberService); rows inserted any other way get the next one
-- from the same sequence row, so numbers stay unique whichever path created the lead.
CREATE OR REPLACE FUNCTION assign_lead_number() RETURNS trigger
    LANGUAGE plpgsql AS
$$
DECLARE
    yr  text := to_char(now() AT TIME ZONE 'Asia/Kolkata', 'YYYY');
    val integer;
BEGIN
    IF NEW.lead_number IS NULL THEN
        INSERT INTO document_sequences (prefix, fy_tag, last_value) VALUES ('LEAD', yr, 1)
        ON CONFLICT (prefix, fy_tag) DO UPDATE SET last_value = document_sequences.last_value + 1
        RETURNING last_value INTO val;
        NEW.lead_number := 'LEAD-' || yr || '-' || lpad(val::text, 6, '0');
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_leads_assign_number BEFORE INSERT ON leads
    FOR EACH ROW EXECUTE FUNCTION assign_lead_number();

ALTER TABLE leads
    ALTER COLUMN lead_number SET NOT NULL,
    ADD CONSTRAINT ck_leads_client_email CHECK (
        client_email IS NULL OR client_email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$');

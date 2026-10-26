-- Phase 10: payments management (DECISIONS.md §K).
--   * Payments are an append-only ledger: receipts against issued invoices, and reversals that point at the receipt
--     they cancel. Rows are never updated or deleted, so the original receipt is always kept.
--   * Every entry has a number (PAY-YYYY-NNNNNN); a client idempotency key stops double submissions.
--   * Invoice balances stay derived (grand total − Σ amounts, reversals are negative), so issued invoices are never
--     touched by payments.
--   * Company settings record whether e-invoicing (IRN/QR) applies, which this system does not support (§K, P10-G).
-- Applied migrations are not modified; existing payment rows are carried over unchanged.

-- ---------------------------------------------------------------- e-invoicing declaration
-- NULL = not declared yet. Invoices can't be issued until it is declared, nor while it is true.
ALTER TABLE company_settings
    ADD COLUMN einvoicing_applicable boolean;

-- ---------------------------------------------------------------- payment ledger
ALTER TABLE payments
    ADD COLUMN payment_number      varchar(20) CONSTRAINT ux_payments_number UNIQUE,
    ADD COLUMN entry_type          varchar(10) NOT NULL DEFAULT 'RECEIPT',
    ADD COLUMN reversed_payment_id uuid CONSTRAINT ux_payments_reversed UNIQUE REFERENCES payments (id),
    ADD COLUMN reversal_reason     varchar(500),
    ADD COLUMN notes               varchar(500),
    ADD COLUMN idempotency_key     uuid CONSTRAINT ux_payments_idempotency UNIQUE,
    ADD COLUMN request_hash        varchar(64);

-- Rows recorded before Phase 10 (tests and fixtures only): refunds keep their sign and become REFUND entries.
UPDATE payments SET entry_type = 'REFUND' WHERE is_refund;

ALTER TABLE payments
    DROP CONSTRAINT ck_payments_refund_sign,
    ADD CONSTRAINT ck_payments_type CHECK (
        (entry_type = 'RECEIPT' AND amount > 0 AND NOT is_refund AND reversed_payment_id IS NULL
            AND reversal_reason IS NULL)
        OR (entry_type = 'REFUND' AND amount < 0 AND is_refund AND reversed_payment_id IS NULL
            AND reversal_reason IS NULL)
        OR (entry_type = 'REVERSAL' AND amount < 0 AND NOT is_refund AND reversed_payment_id IS NOT NULL
            AND reversal_reason IS NOT NULL AND btrim(reversal_reason) <> '')),
    ADD CONSTRAINT ck_payments_net CHECK (
        abs(net_amount) <= abs(amount) AND (net_amount = 0 OR sign(net_amount) = sign(amount))),
    ADD CONSTRAINT ck_payments_reference CHECK (reference_number IS NULL OR btrim(reference_number) <> ''),
    ADD CONSTRAINT ck_payments_idempotency CHECK ((idempotency_key IS NULL) = (request_hash IS NULL));

CREATE INDEX ix_payments_reference ON payments (lower(reference_number)) WHERE reference_number IS NOT NULL;
CREATE INDEX ix_payments_created ON payments (created_at);

-- Append-only: a payment is recorded against an issued invoice (a reversal also against a cancelled one, for rows
-- carried over), and afterwards it can't change or disappear. The only permitted update is the lead link being
-- cleared by ON DELETE SET NULL. A reversal must mirror the receipt it reverses, on the same invoice.
CREATE FUNCTION payments_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    invoice_status varchar(20);
    original       payments%ROWTYPE;
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Payment % can''t be deleted; reverse it instead', coalesce(OLD.payment_number, OLD.id::text)
            USING ERRCODE = 'check_violation';
    END IF;
    IF TG_OP = 'UPDATE' THEN
        IF (NEW.invoice_id, NEW.partner_id, NEW.payment_date, NEW.amount, NEW.net_amount, NEW.is_refund,
            NEW.payment_method, NEW.reference_number, NEW.payment_number, NEW.entry_type, NEW.reversed_payment_id,
            NEW.reversal_reason, NEW.notes, NEW.idempotency_key, NEW.request_hash, NEW.created_at, NEW.created_by)
           IS DISTINCT FROM
           (OLD.invoice_id, OLD.partner_id, OLD.payment_date, OLD.amount, OLD.net_amount, OLD.is_refund,
            OLD.payment_method, OLD.reference_number, OLD.payment_number, OLD.entry_type, OLD.reversed_payment_id,
            OLD.reversal_reason, OLD.notes, OLD.idempotency_key, OLD.request_hash, OLD.created_at, OLD.created_by)
           OR (NEW.lead_id IS NOT NULL AND NEW.lead_id IS DISTINCT FROM OLD.lead_id) THEN
            RAISE EXCEPTION 'Payment % can''t be changed; reverse it and record it again',
                coalesce(OLD.payment_number, OLD.id::text) USING ERRCODE = 'check_violation';
        END IF;
        RETURN NEW;
    END IF;
    SELECT status INTO invoice_status FROM invoices WHERE id = NEW.invoice_id AND deleted_at IS NULL;
    IF NEW.entry_type = 'REVERSAL' THEN
        SELECT * INTO original FROM payments WHERE id = NEW.reversed_payment_id;
        IF original.id IS NULL OR original.entry_type <> 'RECEIPT' OR original.invoice_id <> NEW.invoice_id
           OR original.amount <> -NEW.amount OR original.net_amount <> -NEW.net_amount THEN
            RAISE EXCEPTION 'A reversal must mirror a receipt of the same invoice' USING ERRCODE = 'check_violation';
        END IF;
        IF invoice_status IS NULL OR invoice_status NOT IN ('ISSUED', 'CANCELLED') THEN
            RAISE EXCEPTION 'Payments can only be reversed on issued invoices' USING ERRCODE = 'check_violation';
        END IF;
    ELSIF invoice_status IS DISTINCT FROM 'ISSUED' THEN
        RAISE EXCEPTION 'Payments can only be recorded against issued invoices' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_payments_guard BEFORE INSERT OR UPDATE OR DELETE ON payments
    FOR EACH ROW EXECUTE FUNCTION payments_guard();

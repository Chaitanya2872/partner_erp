-- Payments received and partner payouts (source collections "payments" and "payouts").
-- Only source behaviour is modelled: refunds are negative payments; clawbacks are negative payouts;
-- payout statuses are ACCRUED/PAID. Reversal states (D-11, D-12) are added only if approved.

CREATE TABLE payments (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id       uuid           NOT NULL REFERENCES invoices (id),
    partner_id       uuid           NOT NULL REFERENCES partners (id),
    lead_id          uuid REFERENCES leads (id) ON DELETE SET NULL,
    payment_date     date           NOT NULL,
    amount           numeric(14, 2) NOT NULL CONSTRAINT ck_payments_amount CHECK (amount <> 0),  -- incl. GST; negative = refund
    net_amount       numeric(14, 2) NOT NULL,                                                   -- ex-GST realised
    is_refund        boolean        NOT NULL DEFAULT false,
    payment_method   varchar(20)    NOT NULL CONSTRAINT ck_payments_method CHECK (payment_method IN ('UPI', 'NEFT_RTGS', 'CHEQUE', 'CASH', 'CARD')),
    reference_number varchar(100),
    created_at       timestamptz    NOT NULL DEFAULT now(),
    created_by       uuid REFERENCES users (id),
    CONSTRAINT ck_payments_refund_sign CHECK (is_refund = (amount < 0))
);
CREATE INDEX ix_payments_invoice ON payments (invoice_id);
CREATE INDEX ix_payments_partner_date ON payments (partner_id, payment_date);
CREATE INDEX ix_payments_date ON payments (payment_date);

-- One payout per payment (source id 'po_'+paymentId), which prevents duplicate fee accrual.
CREATE TABLE partner_payouts (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id         uuid           NOT NULL CONSTRAINT ux_payouts_payment UNIQUE REFERENCES payments (id) ON DELETE CASCADE,
    partner_id         uuid           NOT NULL REFERENCES partners (id),
    invoice_id         uuid           NOT NULL REFERENCES invoices (id),
    base_amount        numeric(14, 2) NOT NULL,                  -- payment net_amount
    commission_percent numeric(6, 3)  NOT NULL CONSTRAINT ck_payouts_pct CHECK (commission_percent BETWEEN 0 AND 100),
    gross_commission   numeric(14, 2) NOT NULL CONSTRAINT ck_payouts_fee CHECK (gross_commission <> 0), -- negative = clawback
    tds_amount         numeric(14, 2),
    net_payout         numeric(14, 2),
    accrued_on         date           NOT NULL,
    due_date           date           NOT NULL,
    tier_payout_days   smallint,                                 -- business days used for due_date (positive fees)
    paid_date          date,
    utr                varchar(100),
    status             varchar(20)    NOT NULL DEFAULT 'ACCRUED' CONSTRAINT ck_payouts_status CHECK (status IN ('ACCRUED', 'PAID')),
    created_at         timestamptz    NOT NULL DEFAULT now(),
    updated_at         timestamptz    NOT NULL DEFAULT now(),
    created_by         uuid REFERENCES users (id),
    updated_by         uuid REFERENCES users (id),
    version            bigint         NOT NULL DEFAULT 0,
    CONSTRAINT ck_payouts_paid_fields CHECK (status <> 'PAID' OR paid_date IS NOT NULL)
);
CREATE INDEX ix_payouts_partner_status ON partner_payouts (partner_id, status);
CREATE INDEX ix_payouts_status_due ON partner_payouts (status, due_date);
CREATE INDEX ix_payouts_paid_date ON partner_payouts (paid_date);
CREATE TRIGGER trg_partner_payouts_updated_at BEFORE UPDATE ON partner_payouts FOR EACH ROW EXECUTE FUNCTION set_updated_at();

ALTER TABLE partner_tier_reviews
    ADD CONSTRAINT fk_ptr_payment FOREIGN KEY (payment_id) REFERENCES payments (id) ON DELETE SET NULL;

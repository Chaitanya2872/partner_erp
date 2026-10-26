-- Phase 11: partner payouts (DECISIONS.md §L).
--   * Commission is earned on standing receipts: r2(receipt ex-GST amount × the invoice's fee %), using the rate the
--     invoice was issued with, never the partner's current tier.
--   * A payout is a batch for one partner: Draft → Pending approval → Approved → Processing → Paid, or Rejected,
--     Cancelled, Failed. Each receipt or adjustment is allocated to at most one live payout (partial unique indexes).
--   * Completed payouts never change. A receipt reversed after its commission was paid creates a negative partner
--     adjustment that is carried into the partner's next payout.
--   * TDS comes from a configuration Finance confirms; nothing statutory is seeded here.
--   * The Phase 2 per-payment accrual table partner_payouts is superseded: its paid rows are carried over as legacy
--     payouts, and it takes no new rows.
-- Applied migrations are not modified. No permission is granted by this migration.

-- ---------------------------------------------------------------- permissions: capability only
UPDATE modules
   SET supports_approve = true,
       description = 'Partner commission payouts; Approve = approve or reject payouts and adjustments, confirm TDS settings'
 WHERE code = 'PAYOUTS';

-- ---------------------------------------------------------------- partner PAN verification
ALTER TABLE partners
    ADD COLUMN pan_verified_at   timestamptz,
    ADD COLUMN pan_verified_by   uuid REFERENCES users (id),
    ADD COLUMN pan_verification_note varchar(500),
    ADD CONSTRAINT ck_partners_pan_verified CHECK (pan_verified_at IS NULL OR pan IS NOT NULL);

-- A changed or removed PAN is no longer verified.
CREATE FUNCTION partners_pan_verification() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.pan IS DISTINCT FROM OLD.pan THEN
        NEW.pan_verified_at := NULL;
        NEW.pan_verified_by := NULL;
        NEW.pan_verification_note := NULL;
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_partners_pan_verification BEFORE UPDATE OF pan ON partners
    FOR EACH ROW EXECUTE FUNCTION partners_pan_verification();

-- ---------------------------------------------------------------- TDS configuration
-- Versions of the withholding rules. Values are entered by Finance; none is seeded. A DRAFT is edited, then confirmed
-- by a second person; confirmed versions never change. The rule in force on a date is the latest CONFIRMED version
-- effective on or before it.
CREATE TABLE tds_configurations (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    status                   varchar(20)    NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT ck_tds_status CHECK (status IN ('DRAFT', 'CONFIRMED')),
    section_code             varchar(20),
    description              varchar(200),
    rate_percent             numeric(6, 3)  CONSTRAINT ck_tds_rate CHECK (rate_percent BETWEEN 0 AND 100),
    -- NULL: payouts to partners without a verified PAN are blocked instead of taxed at another rate.
    rate_without_pan_percent numeric(6, 3)  CONSTRAINT ck_tds_rate_no_pan CHECK (rate_without_pan_percent BETWEEN 0 AND 100),
    fy_threshold             numeric(14, 2) CONSTRAINT ck_tds_threshold CHECK (fy_threshold >= 0),
    effective_from           date,
    confirmed_at             timestamptz,
    confirmed_by             uuid REFERENCES users (id),
    confirmation_note        varchar(500),
    -- Last person who edited the draft; the confirmer must be someone else.
    edited_by                uuid REFERENCES users (id),
    created_at               timestamptz    NOT NULL DEFAULT now(),
    updated_at               timestamptz    NOT NULL DEFAULT now(),
    created_by               uuid REFERENCES users (id),
    updated_by               uuid REFERENCES users (id),
    version                  bigint         NOT NULL DEFAULT 0,
    CONSTRAINT ck_tds_confirmed CHECK (status <> 'CONFIRMED' OR (
        section_code IS NOT NULL AND btrim(section_code) <> '' AND rate_percent IS NOT NULL
        AND fy_threshold IS NOT NULL AND effective_from IS NOT NULL AND confirmed_at IS NOT NULL
        AND confirmed_by IS NOT NULL AND confirmation_note IS NOT NULL AND btrim(confirmation_note) <> ''
        AND confirmed_by IS DISTINCT FROM edited_by))
);
CREATE UNIQUE INDEX ux_tds_single_draft ON tds_configurations (status) WHERE status = 'DRAFT';
CREATE UNIQUE INDEX ux_tds_effective ON tds_configurations (effective_from) WHERE status = 'CONFIRMED';
CREATE TRIGGER trg_tds_configurations_updated_at BEFORE UPDATE ON tds_configurations
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE FUNCTION tds_configurations_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' OR OLD.status = 'CONFIRMED' THEN
        RAISE EXCEPTION 'A confirmed TDS configuration can''t be changed; create a new version'
            USING ERRCODE = 'check_violation';
    END IF;
    RETURN COALESCE(NEW, OLD);
END;
$$;
CREATE TRIGGER trg_tds_configurations_guard BEFORE UPDATE OR DELETE ON tds_configurations
    FOR EACH ROW EXECUTE FUNCTION tds_configurations_guard();

-- ---------------------------------------------------------------- partner adjustments
-- REVERSAL: created when a receipt whose commission was already paid is reversed (negative, approved by the rule).
-- MANUAL: entered with a reason and approved by a second person. Approved, unallocated adjustments are carried into
-- the partner's next payout.
CREATE TABLE partner_adjustments (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    adjustment_number  varchar(20)    NOT NULL CONSTRAINT ux_adjustments_number UNIQUE,
    partner_id         uuid           NOT NULL REFERENCES partners (id),
    kind               varchar(10)    NOT NULL CONSTRAINT ck_adjustments_kind CHECK (kind IN ('REVERSAL', 'MANUAL')),
    amount             numeric(14, 2) NOT NULL CONSTRAINT ck_adjustments_amount CHECK (amount <> 0),
    reason             varchar(500)   NOT NULL CONSTRAINT ck_adjustments_reason CHECK (btrim(reason) <> ''),
    status             varchar(20)    NOT NULL
        CONSTRAINT ck_adjustments_status CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    source_payment_id  uuid CONSTRAINT ux_adjustments_source_payment UNIQUE REFERENCES payments (id),
    source_line_id     uuid CONSTRAINT ux_adjustments_source_line UNIQUE,
    decided_at         timestamptz,
    decided_by         uuid REFERENCES users (id),
    decision_note      varchar(500),
    idempotency_key    uuid CONSTRAINT ux_adjustments_idempotency UNIQUE,
    created_at         timestamptz    NOT NULL DEFAULT now(),
    created_by         uuid REFERENCES users (id),
    CONSTRAINT ck_adjustments_reversal CHECK (kind <> 'REVERSAL' OR (amount < 0 AND status = 'APPROVED'
        AND source_payment_id IS NOT NULL AND source_line_id IS NOT NULL)),
    CONSTRAINT ck_adjustments_manual CHECK (kind <> 'MANUAL' OR (source_payment_id IS NULL AND source_line_id IS NULL)),
    CONSTRAINT ck_adjustments_decision CHECK (kind <> 'MANUAL' OR status = 'PENDING_APPROVAL'
        OR (decided_at IS NOT NULL AND decided_by IS NOT NULL AND decided_by IS DISTINCT FROM created_by)),
    CONSTRAINT ck_adjustments_rejected CHECK (status <> 'REJECTED' OR (decision_note IS NOT NULL AND btrim(decision_note) <> ''))
);
CREATE INDEX ix_adjustments_partner ON partner_adjustments (partner_id, status);

-- ---------------------------------------------------------------- payouts
CREATE TABLE payouts (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payout_number       varchar(20) CONSTRAINT ux_payouts_number UNIQUE,
    partner_id          uuid           NOT NULL REFERENCES partners (id),
    status              varchar(20)    NOT NULL DEFAULT 'DRAFT'
        CONSTRAINT ck_payouts_new_status CHECK (status IN ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'PROCESSING', 'PAID',
                                                           'REJECTED', 'CANCELLED', 'FAILED')),
    legacy              boolean        NOT NULL DEFAULT false,
    as_of_date          date           NOT NULL,
    fy_start            date           NOT NULL,
    commission_total    numeric(14, 2) NOT NULL DEFAULT 0,
    -- TDS of the latest calculation (payout_tds_calculations); NULL while it can't be calculated.
    tds_status          varchar(30)    NOT NULL DEFAULT 'CONFIG_INCOMPLETE'
        CONSTRAINT ck_payouts_tds_status CHECK (tds_status IN ('CALCULATED', 'CONFIG_INCOMPLETE', 'PAN_NOT_VERIFIED', 'LEGACY')),
    tds_configuration_id uuid REFERENCES tds_configurations (id),
    tds_section         varchar(20),
    tds_rate            numeric(6, 3),
    tds_base            numeric(14, 2),
    tds_amount          numeric(14, 2),
    tds_adjusted        boolean        NOT NULL DEFAULT false,
    net_amount          numeric(14, 2),
    notes               varchar(500),
    submitted_at        timestamptz,
    submitted_by        uuid REFERENCES users (id),
    approved_at         timestamptz,
    approved_by         uuid REFERENCES users (id),
    approval_note       varchar(500),
    processing_at       timestamptz,
    processing_by       uuid REFERENCES users (id),
    bank_account_last4  varchar(4),
    paid_date           date,
    payment_reference   varchar(100),
    paid_at             timestamptz,
    paid_by             uuid REFERENCES users (id),
    closed_at           timestamptz,
    closed_by           uuid REFERENCES users (id),
    status_reason       varchar(500),
    idempotency_key     uuid CONSTRAINT ux_payouts_idempotency UNIQUE,
    request_hash        varchar(64),
    created_at          timestamptz    NOT NULL DEFAULT now(),
    updated_at          timestamptz    NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES users (id),
    updated_by          uuid REFERENCES users (id),
    version             bigint         NOT NULL DEFAULT 0,
    CONSTRAINT ck_payouts_number CHECK (legacy OR payout_number IS NOT NULL),
    CONSTRAINT ck_payouts_net CHECK (net_amount IS NULL OR tds_amount IS NULL OR net_amount = commission_total - tds_amount),
    CONSTRAINT ck_payouts_tds CHECK (tds_amount IS NULL OR tds_amount >= 0),
    CONSTRAINT ck_payouts_separation CHECK (legacy OR approved_by IS NULL
        OR (approved_by IS DISTINCT FROM created_by AND approved_by IS DISTINCT FROM submitted_by)),
    CONSTRAINT ck_payouts_approved CHECK (legacy OR status NOT IN ('APPROVED', 'PROCESSING', 'PAID')
        OR (approved_at IS NOT NULL AND approved_by IS NOT NULL AND tds_status = 'CALCULATED' AND net_amount > 0)),
    CONSTRAINT ck_payouts_paid CHECK (status <> 'PAID' OR (paid_date IS NOT NULL
        AND (legacy OR (payment_reference IS NOT NULL AND btrim(payment_reference) <> '' AND paid_by IS NOT NULL)))),
    CONSTRAINT ck_payouts_reason CHECK (status NOT IN ('REJECTED', 'CANCELLED', 'FAILED')
        OR (status_reason IS NOT NULL AND btrim(status_reason) <> '' AND closed_at IS NOT NULL)),
    CONSTRAINT ck_payouts_idempotency CHECK ((idempotency_key IS NULL) = (request_hash IS NULL))
);
-- At most one open (draft or pending) payout per partner, so generation for one partner can't run twice in parallel.
CREATE UNIQUE INDEX ux_payouts_open_partner ON payouts (partner_id) WHERE status IN ('DRAFT', 'PENDING_APPROVAL');
CREATE INDEX ix_payouts_partner_fy ON payouts (partner_id, fy_start, status);
CREATE INDEX ix_payouts_status ON payouts (status, created_at);
CREATE TRIGGER trg_payouts_updated_at BEFORE UPDATE ON payouts FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Allowed transitions; finished payouts never change and no payout is deleted.
CREATE FUNCTION payouts_guard() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'Payout % can''t be deleted; cancel it instead', coalesce(OLD.payout_number, OLD.id::text)
            USING ERRCODE = 'check_violation';
    END IF;
    IF OLD.status IN ('PAID', 'REJECTED', 'CANCELLED', 'FAILED') THEN
        RAISE EXCEPTION 'Payout % is % and can''t change', coalesce(OLD.payout_number, OLD.id::text), OLD.status
            USING ERRCODE = 'check_violation';
    END IF;
    IF NEW.status IS DISTINCT FROM OLD.status AND NOT (
           (OLD.status = 'DRAFT' AND NEW.status IN ('PENDING_APPROVAL', 'CANCELLED'))
        OR (OLD.status = 'PENDING_APPROVAL' AND NEW.status IN ('APPROVED', 'REJECTED', 'CANCELLED'))
        OR (OLD.status = 'APPROVED' AND NEW.status IN ('PROCESSING', 'CANCELLED'))
        OR (OLD.status = 'PROCESSING' AND NEW.status IN ('PAID', 'FAILED'))) THEN
        RAISE EXCEPTION 'A payout can''t move from % to %', OLD.status, NEW.status USING ERRCODE = 'check_violation';
    END IF;
    -- Once approved, the amounts are frozen.
    IF OLD.status IN ('APPROVED', 'PROCESSING') AND (NEW.commission_total, NEW.tds_base, NEW.tds_amount, NEW.net_amount,
            NEW.tds_rate, NEW.tds_section, NEW.partner_id, NEW.as_of_date, NEW.fy_start, NEW.approved_by)
            IS DISTINCT FROM (OLD.commission_total, OLD.tds_base, OLD.tds_amount, OLD.net_amount, OLD.tds_rate,
            OLD.tds_section, OLD.partner_id, OLD.as_of_date, OLD.fy_start, OLD.approved_by) THEN
        RAISE EXCEPTION 'An approved payout''s amounts can''t change' USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_payouts_guard BEFORE UPDATE OR DELETE ON payouts FOR EACH ROW EXECUTE FUNCTION payouts_guard();

-- ---------------------------------------------------------------- payout lines (allocations)
CREATE TABLE payout_lines (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payout_id          uuid           NOT NULL REFERENCES payouts (id),
    line_type          varchar(12)    NOT NULL CONSTRAINT ck_payout_lines_type CHECK (line_type IN ('COMMISSION', 'ADJUSTMENT')),
    payment_id         uuid REFERENCES payments (id),
    adjustment_id      uuid REFERENCES partner_adjustments (id),
    invoice_id         uuid REFERENCES invoices (id),
    payment_date       date,
    base_amount        numeric(14, 2),
    commission_percent numeric(6, 3)  CONSTRAINT ck_payout_lines_pct CHECK (commission_percent BETWEEN 0 AND 100),
    amount             numeric(14, 2) NOT NULL CONSTRAINT ck_payout_lines_amount CHECK (amount <> 0),
    released_at        timestamptz,
    release_reason     varchar(20)    CONSTRAINT ck_payout_lines_release CHECK (release_reason IN
                                          ('RECALCULATED', 'REJECTED', 'CANCELLED', 'FAILED')),
    created_at         timestamptz    NOT NULL DEFAULT now(),
    CONSTRAINT ck_payout_lines_source CHECK (
        (line_type = 'COMMISSION' AND payment_id IS NOT NULL AND adjustment_id IS NULL AND invoice_id IS NOT NULL
            AND base_amount IS NOT NULL AND commission_percent IS NOT NULL AND payment_date IS NOT NULL)
        OR (line_type = 'ADJUSTMENT' AND adjustment_id IS NOT NULL AND payment_id IS NULL)),
    CONSTRAINT ck_payout_lines_released CHECK ((released_at IS NULL) = (release_reason IS NULL))
);
-- A receipt's commission and an adjustment are allocated to at most one live payout.
CREATE UNIQUE INDEX ux_payout_lines_payment ON payout_lines (payment_id) WHERE released_at IS NULL AND payment_id IS NOT NULL;
CREATE UNIQUE INDEX ux_payout_lines_adjustment ON payout_lines (adjustment_id) WHERE released_at IS NULL AND adjustment_id IS NOT NULL;
CREATE INDEX ix_payout_lines_payout ON payout_lines (payout_id);

ALTER TABLE partner_adjustments
    ADD CONSTRAINT fk_adjustments_source_line FOREIGN KEY (source_line_id) REFERENCES payout_lines (id);

-- ---------------------------------------------------------------- TDS calculation history (append-only)
CREATE TABLE payout_tds_calculations (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payout_id              uuid           NOT NULL REFERENCES payouts (id),
    event                  varchar(20)    NOT NULL CONSTRAINT ck_ptc_event CHECK (event IN
                               ('GENERATED', 'RECALCULATED', 'ADJUSTED', 'APPROVED', 'LEGACY')),
    status                 varchar(30)    NOT NULL CONSTRAINT ck_ptc_status CHECK (status IN
                               ('CALCULATED', 'CONFIG_INCOMPLETE', 'PAN_NOT_VERIFIED', 'LEGACY')),
    tds_configuration_id   uuid REFERENCES tds_configurations (id),
    section_code           varchar(20),
    rate_percent           numeric(6, 3),
    fy_threshold           numeric(14, 2),
    fy_start               date           NOT NULL,
    pan_verified           boolean        NOT NULL,
    prior_fy_commission    numeric(14, 2) NOT NULL,
    prior_fy_tds_base      numeric(14, 2) NOT NULL,
    commission_total       numeric(14, 2) NOT NULL,
    tds_base               numeric(14, 2),
    computed_tds           numeric(14, 2),
    tds_amount             numeric(14, 2),
    adjustment_reason      varchar(500),
    created_at             timestamptz    NOT NULL DEFAULT now(),
    created_by             uuid REFERENCES users (id),
    CONSTRAINT ck_ptc_adjusted CHECK ((adjustment_reason IS NULL) OR (event = 'ADJUSTED' AND btrim(adjustment_reason) <> ''))
);
CREATE INDEX ix_ptc_payout ON payout_tds_calculations (payout_id, created_at);

-- Lines, adjustments and calculations are append-only. A line may only be released (once); an adjustment may only be
-- decided (once); lines are only added while the payout is a draft or pending approval.
CREATE FUNCTION payout_records_guard() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    payout_status varchar(20);
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION '% rows can''t be deleted', TG_TABLE_NAME USING ERRCODE = 'check_violation';
    END IF;
    IF TG_TABLE_NAME = 'payout_lines' THEN
        IF TG_OP = 'INSERT' THEN
            SELECT status INTO payout_status FROM payouts WHERE id = NEW.payout_id;
            IF payout_status NOT IN ('DRAFT', 'PENDING_APPROVAL') THEN
                RAISE EXCEPTION 'Lines are added only to draft or pending payouts' USING ERRCODE = 'check_violation';
            END IF;
            RETURN NEW;
        END IF;
        IF OLD.released_at IS NOT NULL OR NEW.released_at IS NULL
           OR (NEW.payout_id, NEW.line_type, NEW.payment_id, NEW.adjustment_id, NEW.invoice_id, NEW.payment_date,
               NEW.base_amount, NEW.commission_percent, NEW.amount, NEW.created_at)
              IS DISTINCT FROM (OLD.payout_id, OLD.line_type, OLD.payment_id, OLD.adjustment_id, OLD.invoice_id,
               OLD.payment_date, OLD.base_amount, OLD.commission_percent, OLD.amount, OLD.created_at) THEN
            RAISE EXCEPTION 'A payout line can only be released, once' USING ERRCODE = 'check_violation';
        END IF;
        SELECT status INTO payout_status FROM payouts WHERE id = NEW.payout_id;
        -- Lines are released before the payout's status changes, so the status here is the one being left.
        IF NOT ((payout_status IN ('DRAFT', 'PENDING_APPROVAL') AND NEW.release_reason IN ('RECALCULATED', 'CANCELLED', 'REJECTED'))
                OR (payout_status = 'APPROVED' AND NEW.release_reason = 'CANCELLED')
                OR (payout_status = 'PROCESSING' AND NEW.release_reason = 'FAILED')) THEN
            RAISE EXCEPTION 'This payout''s lines can''t be released' USING ERRCODE = 'check_violation';
        END IF;
        RETURN NEW;
    END IF;
    IF TG_TABLE_NAME = 'partner_adjustments' THEN
        IF OLD.status <> 'PENDING_APPROVAL'
           OR (NEW.adjustment_number, NEW.partner_id, NEW.kind, NEW.amount, NEW.reason, NEW.source_payment_id,
               NEW.source_line_id, NEW.created_at, NEW.created_by)
              IS DISTINCT FROM (OLD.adjustment_number, OLD.partner_id, OLD.kind, OLD.amount, OLD.reason,
               OLD.source_payment_id, OLD.source_line_id, OLD.created_at, OLD.created_by) THEN
            RAISE EXCEPTION 'An adjustment can only be approved or rejected, once' USING ERRCODE = 'check_violation';
        END IF;
        RETURN NEW;
    END IF;
    RAISE EXCEPTION '% rows can''t be changed', TG_TABLE_NAME USING ERRCODE = 'check_violation';
END;
$$;
CREATE TRIGGER trg_payout_lines_guard BEFORE INSERT OR UPDATE OR DELETE ON payout_lines
    FOR EACH ROW EXECUTE FUNCTION payout_records_guard();
CREATE TRIGGER trg_partner_adjustments_guard BEFORE UPDATE OR DELETE ON partner_adjustments
    FOR EACH ROW EXECUTE FUNCTION payout_records_guard();
CREATE TRIGGER trg_payout_tds_calculations_guard BEFORE UPDATE OR DELETE ON payout_tds_calculations
    FOR EACH ROW EXECUTE FUNCTION payout_records_guard();

-- ---------------------------------------------------------------- legacy accruals
-- Paid rows of the Phase 2 per-payment table become legacy payouts (one per row) with one line, so their commission
-- is never recognised again and a later reversal of the receipt creates an adjustment. Unpaid accruals are not
-- carried over: the commission of a standing receipt is calculated again by the payout run.
DO $$
DECLARE
    r record;
    new_id uuid;
BEGIN
    ALTER TABLE payout_lines DISABLE TRIGGER trg_payout_lines_guard;
    FOR r IN SELECT * FROM partner_payouts WHERE status = 'PAID' ORDER BY paid_date, created_at LOOP
        INSERT INTO payouts (partner_id, status, legacy, as_of_date, fy_start, commission_total, tds_status, tds_base,
                             tds_amount, net_amount, paid_date, payment_reference, paid_at, created_at, created_by)
        VALUES (r.partner_id, 'PAID', true, r.paid_date,
                make_date(CASE WHEN extract(month FROM r.paid_date) >= 4 THEN extract(year FROM r.paid_date)::int
                               ELSE extract(year FROM r.paid_date)::int - 1 END, 4, 1),
                r.gross_commission, 'LEGACY', NULL, COALESCE(r.tds_amount, 0),
                r.gross_commission - COALESCE(r.tds_amount, 0), r.paid_date, r.utr, r.updated_at, r.created_at,
                r.created_by)
        RETURNING id INTO new_id;
        INSERT INTO payout_lines (payout_id, line_type, payment_id, invoice_id, payment_date, base_amount,
                                  commission_percent, amount, created_at)
        SELECT new_id, 'COMMISSION', r.payment_id, r.invoice_id, p.payment_date, r.base_amount, r.commission_percent,
               r.gross_commission, r.created_at
          FROM payments p WHERE p.id = r.payment_id;
    END LOOP;
    ALTER TABLE payout_lines ENABLE TRIGGER trg_payout_lines_guard;
END;
$$;

CREATE FUNCTION partner_payouts_legacy() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'partner_payouts is superseded by payouts (V18) and takes no new rows'
        USING ERRCODE = 'check_violation';
END;
$$;
CREATE TRIGGER trg_partner_payouts_legacy BEFORE INSERT ON partner_payouts
    FOR EACH ROW EXECUTE FUNCTION partner_payouts_legacy();

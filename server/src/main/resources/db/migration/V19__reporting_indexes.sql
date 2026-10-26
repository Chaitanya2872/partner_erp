-- Phase 12: indexes for dashboard and report aggregation (DECISIONS.md §M). Forward-only; V1–V18 are unchanged.
-- Already covered by earlier migrations: invoices by invoice_date and status/due_date, payments by payment_date,
-- partner/date, invoice and reversed_payment_id (unique), leads by created_at, stage and partner, stage history by
-- lead/occurred_at, live estimates by estimate_date (ix_estimates_live_date), invoice lines by invoice and product,
-- and live payout lines by payment.

-- Partners created in a period (new-partner KPI and trend).
CREATE INDEX ix_partners_created ON partners (created_at);

-- Payouts paid in a period (paid commission, TDS and net). The name ix_payouts_paid_date is taken by the legacy
-- partner_payouts table.
CREATE INDEX ix_payouts_paid_on ON payouts (paid_date) WHERE status = 'PAID';

-- Payouts by as-of date (payouts and TDS reports).
CREATE INDEX ix_payouts_as_of ON payouts (as_of_date);

-- Adjustments by creation date (TDS and adjustments report).
CREATE INDEX ix_adjustments_created ON partner_adjustments (created_at);

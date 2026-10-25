-- Paid amount and balance per invoice (source paidOf: sum of payment amounts, refunds negative).
-- The payment status itself (Paid / Partly paid / Overdue ...) depends on "today" and the ₹0.50
-- tolerance, so it is derived in the service layer (BUSINESS_RULES BR-V6), not here.

CREATE VIEW v_invoice_balance AS
SELECT i.id                                           AS invoice_id,
       i.grand_total                                  AS grand_total,
       COALESCE(SUM(p.amount), 0)::numeric(14, 2)     AS paid,
       (i.grand_total - COALESCE(SUM(p.amount), 0))::numeric(14, 2) AS balance
FROM invoices i
         LEFT JOIN payments p ON p.invoice_id = i.id
GROUP BY i.id, i.grand_total;

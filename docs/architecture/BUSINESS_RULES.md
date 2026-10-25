# IOTIQ Partner ERP — Business Rules

**Phase:** 0 (Source analysis) · **Date:** 2026-10-24
**Primary source:** `docs/reference/IOTIQ_Partner_ERP_original.html`, extracted from `IOTIQ_Partner_ERP_Code.docx`. Function names below (`totals`, `invStatus`, `recPayForm`, …) refer to that file.
**Secondary sources:** `IOTIQ_Partner_ERP_SRS_Claude.md` (SRS), `docs/database/IOTIQ_Partner_ERP_ER_Diagram.md` (ER).

Each rule is tagged:

- **[SRC]**: behaviour taken directly from the original HTML. The backend must reproduce it exactly unless a decision in `DECISIONS.md` changes it.
- **[SRC-DEFECT]**: source behaviour that looks unintended. It is documented here and **must not be changed without approval** (see the linked decision).
- **[PROPOSED]**: a production addition that the source does not have. It needs approval.

All dates are local calendar dates. The source uses the browser's local time; production will use `Asia/Kolkata` (D-24).

---

## 1. Primitive helpers (exact source semantics)

| Helper | Source | Rule |
|---|---|---|
| `r2(n)` | `Math.round(n*100)/100` | Rounds to 2 dp, with halves going toward +∞. Every stored money value is rounded with `r2`. The backend uses `BigDecimal` with `RoundingMode.HALF_UP` at scale 2. This matches the source for positive values. For negative values (refunds/clawbacks) JS rounds −x.xx5 toward zero, whereas HALF_UP rounds away from zero. See D-25. |
| `inr(n)` | `'₹'+r2(n).toLocaleString('en-IN',{max 2 dp, min 0 dp})` | Indian digit grouping, no trailing `.00`. Display only. |
| `addDays(s,n)` | calendar days | Used for protection expiry, estimate validity, invoice due date and the 365-day revenue window. |
| `addBiz(s,n)` | skip Sat/Sun only | Counting starts on the day *after* `s`. **Used in exactly one place: the payout due date.** No holiday calendar. |
| `diffDays(a,b)` | `round((a−b)/1 day)` | Calendar-day difference. |
| `fyOf(s)` | FY starts 1 April | `fyStart = <fy>-04-01`. `fyTag = YY(fy)+YY(fy+1)`, e.g. 2026-10-24 gives `2627`. |
| `digits(s)` | last 10 digits | Phone normalisation for duplicate checks. |

### 1.1 Where business days apply, and where they don't

| Clock | Unit | Source |
|---|---|---|
| Lead acknowledgement SLA | **Calendar hours**, rounded (`Math.round(ms/36e5)`) | `leadSla` |
| Estimate SLA | **Calendar days** (`diffDays`) | `leadSla` |
| Lead protection | **Calendar days** | `ackLead` |
| Estimate validity / invoice due | **Calendar days** | `seedDoc` |
| Clawback window | **Calendar days** | `recPayForm` |
| Tier revenue window | **365 calendar days** before *today* | `cpRev12` |
| **Payout due date** | **Business days (Mon–Fri)** | `recPayForm` → `addBiz` |

---

## 2. Configuration defaults [SRC]

`DEF` merged with stored `settings.main` (`cfg()`):

| Key | Default | Meaning |
|---|---|---|
| company | `IOTIQ` | Company name on documents |
| address | `6th Floor, Pardha's Picasa, Durgam Cheruvu Road, Kavuri Hills, Madhapur, Hyderabad, Telangana - 500081` | |
| gstin | `''` | Printed only if set |
| phone | `8121234162` | |
| email | `gowtham.k@iotiq.co.in` | |
| terms | `Quote valid for the period shown. Prices exclude site civil, wiring and back-box work unless listed. Payment: as agreed in the order confirmation.` | Default document notes |
| protectDays | 90 | Lead protection |
| clawbackDays | 90 | Refund clawback window |
| tdsPct | 2 | TDS % on referral fee |
| tdsThreshold | 20000 | ₹ per partner per FY |
| ackHours | 48 | Acknowledgement SLA |
| estimateDays | 5 | Estimate SLA after acknowledgement |
| validDays | 15 | Estimate validity |
| dueDays | 15 | Invoice payment terms |
| defaultGst | 18 | Default line GST % |

Settings are saved with `+value||0`, so blank or invalid numbers become 0. **[PROPOSED]** The backend validates ranges instead (e.g. hours/days ≥ 1, percentages 0–100). Settings-screen notes in the source: "SLA targets are placeholders: set them from Annexure B of the agreement" and "Verify the TDS section and threshold with your CA."

### 2.1 Tiers [SRC] (`DEFTIERS`)

| n | Name | Fee % | Min paid revenue (₹, trailing 12 m, ex-GST) | Payout (business days) |
|---|---|---:|---:|---:|
| 1 | Registered | 8 | 0 | 15 |
| 2 | Preferred | 10 | 10,00,000 | 12 |
| 3 | Elite | 12 | 30,00,000 | 10 |
| 4 | Strategic | 15 | 1,00,00,000 | 7 |

- There are exactly four tiers. Name, fee, minimum and payout days are editable. Tiers cannot be added or removed.
- `tierForRev(rev)` returns the highest tier whose `min <= rev`.

---

## 3. Partners

| ID | Rule | Tag |
|---|---|---|
| BR-P1 | Required: name ("Name is required."). | SRC |
| BR-P2 | Categories (`CATS`): Interior designer; Architect; Builder; Developer; Contractor (civil/electrical/MEP); Electrical dealer; Kitchen / furniture showroom; Society / real-estate app; Other. | SRC |
| BR-P3 | Status: Prospect, Onboarding, Active, Inactive (default shown: Prospect). "Activate/deactivate" maps to this field. | SRC |
| BR-P4 | Agreement (`slaStatus`): Not sent (default), Sent, Signed. Version defaults to `v2.1`. Agreement date field is labelled "Signed / sent on". | SRC |
| BR-P5 | Assigned tier is chosen manually (default 1). If the **programme** flag ("Signed builder / developer / contractor programme") is ticked, the tier is forced to 4 on save. | SRC |
| BR-P6 | Alert: Active partner whose agreement is not Signed. | SRC |
| BR-P7 | Payout guard: a positive payout cannot be marked paid if PAN or bank account is missing. | SRC |
| BR-P8 | Architect category shows the warning "take legal advice before paying a cash referral fee (CoA conduct rules)" on the detail page and a "CoA check" pill on payouts. | SRC |
| BR-P9 | Bank account is shown masked as `••` plus the last 4 digits. | SRC |
| BR-P10 | Partners cannot be deleted in the source; only sample records can be. | SRC |
| BR-P11 | Partner list search covers name, city, category and contact. The list is sorted by name. The tier column shows the assigned tier and, if different, "→ calculated tier". | SRC |
| BR-P12 | **Paid revenue (12 m)** = Σ `payment.net` for the partner where `payment.date >= today − 365 days`. Refunds (negative net) reduce it. All of the partner's payments count, including fee-0 "partner buys" invoices and future-dated payments. **The basis is collected ex-GST revenue, never invoiced revenue.** | SRC |
| BR-P13 | Detail tiles: progress to the next tier = `min(100, round(rev/next.min×100))`, plus the amount remaining. | SRC |

### 3.1 Tier review (`reviewCp`, `tierReview`) [SRC]

```
target = programme ? 4 : tierForRev(paidRevenue12m).n
if target > current → UPGRADE to target (may jump several tiers), grace=false
if target < current → if grace: DOWNGRADE to current-1, grace=false
                      else:     set grace=true ("grace quarter starts"), tier unchanged
if target == current → if grace: grace=false ("recovered") else no change
lastReview = today on every applied change
```

- The review is manual: "Run tier review" covers all non-Inactive partners, and "Tier review" covers one partner. It shows a preview and is applied on confirmation.
- The "grace quarter" is only a Boolean flag. Its length equals the interval between two manual reviews (D-07).
- Fee rates already locked on acknowledged leads do not change ("Upgrades apply to the next lead").

### 3.2 Automatic upgrade on payment (`recPayForm`) [SRC] / [SRC-DEFECT]

The automatic upgrade happens only when all of the following hold: the payment is not a refund, the invoice has a partner, the fee ≠ 0, and the partner is not on the programme. In that case:

`rev = cpRev12(partner) + net`. If `tierForRev(rev) > current`, set the tier to it and clear grace.

**Defect:** in the standalone build the store updates synchronously, so `cpRev12` already includes the new payment. The new `net` is therefore counted **twice**. Recommendation: count it once (D-06). Also, no automatic upgrade happens for fee-0 invoices (billTo = partner).

---

## 4. Leads and deal registration

| ID | Rule | Tag |
|---|---|---|
| BR-L1 | Required: client name, referring partner. At least one partner must exist ("Add a partner first"). | SRC |
| BR-L2 | Project types: Villa; Apartment; Multi-unit project; Office; Retrofit / renovation; Other. | SRC |
| BR-L3 | Stages in order: New, Registered, Presales, Site visit, Estimate sent, Negotiation, Won, Lost. "Open" means any stage except Won/Lost. | SRC |
| BR-L4 | **Stage changes are unrestricted:** an inline dropdown allows any stage to any stage. | SRC (see D-04) |
| BR-L5 | **Acknowledge** (only when not yet acknowledged, with confirmation) sets: `ackAt = now`; `lockedFeePct = current tier fee`; `protectedUntil = today + protectDays`; stage New → Registered (other stages unchanged). | SRC |
| BR-L6 | **Duplicate/conflict check** runs on every save. A conflict exists when another lead has the same last-10 phone digits (non-empty), a **different** partner, and `protectedUntil >= today`. The result is a **warning only**: the lead is saved with `conflict=true`, shows a "Possible duplicate" pill, and the toast reads "Saved. Same phone is already protected for X." | SRC (see D-05) |
| BR-L7 | Protection display: "to <date>" if `protectedUntil >= today`, otherwise "expired". | SRC |
| BR-L8 | Lead delete is allowed (with confirmation). "Linked estimates and invoices stay", so they keep a dangling lead reference. | SRC (see D-30) |
| BR-L9 | Search covers client, city and partner name. Filters: stage, partner. Sorted newest first. | SRC |
| BR-L10 | Presales owner is free text. Units is a number. Expected close is a date. Source is free text (placeholder "Partner referral, builder tie-up…"). | SRC |

### 4.1 SLA (`leadSla`) [SRC]

```
ackHrs:
  if ackAt:                 ackHrs = round((ackAt − createdAt)/1h); ackBreach = ackHrs > ackHours
  else if stage not Won/Lost: ackHrs = round((now − createdAt)/1h);  ackBreach = ackHrs > ackHours
  (an unacknowledged Won/Lost lead has ackHrs = null and ackBreach = false)
est = earliest-dated estimate for the lead (ANY status, including draft)
if ackAt:
  ackDate = local date of ackAt
  if est:                   estDays = diffDays(est.date, ackDate); estBreach = estDays > estimateDays
  else if stage not Won/Lost: estDays = diffDays(today, ackDate);  estBreach = estDays > estimateDays
```

Because of rounding, the acknowledgement breach triggers once elapsed time is ≥ 48.5 h (round → 49 > 48).

### 4.2 Lead alerts (open leads only) [SRC]

- Not acknowledged and `ackBreach`: "Lead "X" (Partner) not acknowledged within 48 h" (bad).
- Acknowledged, no estimate, `estBreach`: "No estimate yet for "X": N days since acknowledgement (target 5)" (bad).
- `0 <= diffDays(protectedUntil, today) <= 15`: "Protection for "X" ends in N day(s)" (warn).

### 4.3 Automatic stage side effects [SRC]

| Trigger | Lead stage effect |
|---|---|
| Acknowledge | New → Registered |
| Estimate marked **sent** | → Estimate sent, only if the current stage index < 4 (before "Estimate sent") |
| Estimate **accepted** | → Won |
| Estimate **rejected** | → Lost, unless already Won |
| Invoice saved (create **or** edit) | → Won (if not already) |

---

## 5. Products (item & price sheet)

| ID | Rule | Tag |
|---|---|---|
| BR-I1 | Required: SKU, B2C name, partner-facing name. SKU is unique **case-insensitively**. | SRC |
| BR-I2 | Categories (`ICATS`): Smart Touch Interfaces, Smart Relay & Sensing, Smart Glide, Locks & Entry, Services. **No seeded product is in Services.** | SRC |
| BR-I3 | Fields: sku, category, unit (default `nos`), hsn, b2cName, cpName, aliases (list; UI suggests 6–7), b2cPrice (ex-GST), cpPrice (ex-GST), gst (default 18), cost (optional landed cost). | SRC |
| BR-I4 | All 29 seeded products have `b2cPrice = 0`, `cost = 0`, `hsn = ''`, `aliases = []`, `gst = 18`, `unit = nos`. **Do not fabricate B2C prices.** | SRC |
| BR-I5 | The list shows a "B2C price needed" pill when b2cPrice is 0/blank, and a "same as B2C" warning when cpName == b2cName (true for all 5 Locks & Entry seeds). | SRC |
| BR-I6 | Discount % column = `round((1 − cpPrice/b2cPrice)×100)` when b2cPrice > 0, otherwise 0. | SRC |
| BR-I7 | Delete is allowed. "Existing documents keep their copied lines": document lines are **snapshots**. | SRC (see D-17) |
| BR-I8 | List sorted by category+SKU. Search covers SKU, B2C name, partner name and category. | SRC |
| BR-I9 | Partner-facing documents never show B2C name, B2C price, cost or discount. | SRC |

### 5.1 CSV [SRC]

- Columns (export and template): `sku, category, b2c_name, cp_name, cp_alternatives (| separated), hsn, gst_percent, unit, b2c_price, cp_price, cost`.
- Import matches headers by lowercase **prefix** (`startsWith`). `sku`, `b2c_name` and `cp_name` are required. Rows are **upserted by SKU (case-insensitive)**. Rows missing SKU, B2C name or partner name are skipped and counted.
- Import defaults: category `Other` (not a valid UI category); gst 18 if the column is absent, but 0 if the cell is blank; unit `nos`; aliases are kept from the existing item if the column is absent.
- **[SRC-DEFECT]** If the `b2c_price` / `cp_price` columns are absent or blank, the existing prices are **overwritten with 0** (D-18).
- **[SRC-DEFECT]** The template example row uses category `Switching`, which is not a valid category (D-18).
- The CSV parser handles quotes, `""` escapes, CRLF, and skips empty rows. On output, values containing `"`, `,` or newline are quoted.

---

## 6. Document calculations (estimates, invoices, B2C twins)

### 6.1 `totals(lines, discountPct, rateKey)` [SRC]

```
sub   = Σ qty × rate
tax   = Σ qty × rate × (1 − disc/100) × gst/100      (per-line GST)
discA = sub × disc/100
taxable = sub − discA
return { sub:r2(sub), disc:r2(discA), taxable:r2(taxable), tax:r2(tax), total:r2(taxable + tax) }
```

- `total` is computed from the **unrounded** taxable and tax, then rounded. It can differ by ₹0.01 from `r2(taxable)+r2(tax)`. The backend must reproduce this: compute everything at full precision and round each output once.
- The discount is a **document-level %** applied to every line. There are no per-line discounts.
- The partner totals (`cpT`) use `rate`. The B2C totals (`b2cT`) use `b2cRate`, with the same quantities, discount and GST.
- **Tax split:** `igst=false` gives CGST = SGST = `tax/2` (displayed with `inr`, not stored or rounded separately; labelled "CGST + SGST (Telangana)"). `igst=true` gives a single IGST line. **[PROPOSED]** Store CGST/SGST as rounded amounts so they reconcile with the printed values (D-21).
- The line "Amount" column shows `qty × rate` (before discount).
- There are no bounds checks in the source: discount > 100 or negative qty are possible. **[PROPOSED]** Validate 0 ≤ discount ≤ 100, qty > 0, rate ≥ 0, 0 ≤ gst ≤ 100.

### 6.2 Line snapshot [SRC]

Selecting a product copies: `itemId, name = cpName||b2cName, b2cName, hsn, unit, rate = cpPrice, b2cRate = b2cPrice, gst = item.gst ?? defaultGst`. The user can then edit qty, partner rate and GST. B2C rate is **not** editable. Lines without a product are dropped on save, and at least one line is required. **Free-text lines are not supported.**

### 6.3 Document numbering (`nextNo`) [SRC]

- Format `PREFIX-YYZZ-NNNN`. Prefixes: `EST` for estimates, `INV` for invoices. Twins use `INT-` + the parent number (`INT-EST-2627-0001`, `INT-INV-2627-0001`).
- NNNN = max existing sequence with the same `PREFIX-YYZZ-` + 1 (4-digit pad). Each FY starts again at 0001.
- The FY tag comes from **today's date when the editor opens**, not from the document date. **[PROPOSED]** A server-side `document_sequences` row lock allocates the number on save (D-19).

### 6.4 Estimates [SRC]

| ID | Rule |
|---|---|
| BR-E1 | Required: lead ("Choose a lead so the document knows the client and the partner."), ≥1 product line. The partner comes from the lead. |
| BR-E2 | Defaults: date = today, validTill = today + validDays, discount 0, CGST+SGST, billTo = client, notes = settings terms, status = `draft`. |
| BR-E3 | Fee %: if `billTo='partner'` ("Partner buys (bulk)") it is 0. Otherwise it is the lead's `lockedFeePct`, falling back to the partner's current tier fee. The user can edit it ("Locked rate from registration"). |
| BR-E4 | Statuses: `draft → sent` ("Mark sent", sets `sentOn`); `sent → accepted / rejected`; `sent / accepted → Convert to invoice`; conversion sets the estimate to `invoiced`. |
| BR-E5 | Lead dropdown excludes Lost leads, except the one already selected. |
| BR-E6 | Edit is allowed in any status, and the status is kept. Delete is allowed in any status and deletes the twin too (D-29). |
| BR-E7 | Alert: `status='sent' && validTill < today` gives "Estimate X expired on D" (warn). There is no stored "expired" status. |
| BR-E8 | The editor shows a warning stamp when lines lack a B2C price: "N line(s) have no B2C price, so the internal B2C copy will show ₹0 for them…" |

### 6.5 Invoices [SRC]

| ID | Rule |
|---|---|
| BR-V1 | Created either by converting an estimate (copies lines, discount, igst, billTo, feePct, notes, lead, partner; sets `estimateId`) or directly ("standalone"). **A lead is still required.** |
| BR-V2 | Defaults: date today, dueDate = today + dueDays, stored status `open`. |
| BR-V3 | On every save: the linked estimate is set to `invoiced` (if not already) and the lead goes to Won. |
| BR-V4 | Edit is allowed at any time, even after payments or cancellation. Totals are recalculated, and existing payments and payouts are **not** adjusted (D-09). |
| BR-V5 | Cancel only when the invoice is not cancelled **and** paid = 0. Stored status becomes `cancelled`. Invoices cannot be deleted. |
| BR-V6 | The stored status is only `open`/`cancelled`. The payment status is **derived** (`invStatus`): |

```
if cancelled → "Cancelled"
paid = r2(Σ payment.amount)           (refunds are negative)
if paid >= total − 0.50 → "Paid"      (₹0.50 tolerance)
overdue = dueDate && dueDate < today
if paid > 0 → overdue ? "Partly paid · overdue" (bad) : "Partly paid" (warn)
else        → overdue ? "Overdue" (bad)               : "Unpaid" (info)
```

- Balance = `total − paid` (can be negative on overpayment; the source has no guard, see D-10).
- Alert: every non-cancelled invoice whose status is in the "bad" class gives "Invoice X overdue: ₹bal outstanding".

### 6.6 Internal B2C twin (`saveDoc`) [SRC]

On **every** estimate/invoice save, write the twin `{ id:'b2c_'+docId, type:'estimate'|'invoice', parentId, parentNo, no:'INT-'+parentNo, cpId, leadId, date, discount, igst, lines:[{name:b2cName, cpName, hsn, unit, qty, rate:b2cRate, gst}], t:b2cT, cpT, uplift:r2(b2cT.taxable − cpT.taxable) }`.

- One twin per parent (deterministic id), overwritten on each save. Deleting an estimate deletes its twin.
- The source writes the parent and twin sequentially without atomicity. **Production: one DB transaction** (SRS FR-09).
- The internal view shows the stamp "INTERNAL ONLY. Shows B2C product names and prices. Never send to the partner or client. Partner discount vs B2C list: ₹uplift", plus "Partner document: <no>" and each line's partner copy name. Download file name: `INTERNAL-<no>.html`.
- Dashboard label for Σ uplift: **"Discount vs B2C list"**.

---

## 7. Payments [SRC]

| ID | Rule |
|---|---|
| BR-M1 | Only against non-cancelled invoices. Fields: invoice, received on (date), amount incl. GST, mode (UPI, NEFT / RTGS, Cheque, Cash, Card), reference/UTR, refund checkbox. The amount defaults to `max(0, balance)`. |
| BR-M2 | `amount = abs(input)`; if refund, `amount = −amount`. Amount and date are required. |
| BR-M3 | `net = r2(amount × invoice.taxable / invoice.total)` (proportional ex-GST). **[SRC-DEFECT]** Division by zero when total = 0 (D-10). |
| BR-M4 | `fee = r2(net × invoice.feePct / 100)`. The payout is considered only when the invoice has a partner and fee ≠ 0. |
| BR-M5 | **Clawback window:** a refund creates a negative payout only if the invoice's **first positive payment** exists and `diffDays(refundDate, firstPaymentDate) <= clawbackDays`. Otherwise the message is "Refund is outside the clawback window, so no fee reversal". A refund with no positive payments creates no clawback. |
| BR-M6 | Payout created: `{id:'po_'+paymentId, paymentId, cpId, invoiceId, base:net, pct:invoice.feePct, fee, accruedOn:paymentDate, dueOn: fee<0 ? paymentDate : addBiz(paymentDate, currentTier.payoutDays), status:'accrued'}`. **The due date uses the partner's tier at payment time**, before any automatic upgrade from the same payment. |
| BR-M7 | After the payout is created, the automatic tier upgrade runs (§3.2). |
| BR-M8 | Delete payment: blocked if its payout is paid ("Its fee was already paid out. Reverse that first."). Otherwise it deletes the payment and its payout. **There is no edit.** |
| BR-M9 | No overpayment, duplicate or future-date checks (D-10, D-11). |

## 8. Partner payouts [SRC]

| ID | Rule |
|---|---|
| BR-O1 | One payout per payment (deterministic id `po_<paymentId>`), which prevents duplicate accrual. |
| BR-O2 | Display status (`poStatus`): `paid` → Paid; fee < 0 → Clawback; `dueOn <= today` → Due; else Accrued. |
| BR-O3 | Overdue alert: not paid, `dueOn < today`, fee > 0. |
| BR-O4 | Mark paid (button "Mark paid", or "Set off" for negative): blocked for positive fees when PAN/bank is missing (BR-P7). Inputs: paid on (default today), UTR, TDS (prefilled with suggestion, editable). Sets `status='paid', paidOn, utr, tds, net = r2(fee − tds)`. |
| BR-O5 | **TDS suggestion (`tdsFor`)**, exact: |

```
if fee <= 0 → 0
prev = Σ fee of this partner's payouts with status 'paid' and paidOn >= fyStart(today)
if prev >= threshold          → base = fee
else if prev + fee > threshold → base = prev + fee      (catch-up on the FY total, including earlier untaxed fees)
else                           → base = 0
tds = r2(base × tdsPct / 100)
```

Notes: the FY is taken from *today*, not from the paid-on date. Clawbacks marked paid (negative fees) reduce `prev`. Crossing exactly at `prev + fee == threshold` gives 0 (strict `>`). Help text: "Suggested at 2% once the FY total passes ₹20,000. Verify with your CA."

| BR-O6 | Filters: All / Unpaid / Paid. Sorted by due date. Tiles show per-partner unpaid balance "net of clawbacks". |
| BR-O7 | There is **no reversal of a paid payout** in the source, even though the payment-delete message refers to one (D-12). |
| BR-O8 | Fee statement CSV (partner detail): Invoice, Net base, Rate %, Fee, Due, Status, Paid on, TDS, Net paid, UTR. |

## 9. Dashboard [SRC]

| KPI | Formula |
|---|---|
| Active partners | count status = Active; subtext "N on record" (total partners) |
| Open leads | count stage ∉ {Won, Lost}; subtext Σ budget of open leads |
| Invoiced (FY, ex-GST) | Σ `cpT.taxable` of non-cancelled invoices with date ≥ FY start |
| Collected (FY, ex-GST) | Σ `payment.net` with date ≥ FY start ("basis for tiers") |
| Receivables (incl. GST) | Σ `max(0, total − paid)` over non-cancelled invoices (no ₹0.50 tolerance) |
| Partner fees unpaid | Σ fee of payouts not paid (clawbacks net off) |
| Discount vs B2C list | Σ `uplift` of **invoice** twins dated ≥ FY start. **Includes cancelled invoices** [SRC-DEFECT, D-14] |
| Lead funnel | count per stage, bar width relative to the max; "Won N of M leads (P%)" |
| Needs attention | `alerts()`, first 9 then "and N more". The count also appears as a badge on the Dashboard nav item. |
| Top partners | top 5 by paid revenue 12 m |
| Tier mix | count per tier, **excluding Inactive** partners |

Quick actions: + Lead, + Estimate, + Partner. Subtitle: "Financial year 2026-27 · <date>".

## 10. Reports [SRC] (all exported as CSV, file name `iotiq-<key>.csv`)

| Key | Name | Columns / rules |
|---|---|---|
| funnel | Funnel | Stage, Leads, Budget, % of leads |
| score | Partner scorecard | Partner, Category, Tier, Leads, Won, Win rate %, Paid revenue 12 m, Invoiced ex-GST (non-cancelled), Fee earned (Σ fee), Fee paid, Fee unpaid |
| recv | Receivables ageing | Non-cancelled with balance > 0.50. age = `max(0, diffDays(today, dueDate||date))`. Buckets: 0 Not due; 1–30; 31–60; 61–90; 90+ |
| payout | Payout ledger | Partner, Invoice, Net base, Rate %, Fee, TDS, Net paid, Due, Paid on, Status, UTR. Sorted by due date |
| margin | Partner price vs B2C (internal) | Invoice twins only: Invoice, Partner, Date, Partner taxable, B2C taxable, Discount vs B2C, Discount % = `round(uplift/b2cTaxable×1000)/10`. Includes cancelled invoices (D-14) |
| monthly | Monthly sales | Per `YYYY-MM`: invoiced ex-GST (non-cancelled, by invoice date), collected ex-GST (by payment date), fee accrued (by `accruedOn`) |
| sla | SLA performance | Client, Partner, Stage, Ack hours, Ack within target (Yes/No), Days to estimate, Estimate within target (blank when not applicable) |
| tiers | Tier summary | Tier, Fee %, Partners (**including Inactive**), Paid revenue 12 m, Fees earned |

The source reports have **no filters** (no date, FY, partner or status filters). Filters are a production enhancement (prompt §5.11).

## 11. Security / visibility rules

| ID | Rule | Tag |
|---|---|---|
| BR-S1 | Partner copies never contain B2C names, prices, cost or uplift. Sidebar footer: "Internal use only. Partner copies never show B2C names or prices." | SRC |
| BR-S2 | The source has no authentication. `can('data.write')` returns true. A read-only mode hides write buttons (`.w`). | SRC |
| BR-S3 | B2C data is visible inside the editor to every internal user (per-line B2C rate, "Internal B2C copy total"). | SRC → role-gate in production (see API_DESIGN §2) |
| BR-S4 | Server-side recalculation of all totals, net, fee, TDS suggestion and due dates. The client can submit only inputs (qty, rate, gst, discount, fee % subject to permission). | PROPOSED |

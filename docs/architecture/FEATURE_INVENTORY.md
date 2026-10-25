# IOTIQ Partner ERP — Feature Inventory

**Phase:** 0 · **Date:** 2026-10-24

The **Source** column records whether the feature exists in the original HTML: ✓ = present; ◐ = partly present; ✗ = absent, so it is a **production enhancement (ENH)**.
**Repo today** is the same for every row: **empty placeholder file or missing**. No feature is implemented in `client/` or `server/` (see PROJECT_ANALYSIS §2).
**Phase** is the phase from the master prompt that implements it.

Business rule IDs (BR-…) point to `BUSINESS_RULES.md`. Decisions (D-…) point to `DECISIONS.md`.

## 1. Navigation & shell

| Feature | Source | Detail | Phase |
|---|---|---|---|
| Sidebar groups | ✓ | Dashboard · **Funnel:** Partners, Leads · **Sales:** Estimates, Invoices, Payments, Partner payouts · **Master:** Item & price sheet, Reports, Settings | 4 |
| Brand block | ✓ | "IOTIQ" + gold "PARTNER ERP" | 4 |
| Alert badge on Dashboard nav | ✓ | count of alerts | 4/12 |
| Sidebar footer | ✓ | "Internal use only. Partner copies never show B2C names or prices." | 4 |
| Remember last view | ✓ | `localStorage iotiq_erp_view`, which becomes a router URL | 4 |
| Responsive rail | ✓ | fixed 216 px; ≤820 px becomes a horizontal scroll bar | 4 |
| Light/dark theme | ◐ | OS-driven plus `data-theme` CSS; **no toggle** (D-20) | 4 |
| Read-only mode | ◐ | hides `.w` write buttons when no write permission, which becomes RBAC-driven | 3/4 |
| Toast, confirm dialog, modal forms, Esc closes | ✓ | | 4 |
| Login/logout/session | ✗ ENH | | 3 |
| Breadcrumbs | ✗ ENH | | 4 |

## 2. Dashboard (`vDash`)

| Feature | Source | Phase |
|---|---|---|
| 7 KPI tiles: Active partners (+total), Open leads (+budget), Invoiced FY ex-GST, Collected FY ex-GST, Receivables incl. GST, Partner fees unpaid, Discount vs B2C list | ✓ BR §9 | 12 |
| Lead funnel bars + win rate | ✓ | 12 |
| Needs attention (7 alert types, top 9) | ✓ BR §4.2, BR-E7, BR-V6, BR-O3, BR-P6 | 12 |
| Top 5 partners by paid revenue 12 m | ✓ | 12 |
| Tier mix (excludes Inactive) | ✓ | 12 |
| Quick actions + Lead / + Estimate / + Partner | ✓ | 12 |
| FY subtitle | ✓ | 12 |
| Recent activity feed | ✗ ENH (from audit_logs) | 12 |
| Charts via Recharts | ✗ ENH (source uses CSS bars) | 12 |

## 3. Partners (`vPartners`, `vPartnerDetail`, `cpForm`, `tierReview`)

| Feature | Source | Phase |
|---|---|---|
| List: Partner, Category, City, Tier (+→calculated), Paid revenue 12 m, Leads, Agreement pill, Status pill | ✓ | 5 |
| Search (name/city/category/contact) | ✓ | 5 |
| Filters by status/category/tier | ✗ ENH | 5 |
| Create/edit form, 3 sections (Partner; Tier & agreement; Tax & bank) + notes, 19 fields | ✓ BR-P1..P5 | 5 |
| Programme flag forces Strategic | ✓ | 5 |
| Detail: 4 tiles (tier, revenue + progress, fees unpaid/paid, agreement) | ✓ | 5 |
| Detail: contact & compliance, masked bank, PAN/bank-missing warning, Architect CoA warning | ✓ | 5 |
| Detail: linked Leads, Estimates, Invoices tables | ✓ | 5 (populated in 6/8/9) |
| Detail: linked payments, payouts / commission history | ◐ (fee statement CSV only) | 11 |
| Fee statement CSV | ✓ BR-O8 | 11 |
| Tier review: all / single, preview, apply | ✓ §3.1 | 5 |
| Activate/deactivate | ◐ (status field) | 5 |
| Address, partner code | ✗ ENH (D-16) | 5 |
| Delete partner | ✗ (not in source; not planned) | — |
| Tier review history | ✗ ENH | 5 |

## 4. Leads (`vLeads`, `leadForm`, `ackLead`, `leadSla`)

| Feature | Source | Phase |
|---|---|---|
| List: Client/project (+type, units, city, duplicate pill), Partner link, inline Stage select, Budget, Registration (Acknowledge button or "Acknowledged · X% locked · protection"), SLA pills, actions (Edit, Estimate, ×) | ✓ | 6 |
| Search + stage filter + partner filter | ✓ | 6 |
| Form: 12 fields (client, phone, city, partner, project type, units, budget, stage, owner, expected close, source, notes) | ✓ BR-L1..L10 | 6 |
| Acknowledge (confirm, lock fee, protection, New→Registered) | ✓ BR-L5 | 6 |
| Duplicate/conflict warning | ✓ BR-L6 | 6 |
| SLA calc (ack hours, estimate days) | ✓ §4.1 | 6 |
| Lead protection & expiry alert | ✓ | 6/12 |
| Delete lead | ✓ (soft delete, D-26) | 6 |
| Lead history | ✗ ENH | 6 |
| Lead detail page | ✗ ENH (source has none) | 6 |
| Transition rules | ◐ (free; D-04) | 6 |
| Client email, lead number | ✗ ENH (D-16) | 6 |

## 5. Item & price sheet (`vItems`, `itemForm`, `importItems`)

| Feature | Source | Phase |
|---|---|---|
| List: SKU, Category, Partner-facing name (+B2C name, "same as B2C"), Alt names count, HSN, GST, B2C price ("B2C price needed"), Partner price, Disc. %, actions | ✓ | 7 |
| Search | ✓ | 7 |
| Category filter | ✗ ENH | 7 |
| Create/edit (11 fields, SKU uniqueness) | ✓ BR-I1..I3 | 7 |
| Delete | ✓, becomes deactivate (D-17) | 7 |
| CSV import / export / template | ✓ §5.1 (with D-18 fixes) | 7 |
| Seed of 29 products | ✓ DATABASE_DESIGN §7.1 | 2 |
| Role-gated B2C/cost visibility | ✗ ENH | 7 |

## 6. Estimates & invoices (`vEstimates`, `vInvoices`, editor, viewer)

| Feature | Source | Phase |
|---|---|---|
| Estimate list: No., Date, Client, Partner, Status pill, Total incl. GST, actions (Open, Edit, ×); search | ✓ | 8 |
| Invoice list: No., Date, Client, Partner, Status (derived), Total, Balance, actions (Open, Payment, Edit); search | ✓ | 9 |
| Editor: lead, date, valid-till/due, discount %, tax type (CGST+SGST / IGST), who buys (client/partner), fee %, product lines grouped by category (qty, partner rate, GST), B2C rate hint, missing-B2C stamp, live totals, internal B2C total, notes | ✓ §6 | 8/9 |
| Numbering EST/INV-YYZZ-NNNN | ✓ §6.3 | 8/9 |
| Estimate status actions (Mark sent, Accepted, Rejected) and lead side effects | ✓ BR-E4 | 8 |
| Convert estimate to invoice (prefilled editor) | ✓ BR-V1 | 9 |
| Cancel invoice (unpaid only) | ✓ BR-V5 | 9 |
| Derived payment status (6 states, ₹0.50 tolerance) | ✓ BR-V6 | 9/10 |
| Viewer: Partner copy / Internal B2C copy toggle, paper-white document, INTERNAL stamp | ✓ | 8/9 |
| Download document as HTML | ✓ | 8/9 (becomes PDF + print, D-15) |
| Print, PDF | ✗ ENH (D-15) | 8/9 |
| Filters on lists (status, partner, date) | ✗ ENH | 8/9 |
| Edit locks | ✗ ENH (D-09) | 8/9 |

## 7. Internal B2C twins (`saveDoc`)

| Feature | Source | Phase |
|---|---|---|
| Auto create/update twin on every save, `INT-` number, B2C lines, both totals, uplift | ✓ §6.6 | 8/9 |
| Delete twin with estimate | ✓ | 8 |
| Transactional atomicity | ✗ ENH (required) | 8/9 |
| Internal-only access control | ✗ ENH (required) | 3/8 |
| B2C documents list screen | ✗ ENH (source has toggle only; no sidebar item, SRS §2.1) | 8, as an internal tab, not a top-level nav item unless approved |
| Internal PDF | ✗ ENH | 8/9 |

## 8. Payments (`vPayments`, `recPayForm`, `delPay`)

| Feature | Source | Phase |
|---|---|---|
| List: Date, Invoice, Partner, Mode, Reference, Amount (+Refund pill), Net of GST, × | ✓ | 10 |
| Record payment form (6 fields), defaults to balance | ✓ BR-M1..M2 | 10 |
| Net ex-GST proportional | ✓ BR-M3 | 10 |
| Refund (negative) | ✓ | 10 |
| Fee accrual & clawback window | ✓ BR-M4..M6 | 11 |
| Auto tier upgrade on payment | ✓ §3.2 (D-06) | 11 |
| Delete, which becomes Reverse | ◐ (D-11) | 10 |
| Overpayment/duplicate guard, idempotency | ✗ ENH (D-10) | 10 |
| Filters | ✗ ENH | 10 |

## 9. Partner payouts (`vPayouts`, `payoutForm`, `tdsFor`)

| Feature | Source | Phase |
|---|---|---|
| Per-partner unpaid tiles | ✓ | 11 |
| Filter All / Unpaid / Paid | ✓ | 11 |
| List: Partner (+CoA pill), Invoice, Net base, Rate, Fee, Due, Status, paid details / Mark paid / Set off | ✓ | 11 |
| Mark paid with PAN/bank guard, TDS suggestion (editable), UTR, net | ✓ BR-O4..O5 | 11 |
| Business-day due date per tier | ✓ | 11 |
| Payout reversal | ✗ ENH (D-12) | 11 |
| Overdue alert | ✓ | 11/12 |

## 10. Reports (`REPORTS`)

All eight are ✓ in the source, with CSV download, and are implemented in Phase 12: Funnel · Partner scorecard · Receivables ageing · Payout ledger · Partner price vs B2C (internal) · Monthly sales · SLA performance · Tier summary. Filters (date/FY/partner/stage/status) are ✗ ENH.

## 11. Settings (`vSettings`)

| Feature | Source | Phase |
|---|---|---|
| Company on documents: name, GSTIN, phone, email, address, terms | ✓ | 13 (read-only defaults used from Phase 8) |
| Rules & SLA: 9 numeric settings | ✓ | 13 |
| Partner tiers table (4 rows × name/fee/min/days) | ✓ | 13 |
| Backup / restore (browser) | ✓, dropped (D-27) | — |
| Delete sample data | ✓, dropped (D-27) | — |
| Validation of ranges, audit | ✗ ENH | 13 |
| User management, role management, password policy, audit log viewer | ✗ ENH | 3 (core auth) / 13 (screens) |

## 12. Cross-cutting

| Feature | Source | Phase |
|---|---|---|
| INR formatting (en-IN, ₹) | ✓ | 4 |
| Date format `DD Mon YYYY` | ✓ | 4 |
| Empty-state messages (source text per table) | ✓ | 4+ |
| Error message texts | ✓ | 5+ |
| OpenAPI docs, health, structured logs | ✗ ENH | 1 |
| Audit log | ✗ ENH | 2/13 |
| Docker/compose/nginx/HTTPS | ✗ ENH | 15 |

## 13. Counts (for completion tracking)

- Screens: 10 views + partner detail + 6 modal forms (partner, lead, item, payment, payout, settings inline) + document editor + document viewer + tier review + confirm dialog.
- Source actions (`ACT`): 39 handlers, plus change/input handlers (inline stage change, filters, editor fields, CSV/backup file inputs). All are mapped above.
- Reports: 8. Alert types: 7. Seeded products: 29. Tiers: 4. Settings: 6 text + 9 numeric.

# IOTIQ Partner ERP — Software Requirements Specification & Claude Implementation Brief
**Version:** 1.0 | **Basis:** `IOTIQ_Partner_ERP_Code(1).docx` (31-page standalone HTML source) | **Status:** Source-derived requirements with separately marked implementation recommendations.

## 1. Purpose and implementation instruction
Rebuild the supplied single-file HTML Partner ERP as a production-oriented **React + TypeScript + Vite frontend** in `client/` and **Java 21 + Spring Boot + Maven + PostgreSQL backend** in `server/`; retain `prd/` for deployment. **The supplied HTML is the behavioral and visual reference**, not a file to embed in an iframe. Inspect the entire document/source before coding. Preserve its calculations, nomenclature, workflows, seeded catalog, screen navigation, and internal-vs-partner information separation. Where this specification flags ambiguities, request decisions rather than silently inventing financial or legal policy.

## 2. Scope and source fidelity
The source describes a standalone browser application with modules: Dashboard, Partners, Leads, Estimates, Invoices, Payments, Partner payouts, Item & price sheet, Reports, Settings. It also creates internal B2C copies of estimates and invoices. The source stores data in browser `localStorage` under `iotiq_erp_v1` and uses a simulated `window.claude` database/download interface; replace these with persisted REST services and server-side transactions. The existing HTML grants write access via `can:()=>true`; this is **not authentication**.

### 2.1 Functional navigation
Sidebar group **Funnel:** Partners, Leads. **Sales:** Estimates, Invoices, Payments, Partner payouts. **Master:** Item & price sheet, Reports, Settings. Dashboard is the landing page. The internal B2C document set is visible through internal reports/document workflows rather than a separate original sidebar item. Preserve this distinction.

## 3. Business configuration and defaults
- Company display name: IOTIQ; company address, GSTIN, phone, email and document terms are editable. The source includes prefilled contact details; migrate them as configurable values, not immutable constants.
- Lead protection: **90 calendar days**; refund clawback: **90 calendar days**.
- Lead acknowledgement SLA: **48 hours**; estimate after acknowledgement: **5 days** (source comparison uses calendar-day difference).
- Estimate validity: **15 days**; invoice due terms: **15 days**; default GST: **18%**.
- TDS reference settings: **2%** and **₹20,000 threshold per partner per financial year**; source explicitly says verify tax rule with CA. Do not present as legally verified.
- Fiscal year begins **April 1**. Document numbering uses a prefix, `YYZZ` fiscal-year tag and four-digit sequence (e.g. `PREFIX-2627-0001`); derive exact prefixes from HTML source before implementation.
- Currency: INR, Indian digit grouping; persist monetary amounts using `BigDecimal` and defined rounding rules.

### 3.1 Partner tiers
| Tier | Name | Referral fee | Minimum paid referred revenue, trailing 12 months | Payout business days |
|---|---|---:|---:|---:|
| 1 | Registered | 8% | ₹0 | 15 |
| 2 | Preferred | 10% | ₹10,00,000 | 12 |
| 3 | Elite | 12% | ₹30,00,000 | 10 |
| 4 | Strategic | 15% | ₹1,00,00,000 | 7 |

Upgrade immediately on crossing a threshold; when revenue falls below a tier, first mark a grace review, then downgrade one tier on a later review. A signed builder/developer/contractor programme can qualify for Strategic. The source refers to a grace **quarter**, but the code uses a Boolean grace flag; production scheduling and quarter boundaries need explicit confirmation. Payout business-day calculation in source excludes Saturday and Sunday only; holiday calendar is an optional extension.

## 4. Functional requirements
### FR-01 Dashboard
Show active partners; open leads and their budget; current-FY invoiced taxable value; current-FY collected ex-GST; outstanding receivables including GST; unpaid partner fees; current-FY B2C-vs-partner taxable difference. Display lead-stage funnel, top five partners by trailing-12-month paid referred revenue, partner tier distribution, and attention alerts. Quick actions: new lead, estimate, partner.

### FR-02 Partner master
Create, edit, search and view partners. Fields: firm/person name (required), category, status, contact, phone, email, city, tier, programme flag, agreement status/version/date, PAN, GSTIN, licence/CoA/RERA, bank name/account/IFSC, notes. Categories: Interior designer; Architect; Builder; Developer; Contractor (civil/electrical/MEP); Electrical dealer; Kitchen/furniture showroom; Society/real-estate app; Other. Statuses: Prospect, Onboarding, Active, Inactive. Agreement statuses: Not sent, Sent, Signed. Partner detail must show linked leads, estimates, invoices, payouts, tier progress, revenue and fee statement CSV. Alert when Active without signed agreement. Architect payout view flags CoA check. Protect bank/tax data in API responses and logs.

### FR-03 Lead and deal registration
Create/edit/search/filter by stage and referring partner. Fields: client name (required), phone, city/locality, referring partner (required), project type, units, budget, stage, presales owner, expected close date, source, notes. Project types: Villa; Apartment; Multi-unit project; Office; Retrofit/renovation; Other. Stages in exact order: **New → Registered → Presales → Site visit → Estimate sent → Negotiation → Won → Lost**. Track created and acknowledged timestamps, lead protection expiry, and duplicate/conflict checks using last ten phone digits across different partners within protection windows. Source behavior for conflicting registration should be verified against HTML action handler before deciding block vs warning. Alert if unacknowledged after 48 hours, estimate missing 5 days after acknowledgement, or protection expires within 15 days.

### FR-04 Item and price sheet
Seed **all 29 source products** exactly (SKU, category, B2C name, partner-facing name, partner price, GST, unit, cost and aliases). Source categories: Smart Touch Interfaces; Smart Relay & Sensing; Smart Glide; Locks & Entry; Services. Product fields: SKU (unique, case-insensitive), category, unit, HSN/SAC, B2C name, partner-facing name, alternate names (suggested 6–7), B2C ex-GST price, partner ex-GST price, GST%, optional landed cost. Search, create/edit/delete, CSV template/import/export. **All 29 seeded B2C prices are zero/missing and must not be fabricated.** Show a missing-price indicator and require deliberate completion before treating internal B2C comparisons as meaningful. Partner-facing documents must not expose B2C names, B2C prices, cost or uplift.

### FR-05 Estimates
Create/edit/view/print partner-price estimates linked to partner and lead. Capture line-item product, partner-facing description, quantity, unit, HSN, rate, GST, discount, date, validity and notes/terms. Preserve statuses shown in source: sent, accepted, invoiced, rejected (and verify any additional statuses in HTML). Compute partner totals and internal B2C totals independently. Automatically create/update the internal twin **atomically** whenever the estimate is saved. Twin number `INT-<estimate number>` and internal line descriptions/prices. Expired sent estimates generate alerts. Printable output remains paper-white in dark mode.

### FR-06 Invoices
Create/edit/view/print invoices linked to partner/lead; include invoice date, due date, GST, discounts, line items and fee rate snapshot. On save, atomically create/update internal B2C invoice twin. Derive display payment status from actual payments: Cancelled; Paid; Partly paid; Partly paid · overdue; Overdue; Unpaid. Use outstanding balance `invoice grand total – sum(received payment amounts)`; source uses ₹0.50 tolerance to regard an invoice as paid. Show record-payment action. Preserve source cancellation and estimate-to-invoice behavior after inspecting HTML.

### FR-07 Payments
Record receipt against a non-cancelled invoice: date, amount including GST, mode (UPI, NEFT/RTGS, Cheque, Cash, Card), reference/UTR and refund flag. Refund is stored as a negative amount. Derive ex-GST realized amount proportionally: `receivedAmount × invoiceTaxable / invoiceGrandTotal` (handle zero grand total safely). Each payment may generate a partner-fee accrual at the invoice fee percentage. Paid receipts contribute to trailing-12-month tier revenue. Refund within clawback window generates negative fee accrual; outside window does not reverse fee under source behavior. Protect against duplicate submissions, overpayment and inconsistent deletions (production safety additions requiring agreed policy).

### FR-08 Partner payouts
On qualifying payment, create payout ledger entry with payment, partner, invoice, ex-GST base, fee percentage, fee amount, accrued date and due date. Due date = receipt date + tier-specific business days (weekends excluded). Negative payout represents clawback/set-off. Filters: All, Unpaid, Paid. For positive payout, block marking paid if PAN or bank account is missing. Mark paid with paid date, UTR, editable TDS and net paid = fee – TDS. Source proposes TDS based on paid referral fees during current FY and threshold; validate formula with finance before production. Maintain auditable payout state changes.

### FR-09 Internal B2C twins
For **every saved estimate and invoice**, create/update one linked internal-only document, with deterministic association, `INT-` prefix, B2C names/rates, original partner taxable totals, internal taxable totals and `uplift = internal taxable – partner taxable`. Never expose B2C fields in partner documents, public endpoints, exports or partner roles. If B2C prices remain unset, label comparisons incomplete rather than presenting zero as a real selling price. Prevent orphaned twins; transactionally save original and twin.

### FR-10 Reports and exports
Implement all eight source reports with CSV download: **Funnel; Partner scorecard; Receivables ageing; Payout ledger; Partner price vs B2C (internal); Monthly sales; SLA performance; Tier summary.** Receivables ageing buckets: Not due, 1–30, 31–60, 61–90, 90+ days. Include report filters where needed and apply permission controls to internal margin/B2C data. Partner detail fee statement CSV. Item import/export CSV. Browser standalone backup/download is a legacy feature; database backup strategy is separate from user export.

### FR-11 Settings
Edit company identity and printable terms, all business-rule defaults, tax reference settings and four-tier names/fees/revenue thresholds/payout days. Validate numeric ranges and audit changes. Original standalone offers Settings > Download backup; production should offer approved data export rather than unsafe full-database exposure.

### FR-12 Alerts and scheduled evaluation
Generate dashboard attention items for acknowledgement breach, estimate delay, expiring protection, expired estimates, overdue invoices, overdue payouts, and active partners with unsigned agreements. Derive alerts consistently server-side; use scheduled evaluation or query-time derivation with idempotent notifications.

## 5. Calculations and acceptance rules
- Line subtotal: `Σ(quantity × selectedRate)`.
- Document discount: `subtotal × discountPercent / 100`.
- Taxable: `subtotal – discount`.
- Tax: `Σ(quantity × rate × (1 – discountPercent/100) × lineGSTPercent/100)`.
- Grand total: `taxable + tax`; round currency values to 2 decimal places. Preserve source result by comparing representative cases.
- Partner fee: `realized ex-GST amount × invoice fee percent / 100`; record fee percent snapshot at invoice/payment time, do not recalculate historical entries after tier change.
- Fiscal-year collected revenue uses net/ex-GST payment amounts; dashboard receivables use gross/incl-GST.
- Invoice overdue when due date < today and unpaid; payout overdue when due date < today and unpaid (source payout badge marks Due at `<= today`).
- Ensure no race condition in document sequence generation or payment-to-payout creation.

## 6. Data model (recommended production translation)
`users`, `roles`, `user_roles`, `partners`, `partner_tier_definitions`, `partner_tier_reviews`, `leads`, `lead_events`, `products`, `product_aliases`, `estimates`, `estimate_lines`, `invoices`, `invoice_lines`, `payments`, `partner_payouts`, `internal_b2c_documents`, `internal_b2c_lines`, `company_settings`, `audit_logs`, `document_sequences`. Foreign keys and immutable financial snapshots required. Prefer UUID primary keys, timestamps with time zone, `BigDecimal` for amounts and optimistic locking for updates. The original uses collections `cps`, `leads`, `items`, `estimates`, `invoices`, `payments`, `payouts`, `b2c_docs`, `settings`.

## 7. API contract (proposed; not present in original HTML)
Base `/api/v1`. Suggested resources: `/auth/*`, `/dashboard/summary`, `/partners`, `/partners/{id}`, `/partners/{id}/tier-review`, `/partners/{id}/statement`, `/leads`, `/leads/{id}/acknowledge`, `/products`, `/products/import`, `/products/export`, `/estimates`, `/estimates/{id}/print`, `/invoices`, `/invoices/{id}/print`, `/payments`, `/payouts`, `/payouts/{id}/mark-paid`, `/reports/{type}`, `/settings`, `/alerts`. Pagination, filtering, validation errors, consistent API envelopes and access checks. Explicitly keep `/internal/b2c-documents` admin-only. Do not implement unauthenticated blanket write access from standalone HTML.

## 8. UX requirements from source HTML
Use the original **ivory/forest/gold** visual identity: light `#f4efe2`, surface `#fbf8ef`, forest `#11271c`, gold `#c39c67`; dark theme with deep green backgrounds. Typography: Bricolage Grotesque for display, Instrument Sans for body, IBM Plex Mono for numbers. Fixed 216px left navigation on desktop, horizontal scroll navigation on widths ≤820px. KPI tiles, searchable zebra-striped tables, status pills, responsive modal forms, confirmation dialogs, toasts, compact controls, print-friendly white documents. Respect keyboard focus and reduced-motion preference. The original source has both automatic OS dark mode and explicit theme overrides. Preserve role-specific masking of sensitive information.

## 9. Security and nonfunctional requirements (production recommendations)
- Spring Security with authenticated users and least-privilege RBAC; at minimum admin, sales/presales, finance and partner-facing permission boundaries (roles are a proposal, not defined by source).
- JWT or secure session design to be agreed; hash passwords, rotate secrets, HTTPS in production, CORS allowlist for client origin, rate limits, input validation and audit trail.
- Never store partner bank account, PAN, secrets or personal client data in browser localStorage; protect sensitive data at rest/in transit and redact logs.
- Database migrations via Flyway; production PostgreSQL; transactional writes for documents/twins and payment/payout pairs; idempotency keys for payment endpoints.
- Integration tests for calculations, tiers, grace behavior, duplicates, SLA clocks, refund clawback, B2C access separation, CSV import and invoice status.
- Accessibility: responsive layouts, labels, keyboard navigation, error summaries, printable document quality.

## 10. Delivery phases for Claude
**Phase 0 — Source audit:** extract HTML faithfully from DOCX; inventory every event action, form field, status, validation, number prefix, seeded product and report. Produce a gap list; do not start rewriting before audit.
**Phase 1 — Foundation:** configure Maven/Spring Boot, PostgreSQL, Flyway, security baseline, React router/layout/theme, API client, error handling, test harness.
**Phase 2 — Masters:** seed exact 29 products; settings; partner master, tier rules; audit.
**Phase 3 — Funnel:** lead registration, duplicate/protection rules, SLA tracking, dashboard.
**Phase 4 — Sales:** estimates, invoices, print/PDF, B2C twins, transactional numbering.
**Phase 5 — Finance:** payments, refunds, tier changes, payouts/TDS, receivables.
**Phase 6 — Reports & hardening:** eight CSV reports, exports, RBAC penetration checks, automated tests, deployment in `prd/`.

## 11. Definition of done
Every visible source screen and action has an equivalent React workflow; every financial calculation is covered by backend unit tests; seeded catalog exactly matches all 29 source records; no B2C field leaks into partner-facing output; invoice/twin and payment/payout changes are atomic; duplicate lead protection and SLAs are tested; all eight reports export; application builds and runs with documented environment variables and migrations; no secrets are committed; browser reload does not lose persisted data.

## 12. Clarifications required before production release
1. Confirm legal TDS section/threshold and whether GST treatment varies by transaction or geography.
2. Confirm whether a duplicate protected lead is blocked, requires approval, or only warns; reproduce source behavior first.
3. Confirm how long the downgrade grace quarter lasts and who triggers reviews.
4. Confirm document numbering prefixes, cancellation/edit lock policies, overpayments and payment deletion/reversal rules from the full HTML.
5. Supply actual B2C prices, GSTIN/HSN values, final contracting entity, logo, bank/payment terms and partner agreements.
6. Confirm role matrix and whether external partners will log in; source is an internal-use standalone app.
7. Confirm whether production backup download is needed, and if so define permissions and encryption.

## 13. Copy-ready Claude instruction
> You are implementing IOTIQ Partner ERP. Read the attached 31-page `IOTIQ_Partner_ERP_Code(1).docx` and this SRS in full. Extract and audit its standalone HTML before modifying the existing `client/`, `server/`, `prd/` repository. Treat source behavior and the 29 seeded products as authoritative. Implement React/TypeScript/Vite + Spring Boot/Java 21/PostgreSQL/Flyway incrementally, preserving the source design and workflows. Start by reporting a source-to-feature inventory, current repository assessment, discrepancies, and a file-by-file implementation plan. Do not fabricate missing B2C prices or tax rules. Enforce server-side authorization for internal B2C information. After each phase run builds/tests and report results, migrations, endpoints and remaining gaps. Do not delete existing folders or replace working files without inspection.

---
**Traceability:** Source: uploaded `IOTIQ_Partner_ERP_Code(1).docx`, pp. 1–31. Sections 2–5 and 8 largely reflect source code; sections 6–7 and 9–11 are explicit production implementation recommendations, not existing capabilities.

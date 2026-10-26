# IOTIQ Partner ERP — API Design

**Phase:** 0 · **Date:** 2026-10-24 · **Base path:** `/api/v1` · **Format:** JSON, UTF-8. Dates are `YYYY-MM-DD` (Asia/Kolkata business dates). Instants are ISO-8601 with offset. Money is sent as a JSON **string** decimal (`"5749.00"`) so floats never round it.

The API isn't in the original HTML (SRS §7). Endpoints below are validated against the source actions (`ACT.*` in the HTML). The final contract will be published with springdoc OpenAPI at `/v3/api-docs` and `/swagger-ui.html` (disabled in prd unless authorised).

---

## 1. Conventions

**Envelope (all non-file responses):**
```json
{ "success": true, "data": { }, "meta": { "page": 0, "size": 20, "totalElements": 57, "totalPages": 3 }, "timestamp": "…" }
```
**Errors** follow RFC 9457 problem details, wrapped in the same envelope:
```json
{ "success": false, "error": { "type": "about:blank", "title": "Validation failed", "status": 400, "code": "VALIDATION_ERROR",
  "detail": "…", "fieldErrors": [{ "field": "clientName", "message": "Client name is required." }], "requestId": "…" } }
```
Codes: `VALIDATION_ERROR 400`, `UNAUTHENTICATED 401`, `FORBIDDEN 403`, `NOT_FOUND 404`, `CONFLICT 409` (optimistic lock, duplicate SKU), `BUSINESS_RULE 422` (e.g. "PAN and bank details are needed before the first payout"), `RATE_LIMITED 429`. Source error messages are reused verbatim where they exist.

**Lists:** `?page=0&size=20&sort=createdAt,desc&q=…` plus module filters. **Concurrency:** mutable resources return `version`. PUT requires it; a mismatch gives 409. **Idempotency:** `POST /payments` and `POST /payouts/{id}/mark-paid` accept an `Idempotency-Key` header.

**Server-calculated fields:** the client never sends totals, net, fee, TDS suggestions, due dates, numbers or statuses derived from rules. Any such fields in a request body are ignored.

## 2. Roles and permissions (as built in Phase 3)

The proposed 6-role matrix (D-23) was superseded by the Phase 3 instruction. Details and rationale: `DECISIONS.md §D` (P3-01…P3-14).

**Roles** (codes immutable; display names editable by a Super Admin and always read from the database):

| Code | Default name | Access |
|---|---|---|
| `SUPER_ADMIN` | Super Admin | Everything, computed (not stored). Only role that can change permissions, roles and role names. |
| `ADMIN` | Admin | Per-user permissions; default template below. |
| `USER` | User | Per-user permissions; default template below. Can never hold User Management. |

**Modules and permission columns.** `R` Read, `W` Write (implies Read), `D` Delete (needs Write), `E` Export, `A` Approve, `S` data scope. Write never implies D/E/A or actions.

| Module | Columns | Module actions | Admin default | User default |
|---|---|---|---|---|
| DASHBOARD | R | | R | R |
| PARTNERS | R W D E A S | `VIEW_PARTNER_SENSITIVE` (V13) | RW | — |
| LEADS | R W D E A S | | RW | — |
| PRODUCTS | R W D E | `VIEW_INTERNAL_B2C` | RW | R |
| ESTIMATES | R W D E S | | RW | — |
| INVOICES | R W D(=cancel) E S | | RW | — |
| PAYMENTS | R W E S | `REVERSE_PAYMENT` | RW | — |
| PAYOUTS | R W E S | `MARK_PAYOUT_PAID` | R | — |
| INTERNAL_B2C | R E | | — | — |
| REPORTS | R E | | R | — |
| SETTINGS | R W | `MANAGE_SETTINGS` | — | — |
| USER_MANAGEMENT | R W | `MANAGE_USERS`, `MANAGE_PERMISSIONS` (Super Admin only) | — | not grantable |
| ROLE_MANAGEMENT | R W (Super Admin only) | | — | — |
| AUDIT_LOGS | R E | | — | — |

**Authorities.** The principal carries `<MODULE>:<READ|WRITE|DELETE|EXPORT|APPROVE>` and `<MODULE>:<ACTION>` (e.g. `PAYOUTS:MARK_PAYOUT_PAID`) plus `ROLE_<CODE>`. Controllers use `@PreAuthorize("hasAuthority('X:Y')")`; everything not listed as public is denied by default. Permissions are re-read from the database on every request.

**Data scope.** SELF/TEAM/ALL per scope-capable module. Until record ownership (D-30) and teams exist, SELF and TEAM resolve to "no records" (`DataScopeService`); ALL is unrestricted.

**Partner-safe vs internal.** B2C names, B2C prices and landed cost are only serialised in internal DTOs, returned only with `PRODUCTS:VIEW_INTERNAL_B2C` (P3-13). `INTERNAL_B2C:READ` will gate the twin documents (Phase 8).

Enforcement: `SecurityConfig` (authentication, public routes) + `@PreAuthorize` per endpoint + service-level rules (role ceilings, self-protection, last Super Admin) + DTO separation (ADR-06).

## 3. Endpoints

Legend: **Perm** = required permission. **Src** = originating source action.

> **Note (Phase 3):** sections 3.2 and 3.4–3.11 still show the pre-Phase-3 permission names. They map onto the module authorities of §2 and each section is updated when its phase is built: `*_READ` → `<MODULE>:READ`, `*_WRITE` → `<MODULE>:WRITE`, `LEAD_DELETE` → `LEADS:DELETE`, `LEAD_ACK`/`TIER_REVIEW` → `LEADS:APPROVE`/`PARTNERS:APPROVE`, `INVOICE_CANCEL` → `INVOICES:DELETE`, `PAYOUT_PAY` → `PAYOUTS:MARK_PAYOUT_PAID`, `PAYMENT_REVERSE` → `PAYMENTS:REVERSE_PAYMENT`, `B2C_VIEW` → `INTERNAL_B2C:READ` (documents) / `PRODUCTS:VIEW_INTERNAL_B2C` (price sheet), `REPORT_READ` → `REPORTS:READ`, CSV downloads → `<MODULE>:EXPORT`. Login has account lockout but no IP rate limiting yet.

### 3.1 Auth (implemented, Phase 3)
| Method | Path | Perm | Notes |
|---|---|---|---|
| POST | /auth/login | public | `{email,password}` → `{accessToken, tokenType, expiresIn (900), expiresAt, user: Me}`; sets the `iotiq_rt` HttpOnly cookie. Errors: `401 INVALID_CREDENTIALS`, `423 ACCOUNT_LOCKED`, `403 ACCOUNT_DISABLED`. |
| POST | /auth/refresh | cookie + `X-Requested-With` | Rotates the refresh token, returns a new access token. Missing/invalid/reused cookie → `401 SESSION_EXPIRED` and the cookie is cleared; reuse revokes the token family. Missing header → `403 CSRF_HEADER_MISSING`. |
| POST | /auth/logout | cookie + `X-Requested-With` | Revokes the current refresh token, clears the cookie. |
| GET | /auth/me | authenticated | Profile, role (`{id, code, displayName}`), `superAdmin`, `mustChangePassword`, `modules[]` (flags + scope), `authorities[]`. |
| POST | /auth/change-password | authenticated | `{currentPassword,newPassword}`; policy-checked; bumps the session version (all other access tokens stop working), revokes all refresh tokens, returns a new session. |
| GET | /modules | authenticated | Module catalogue: display names, supported columns, module actions. |
| GET | /permissions | authenticated | Action catalogue (generic + module actions). |

### 3.2 Dashboard
| GET | /dashboard/summary | REPORT_READ (uplift field only with B2C_VIEW) | KPIs of BUSINESS_RULES §9 |
|---|---|---|---|
| GET | /dashboard/funnel | LEAD_READ | per stage {stage,count,budget} + win rate |
| GET | /dashboard/alerts | auth (filtered by permission) | `[{level:BAD|WARN, type, text, entityType, entityId}]`. Types: ACK_BREACH, ESTIMATE_BREACH, PROTECTION_EXPIRING, ESTIMATE_EXPIRED, INVOICE_OVERDUE, PAYOUT_OVERDUE, AGREEMENT_UNSIGNED |
| GET | /dashboard/top-partners | REPORT_READ | top 5 by paid revenue 12 m |
| GET | /dashboard/tier-mix | PARTNER_READ | excludes Inactive |

### 3.3 Partners (implemented, Phase 5; src: newCp, editCp, openCp, tierOne, tierAll)
| Method | Path | Authority | Notes |
|---|---|---|---|
| GET | /partners | PARTNERS:READ | `q` (name, city, category label, contact, phone), `status`, `category`, `tier`, `agreementStatus`, `page`, `size` (20), `sort` ∈ name, city, category, status, agreementStatus, createdAt (default name). Rows: tier, calculatedTier, revenue12m, leadCount. No PAN or bank fields. |
| GET | /partners/meta | PARTNERS:READ | categories, statuses, agreement statuses, tiers, default agreement version |
| POST | /partners | PARTNERS:WRITE (+APPROVE for tier ≠ 1 or programme) | 201. Programme ⇒ tier 4 |
| GET | /partners/{id} | PARTNERS:READ | Without PARTNERS:VIEW_PARTNER_SENSITIVE (`sensitiveVisible: false`): PAN and bank account `••` + last 4, GSTIN `••` + last 3 (P5-02); `panOnFile`/`gstinOnFile`/`bankAccountOnFile` flags |
| PUT | /partners/{id} | PARTNERS:WRITE (+APPROVE to change tier/programme) | with `version`. `pan`/`gstin`/`bankAccount`: null keeps, "" removes. A manual tier/programme change clears the grace flag (P5-13) |
| PATCH | /partners/{id}/status | PARTNERS:WRITE | `{status, version}` |
| DELETE | /partners/{id} | — | 405 (BR-P10) |
| GET | /partners/{id}/performance | PARTNERS:READ | tier, calculatedTier, revenue12m, revenueSince, next tier, progress %, remaining, fees unpaid/paid, leads, payoutReady |
| GET | /partners/{id}/tier-reviews | PARTNERS:READ | tier history with actor names |
| POST | /partners/tier-review/preview | PARTNERS:APPROVE | `{partnerIds?}` (default: all non-Inactive) → proposed changes |
| POST | /partners/tier-review/apply | PARTNERS:APPROVE | `{items: [{partnerId, action, toLevel}]}`; recomputed under lock, `409 TIER_REVIEW_STALE` on any mismatch |
| GET | /partners/{id}/estimates, /invoices, /payments, /payouts | respective READ | Phases 8–11. A partner's leads use `GET /leads?partnerId=` (Phase 6) |
| GET | /partners/{id}/statement.csv | PAYOUTS:EXPORT | Phase 11 (BR-O8) |

### 3.4 Leads (implemented, Phase 6; src: newLead, editLead, ackLead, delLead, stage change)
| Method | Path | Authority | Notes |
|---|---|---|---|
| GET | /leads | LEADS:READ | `q` (client, city, partner name, lead number, email), `stage`, `partnerId`, `conflict`, `slaBreach` (true/false), `page`, `size` (20), `sort` ∈ createdAt, clientName, city, budget, expectedClose (default createdAt desc). Rows: partner, stage, budget, `acknowledgedAt`, `lockedFeePercent`, `protectionUntil`, `protectionActive`, `conflict`, `sla` block. Deleted leads never appear |
| GET | /leads/meta | LEADS:READ | stages, project types, referring partners (name, status, tier name, current fee %), SLA targets and protection days |
| POST | /leads | LEADS:WRITE | 201. Stage defaults to NEW. `leadNumber` `LEAD-YYYY-NNNNNN` is assigned by the server and never changes; `clientEmail` optional, format-checked (P8-03). Phone protected for another partner: saved with `conflict: true` and `conflictPartnerName` (warning only, BR-L6/D-05) |
| GET | /leads/{id} | LEADS:READ | + `sla` block (ackHours, ackBreach, estimateDays, estimateBreach, hasEstimate, targets, protectionDaysLeft), `acknowledgedByName`, current `conflictPartnerName` |
| PUT | /leads/{id} | LEADS:WRITE (+APPROVE to leave Won/Lost) | with `version`; re-runs the conflict check; `stage` + optional `stageReason` follow D-04; the partner can change until acknowledgement, never after (P6-06, approved) |
| PATCH | /leads/{id}/stage | LEADS:WRITE (+APPROVE to leave Won/Lost, else `403 STAGE_REOPEN_NOT_PERMITTED`) | `{stage, reason?, version}` |
| POST | /leads/{id}/acknowledge | LEADS:APPROVE | open leads only: `422 LEAD_CLOSED` for Won/Lost (reopen first, P6-13). Locks the partner's current tier fee, protection = today + protection days, NEW → REGISTERED; `409 LEAD_ALREADY_ACKNOWLEDGED` |
| DELETE | /leads/{id} | LEADS:DELETE | 204, soft delete (D-26); `409 LEAD_HAS_DOCUMENTS` while estimates, invoices, payments or internal documents point at it |
| GET | /leads/{id}/history | LEADS:READ | stage history, oldest first, with actor names and reasons |

### 3.5 Products (src: newItem, editItem, delItem, impItems, expItems, tplItems) — as built in Phase 7

Permissions map onto the existing codes (no new permission code): `PRODUCTS:READ/WRITE/DELETE/EXPORT` and the `PRODUCTS:VIEW_INTERNAL_B2C` action ("internal" below). Internal fields are `b2cName`, `aliases`, `b2cPrice`, `costPrice` and the derived `discountPercent`, `b2cPriceMissing`, `sameNameAsB2c`; callers without the action get `ProductResponse`, which has none of them (P3-13, P7-04).

**Current restriction:** `POST /products` and `POST /products/import` require `PRODUCTS:VIEW_INTERNAL_B2C` as well as Products write, because the B2C name is required. D-17 (deactivate, delete only if unreferenced) and D-18 (preview → confirm import) are approved.

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | /products | PRODUCTS:READ | `q` (SKU, partner name, category label; B2C name only for internal callers), `category`, `active`, `b2cMissing` (internal only, ignored otherwise), paging, `sort` ∈ sku, partnerName, category, partnerPrice, updatedAt (default category, SKU) |
| GET | /products/meta | PRODUCTS:READ | categories with total/active counts, default GST (company settings), default unit `nos`, max alternative names (20) |
| GET | /products/options | PRODUCTS:READ **or** ESTIMATES:WRITE (P8-04) | active products for document line pickers, at most 50, `q` and `category` filters: `id, sku, category, name, hsn, unit, rate, gstPercent` (+ `b2cName, b2cRate` for internal callers) |
| GET | /products/{id} | PRODUCTS:READ | internal or partner-safe view |
| GET | /products/{id}/history | PRODUCTS:READ | audited changes, newest first; internal fields removed for other callers, and internal-only updates hidden |
| POST | /products | PRODUCTS:WRITE + internal | `403 INTERNAL_B2C_REQUIRED` without the action (the B2C name is required). `400` field errors; duplicate SKU (ignoring case) → `sku: "That SKU already exists."` |
| PUT | /products/{id} | PRODUCTS:WRITE | needs `version` (`409 CONCURRENT_MODIFICATION` when stale). Without the action, internal fields must be omitted and stay unchanged; sending any → `403 INTERNAL_B2C_REQUIRED` |
| PATCH | /products/{id}/status | PRODUCTS:WRITE | `{active, version}`; deactivate/activate (D-17) |
| DELETE | /products/{id} | PRODUCTS:DELETE | `204`; `409 PRODUCT_IN_USE` while any estimate, invoice or B2C document line references it ("Deactivate it instead") |
| GET | /products/export | PRODUCTS:EXPORT | CSV (UTF-8 with BOM) honouring `q`, `category`, `active`, `b2cMissing`. Internal: `sku, category, b2c_name, cp_name, cp_alternatives (\| separated), hsn, gst_percent, unit, b2c_price, cp_price, cost, active`; otherwise `sku, category, cp_name, hsn, gst_percent, unit, cp_price, active` |
| GET | /products/import-template | PRODUCTS:WRITE | header plus one example row in a valid category (D-18) |
| POST | /products/import?dryRun=true | PRODUCTS:WRITE + internal | multipart `file` (≤ 1 MB, ≤ 5,000 rows). Returns `{dryRun, total, created, updated, unchanged, failed, columns, rows[{row, sku, outcome CREATE/UPDATE/UNCHANGED/ERROR, changes, errors, warnings}]}`. `dryRun=false` saves every valid row in one transaction and skips rows with errors. `400` for a missing file, missing required columns or no data rows; `413 FILE_TOO_LARGE` |

Server-side entry point for Phases 8–9: `ProductService.lineSnapshot(id)` returns the §6.2 line snapshot (`name` = partner name, `rate` = partner price, `b2cName`, `b2cRate` = B2C price or 0, `hsn`, `unit`, `gstPercent`) and refuses deactivated products with `422 PRODUCT_INACTIVE`.

### 3.6 Estimates (src: newEst, editDoc, delDoc, estStatus, viewDoc, dlDoc) — as built in Phase 8

Permissions use the existing codes: `ESTIMATES:READ/WRITE/DELETE/EXPORT`; fee override `PARTNERS:APPROVE` (D-13, P8-07). D-19, D-13, D-09/D-26 and the restricted lead picker were **approved on 2026-10-26**; the CGST/SGST split is provisional (D-21). Every response here is partner-safe: no B2C names or prices, no twin number, no uplift (P8-11).

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | /estimates | ESTIMATES:READ | `q` (estimate no., lead no., client, partner), `status`, `partnerId`, `leadId`, `expired`, paging, `sort` ∈ estimateDate, estimateNumber, validUntil, createdAt (default date, number desc). Rows: number, dates, status, `expired`, lead no., client, partner, bill-to, taxable, total |
| GET | /estimates/meta | ESTIMATES:READ | statuses, bill-to options, validity days, default terms, default GST, max lines (100), `canOverrideFee` |
| GET | /estimates/export | ESTIMATES:EXPORT | CSV (UTF-8 BOM, ≤ 5,000 rows) with the list filters: `estimate, date, valid_until, status, expired, lead, client, partner, bill_to, taxable_amount, grand_total` |
| GET | /estimates/lead-options | ESTIMATES:WRITE (no Leads read needed) | `q` (lead number, client name, partner name only), `leadId` (that lead if eligible). At most 50 live, non-Lost leads, newest first: `id, leadNumber, clientName, partner{id, name}`. Estimates scope must be ALL and, when the user has Leads access, so must the Leads scope; otherwise empty (P8-15) |
| POST | /estimates/preview?estimateId= | ESTIMATES:WRITE | body as POST; nothing saved. `{lines, totals, defaultFeePercent, feePercent, feeOverride}` plus `internal {lines, totals, uplift, missingB2cPrices}` only for `PRODUCTS:VIEW_INTERNAL_B2C` |
| POST | /estimates | ESTIMATES:WRITE | 201. `{leadId, igst, estimateDate?, validUntil?, discountPercent?, billTo?, feePercent?, feeOverrideReason?, notes?, lines:[{productId, quantity, unitPrice, gstPercent}]}`. `igst` is required (`400 igst`, D-21). Number `EST-YYYY-NNNNNN` from the creation date's calendar year, immutable (P8-01); the twin is written in the same transaction. Lost, deleted or out-of-scope lead → `400 leadId` |
| GET | /estimates/{id} | ESTIMATES:READ | partner copy with lines, totals (incl. CGST/SGST), lead and partner refs, `expired`, `sentOn`, `version` |
| PUT | /estimates/{id} | ESTIMATES:WRITE | same body + `version` (`igst` omitted keeps the stored choice); lines with their saved `id` keep their snapshot (P8-06). Allowed in Draft, Sent, Accepted and Rejected; the status never changes. `409 ESTIMATE_INVOICED`, `409 CONCURRENT_MODIFICATION`. Audited as `ESTIMATE_UPDATED` |
| POST | /estimates/{id}/send · /accept · /reject | ESTIMATES:WRITE | optional `{version}`. Draft → Sent → Accepted/Rejected, lead side effects (P8-10). `409 INVALID_STATUS_TRANSITION`; accepting for a Lost lead without `LEADS:APPROVE` → `403 STAGE_REOPEN_NOT_PERMITTED` |
| DELETE | /estimates/{id} | ESTIMATES:DELETE | 204, soft delete in Draft, Sent, Accepted and Rejected (status kept, audited as `ESTIMATE_DELETED`); `409 ESTIMATE_INVOICED` |
| GET | /estimates/{id}/history | ESTIMATES:READ | audited changes, newest first (partner-safe values only) |
| GET | /estimates/{id}/document | ESTIMATES:READ | printable partner copy, `text/html` attachment `<number>.html` (no server PDF, D-15) |

Fee rules (D-13, approved): default = 0 for partner purchases, else the lead's locked rate, else the partner's tier rate. A different value needs `PARTNERS:APPROVE` (`403 FEE_OVERRIDE_NOT_PERMITTED`) and `feeOverrideReason` (`400`). The audit row stores the previous rate, new rate, default rate and reason, with actor and time. Documents are printable HTML until server PDFs are added (mandatory before production, D-15). Convert to invoice: `POST /invoices/from-estimate` (§3.7, Phase 9).

### 3.7 Invoices (src: newInv, editDoc, cancelInv, recPay) — as built in Phase 9

Permissions use the existing codes: `INVOICES:READ/WRITE/DELETE/EXPORT/APPROVE` (APPROVE is supported on Invoices since V16; nobody is granted it by the migration). Lifecycle Draft → Issued → Cancelled; only drafts are editable (DECISIONS.md §J). Every response here is partner-safe: no B2C names or prices, no twin number, no uplift. GSTINs in audit rows are masked.

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | /invoices | INVOICES:READ | `q` (invoice no., estimate no., lead no., client, billing name, partner), `status` (DRAFT, ISSUED, CANCELLED), `payment` (UNPAID, PARTIALLY_PAID, PAID, OVERDUE), `partnerId`, `leadId`, `estimateId`, `from`/`to` (invoice date), paging, `sort` ∈ invoiceDate, invoiceNumber, dueDate, createdAt (default createdAt desc). Rows: number (null for drafts), status, dates, derived payment status, lead, billing name, partner, estimate no., tax type, taxable, total, paid, balance |
| GET | /invoices/meta | INVOICES:READ | statuses, payment filters, bill-to, GST states (code, name, UTGST), place-of-supply bases, default due days, terms, default GST, max lines, `canOverrideFee`, `canApprove`, `canCancel`, `supplierComplete`, `supplierStateCode` |
| GET | /invoices/export | INVOICES:EXPORT | CSV (UTF-8 BOM) with the list filters; partner-safe columns |
| GET | /invoices/lead-options | INVOICES:WRITE (no Leads read needed) | `q`, `leadId`. At most 50 live, non-Lost leads: `id, leadNumber, clientName, partner{id, name}`. The Invoices scope must be ALL (and the Leads scope too, when the user has Leads access) (P9-07) |
| GET | /products/options | PRODUCTS:READ or ESTIMATES:WRITE or INVOICES:WRITE | restricted active-product picker (P8-04); B2C fields only with `PRODUCTS:VIEW_INTERNAL_B2C` |
| POST | /invoices/preview?invoiceId= | INVOICES:WRITE | body as POST/PUT; nothing saved. `{lines (with per-line tax), totals, tax {taxType, utgst, stateTaxLabel, missing, reasons, confirmationRequired, confirmed…}, comparison (converted drafts), defaultFeePercent, feePercent, feeOverride}` plus `internal` only for `PRODUCTS:VIEW_INTERNAL_B2C` |
| POST | /invoices | INVOICES:WRITE | 201, direct draft. `{leadId, billTo?, discountPercent?, feePercent?, feeOverrideReason?, notes?, dueDays?, billingName?, billingAddress?, billingStateCode?, billingGstin?, placeOfSupplyCode?, placeOfSupplyBasis?, lines:[{productId, quantity, unitPrice, gstPercent}]}`. A changed GST % needs INVOICES:APPROVE (`400 lines[i].gstPercent`); a fee override needs PARTNERS:APPROVE + reason. Audited `INVOICE_CREATED` |
| POST | /invoices/from-estimate | INVOICES:WRITE + ESTIMATES:READ | 201. `{estimateId}`. `409 ESTIMATE_DELETED`, `409 ESTIMATE_NOT_CONVERTIBLE` (Draft/Rejected), `409 ESTIMATE_ALREADY_INVOICED` (live invoice exists), `409 LEAD_REQUIRED`. The estimate becomes Invoiced. Audited `INVOICE_CONVERTED` + `ESTIMATE_STATUS_CHANGED` (P9-01) |
| GET | /invoices/{id} | INVOICES:READ | partner copy: supplier and billing parties, place of supply and basis, tax treatment, lines with taxable/CGST/SGST/IGST/tax/total, totals, estimate comparison (drafts), paid, balance, derived payment status, issue and cancellation details, `version` |
| PUT | /invoices/{id} | INVOICES:WRITE | drafts only (`409 INVOICE_ISSUED` / `INVOICE_CANCELLED`), same body + `version`. Converted drafts: lead, bill-to, discount, fee and lines must be omitted or unchanged (`400`). Changing tax inputs clears a tax confirmation. Audited `INVOICE_UPDATED` (changed keys only) |
| POST | /invoices/{id}/confirm-tax | INVOICES:APPROVE | `{version, note}` (note required, ≤ 500). Only when a confirmation is required. Audited `INVOICE_TAX_CONFIRMED` |
| POST | /invoices/{id}/issue | INVOICES:WRITE | `{version, expectedGrandTotal?, regularSupplyConfirmed}` (`regularSupplyConfirmed: true` required since Phase 10, `400` otherwise). `422 SUPPLIER_REGISTRATION_INCOMPLETE` (also while the e-invoicing declaration is missing), `422 EINVOICING_NOT_SUPPORTED`, `422 BILL_OF_SUPPLY_NOT_SUPPORTED` (all lines at 0% GST), `400` for missing billing name/address/place of supply/basis/lines, `422 TAX_CONFIRMATION_REQUIRED`, `409 TOTALS_CHANGED`, `409 LEAD_REQUIRED`; Lost lead without LEADS:APPROVE → `403 STAGE_REOPEN_NOT_PERMITTED`. Assigns `INV-YYYY-NNNNNN`, dates, supplier snapshot, B2C twin; lead → Won. Audited `INVOICE_ISSUED` |
| POST | /invoices/{id}/cancel | INVOICES:DELETE | `{version, reason}` (reason required, ≤ 500). `409 INVOICE_DRAFT`, `409 INVOICE_CANCELLED`, `409 INVOICE_HAS_PAYMENTS` (a live, unreversed receipt or any payout; since Phase 10). Number never reused. Audited `INVOICE_CANCELLED` |
| DELETE | /invoices/{id} | INVOICES:DELETE | 204, discards a draft (soft delete); a converted draft restores the estimate's previous status. Issued → `409 INVOICE_ISSUED`. Audited `INVOICE_DRAFT_DELETED` |
| GET | /invoices/{id}/history | INVOICES:READ | audited changes, newest first (partner-safe values, masked GSTINs) |
| GET | /invoices/{id}/document | INVOICES:READ | printable tax invoice, `text/html`; drafts print "DRAFT INVOICE · not a tax invoice", cancelled invoices a CANCELLED stamp |
| GET | /internal/invoices/{id} · /{id}/document | INTERNAL_B2C:READ + INVOICES:READ | internal B2C twin (`INT-INV-…`) of an issued invoice; draft → `409 INTERNAL_COPY_NOT_READY`; stamped INTERNAL ONLY |
| GET | /settings/gst-registration | SETTINGS:READ or INVOICES:READ | `{legalName, address, stateCode, stateName, gstin, einvoicingApplicable, complete, version, states}` (`complete` also needs the e-invoicing declaration) |
| PUT | /settings/gst-registration | SETTINGS:WRITE | `{legalName, address, stateCode, gstin, einvoicingApplicable, version}` (`einvoicingApplicable` required since Phase 10); GSTIN format, state code, check character and state prefix are validated. Audited `GST_REGISTRATION_UPDATED` (GSTIN masked). Issued invoices keep their snapshot |

Payments against invoices are recorded under `/payments` (§3.8, Phase 10); the invoice's ledger is `GET /payments?invoiceId=`. Credit notes are not implemented.

### 3.8 Payments (src: recPay, delPay) — as built in Phase 10

Permissions use the existing codes: `PAYMENTS:READ/WRITE/EXPORT` and the action `PAYMENTS:REVERSE_PAYMENT` (V12; granted by no migration). There is no edit or delete: the ledger is append-only, and a mistaken receipt is reversed (DECISIONS.md §K). Amounts are BigDecimal strings or numbers, include GST, and are computed on the server. All responses are partner-safe.

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | /payments | PAYMENTS:READ | `q` (payment no., reference, invoice no., billing name, partner), `method` (UPI, NEFT_RTGS, CHEQUE, CASH, CARD), `type` (RECEIPT, REVERSAL, REFUND), `state` (ACTIVE, REVERSED: receipts), `invoiceId`, `partnerId`, `from`/`to` (payment date), paging, `sort` ∈ paymentDate, paymentNumber, amount, createdAt (default paymentDate desc). Rows: number, type, date, amount, net, method, reference, invoice {id, number}, billing name, partner, `reversed`, `reversalOf`, `reversalReason` |
| GET | /payments/report | PAYMENTS:READ | same filters: `{entries, receipts, reversals, received, reversed, refunded, netReceived, netReceivedExGst, byMethod[{method, label, receipts, netReceived}], receivables{outstandingInvoices, outstandingAmount, overdueInvoices, overdueAmount}}`. Receivables cover live issued invoices with balance > ₹0.50 and are zero unless the Payments (and Invoices) scope is ALL |
| GET | /payments/meta | PAYMENTS:READ | methods, entry types, states, `referenceOptional` (CASH), `maxReason`, `maxNotes`, `canWrite`, `canReverse`, `canExport` |
| GET | /payments/invoice-options | PAYMENTS:WRITE (no Invoices read needed) | `q`, `invoiceId`. At most 50 issued invoices with a balance: `id, invoiceNumber, invoiceDate, dueDate, billingName, partner{id, name}, grandTotal, paidAmount, balance, paymentStatusLabel` |
| GET | /payments/export | PAYMENTS:EXPORT | CSV (UTF-8 BOM, `no-store`, formula-guarded), list filters, ≤ 5,000 rows; columns: payment, type, date, invoice, billed_to, partner, method, reference, amount_incl_gst, amount_ex_gst, reversed, reversal_of, reversal_reason, recorded_at |
| GET | /payments/{id} | PAYMENTS:READ | entry + `invoice{id, number, status, billingName, invoiceDate, dueDate, grandTotal, taxableAmount, paidAmount, balance, paymentStatus}`, `reversal{id, number, date, reason, createdAt, createdByName}` (reversed receipts), `reversalOf` and `reversalReason` (reversal entries), `notes`, `canReverse`, `reverseBlockedBy`, created by/at |
| GET | /payments/{id}/history | PAYMENTS:READ | `PAYMENT_RECORDED` / `PAYMENT_REVERSED` audit rows (references masked) |
| POST | /payments | PAYMENTS:WRITE | `{invoiceId, paymentDate, amount, method, reference?, notes?, idempotencyKey (UUID), confirmDuplicate?}` → **201** with the entry, or **200** with the existing one when the key is replayed with the same body. `400` field errors (date in the future or before the invoice date, amount ≤ 0 / > 2 decimals / too large, missing reference except for Cash, card-like reference, lengths). `409 INVOICE_DRAFT`, `409 INVOICE_CANCELLED`, `409 INVOICE_SETTLED`, `422 OVERPAYMENT`, `409 DUPLICATE_REFERENCE`, `409 POSSIBLE_DUPLICATE` (resend with `confirmDuplicate: true`), `409 IDEMPOTENCY_KEY_REUSED`. Audited `PAYMENT_RECORDED` |
| POST | /payments/{id}/reverse | PAYMENTS:READ + PAYMENTS:REVERSE_PAYMENT | `{reason}` (1–500). Adds a `REVERSAL` entry dated today mirroring the receipt; the receipt is kept. `409 PAYMENT_IS_REVERSAL`, `409 PAYMENT_NOT_REVERSIBLE` (legacy refund), `409 PAYMENT_ALREADY_REVERSED`, `409 PAYMENT_HAS_PAYOUT` (Phase 11). Returns the receipt with `reversal`. Audited `PAYMENT_REVERSED` |

Not provided (DECISIONS.md §K "Needs approval"): refunds, credit/debit notes, advances, customer TDS, payout accrual (Phase 11). The source's toast with payout and tier-upgrade results moves to Phase 11.

### 3.9 Payouts (src: payPayout) — as built in Phase 11

Rules, lifecycle and the reversal behaviour are in DECISIONS.md §L. Every command needs the PAYOUTS scope ALL (`409 PAYOUTS_SCOPE`). Every transition takes the `version` the user saw (`409 CONCURRENT_MODIFICATION` when stale), locks the payout and is audited. No endpoint moves money or calls a bank.

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | /payouts | PAYOUTS:READ | `q` (number, bank reference, partner name or code), `status`, `partnerId`, `from`/`to` (created), paging; sort `createdAt`, `asOfDate`, `payoutNumber`, `commissionTotal`, `netAmount`, `paidDate`. Summary rows with `blocked` when a source receipt was reversed |
| GET | /payouts/dashboard | PAYOUTS:READ | Counts and totals by status, eligible commission and receipts, carried and pending adjustments, paid and TDS this FY, partners with a balance, `tdsConfigured` |
| GET | /payouts/meta | PAYOUTS:READ | Statuses, adjustment statuses, limits, TDS readiness and message, and the caller's `canWrite/canApprove/canMarkPaid/canExport/canConfigureTds/canViewSensitive` |
| GET | /payouts/export | PAYOUTS:EXPORT | Same filters; CSV (BOM, formula-guarded, ≤ 5,000 rows). No PAN or bank account; the transfer's bank reference (UTR) is included in full for reconciliation |
| GET | /payouts/partners | PAYOUTS:READ | Partner balances (`q`): eligible commission and receipts, carried and pending adjustments, in progress, paid and TDS this FY, total paid, last paid, PAN/bank readiness. Not paginated |
| GET | /payouts/partners/{partnerId} | PAYOUTS:READ | One partner's balance (zeros when there is nothing) |
| POST | /payouts/preview | PAYOUTS:READ | `{partnerId, asOfDate?}` → lines, totals, TDS, net, `canCreate`, `blockers`, `openPayout`. Nothing is saved |
| POST | /payouts | PAYOUTS:WRITE | `{partnerId, asOfDate?, notes?, idempotencyKey}` → **201** draft, or **200** with the same payout when the key is replayed with the same body. `409 PAYOUT_OPEN_EXISTS`, `409 NOTHING_TO_PAY`, `409 IDEMPOTENCY_KEY_REUSED` |
| GET | /payouts/{id} | PAYOUTS:READ | Payout with partner (PAN/bank masked without VIEW_PARTNER_SENSITIVE), lines, released lines, TDS breakdown, calculations, workflow, `reversedSources`, `blockers` and the caller's `actions`. `receiptsTotal` + `adjustmentsTotal` = `commissionTotal`, also for rejected, cancelled and failed payouts (lines released on closing still count) |
| GET | /payouts/{id}/history | PAYOUTS:READ | Audit rows `PAYOUT_*` |
| POST | /payouts/{id}/recalculate | PAYOUTS:WRITE | `{version}`. Draft or pending. The status is unchanged: a pending payout stays Pending approval. Releases reversed lines, adds new receipts and approved adjustments, recalculates TDS and discards a manual TDS change |
| POST | /payouts/{id}/tds-adjustment | PAYOUTS:WRITE | `{version, tdsAmount, reason}`. Draft or pending; `0 ≤ tds < commission`. Its author can't approve the payout afterwards |
| POST | /payouts/{id}/submit | PAYOUTS:WRITE | `{version}`. Draft → pending; `409 PAYOUT_BLOCKED` with reversed sources |
| POST | /payouts/{id}/approve | PAYOUTS:APPROVE | `{version, note?}`. `409 SELF_APPROVAL` (creator, submitter, or author of the manual TDS change in force), `409 TDS_NOT_READY`, `409 PARTNER_NOT_PAYOUT_READY`, `409 PAYOUT_BLOCKED`, `409 PAYOUT_RECALCULATION_REQUIRED` |
| POST | /payouts/{id}/reject | PAYOUTS:APPROVE | `{version, reason}` |
| POST | /payouts/{id}/cancel | PAYOUTS:WRITE (draft, pending) or APPROVE (approved) | `{version, reason}` |
| POST | /payouts/{id}/processing | PAYOUTS:READ + MARK_PAYOUT_PAID | `{version, note?}`. Approved → processing; records the account's last four digits |
| POST | /payouts/{id}/mark-paid | PAYOUTS:READ + MARK_PAYOUT_PAID | `{version, paidDate, reference, acknowledgeReversedSources?}`. `409 PAYOUT_FY_MISMATCH`, `409 PAYOUT_BLOCKED` |
| POST | /payouts/{id}/fail | PAYOUTS:READ + MARK_PAYOUT_PAID | `{version, reason}` |
| GET | /payouts/adjustments | PAYOUTS:READ | `q`, `partnerId`, `status`, `kind`, paging. Each row has `allocatedTo` and the caller's `canDecide` |
| GET | /payouts/adjustments/{id} | PAYOUTS:READ | |
| POST | /payouts/adjustments | PAYOUTS:WRITE | `{partnerId, amount (signed), reason, idempotencyKey}` → pending approval |
| POST | /payouts/adjustments/{id}/approve | PAYOUTS:APPROVE | `{note?}`; not by its creator (`409 SELF_APPROVAL`); `409 ADJUSTMENT_DECIDED` |
| POST | /payouts/adjustments/{id}/reject | PAYOUTS:APPROVE | `{note}` (required) |
| GET | /settings/tds | SETTINGS:READ or PAYOUTS:READ | In force, draft, history, `canEdit`, `canConfirm` |
| PUT | /settings/tds | SETTINGS:WRITE | `{sectionCode?, description?, ratePercent?, rateWithoutPanPercent?, fyThreshold?, effectiveFrom?, version?}` saves the single draft (may be incomplete) |
| POST | /settings/tds/confirm | PAYOUTS:APPROVE | `{version, note}`. `422 TDS_INCOMPLETE`, `409 SELF_APPROVAL` (last editor), `409 TDS_EFFECTIVE_DATE`, `409 TDS_NO_DRAFT` |
| POST | /partners/{id}/pan-verification | PARTNERS:WRITE + VIEW_PARTNER_SENSITIVE | `{verified, note, version}`; a note is required to verify |

Changed elsewhere: `POST /payments/{id}/reverse` no longer refuses receipts with payouts (`PAYMENT_HAS_PAYOUT` is gone); its audit row names the affected payout and any reversal adjustment. Invoice cancellation returns `409 INVOICE_IN_PAYOUT` while a commission line of the invoice sits in an unpaid payout.

### 3.10 Internal B2C (all require **B2C_VIEW**; never routed for PARTNER)

**As built in Phase 8 (estimates):** `B2C_VIEW` is the existing `INTERNAL_B2C:READ`.

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | /internal/estimates/{id} | INTERNAL_B2C:READ + ESTIMATES:READ | twin `INT-EST-YYYY-NNNNNN`: B2C lines (B2C name, partner name, B2C rate), B2C and partner totals, uplift, `b2cIncomplete`, `missingB2cPrices` |
| GET | /internal/estimates/{id}/document | INTERNAL_B2C:READ + ESTIMATES:READ | stamped "INTERNAL ONLY" HTML, attachment `INTERNAL-<twin number>.html` |

Planned (not built):
| GET | /internal/b2c-documents | `type`, `partnerId`, date range |
|---|---|---|
| GET | /internal/b2c-documents/{id} | |
| GET | /internal/b2c-documents/by-parent/{estimate|invoice}/{parentId} | used by the "Internal B2C copy" toggle |
| GET | /internal/b2c-documents/{id}/pdf · /print | stamped INTERNAL ONLY |
| GET | /internal/estimates/{id}/editor-context | per-line B2C rates for the editor (B2C_VIEW only) |

### 3.11 Reports (src: REPORTS, repCsv)
| GET | /reports | list of available report types (filtered by permission) |
|---|---|---|
| GET | /reports/{type} | `type ∈ funnel, partner-scorecard, receivables-ageing, payout-ledger, monthly-sales, sla-performance, tier-summary` (REPORT_READ) and `price-vs-b2c` (B2C_VIEW). Optional filters (enhancement): `from, to, fy, partnerId, stage, status` |
| GET | /reports/{type}/export | CSV with the source column headers |

### 3.12 Settings & administration
| GET/PUT | /settings | SETTINGS_READ / SETTINGS_WRITE | company + rules (validated ranges) |
|---|---|---|---|
| GET/PUT | /settings/tiers | SETTINGS_READ / SETTINGS_WRITE | 4 rows; names, fee, min, payout days |
| GET | /users | USER_MANAGEMENT:READ | `q`, `roleId`, `status`, `page`, `size` (≤200), `sort` (fullName, email, status, createdAt, lastLoginAt) — **implemented** |
| POST | /users | USER_MANAGEMENT:WRITE | `{fullName,email,roleId,temporaryPassword}` → 201; must change password at first sign-in. Non-Super-Admins: User role only — **implemented** |
| GET / PUT | /users/{id} | READ / WRITE | PUT `{fullName,email,roleId,version}`; role change Super Admin only, never on oneself; resets permissions to the role template — **implemented** |
| PATCH | /users/{id}/status | USER_MANAGEMENT:MANAGE_USERS | `{status: ACTIVE|DISABLED, version}`; disabling revokes all sessions; never oneself; last active Super Admin protected (`409 LAST_SUPER_ADMIN`) — **implemented** |
| GET / PUT | /users/{id}/permissions | READ / USER_MANAGEMENT:MANAGE_PERMISSIONS (Super Admin) | `{modules:[{moduleCode,read,write,delete,export,approve,dataScope}], actions:[code]}`; full replacement, validated (400 with field errors); `editable` tells the client whether the viewer may change it — **implemented** |
| — | password reset by an administrator | — | not in Phase 3 (an admin can create a new temporary account; self-service reset is not specified) |
| GET | /roles, /roles/{id} | USER_MANAGEMENT:READ or ROLE_MANAGEMENT:READ | `userCount` only with ROLE_MANAGEMENT:READ — **implemented** |
| PATCH | /roles/{id} | ROLE_MANAGEMENT:WRITE (Super Admin) | `{displayName, description, version}`; `code`/`id` changes → 400; duplicate name → `409 DUPLICATE_ROLE_NAME`; no create/delete endpoints by design — **implemented** |
| GET | /audit-logs | AUDIT_LOGS:READ | `entityType`, `entityId`, `action`, `userId`, `from`, `to`, paging; newest first; values are parsed JSON with secrets redacted — **implemented** |
| GET | /actuator/health, /actuator/info | public (health details internal only) | |

## 4. Differences from the prompt's suggested endpoint list

| Prompt | Final | Reason |
|---|---|---|
| `PATCH /invoices/{id}/status` | `POST /invoices/{id}/cancel` | Cancel is the only manual transition. Payment status is derived. |
| `PATCH /leads/{id}/stage` | kept, + reopen permission | D-04 |
| `POST /estimates/{id}/convert-to-invoice` | returns a prefill, the client then POSTs `/invoices` | Matches the source flow (opens the editor prefilled, user may edit before saving) |
| *(none)* | `/estimates/{id}/send|accept|reject` | Source estimate status actions |
| *(none)* | tier-review preview/apply, partner statement, product import dry-run, payout reverse, `/estimates/preview` | Source actions / production needs |
| `/reports/{reportType}` | kept, with 8 named types | SRS FR-10 |

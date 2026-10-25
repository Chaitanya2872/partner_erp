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
| GET | /leads | LEADS:READ | `q` (client, city, partner name), `stage`, `partnerId`, `conflict`, `slaBreach` (true/false), `page`, `size` (20), `sort` ∈ createdAt, clientName, city, budget, expectedClose (default createdAt desc). Rows: partner, stage, budget, `acknowledgedAt`, `lockedFeePercent`, `protectionUntil`, `protectionActive`, `conflict`, `sla` block. Deleted leads never appear |
| GET | /leads/meta | LEADS:READ | stages, project types, referring partners (name, status, tier name, current fee %), SLA targets and protection days |
| POST | /leads | LEADS:WRITE | 201. Stage defaults to NEW. Phone protected for another partner: saved with `conflict: true` and `conflictPartnerName` (warning only, BR-L6/D-05) |
| GET | /leads/{id} | LEADS:READ | + `sla` block (ackHours, ackBreach, estimateDays, estimateBreach, hasEstimate, targets, protectionDaysLeft), `acknowledgedByName`, current `conflictPartnerName` |
| PUT | /leads/{id} | LEADS:WRITE (+APPROVE to leave Won/Lost) | with `version`; re-runs the conflict check; `stage` + optional `stageReason` follow D-04; the partner can change until acknowledgement, never after (P6-06, approved) |
| PATCH | /leads/{id}/stage | LEADS:WRITE (+APPROVE to leave Won/Lost, else `403 STAGE_REOPEN_NOT_PERMITTED`) | `{stage, reason?, version}` |
| POST | /leads/{id}/acknowledge | LEADS:APPROVE | open leads only: `422 LEAD_CLOSED` for Won/Lost (reopen first, P6-13). Locks the partner's current tier fee, protection = today + protection days, NEW → REGISTERED; `409 LEAD_ALREADY_ACKNOWLEDGED` |
| DELETE | /leads/{id} | LEADS:DELETE | 204, soft delete (D-26); `409 LEAD_HAS_DOCUMENTS` while estimates, invoices, payments or internal documents point at it |
| GET | /leads/{id}/history | LEADS:READ | stage history, oldest first, with actor names and reasons |

### 3.5 Products (src: newItem, editItem, delItem, impItems, expItems, tplItems)

Phase 3 implements the read side only: `GET /products` (`q`, `category`, paging) and `GET /products/{id}`, both `PRODUCTS:READ`; internal fields only with `PRODUCTS:VIEW_INTERNAL_B2C`. Maintenance endpoints below arrive in Phase 7.

| GET | /products | PRODUCT_READ | `q`, `category`, `active`, `b2cMissing`. B2C/cost fields only with B2C_VIEW |
|---|---|---|---|
| POST | /products | PRODUCT_WRITE (+B2C_PRICE_WRITE for b2c/cost) | |
| GET | /products/{id} | PRODUCT_READ | |
| PUT | /products/{id} | PRODUCT_WRITE | |
| PATCH | /products/{id}/status | PRODUCT_WRITE | activate/deactivate (D-17) |
| POST | /products/import?dryRun=true | PRODUCT_IMPORT | multipart CSV → per-row result; `dryRun=false` commits in one transaction |
| GET | /products/export | PRODUCT_READ | CSV. B2C/cost columns only with B2C_VIEW |
| GET | /products/import-template | PRODUCT_READ | CSV template |

### 3.6 Estimates (src: newEst, editDoc, delDoc, estStatus, toInv, viewDoc, dlDoc)
| GET | /estimates | DOC_READ | `q` (no/partner/client), `status`, `partnerId`, `leadId`, `expired` |
|---|---|---|---|
| POST | /estimates | DOC_WRITE | `{leadId, estimateDate, validUntil?, discountPercent, igst, billTo, feePercent?, notes, lines:[{productId, quantity, unitPrice, gstPercent}]}`. feePercent ≠ default needs DOC_FEE_OVERRIDE + `feeOverrideReason`. Creates twin in the same transaction. |
| GET | /estimates/{id} | DOC_READ | partner-safe DTO |
| PUT | /estimates/{id} | DOC_WRITE | blocked when INVOICED (D-09) |
| DELETE | /estimates/{id} | DOC_WRITE | 422 when INVOICED (D-26); cascades twin |
| POST | /estimates/{id}/send · /accept · /reject | DOC_WRITE | status transitions + lead side effects (BR-E4, §4.3) |
| POST | /estimates/{id}/convert-to-invoice | DOC_WRITE | returns invoice draft payload (prefill). The invoice is created by `POST /invoices` with `estimateId` |
| POST | /estimates/preview | DOC_WRITE | server-side totals for the editor (no persistence) |
| GET | /estimates/{id}/pdf · /print | DOC_READ | partner copy only |

### 3.7 Invoices (src: newInv, editDoc, cancelInv, recPay)
| GET | /invoices | DOC_READ | `q`, `paymentStatus` (UNPAID, PARTLY_PAID, PAID, OVERDUE, PARTLY_PAID_OVERDUE, CANCELLED), `partnerId`, date range |
|---|---|---|---|
| POST | /invoices | DOC_WRITE | as estimate + `dueDate?`, `estimateId?`. Side effects BR-V3 |
| GET | /invoices/{id} | DOC_READ | includes paid, balance, derived paymentStatus |
| PUT | /invoices/{id} | DOC_WRITE | only if no active payments and not cancelled (D-09) |
| POST | /invoices/{id}/cancel | INVOICE_CANCEL | `{reason}`. Only if paid = 0 (BR-V5). *Replaces the prompt's generic `PATCH /invoices/{id}/status`, because cancellation is the only manual status change in the source.* |
| GET | /invoices/{id}/payments | PAYMENT_READ | |
| GET | /invoices/{id}/pdf · /print | DOC_READ | partner copy only |

### 3.8 Payments (src: recPay, delPay)
| GET | /payments | PAYMENT_READ | date range, partnerId, invoiceId, refund, status |
|---|---|---|---|
| POST | /payments | PAYMENT_WRITE | `{invoiceId, paymentDate, amount, method, reference, refund}` + Idempotency-Key → `{payment, payout?, tierUpgrade?, message}` (source toast text) |
| GET | /payments/{id} | PAYMENT_READ | |
| POST | /payments/{id}/reverse | PAYMENT_REVERSE | `{reason}`. 422 if payout PAID (D-11) |

### 3.9 Payouts (src: payPayout)
| GET | /payouts | PAYOUT_READ | `status=open|paid|all`, partnerId, overdue. Returns displayStatus (Paid/Clawback/Due/Accrued) |
|---|---|---|---|
| GET | /payouts/summary | PAYOUT_READ | per-partner unpaid balance tiles |
| GET | /payouts/{id} | PAYOUT_READ | + `suggestedTds` |
| POST | /payouts/{id}/mark-paid | PAYOUT_PAY | `{paidDate, utr, tdsAmount}`. 422 if PAN/bank missing and fee > 0 |
| POST | /payouts/{id}/reverse | PAYOUT_REVERSE | `{reason}` (D-12) |

### 3.10 Internal B2C (all require **B2C_VIEW**; never routed for PARTNER)
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

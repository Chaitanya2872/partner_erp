# IOTIQ Partner ERP — Database Design

**Phase:** 0 · **Date:** 2026-10-24 · **Engine:** PostgreSQL (local dev 17.6; target ≥ 16)
**Inputs:** ER diagram (`docs/database/IOTIQ_Partner_ERP_ER_Diagram.md`), source data shapes (`cps, leads, items, estimates, invoices, payments, payouts, b2c_docs, settings`), SRS §6.

Conventions: `uuid` PKs (`gen_random_uuid()`), `timestamptz` for instants, `date` for business dates, `NUMERIC(14,2)` money, `NUMERIC(6,3)` percentages, `NUMERIC(12,3)` quantity, `created_at/updated_at/created_by/updated_by` on mutable tables, `version bigint` for optimistic locking, snake_case names, enums as `varchar` + `CHECK` (easier to migrate than PG enums).

---

## 1. ER diagram validation

The ER has 17 tables, which is correct. Gaps against the source:

| ER table | Missing vs source (must add) | Other issues |
|---|---|---|
| partners | `city`, `contact_name` ✓present, `agreement_version`, `agreement_date`, `licence_no`, `bank_name`, `bank_account`, `ifsc`, `notes`, `programme` (ER: `strategic_programme` ✓), `grace` (ER: `downgrade_grace` ✓), `last_tier_review_on`, `status` values | ER uses `tier_id` FK. The source stores the tier *number*, and tier rows are fixed (1–4), so an FK to `partner_tiers` is fine. |
| leads | `city`, `units`, `presales_owner`, `expected_close`, `source`, `notes`, `locked_fee_pct`, `conflict`, `acknowledged_by` | `client_email`, `lead_number` are not in source (D-16). `created_by` FK is fine. |
| products | **`unit`**, `aliases`, `version` | `active` not in source (D-17, accepted as soft delete). |
| estimates | `discount_percent` ✓, **`igst`**, **`bill_to`**, **`fee_percent`**, `notes`, `sent_on`, `discount_amount`, `taxable_amount`, `created_by` | ER `subtotal/gst_amount/grand_total` OK, but `taxable` is also needed (all fee/net maths uses taxable). |
| estimate_items | `hsn`, `unit`, `b2c_name` snapshot, line ordering | ER `b2c_unit_price` ✓. `line_total` should be `qty×rate` (pre-discount, as displayed). |
| invoices | **`discount_percent`** (missing in ER), `igst`, `bill_to`, `fee_percent`, `notes`, taxable/discount amounts, `cancelled_at/by` | ER `status` must be lifecycle only (`OPEN/CANCELLED`). Payment status is derived (ADR-08). |
| invoice_items | as estimate_items | |
| payments | `lead_id` (source stores it), `is_refund` (ER `payment_type` covers it), `status` (ACTIVE/REVERSED), `idempotency_key`, reversal fields | `amount` signed (refund negative), `net_amount` ✓ |
| partner_payouts | **`invoice_id`**, **`base_amount`**, `accrued_on`, `utr`, `tier_payout_days` snapshot | ER `gross_commission` = source `fee`. `status` values: ACCRUED/PAID/REVERSED. |
| b2c_documents | `lead_id`, `parent_number`, `document_date`, `discount_percent`, `igst`, full partner & B2C total sets (sub/disc/taxable/tax/total), `uplift` (ER `price_difference`) | `estimate_id` and `invoice_id` should be mutually exclusive: CHECK exactly one, each UNIQUE. |
| b2c_document_items | `partner_name` (cpName), `hsn`, `unit`, line no | |
| company_settings | `address`, `terms`, `clawback_days`, `estimate_validity_days`, `default_gst_percent`, `home_state` (D-21) | Single-row table (`id` fixed / `singleton boolean UNIQUE CHECK`). |
| audit_logs | `ip_address`, `request_id` | jsonb diffs ✓ (redact bank/PAN) |

## 2. Additional tables: need assessment

| Candidate | Needed? | Purpose / reason |
|---|---|---|
| `refresh_tokens` | **Yes** | Hashed rotating refresh tokens, family id, revoked/replaced, expiry (ADR-07). |
| `document_sequences` | **Yes** | `(prefix, fy_tag) → last_value`, row-locked allocation, so concurrent saves can't produce duplicate numbers (D-19). |
| `lead_stage_history` | **Yes** | Lead history requirement. Records stage changes, acknowledgement and conflict flags with actor/time. Also gives SLA traceability. |
| `partner_tier_reviews` | **Yes** | Audit of tier review outcomes (from, to, action, revenue at review, actor). Without it, grace state has no history (D-07). |
| `product_aliases` | No, use a `text[]` column | Aliases are display-only, with no FK needs. |
| `partner_agreements` | **No (v1)** | The source models one current agreement (status/version/date) on the partner. Columns are enough, and history is covered by `audit_logs`. Revisit if multiple agreement documents/uploads are needed. |
| `financial_adjustments` | **No (v1)** | Refunds are negative payments. Clawbacks are negative payouts. Reversals are status changes. A credit-note feature (D-09 follow-up) would introduce it later. |
| `notifications` | **No (v1)** | Alerts are query-time derived (ADR-09). |
| `roles`/`user_roles`/`permissions` | roles + user_roles **yes**. `role_permissions` **yes** | Permissions are seeded per role (`API_DESIGN §2`). A small `permissions` + `role_permissions` pair makes role management (prompt §5.12) data-driven. |

Total: 17 core + `refresh_tokens`, `document_sequences`, `lead_stage_history`, `partner_tier_reviews`, `permissions`, `role_permissions` = **23 tables**.

## 3. Table definitions (target)

### Identity
- **users**: id, full_name, email (UNIQUE, lower-cased), password_hash, status (`ACTIVE|DISABLED|LOCKED`), partner_id NULL FK→partners (only for PARTNER users, D-22), must_change_password bool, failed_login_count, last_login_at, password_changed_at, audit cols, version.
- **roles**: id, name UNIQUE (`SUPER_ADMIN, ADMIN, SALES_MANAGER, SALES_EXECUTIVE, FINANCE, PARTNER`), description.
- **permissions**: id, code UNIQUE (e.g. `B2C_VIEW`, `PAYOUT_PAY`), description.
- **user_roles**: (user_id, role_id) PK.
- **role_permissions**: (role_id, permission_id) PK.
- **refresh_tokens**: id, user_id FK, token_hash UNIQUE, family_id, expires_at, revoked_at, replaced_by, created_at, user_agent, ip.

### Masters
- **partner_tiers**: id, tier_level SMALLINT UNIQUE CHECK 1–4, name, commission_percent, minimum_revenue NUMERIC(14,2), payout_business_days SMALLINT, version. Seeded with the 4 source rows.
- **partners**: id, partner_code UNIQUE (D-16), name NOT NULL, category CHECK(9 values), status CHECK(`PROSPECT|ONBOARDING|ACTIVE|INACTIVE`) default PROSPECT, contact_name, phone, email, city, address_line/state/pincode (optional, D-16), tier_id FK NOT NULL, programme bool, grace bool, last_tier_review_on date, agreement_status CHECK(`NOT_SENT|SENT|SIGNED`), agreement_version default 'v2.1', agreement_date, pan, gstin, licence_no, bank_name, bank_account, ifsc, notes, audit, version. Indexes: lower(name), status, tier_id. *PAN/bank are masked in DTOs and redacted in audit/logs. Column encryption is a Phase 15 option.*
- **products**: id, sku NOT NULL + UNIQUE INDEX on lower(sku), category CHECK(5 values), unit default 'nos', hsn, b2c_name NOT NULL, partner_name NOT NULL, aliases text[] default '{}', b2c_price NUMERIC(14,2) NULL (NULL/0 = "B2C price needed"), partner_price NUMERIC(14,2) NOT NULL ≥0, gst_percent ≥0 ≤100, cost_price NULL, active bool default true, audit, version.
- **company_settings**: single row. company_name, address, gstin, phone, email, terms, home_state ('Telangana'), lead_protection_days, clawback_days, tds_percent, tds_threshold, acknowledgement_hours, estimate_sla_days, estimate_validity_days, invoice_due_days, default_gst_percent, audit, version. CHECK ranges.

### Funnel
- **leads**: id, lead_number UNIQUE (D-16), partner_id FK NOT NULL, client_name NOT NULL, client_phone, client_phone_digits (generated: last 10 digits), client_email, city, project_type CHECK(6), units int, budget NUMERIC(14,2), stage CHECK(8) default NEW, presales_owner text, owner_user_id FK NULL (D-30), expected_close date, source, notes, acknowledged_at timestamptz, acknowledged_by FK, locked_fee_percent, protection_until date, conflict bool, deleted_at (D-26), created_by, audit, version. Indexes: (partner_id), (stage), (client_phone_digits, protection_until), created_at desc.
- **lead_stage_history**: id, lead_id FK, from_stage, to_stage, event (`CREATED|STAGE_CHANGED|ACKNOWLEDGED|AUTO_STAGE`), reason, actor_id, occurred_at.
- **partner_tier_reviews**: id, partner_id FK, from_level, to_level, action (`UPGRADE|DOWNGRADE|GRACE|RECOVERED|AUTO_UPGRADE`), revenue_12m, programme, actor_id NULL, payment_id NULL, reviewed_at.

### Sales
- **estimates**: id, estimate_number UNIQUE, partner_id FK, lead_id FK NOT NULL, estimate_date, valid_until, status CHECK(`DRAFT|SENT|ACCEPTED|REJECTED|INVOICED`), sent_on, discount_percent, igst bool, bill_to CHECK(`CLIENT|PARTNER`), fee_percent, notes, subtotal, discount_amount, taxable_amount, gst_amount, cgst_amount, sgst_amount, igst_amount, grand_total, created_by, audit, version. Index: (lead_id, estimate_date), partner_id, status.
- **estimate_items**: id, estimate_id FK ON DELETE CASCADE, line_no, product_id FK, description (partner name snapshot), b2c_name, hsn, unit, quantity, unit_price, b2c_unit_price, gst_percent, line_total. UNIQUE(estimate_id, line_no).
- **invoices**: id, invoice_number UNIQUE, partner_id FK, lead_id FK NOT NULL, estimate_id FK NULL, invoice_date, due_date, status CHECK(`OPEN|CANCELLED`), cancelled_at, cancelled_by, cancel_reason, discount_percent, igst, bill_to, fee_percent, notes, (same amount columns as estimates), created_by, audit, version. Indexes: partner_id, lead_id, estimate_id, (status, due_date), invoice_date.
- **invoice_items**: as estimate_items with invoice_id.
- **b2c_documents**: id, document_type CHECK(`ESTIMATE|INVOICE`), estimate_id UNIQUE NULL FK ON DELETE CASCADE, invoice_id UNIQUE NULL FK, CHECK((estimate_id IS NULL) <> (invoice_id IS NULL)), internal_number UNIQUE (`INT-`+parent no), parent_number, partner_id, lead_id, document_date, discount_percent, igst, partner_subtotal/discount/taxable/tax/total, b2c_subtotal/discount/taxable/tax/total, uplift (= b2c_taxable − partner_taxable), b2c_incomplete bool (any line with no B2C price), audit.
- **b2c_document_items**: id, b2c_document_id FK CASCADE, line_no, product_id, description (b2c name), partner_name, hsn, unit, quantity, unit_price, gst_percent, line_total.
- **document_sequences**: (prefix, fy_tag) PK, last_value int. Prefixes: EST, INV, LD, CP.

### Finance
- **payments**: id, invoice_id FK, partner_id FK, lead_id FK, payment_date, amount NUMERIC(14,2) ≠ 0 (negative = refund), net_amount, is_refund bool, payment_method CHECK(`UPI|NEFT_RTGS|CHEQUE|CASH|CARD`), reference_number, status CHECK(`ACTIVE|REVERSED`), reversed_at/by/reason, idempotency_key UNIQUE NULL, created_by, created_at. CHECK(is_refund = (amount<0)). Indexes: invoice_id, (partner_id, payment_date), payment_date.
- **partner_payouts**: id, payment_id UNIQUE FK (one payout per payment, BR-O1), partner_id FK, invoice_id FK, base_amount, commission_percent, gross_commission (signed), tds_amount, net_payout, accrued_on, due_date, tier_payout_days, paid_date, utr, status CHECK(`ACCRUED|PAID|REVERSED`), reversed_at/by/reason, audit, version. Indexes: (partner_id, status), (status, due_date), paid_date.

### Audit
- **audit_logs**: id, user_id NULL, entity_type, entity_id, action, old_values jsonb, new_values jsonb (sensitive keys redacted), ip_address, request_id, created_at. Index (entity_type, entity_id), created_at. Append-only (no UPDATE/DELETE grants for the app role in prd).

## 4. Transaction boundaries

| Operation | Single transaction contains |
|---|---|
| Save estimate | allocate number (new) → estimate + items → B2C twin + items → audit |
| Save invoice | allocate number → invoice + items → B2C twin → linked estimate → INVOICED → lead → WON (+history) → audit |
| Record payment | lock invoice row (`SELECT … FOR UPDATE`) → validate balance → payment → payout (if fee ≠ 0 and within window) → auto tier upgrade (+tier review row) → audit |
| Reverse payment | lock payment & payout → guard payout not PAID → payment REVERSED → payout REVERSED → audit |
| Mark payout paid | lock payout → validate PAN/bank → set paid fields → audit |
| Acknowledge lead | lock lead → set ack/lock fee/protection/stage → history → audit |
| Tier review apply | per partner: update tier/grace/last_review → tier review row → audit |

Isolation: READ COMMITTED with explicit row locks. Optimistic `version` on user-edited aggregates returns 409.

## 5. Derived views (read models)

- `v_invoice_balance` (invoice_id, paid = Σ active payment amounts, balance, payment_status per BR-V6, overdue flag). Used by invoices, dashboard, receivables, alerts.
- `v_partner_revenue_12m`: computed in the service with a parameter date (window depends on "today").
- Report queries live in `reports` module repositories. They must reuse the same derivation functions as the dashboard so figures reconcile (Phase 12 test).

## 6. Migration plan (replaces the empty V1–V9; see ADR-03)

| Version | Content | Phase |
|---|---|---|
| V1__baseline_extensions.sql | `pgcrypto`, common functions (`set_updated_at` trigger) | 1 |
| V2__identity.sql | users, roles, permissions, user_roles, role_permissions, refresh_tokens + role/permission seed | 2 (seed admin user in 3, via env-provided bootstrap, never a hard-coded password) |
| V3__settings_and_tiers.sql | company_settings (+ source defaults), partner_tiers (+4 rows) | 2 |
| V4__partners.sql | partners, partner_tier_reviews | 2 |
| V5__leads.sql | leads, lead_stage_history | 2 |
| V6__products.sql | products | 2 |
| V7__seed_products.sql | 29 products, `INSERT … ON CONFLICT (lower(sku)) DO NOTHING` (idempotent), b2c_price NULL | 2 |
| V8__sales_documents.sql | document_sequences, estimates, estimate_items, invoices, invoice_items, b2c_documents, b2c_document_items | 2 |
| V9__finance.sql | payments, partner_payouts | 2 |
| V10__audit.sql | audit_logs | 2 |
| V11__views.sql | v_invoice_balance | 2 |

All tables are created in Phase 2 (prompt Phase 2 = full schema). Later phases add only new versions.

## 7. Seed data (exact, from source)

### 7.1 Products (29). All: GST 18 %, unit `nos`, HSN blank, aliases empty, B2C price **unset**, cost unset.

| # | SKU | Category | Partner-facing name | B2C name | Partner price (₹ ex-GST) |
|---|---|---|---|---|---:|
| 1 | STI-2M | Smart Touch Interfaces | Smart Touch Interface - 2M (STI-2M) | 2 Module With Edge | 5749 |
| 2 | STI-4M | Smart Touch Interfaces | Smart Touch Interface - 4M (STI-4M) | 4 Module With Edge | 8049 |
| 3 | STI-6M | Smart Touch Interfaces | Smart Touch Interface - 6M (STI-6M) | 6 Module With Edge | 10349 |
| 4 | STI-8M | Smart Touch Interfaces | Smart Touch Interface - 8M (STI-8M) | 8 Module With Edge | 13799 |
| 5 | STI-12M | Smart Touch Interfaces | Smart Touch Interface - 12M (STI-12M) | 12 Module With Edge | 20699 |
| 6 | STI-18M | Smart Touch Interfaces | Smart Touch Interface - 18M (STI-18M) | 18 Module With No Edge | 27599 |
| 7 | SRC-6P1 | Smart Relay & Sensing | 6+1 Smart Relay controller | 6+1 Switch Controller | 8625 |
| 8 | SRC-6 | Smart Relay & Sensing | 6 Smart Relay controller | 6 Switch Controller | 8099 |
| 9 | SRC-5 | Smart Relay & Sensing | 5 Smart Relay controller | 5 Switch Controller | 7836 |
| 10 | SRC-4P1 | Smart Relay & Sensing | 4+1 Smart Relay controller | 4+1 Switch Controller | 6785 |
| 11 | SRC-4 | Smart Relay & Sensing | 4 Smart Relay controller | 4 Switch Controller | 6508 |
| 12 | SRC-3 | Smart Relay & Sensing | 3 Smart Relay controller | 3 Switch Controller | 5180 |
| 13 | SRC-2 | Smart Relay & Sensing | 2 Smart Relay controller | 2 Switch Controller | 3864 |
| 14 | SRC-1P1 | Smart Relay & Sensing | 1+1 Smart Relay controller | 1+1 Switch Controller | 3501 |
| 15 | SRH-15A | Smart Relay & Sensing | Smart Relay Heavy Module - 15A | HDR 15A | 2625 |
| 16 | SRH-25A | Smart Relay & Sensing | Smart Relay Heavy Module - 25A | HDR 25A | 3289 |
| 17 | PIR-01 | Smart Relay & Sensing | Occupancy Detection Module(PIR) | Motion Sensor | 2530 |
| 18 | IRB-01 | Smart Relay & Sensing | IR Bridge | IR Blaster | 3322 |
| 19 | TIK-01 | Smart Relay & Sensing | Touch Interface key | Automation Dongle | 1299 |
| 20 | STI-RMT | Smart Relay & Sensing | STI Remote | Touch Panel Remote | 920 |
| 21 | SG-10F | Smart Glide | Smart Glide 10F | 3.2 Curtain Controller | 22960 |
| 22 | SG-17F | Smart Glide | Smart Glide 17F | 5.2 Curtain Controller | 25300 |
| 23 | SG-23F | Smart Glide | Smart Glide 23F | 7.2 Curtain Controller | 27720 |
| 24 | SG-33F | Smart Glide | Smart Glide 33F | 10.2 Curtain Controller | 31900 |
| 25 | LK-ZURI | Locks & Entry | Yale Zuri Smart Lock | Yale Zuri Smart Lock | 14500 |
| 26 | LK-KYRA | Locks & Entry | Yale Kyra Pro Smart Lock | Yale Kyra Pro Smart Lock | 32999 |
| 27 | VDB-01 | Locks & Entry | One Touch Video Doorbell with Screen | One Touch Video Doorbell with Screen | 27560 |
| 28 | WIFI-BR | Locks & Entry | Wi-Fi Bridge (Smart Lock Accessories) | Wi-Fi Bridge (Smart Lock Accessories) | 6473 |
| 29 | BT-MOD | Locks & Entry | Bluetooth Module (Smart Lock Accessories) | Bluetooth Module (Smart Lock Accessories) | 4055 |

Category counts: Smart Touch Interfaces 6, Smart Relay & Sensing 14, Smart Glide 4, Locks & Entry 5, Services 0. Σ partner prices = ₹3,44,094 (seed checksum used by the Phase 2 test). Source ids `it_cp00…it_cp28` are kept as `legacy_id` comments in the seed for traceability.

### 7.2 Tiers and settings: see `BUSINESS_RULES.md §2`.

No partners, leads or documents are seeded in production. Dev-only demo data, if wanted, goes in a separate `db/dev-seed` location that is never run in `prd`.

## 8. Phase 2 implementation notes (as built, 2026-10-24)

V1–V11 are implemented as planned in §6 and applied to `partner_erp` (dev) and `partner_erp_test` (integration tests). The following choices are deliberate, so the schema doesn't pre-empt open decisions:

| Topic | As built | Why / decision |
|---|---|---|
| Pending-proposal columns | **Not created:** payment reversal fields and idempotency key (D-11), payout `REVERSED` status (D-12), partner address (D-16), CGST/SGST split and home state (D-21), `users.partner_id` (D-22), `deleted_at` soft delete (D-26), lead `owner_user_id` (D-30) | Source behaviour only, until approved. Each would be added in a new migration. |
| ER columns without source behaviour | `partners.partner_code`, `leads.lead_number`, `leads.client_email` exist, are **nullable**, and have no generation logic. `products.active` defaults to true | They are in the ER diagram; population rules are pending D-16/D-17. |
| Roles and permissions | 6 roles and 29 permission codes are seeded. **`role_permissions` is empty** and no users are seeded | The mapping is pending D-23. The bootstrap admin is created in Phase 3 from env, never with a hard-coded password. |
| Product B2C price / cost / HSN | `NULL` (the source stores 0 / blank) | NULL means "not set", so B2C totals can be flagged incomplete instead of silently showing ₹0 (SRS FR-09). No prices are invented. |
| Lead/estimate deletes | FKs from estimates, invoices, payments and B2C twins to `leads` are `ON DELETE SET NULL`. `estimates → invoices` is `SET NULL`. Items and B2C twins `CASCADE` from their parent | This matches the source's delete semantics (documents survive lead deletion), pending D-26. |
| Range checks | Discount, fee, GST and commission percentages are 0–100. Quantities and prices are ≥ 0. Settings are non-negative. `payments.amount <> 0` with `is_refund = (amount < 0)`. `PAID` payouts need `paid_date`. Tier level is 1–4 | Data integrity only; no source calculation changes. |
| Twin integrity | `ck_b2c_parent` requires exactly one parent, matching `document_type`. One twin per estimate/invoice (unique FKs) | The source keeps one twin per document (`'b2c_'+id`). |
| Phone conflict key | `leads.client_phone_digits` is a stored generated column holding the last 10 digits | It mirrors source `digits()` and is indexed with `protection_until` for the conflict check. |

JPA mapping:
- 21 entities, one per table. Join tables are mapped with `@ManyToMany`.
- Shared `BaseEntity`: UUID id and proxy-safe equality.
- `AuditableEntity`: created/updated at/by and `@Version`.
- `CreatedAuditEntity`: for append-only rows.
- `DocumentTotals` embeddable, reused on twins with `partner_*`/`b2c_*` overrides.
- `SalesDocument`/`DocumentLine` mapped superclasses.
- Hibernate runs with `ddl-auto=validate`, so the context fails to start if a mapping drifts from the migrations.

## 9. Phase 3 migration: V12 (as built, 2026-10-25)

`V12__roles_and_module_permissions.sql` moves identity to the three-role model. It is forward-only, and was dry-run in a rolled-back transaction before being applied to `partner_erp_test` and `partner_erp` (Flyway: "Migrating schema \"public\" to version \"12 - roles and module permissions\" … now at version v12").

| Change | Detail |
|---|---|
| Safety guard | Raises an exception (no partial change) if `role_permissions` has rows. Both databases had 0 users and 0 mappings. |
| `roles` | `name` → `code` (`ux_roles_code`), plus `display_name`, `active`, `created_at`, `updated_at`, `updated_by`, `version`. `SALES_MANAGER`, `SALES_EXECUTIVE`, `FINANCE`, `PARTNER` set `active=false` (kept for history); `USER` inserted. `ck_roles_display_name` (≥2 chars), partial unique index `ux_roles_display_name` on `lower(btrim(display_name)) WHERE active`. Trigger `trg_roles_code_immutable` rejects code changes (check_violation). |
| `users` | `role_id` FK (backfilled SA > ADMIN > USER from `user_roles`, otherwise USER; then NOT NULL), `session_version int default 0`, `locked_until`; indexes `ix_users_role`, `ix_users_status`. |
| Dropped | `user_roles`, `role_permissions` (empty; replaced by the tables below so there is a single source of truth). |
| `modules` | 14 rows: code, display name, sort order, `supports_write/delete/export/approve/scope`, `super_admin_only`. |
| `permissions` | Adds `display_name`, `kind` (GENERIC/MODULE), `module_id`, `active`, `super_admin_only`, `sort_order`. The 29 Phase 2 codes are set inactive; 9 active codes: DELETE, EXPORT, APPROVE (generic) and MARK_PAYOUT_PAID, REVERSE_PAYMENT, VIEW_INTERNAL_B2C, MANAGE_USERS, MANAGE_PERMISSIONS (Super Admin only), MANAGE_SETTINGS. |
| `user_module_permissions` | One row per readable module: `can_read` (CHECK true), `can_write`, `can_delete`, `can_export`, `can_approve`, `data_scope` (SELF/TEAM/ALL, default ALL), created/updated at/by. Unique `(user_id, module_id)`; `ck_ump_delete_needs_write`. |
| `user_action_permissions` | PK `(user_id, permission_id)`, `granted_at`, `granted_by`. |
| `role_permission_templates` | PK `(role_id, module_id)` with flags and scope; seeded ADMIN (9 modules) and USER (Dashboard, Products read). |

Not changed: `refresh_tokens` (already had `token_hash`, family, `revoked_at`, `replaced_by`), `audit_logs`. `created_by`/`updated_by` are now filled by `SecurityAuditorAware` for authenticated requests.

## 10. Phase 9 migration: V16 (as built, 2026-10-26)

`V16__invoice_management.sql` is forward-only. It was applied to `partner_erp_test` (integration tests and browser verification). The development database `partner_erp` is still at V15; it migrates when its server restarts with this build. Existing rows are kept: no estimate value or total is changed.

| Change | Detail |
|---|---|
| `company_settings` | `state_code` (2-digit GST state). Checks: GSTIN format, and the GSTIN's first two digits = `state_code`. Seeded values unchanged (GSTIN/state empty until set in Settings). |
| `modules` | INVOICES `supports_approve = true` and an updated description. No permission rows granted, no new permission code. |
| `invoices` status | CHECK DRAFT/ISSUED/CANCELLED, default DRAFT; existing OPEN rows → ISSUED. `invoice_number` and `invoice_date` nullable (drafts). |
| `invoices` new columns | `issued_at`, `issued_by`, `cancel_reason`, `deleted_at`, `deleted_by`, `due_days smallint`, `source_estimate_status`; supplier snapshot `supplier_name`, `supplier_address`, `supplier_gstin`, `supplier_state_code`; billing `billing_name`, `billing_address`, `billing_state_code`, `billing_gstin`; `place_of_supply_code`, `place_of_supply_basis`, `tax_type` (INTRA/INTER), `utgst`; confirmation `tax_confirmed_at`, `tax_confirmed_by`, `tax_confirmation_note`, `tax_confirmation_key`; totals `cgst_amount`, `sgst_amount`, `igst_amount`. |
| `invoices` constraints | `ck_invoices_draft_number` (draft ⇔ no number), `ck_invoices_issued` (issued/cancelled need date, issued_at, tax type), `ck_invoices_cancelled` (cancelled ⇔ cancelled_at, with a reason), `ck_invoices_deleted_draft` (only drafts are soft-deleted). Partial unique index `ux_invoices_live_estimate` on `estimate_id` for live (non-deleted, non-cancelled) invoices; index `ix_invoices_created`. |
| `invoice_items` | `taxable_value`, `cgst_amount`, `sgst_amount`, `igst_amount`, `tax_amount`, `total_amount` with `ck_ii_tax` (components add up). Existing lines backfilled from their stored amounts. |
| Triggers | `invoices_guard_issued` (BEFORE UPDATE OR DELETE): an issued or cancelled invoice can't be deleted, can't go back to draft, and can't change its number, dates, parties, place of supply, tax or totals; only the cancellation columns may change, once. `invoice_items_guard_issued` (BEFORE INSERT/UPDATE/DELETE): the lines of an issued or cancelled invoice can't change. |

Concurrency: invoice numbers come from `document_sequences` (prefix `INV`, calendar year) through the atomic upsert used for estimates. Conversion locks the estimate row, issue locks the invoice and the lead, and the partial unique index prevents two live invoices for one estimate.

## 11. Phase 10 migration: V17 (as built, 2026-10-26)

`V17__payments_management.sql` is forward-only and doesn't modify V1–V16. It was applied to `partner_erp_test` (integration tests and browser verification). The development database `partner_erp` is still at V15 and will apply V16 and V17 when its server restarts with this build. Existing payment rows are kept: refunds keep their sign and become `REFUND` entries, and other rows become `RECEIPT` entries without a number.

| Change | Detail |
|---|---|
| `company_settings` | `einvoicing_applicable boolean` (NULL = not declared). Issue needs it declared and false (DECISIONS.md §K). |
| `payments` new columns | `payment_number varchar(20)` UNIQUE (`PAY-YYYY-NNNNNN`), `entry_type` (RECEIPT/REVERSAL/REFUND, default RECEIPT), `reversed_payment_id uuid` UNIQUE FK → `payments(id)` (one reversal per receipt), `reversal_reason varchar(500)`, `notes varchar(500)`, `idempotency_key uuid` UNIQUE, `request_hash varchar(64)` (SHA-256 of the request). |
| `payments` constraints | `ck_payments_refund_sign` replaced by `ck_payments_type`: RECEIPT has amount > 0, no refund flag, no reversal link; REFUND has amount < 0 and the refund flag; REVERSAL has amount < 0, a reversal link and a non-blank reason. `ck_payments_net`: \|net\| ≤ \|amount\|, with the same sign. `ck_payments_reference`: no blank reference. `ck_payments_idempotency`: key and hash are set together. |
| Indexes | `ix_payments_reference` on `lower(reference_number)` (duplicate checks); `ix_payments_created`. |
| Trigger `trg_payments_guard` | BEFORE INSERT/UPDATE/DELETE, function `payments_guard()`. DELETE is always refused ("reverse it instead"). UPDATE is refused unless the only change is `lead_id` being cleared (`ON DELETE SET NULL`). A RECEIPT needs an ISSUED, non-deleted invoice. A REVERSAL must mirror a RECEIPT of the same invoice (negated amount and net), on an ISSUED or CANCELLED invoice. |

Derived values (unchanged views and queries): invoice paid = Σ `payments.amount`, balance = grand total − paid. Reversals are negative, so they restore the balance without touching the invoice. `v_invoice_balance` (V11) and `InvoiceRules.paymentStatus` therefore need no change. `InvoiceRepository.livePaymentCount` counts receipts that are not reversed; invoice cancellation uses it. Partner revenue (`PartnerMetricsRepository`) excludes reversed receipts and reversal rows.

Concurrency: recording and reversing lock the invoice row first (`PESSIMISTIC_WRITE`, then the payment for reversals), so balance checks, duplicate checks and the idempotency re-check run serialised per invoice. The UNIQUE indexes on `idempotency_key` and `reversed_payment_id` back these up. `PaymentApiIT` covers concurrent overpayment, concurrent identical submissions and concurrent reversals.

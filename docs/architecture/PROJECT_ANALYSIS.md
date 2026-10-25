# IOTIQ Partner ERP — Project Analysis

**Phase:** 0 (Source analysis & architecture) · **Date:** 2026-10-24 · **Root:** `C:\Projects\partner-erp`

## 1. Reference documents

| # | Document | Location | Status |
|---|---|---|---|
| 1 | SRS | `IOTIQ_Partner_ERP_SRS_Claude.md` (+ `.docx`) in repo root | Read in full (123 lines). Identical to the copy in `Downloads`. |
| 2 | ER diagram | `docs/database/IOTIQ_Partner_ERP_ER_Diagram.md` | Read in full. 17 tables, Mermaid. Identical to the copy in `Downloads`. |
| 3 | Original HTML source | **Not in repo.** Found at `C:\Users\USER\Downloads\IOTIQ_Partner_ERP_Code.docx` (the prompt's name has "(1)", see D-32) | Extracted from `word/document.xml` (868 paragraphs, about 96 KB). HTML portion saved to `docs/reference/IOTIQ_Partner_ERP_original.html` (859 lines). Read in full: CSS, store seed, helpers, domain logic, 10 views, editor, viewer, forms, 39 actions, events, CSV import, boot. |

Source characteristics: a single-file vanilla-JS app. Data lives in `localStorage['iotiq_erp_v1']` through a `window.claude` shim (`db`/`user`/`downloads`). Collections: `cps, leads, items, estimates, invoices, payments, payouts, b2c_docs, settings`. The seed contains **only** 29 items and settings (tiers + main). There are no sample partners or leads, even though the code supports a `sample` flag.

## 2. Repository assessment

**Overall:** the repository is a **scaffold of empty files**. Of the 225 files in `client/`, `server/` and `.github/` (excluding `node_modules`), **211 are 0 bytes**. No backend or frontend feature is implemented. It is not a git repository.

### 2.1 `server/`

| Item | State |
|---|---|
| `pom.xml`, `Dockerfile`, `README.md`, `.env.example`, `.gitignore` | **Empty (0 B)** |
| `application.yml`, `application-dev.yml`, `application-prd.yml` | **Empty** |
| `db/migration/V1__initial_schema.sql … V9__create_b2c_documents.sql` | **Empty**. They can never have been applied, because the app had no build or config (ADR-03). |
| Java sources: `PartnerErpApplication`, `config/*` (Audit, Cors, Jpa, OpenApi, Security), `security/*` (JwtService, JwtAuthenticationFilter, CustomUserDetailsService, SecurityUtils), `common/*` (enums, exceptions, ApiResponse, PageResponse, Currency/Date/Number/Tax utils), `jobs/*` (LeadSla, PartnerTierReview, PayoutReminder), 12 modules × (controller, dto request/response, entity, mapper, repository, service) | **All empty.** That's 105 `.java` files, all 0 B. |
| Tests | Only `.gitkeep` in `unit/` and `integration/` |

Package layout `com.iotiq.partnererp.modules.<module>.{controller,dto,entity,mapper,repository,service}` is sensible and will be kept (ADR-01/02). Naming adjustments: `auth/entity/Auth` → `User`/`Role`/`RefreshToken`; `payouts/entity/Payout` → `PartnerPayout`; `settings/entity/Setting` → `CompanySettings`. Modules without entities (dashboard, reports) are correct as is. A `users`/admin module and `audit` module will be added.

`jobs/LeadSlaJob` and `PayoutReminderJob` aren't needed in v1, because alerts are derived at query time (ADR-09). `PartnerTierReviewJob` maps to the optional scheduled review (D-07). These stay empty or get removed after approval.

### 2.2 `client/`

| Item | State |
|---|---|
| `package.json` | Vite scaffold named `vite-temp`. Deps: react/react-dom ^19.3.1. Dev: vite ^8.3.4, @vitejs/plugin-react ^6.1.2, tailwindcss + @tailwindcss/vite ^4.3.3, typescript ~6.0.3, eslint 10, typescript-eslint. **Missing:** react-router, @tanstack/react-query, react-hook-form, zod, @hookform/resolvers, axios, recharts, lucide-react, shadcn deps (clsx, tailwind-merge, class-variance-authority, radix), test stack (vitest, testing-library, msw). |
| `node_modules` | Installed (118 top-level entries), matching the scaffold |
| `vite.config.ts` | React + Tailwind plugins, `@` → `src` alias ✓ |
| `tsconfig.app.json` | **No `paths` for `@/*`** (needed by TS and shadcn) |
| `index.html` | title `vite-temp`, references missing `/favicon.svg` |
| `components.json` (shadcn), `.env`, `.env.production`, `.gitignore` | **Empty** |
| `src/` | `styles/globals.css` = `@import "tailwindcss";` and `vite-env.d.ts`. **The other 83 files are all empty**, including `main.tsx`, so the project cannot currently build a real app. |
| `dist/` | Stale 670-byte build of the scaffold. Should be deleted and git-ignored. |
| `public/icons`, `public/images` | empty dirs |

The planned folder structure (`app/`, `components/`, `layouts/`, `modules/<feature>/{components,pages,services,types,hooks}`, `services/api.ts`, `context/`) is good and will be kept. `services/storage.service.ts` must **not** be used to store tokens (ADR-07).

### 2.3 `prd/`
Empty directory. Nothing to preserve.

### 2.4 Other
- `.github/modernize/java-upgrade/` contains hook scripts from the VS Code "App Modernization" extension (tool-use recorder). They're unrelated to the app. Leave them untouched.
- No Docker Compose file and no nginx config.

### 2.5 Toolchain on this machine

| Tool | Found | Required | Gap |
|---|---|---|---|
| JDK | 17.0.12 (PATH), MS 17.0.16 | 17 (changed from 21 on 2026-10-24, see D-01) | OK |
| Maven | 3.9.11 | 3.9+ | OK |
| Node / npm | 24.7.0 / 11.6.4 | 20.19+/22+ for Vite 8 | OK |
| PostgreSQL | 17.6 server on :5432 | ≥16 | OK. Credentials needed (D-03). |
| Docker / Compose | 29.8 CLI / v5.5 | daemon for Testcontainers & prd | **Daemon not running (D-02)** |
| Git | 2.53 | | OK. Repo not initialised (D-04a). |

## 3. Source application analysis (summary)

The full details are in `BUSINESS_RULES.md` (rules) and `FEATURE_INVENTORY.md` (screens and actions).

- **Navigation:** 10 views in 3 groups plus Dashboard. Partner detail is a sub-view. Everything else is a modal.
- **Visual identity:** ivory `#f4efe2` / surface `#fbf8ef` / forest `#11271c` / gold `#c39c67`. Status colours good `#2f7a4d`, warn `#a8680f`, bad `#b3402f`, info `#2f5f8a`, each with a tinted background. Dark palette: bg `#0c1a13`, surface `#12291d`. Fonts: Bricolage Grotesque (display), Instrument Sans (body), IBM Plex Mono (numbers, tabular). Zebra tables, uppercase micro-labels, pills, KPI tiles, 4-column modal grid (1 column under 820 px). Printable document is always white with a forest table header and a gold rule. The internal copy has a dashed amber border.
- **Workflow:** Partner → Lead (acknowledge locks fee and starts protection) → Estimate (draft → sent → accepted/rejected) → Invoice (from estimate or direct; lead → Won) → Payment (net ex-GST; fee accrual; auto upgrade) → Payout (business-day due date; TDS; mark paid). The internal B2C twin is written on every estimate/invoice save.
- **Calculations:** document-level discount %, per-line GST, `r2` rounding, ₹0.50 paid tolerance, proportional net, fee = net × locked %, TDS catch-up formula, 365-day collected-revenue tiers.
- **Document generation:** HTML only (download `<no>.html` / `INTERNAL-<no>.html`).
- **CSV:** item import/export/template, 8 report exports, partner fee statement.
- **Defects/ambiguities found:** 32 decision items, listed in `DECISIONS.md`. The most material are D-06 (tier double count), D-09 (editing paid invoices), D-10 (overpayment / zero total), D-11/D-12 (no reversal model), D-13 (fee % is client-editable), D-14 (cancelled invoices in uplift), D-18 (CSV import zeroes prices).

## 4. Document conflicts summary

| Conflict | Sources | Resolution proposed |
|---|---|---|
| "Services" products | Prompt implies them; source has none | Category only, no invented products (D-31) |
| Partner address | Prompt asks for it; source has city only | Optional fields (D-16) |
| Lead transitions | Prompt says prevent invalid ones; source has free transitions | Reopen needs permission (D-04) |
| Estimate statuses | SRS: 4; source: 5; prompt: "expired" | 5 stored + derived expired (D-29) |
| Invoice status | ER has a stored `status`; prompt and SRS want it derived | Lifecycle stored, payment status derived (ADR-08) |
| Table names | SRS (`partner_tier_definitions`, `internal_b2c_documents`, `estimate_lines` …) vs ER (`partner_tiers`, `b2c_documents`, `estimate_items`) | Use **ER names** (the prompt lists them) |
| ER omissions | `products.unit`, `invoices.discount_percent`, `partner_payouts.invoice_id/base`, settings fields, … | Added (DATABASE_DESIGN §1) |
| Phases | SRS 6 vs prompt 16 | Prompt (D-28) |
| PDF | Prompt wants PDF; source has HTML download | Server PDF + print (D-15) |
| Partner role | Prompt proposes it; source is internal-only | Model it, but don't build a portal unless confirmed (D-22) |
| Payout reversal | Prompt asks for it; source lacks it (only mentioned in a message) | Add with FINANCE permission (D-12) |
| TDS legal basis | SRS: "verify with CA" | Implement as an editable suggestion (D-08) |

## 5. Risks

| Risk | Mitigation |
|---|---|
| Financial maths diverging from the source in edge cases | Golden test vectors computed by running the original JS functions (`totals`, `invStatus`, `tdsFor`, `addBiz`, `leadSla`) in Node, then asserted in JUnit (Phase 8–11) |
| B2C leakage | DTO separation, `/internal/**` route guard, JSON-key leak tests on every partner-safe endpoint, PDF template separation |
| Concurrency on numbering/payments | `document_sequences` row lock, `FOR UPDATE` on invoice when recording a payment, idempotency keys |
| Environment gaps (Docker daemon) | Docker needed from Phase 2 (Testcontainers) and Phase 15 |
| Scope creep from ENH items | Each ENH is tagged and gated by decisions |

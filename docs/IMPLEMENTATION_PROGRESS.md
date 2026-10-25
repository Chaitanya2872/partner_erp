# IOTIQ Partner ERP — Implementation Progress

Statuses: `NOT_STARTED` · `IN_PROGRESS` · `BLOCKED` · `COMPLETED`

| Phase | Name | Status | Started | Completed |
|---|---|---|---|---|
| 0 | Source analysis & architecture | COMPLETED (approved 2026-10-24) | 2026-10-24 | 2026-10-24 |
| 1 | Backend foundation | COMPLETED (approved 2026-10-24) | 2026-10-24 | 2026-10-24 |
| 2 | Database schema & seed data | COMPLETED (approved 2026-10-25) | 2026-10-24 | 2026-10-24 |
| 3 | Authentication & authorization (incl. auth/admin screens) | COMPLETED (awaiting approval) | 2026-10-25 | 2026-10-25 |
| 4 | Frontend foundation | NOT_STARTED (shell, routing, API client and auth screens already delivered in Phase 3) | — | — |
| 5 | Partner management | NOT_STARTED | — | — |
| 6 | Lead management | NOT_STARTED | — | — |
| 7 | Product & price sheet | NOT_STARTED | — | — |
| 8 | Estimate management | NOT_STARTED | — | — |
| 9 | Invoice management | NOT_STARTED | — | — |
| 10 | Payment management | NOT_STARTED | — | — |
| 11 | Partner payouts | NOT_STARTED | — | — |
| 12 | Dashboard & reports | NOT_STARTED | — | — |
| 13 | Settings & administration | NOT_STARTED | — | — |
| 14 | End-to-end testing & QA | NOT_STARTED | — | — |
| 15 | Production preparation | NOT_STARTED | — | — |

---

## Phase 0: Source analysis & architecture

- **Status:** COMPLETED. Approved on 2026-10-24 together with ADR-01 to ADR-14. Business decisions D-04 to D-32 stay open (source behaviour applies).
- **Start date:** 2026-10-24
- **Completed tasks:**
  - Inspected the repository. It is an all-empty scaffold: 211 of 225 files are 0 bytes, and none of the 105 Java files or the 9 migrations contain code.
  - Read the SRS and ER diagram in full.
  - Located the original source at `C:\Users\USER\Downloads\IOTIQ_Partner_ERP_Code.docx` (not in the repo; the name differs from the prompt's). Extracted it, read it in full, and saved it to `docs/reference/IOTIQ_Partner_ERP_original.html`.
  - Verified the 29 seeded products programmatically (category counts 6/14/4/5/0, Σ partner price ₹3,44,094, all B2C prices 0).
  - Inventoried 10 views, 39 actions, 8 reports and 7 alert types. Extracted every calculation, status machine and default.
  - Validated the ER diagram (gaps listed) and decided on 6 additional tables.
  - Defined the API, the permission matrix (proposed) and the phase plan.
  - Logged 32 decisions/conflicts and 14 ADRs.
- **Files created:**
  - `docs/architecture/PROJECT_ANALYSIS.md`
  - `docs/architecture/FEATURE_INVENTORY.md`
  - `docs/architecture/IMPLEMENTATION_PLAN.md`
  - `docs/architecture/API_DESIGN.md`
  - `docs/architecture/DATABASE_DESIGN.md`
  - `docs/architecture/BUSINESS_RULES.md`
  - `docs/architecture/DECISIONS.md`
  - `docs/reference/IOTIQ_Partner_ERP_original.html` (verbatim extraction of the source HTML)
  - `docs/IMPLEMENTATION_PROGRESS.md`
- **Modified files:** none. No application code or config was touched.
- **Database migrations:** none (design only).
- **APIs implemented:** none (design only).
- **Frontend screens implemented:** none.
- **Tests executed:** none applicable. Product seed verification was done with a script against the extracted source.
- **Known issues / blockers:** resolved in Phase 1 (D-01 Java 17 accepted, D-03 credentials supplied, D-04a git initialised, D-32 source verified). D-02 Docker is deferred to when containers are needed.
- **Pending decisions:** see `docs/architecture/DECISIONS.md`. Items marked *Needs approval*, especially D-04, D-05, D-06, D-09 to D-14, D-16 to D-19, D-21 to D-23, D-25 and D-26.
- **Next phase:** Phase 1, backend foundation, after approval and once the blockers are resolved.

---

## Phase 1: Backend foundation

- **Status:** COMPLETED. All acceptance criteria were verified on 2026-10-24. Waiting for approval before Phase 2.
- **Start date:** 2026-10-24
- **Versions:**
  - Java **17.0.12** (Oracle JDK, `--release 17`); Maven 3.9.11
  - Spring Boot **4.1.1** (Spring Framework 7.0.9, Spring Security 7.1.1, Hibernate 7.4.5, Jackson 3.1.5)
  - Flyway 12.4.0 (+ flyway-database-postgresql); PostgreSQL JDBC 42.7.13; springdoc-openapi 3.1.1
  - Why 4.1.1: current stable line, supports Java 17 and was released 2026-08-20. 4.1.2 (2026-10-22) is too new. 3.5.x is past OSS support.
- **Database:** PostgreSQL 17.6 at `localhost:5432`, database `partner_erp` (UTF8, created in this phase), user `postgres`. The password comes from the `DB_PASSWORD` env var through the git-ignored `server/.env`. No other database was touched and no server or auth settings were changed.
- **Completed tasks:**
  1. Java 17 Maven config (`java.version=17`) and Spring Boot parent 4.1.1.
  2. `PartnerErpApplication` with `@ConfigurationPropertiesScan`.
  3. Dependencies: webmvc, validation, data-jpa, flyway, postgresql, security, actuator, springdoc; test: starter-test, security-test.
  4. Datasource from env (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`). Hikari pool. `ddl-auto=validate`. `open-in-view=false`. JDBC time zone UTC.
  5. Flyway: the 9 empty placeholder migrations were confirmed unapplied (the database didn't exist, so there was no schema history) and replaced by `V1__baseline_extensions.sql` (pgcrypto, `set_updated_at()` trigger function), per ADR-03.
  6. Profiles: `application.yml` (shared, default profile `dev`), `application-dev.yml` (reads `.env`, Swagger on, health details, readable logs with requestId), `application-prd.yml` (env-only values with no secret defaults, Swagger off, health details off, JSON logs, `flyway.clean-disabled`).
  7. `GlobalExceptionHandler`: one error envelope for validation (body, method, constraint), malformed JSON, type mismatch, missing param, 404, 405, 415, business 422/409, optimistic lock 409, integrity 409, 401/403, and a generic 500 that never leaks internal text.
  8. Bean Validation wired (field errors returned as `fieldErrors`). `AppProperties` is validated at startup.
  9. Actuator: `health` (db, liveness and readiness probes) and `info` exposed; everything else is unexposed and requires authentication.
  10. OpenAPI: `OpenApiConfig` (title, version, bearer scheme). UI at `/swagger-ui.html` in dev only.
  11. CORS: explicit allowlist from `CORS_ORIGINS` for `/api/**`, credentials allowed, `X-Request-Id` exposed.
  12. Logging: `RequestIdFilter` (X-Request-Id echo/generation, MDC). Dev pattern includes the requestId. Prd uses Spring Boot structured logging (ECS JSON).
  13. Security baseline: stateless, deny by default, JSON 401/403 (`JsonSecurityErrorHandler`), security headers. A placeholder `UserDetailsService` stops Boot from generating an in-memory password. JWT comes in Phase 3.
  14. `Clock` bean (Asia/Kolkata) and JPA auditing date provider (`AuditConfig`). `JpaConfig` scans `modules.*` repositories.
  15. Git initialised (`master`) with root, server and client `.gitignore`. The Dockerfile uses Temurin 17 (multi-stage, non-root).
- **Files created:**
  - `.gitignore`
  - `server/src/main/java/com/iotiq/partnererp/config/AppProperties.java`
  - `server/src/main/java/com/iotiq/partnererp/common/web/RequestIdFilter.java`
  - `server/src/main/java/com/iotiq/partnererp/security/JsonSecurityErrorHandler.java`
  - `server/src/test/java/com/iotiq/partnererp/unit/common/GlobalExceptionHandlerTest.java`
  - `server/.env` (local only, git-ignored, not committed)
- **Files modified (previously 0-byte placeholders):** `server/pom.xml`, `server/Dockerfile`, `server/README.md`, `server/.env.example`, `server/.gitignore`, `client/.gitignore`, `application.yml`, `application-dev.yml`, `application-prd.yml`, `PartnerErpApplication.java`, `common/constants/AppConstants.java`, `common/response/ApiResponse.java`, `common/response/PageResponse.java`, `common/exception/{BusinessException,ResourceNotFoundException,GlobalExceptionHandler}.java`, `config/{AuditConfig,CorsConfig,JpaConfig,OpenApiConfig,SecurityConfig}.java`; docs (`DECISIONS.md`, `PROJECT_ANALYSIS.md`, `IMPLEMENTATION_PLAN.md`) updated for Java 17 and the approvals.
- **Files renamed or removed:** `V1__initial_schema.sql` → `V1__baseline_extensions.sql`. Deleted the empty, never-applied `V2__create_partners.sql` … `V9__create_b2c_documents.sql` and `src/test/.../unit/.gitkeep`.
- **Database migrations:** V1 baseline applied (`flyway_schema_history`: version 1 "baseline extensions", success=true).
- **APIs implemented:** none for business features. Infrastructure endpoints: `/actuator/health` (+ `/liveness`, `/readiness`), `/actuator/info`, `/v3/api-docs`, `/swagger-ui.html` (dev).
- **Frontend screens implemented:** none (Phase 4).
- **Build results:**
  - `mvn -B clean compile`: BUILD SUCCESS, 108 sources, `release 17`.
  - `mvn -B test`: 6 tests, 0 failures, 0 errors.
  - `mvn -B clean verify`: BUILD SUCCESS, executable jar `partner-erp-server-0.1.0-SNAPSHOT.jar` built.
  - `mvn -B spring-boot:run` (dev): started in 20.8 s with no WARN or ERROR lines. Hikari connected to PostgreSQL 17.6. Flyway validated and applied V1.
- **Runtime verification (curl):**
  - `GET /actuator/health` → 200, status UP, db UP. `GET /actuator/health/liveness` → 200 UP. `GET /actuator/info` → 200 (java 17).
  - `GET /api/v1/partners` → 401 JSON envelope with requestId. `GET /actuator/env` → 401. A supplied `X-Request-Id: phase1-check` is echoed in the header and the body.
  - CORS preflight from `http://localhost:5173` → 200 with `Access-Control-Allow-Origin`. From `http://evil.example` → 403.
  - Swagger UI → 200. `/v3/api-docs` → OpenAPI 3.1 document.
  - `prd` profile without env vars → refuses to start (exit 1). Logs are ECS JSON.
  - DB: `pgcrypto` extension and `set_updated_at()` function exist in `partner_erp`.
- **Not verified in this phase:** Testcontainers-based tests (Docker isn't running; planned for Phase 2). Docker image build (Phase 15).
- **Known issues:**
  - Swagger/OpenAPI paths and `/api/v1/auth/**` are permitted in the security config; in prd the docs are disabled through springdoc properties. Auth endpoints arrive in Phase 3.
  - CSRF is disabled for the stateless API. The refresh-cookie CSRF design is part of Phase 3 (ADR-07).
  - Empty placeholder Java files (entities, services, jobs, utils) remain for later phases. They compile as empty compilation units.
  - Nothing is committed to git yet (no commit was requested).
- **Pending decisions:** D-04 to D-31 business decisions stay open; source behaviour applies until you approve a change.
- **Next phase:** Phase 2, database schema and seed data (migrations V2–V11, entities, repositories, the 29-product seed, tiers, settings, roles). Testcontainers needs Docker Desktop running; otherwise DB tests run against a dedicated local test database (needs your preference).

---

## Phase 2: Database schema & seed data

- **Status:** COMPLETED. Approved 2026-10-25 (Phase 3 instruction).
- **Start date:** 2026-10-24
- **Test database:** `partner_erp_test`, created on the same local PostgreSQL 17.6 (you chose "local db"). No other database was touched and no auth settings were changed. Tests rebuild its schema with Flyway clean + migrate when the context starts. The cleaner refuses any database whose name doesn't end in `_test`.
- **Completed tasks:**
  1. Migrations V2–V11 (identity, settings and tiers, partners, leads, products, product seed, sales documents, finance, audit, views) per `DATABASE_DESIGN.md §6`. As-built notes and deliberate exclusions are in §8.
  2. Seeds: 4 tiers, company settings defaults, 29 products (generated from the source HTML; idempotent), 6 roles, 29 permission codes. Not seeded: the role→permission mapping (D-23), users, and any partners, leads or documents.
  3. 21 JPA entities with shared persistence base classes.
  4. 18 Spring Data repositories with the finders the next phases need: case-insensitive SKU, tier by level, settings singleton, payout by payment, twin by parent, and a row-locked document sequence.
  5. Removed 6 empty placeholders that real types replace: `Auth`, `AuthRepository`, `Payout`, `PayoutRepository`, `Setting`, `SettingRepository`.
  6. Test profile (`src/test/resources/application-test.yml`), `CleanMigrateTestConfig` and `AbstractIntegrationTest`. Maven Failsafe runs the `*IT` classes in `verify`.
- **Files created:**
  - `server/src/main/resources/db/migration/V2__identity.sql` … `V11__views.sql`
  - `common/persistence/{BaseEntity,AuditableEntity,CreatedAuditEntity,DocumentTotals,DocumentLine,SalesDocument}.java`
  - Enums:
    - `common/enums/{LeadStage,PartnerStatus,InvoiceStatus,PaymentStatus,BillTo}`
    - `partners/entity/{PartnerCategory,AgreementStatus,TierReviewAction}`
    - `leads/entity/{ProjectType,LeadEventType}`
    - `products/entity/ProductCategory`, `estimates/entity/EstimateStatus`, `payments/entity/PaymentMethod`, `payouts/entity/PayoutStatus`, `b2c/entity/B2CDocumentType`, `auth/entity/UserStatus`
  - Entities: `User, Role, Permission, RefreshToken, CompanySettings, PartnerTier, Partner, PartnerTierReview, Lead, LeadStageHistory, Product, Estimate, EstimateItem, Invoice, InvoiceItem, B2CDocument, B2CDocumentItem, Payment, PartnerPayout, AuditLog, DocumentSequence`
  - Repositories:
    - New: `User, Role, Permission, RefreshToken, CompanySettings, PartnerTier, PartnerTierReview, LeadStageHistory, PartnerPayout, AuditLog, DocumentSequence`
    - Filled in (previously empty files): `Partner, Lead, Product, Estimate, Invoice, Payment, B2CDocument`
  - Tests: `integration/support/{AbstractIntegrationTest,CleanMigrateTestConfig}.java`, `integration/persistence/{MigrationAndSeedIT,ConstraintsIT,PersistenceIT}.java`, `src/test/resources/application-test.yml`
- **Files modified:** `server/pom.xml` (maven-failsafe-plugin), `docs/architecture/DATABASE_DESIGN.md` (§8), this file.
- **Database migrations:** V2–V11 applied to `partner_erp` (Flyway: "Successfully applied 10 migrations … now at version v11"). They are also applied to `partner_erp_test` on every test run.
- **APIs implemented:** none (schema phase).
- **Frontend screens implemented:** none.
- **Build and test results:** `mvn clean verify` → BUILD SUCCESS. 34 tests: 0 failures, 0 errors, 0 skipped.
  - `GlobalExceptionHandlerTest`: 6 unit tests.
  - `MigrationAndSeedIT`: 7 tests. They check:
    - V1–V11 all applied successfully.
    - 29 products; categories 6/14/4/5/0; Σ partner price 344,094.
    - B2C, cost and HSN all NULL; GST 18; unit nos; spot rows.
    - Re-running V7 leaves 29 rows.
    - Tiers: 8/10/12/15 %, ₹0/10L/30L/1Cr, 15/12/10/7 days.
    - Settings defaults; 6 roles, 29 permissions, 0 mappings, 0 users.
  - `ConstraintsIT`: 13 tests. Each asserts that the *named* constraint fires: case-insensitive SKU, category, tier level, settings singleton, generated phone digits, ack completeness, discount range, refund sign, zero payment, one payout per payment, paid date, twin parent, one twin per estimate.
  - `PersistenceIT`: 8 tests:
    - Partner/lead round-trip with auditing, `@Version` increment and generated phone digits.
    - Product `text[]` aliases.
    - Full estimate→twin→invoice→payment→payout graph.
    - Estimate delete cascades to items and twin and detaches the invoice.
    - Lead delete keeps documents.
    - User↔role mapping.
    - `jsonb` audit values.
    - Locked sequence increment.
- **Runtime check:**
  - Started the app on dev with `java -jar`. Flyway migrated to v11 and Hibernate validation passed.
  - `/actuator/health` → UP (db UP). `/api/v1/products` → 401.
  - No WARN or ERROR lines, and no credentials in the log.
  - Dev DB: 29 products, Σ 344,094.00, 4 tiers, 29 permissions.
- **Known issues:**
  - `created_by`/`updated_by` stay NULL until an `AuditorAware` is registered with authentication (Phase 3).
  - The first run failed 3 tests because product fixtures lacked the NOT NULL names. That was a bug in the tests, now fixed; the schema didn't change.
  - Nothing is committed to git yet (no commit was requested).
- **Pending decisions:**
  - D-23 (role→permission mapping) must be decided before Phase 3 can enforce authorization.
  - D-16 (partner/lead numbering), D-17 (product deactivate vs delete) and D-26 (soft delete) affect columns that were created nullable here.
  - All other decisions from D-04 to D-31 stay open, with source behaviour applying.
- **Next phase:** Phase 3, authentication and authorization, after approval.

---

## Phase 3: Authentication, authorization, role & user management

- **Status:** COMPLETED. Waiting for approval before Phase 4.
- **Start date:** 2026-10-25 · **Completed:** 2026-10-25
- **Decisions made in this phase:** `docs/architecture/DECISIONS.md §D` (P3-01…P3-14). D-23 is resolved by the three-role model.

### Completed tasks
1. **Roles**: exactly three active roles (`SUPER_ADMIN`, `ADMIN`, `USER`).
   - Codes are immutable. Display names live in the DB and only a Super Admin can edit them (unique, non-empty, audited).
   - The four extra Phase 2 roles are retired, not deleted. There is no role create/delete API.
2. **Permissions**: 14 modules, stored per user in PostgreSQL and seeded from role templates (Admin and User defaults as specified).
   - Levels: Read / Read+Write, plus Delete, Export and Approve flags only where the module supports them.
   - Data scope: SELF / TEAM / ALL.
   - Module actions: `MARK_PAYOUT_PAID`, `REVERSE_PAYMENT`, `VIEW_INTERNAL_B2C`, `MANAGE_USERS`, `MANAGE_PERMISSIONS`, `MANAGE_SETTINGS`.
   - Validation: Write needs Read, Delete needs Write, actions need module Read; unsupported flags and scopes are rejected; role ceilings are enforced.
3. **Super Admin**: computed full access and a non-editable matrix. The last active Super Admin is protected, and a Super Admin cannot disable or demote themselves.
4. **Delegation**: only a Super Admin can change permissions. Nobody can change their own role or permissions. Non-Super-Admins with User Management write manage User-role accounts only.
5. **Authentication**:
   - BCrypt(12); generic invalid-credential errors; lockout after 5 failures for 15 minutes; disabled-account handling.
   - 15-minute HS256 JWT. 7-day rotating refresh tokens, stored hashed and sent in an HttpOnly SameSite=Strict cookie, with reuse detection (family revocation). CSRF header required on cookie endpoints.
   - Logout; `/auth/me`; change password (revokes other sessions); forced password change for new and bootstrap accounts.
   - A session version makes deactivation and password changes end sessions immediately.
6. **Authorization**: deny by default and `@PreAuthorize` on every endpoint. The principal and its permissions are resolved from the DB on every request, so changes apply without a restart or re-login.
7. **Data scope**: stored and resolved by `DataScopeService`. SELF and TEAM deny (there is no ownership or team model yet); ALL is unrestricted.
8. **Super Admin bootstrap**: from the `BOOTSTRAP_SUPERADMIN_*` env vars, only when no Super Admin exists. The password is hashed and never logged, and must be changed at first sign-in.
9. **Internal B2C protection**: partner-safe `ProductResponse` vs `InternalProductResponse`. Internal fields and B2C-name search are only available with `PRODUCTS:VIEW_INTERNAL_B2C`.
10. **Audit**:
    - Events: login success/failure, lockout, logout, token reuse, password change, bootstrap, user create/update/role/status, permission changes (before/after snapshots), role renames.
    - Each entry records actor, target, timestamp, IP and request id. Keys that look like secrets are redacted before storage.
    - A read API with filters.
11. **Frontend** (no mock data; every screen calls the real API):
    - **Login** (IOTIQ design): show/hide password, validation, loading, an error message per code, session-ended notice.
    - **Change password**: forced and voluntary.
    - **Shell**: permission-driven sidebar (labels from `/modules`), header with the role display name, protected routes, unauthorized and not-found pages.
    - **User Management**: list, search, role/status filters, paging, create/edit dialogs, role assignment, activate/deactivate with confirmation, a permission matrix page.
    - **Permission matrix**:
      - Read/Write/Delete/Export/Approve checkboxes, a scope dropdown and module actions, with auto-dependencies.
      - Save/Cancel; confirmation for sensitive grants; an explicit scope choice.
      - Read-only mode; role ceilings shown.
    - **Settings → Role Management** (Role name, Internal code, Users, Edit) with a rename dialog.
    - **Products** (read-only; B2C columns only with permission) and **Audit Logs** with before/after details.
    - **Dashboard** showing the user's access (metrics are Phase 12), and labelled "not yet available (Phase N)" pages for the other modules.
    - **Session handling**: in-memory access token, single-flight refresh across tabs (Web Locks), `canRead`/`canWrite`/`canPerform` helpers, permission refresh on focus and after changes, toasts.

### Database changes
- `V12__roles_and_module_permissions.sql` (details: `DATABASE_DESIGN.md §9`).
  - Applied to `partner_erp_test` on every test run.
  - Applied to `partner_erp` (dev) on 2026-10-25: "Current version of schema public: 11 … Successfully applied 1 migration … now at version v12".
- Dev records preserved: 29 products, 4 tiers, 1 settings row, 0 users. No bootstrap variables were set, so no account was created in dev.
- No applied migration was modified.

### Endpoints implemented
- Auth: `POST /api/v1/auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET /auth/me`, `POST /auth/change-password`
- Users: `GET/POST /api/v1/users`, `GET/PUT /users/{id}`, `PATCH /users/{id}/status`, `GET/PUT /users/{id}/permissions`
- Roles: `GET /api/v1/roles`, `GET /roles/{id}`, `PATCH /roles/{id}`
- Catalogue: `GET /api/v1/modules`, `GET /api/v1/permissions`
- Audit: `GET /api/v1/audit-logs`
- Products (read-only): `GET /api/v1/products`, `GET /products/{id}`

### Files
- **Server, new:**
  - Config: `config/{SecurityProperties,BootstrapProperties}`.
  - Security: `security/{AuthenticatedUser,EffectivePermissions,ModuleAccess,UserAuthentication,SecurityUtils,SecurityAuditorAware,JwtConfig,JwtService,UserJwtAuthenticationConverter,PasswordChangeRequiredFilter}`.
  - Common: `common/exception/ValidationException`, `common/web/Pageables`.
  - Auth module:
    - Entities: `{RoleCode,DataScope,PermissionKind,ModuleCode,AppModule,UserModulePermission,UserActionPermission,RolePermissionTemplate}`.
    - Repositories: `{AppModule,UserModulePermission,UserActionPermission,RolePermissionTemplate}Repository`.
    - Services: `{ModuleCatalog,PermissionService,PrincipalService,PasswordPolicy,ClientInfo,RefreshTokenService,RefreshTokenRejectedException,LoginAttemptService,SessionService,AuthService,SuperAdminBootstrap,DataScopeService}`.
    - DTOs (11 records) and `mapper/AuthMapper`.
    - Controllers: `{AuthController,AccessCatalogController,RefreshTokenCookies}`.
  - Users: `modules/users/{controller/UserController,service/UserService,dto/*}`.
  - Roles: `modules/roles/{controller/RoleController,service/RoleService,dto/*}`.
  - Audit: `modules/audit/{controller/AuditLogController,service/AuditService,service/AuditActions,service/AuditLogQueryService,dto/AuditLogResponse}`.
  - Products: `modules/products/{dto/ProductView,dto/InternalProductResponse,mapper/ProductMapper,service/ProductService,controller/ProductController}`; `ProductResponse` filled in.
  - Migration: `db/migration/V12__roles_and_module_permissions.sql`.
- **Server, modified:**
  - `pom.xml` (OAuth2 resource server starter).
  - Config files: `application.yml`, `application-dev.yml`, `application-prd.yml`, `src/test/resources/application-test.yml`.
  - `.env.example` (placeholders only).
  - `SecurityConfig`, `CorsConfig`, `AuditConfig`, `GlobalExceptionHandler`, `JsonSecurityErrorHandler`.
  - Entities `User`, `Role`, `Permission`; repositories `User`, `Role`, `Permission`, `RefreshToken`, `Product`, `AuditLog`.
- **Server, deleted** (empty placeholders replaced by real types): `security/JwtAuthenticationFilter.java`, `security/CustomUserDetailsService.java`.
- **Server tests:**
  - New: `integration/support/ApiIntegrationTest`, `integration/security/{AuthIT,RoleManagementIT,UserManagementIT,PermissionIT,InternalB2cSecurityIT,AuditAndBootstrapIT}`, `unit/security/EffectivePermissionsTest`.
  - Adapted to V12, with assertions updated and no test removed: `MigrationAndSeedIT`, `PersistenceIT`.
- **Client:**
  - Config: `vite.config.ts` (proxy, Vitest), `tsconfig.app.json` (strict, `@/*` paths), `index.html`, `eslint.config.js` (was missing), `package.json` (name, test scripts).
  - App shell:
    - `src/main.tsx`, `src/app/{App,providers,router,navigation}`
    - `src/layouts/{MainLayout,AuthLayout,Sidebar,Header,ProtectedRoute}`
    - `src/context/AuthContext`, `src/hooks/{useAuth,useDebounce,useModules}`
  - Services and shared code:
    - `src/services/{api,auth.service,storage.service}`, `src/lib/{permissions,cn}`, `src/types/{api.types,common.types}`
    - `src/components/{ui/Dialog,modals/ConfirmDialog,forms/FormField,tables/DataTable,common/{LoadingSpinner,EmptyState,ErrorBoundary}}`
    - `src/pages/{UnauthorizedPage,NotFoundPage,ComingSoonPage}`
    - `src/utils/{formatCurrency,formatDate}`, `src/styles/globals.css`
  - Modules:
    - `auth/{components/LoginForm,pages/LoginPage,pages/ChangePasswordPage,types/auth.types}`
    - `dashboard/pages/DashboardPage`
    - `users/{pages/UsersPage,pages/UserPermissionsPage,components/UserFormDialog,components/PermissionMatrix,lib/permissionMatrix,services/userService,types/user.types}`
    - `settings/{pages/SettingsPage,components/RoleManagement,services/roleService,hooks/useRoles}`
    - `products/{pages/ProductsPage,services/productService,types/product.types}`
    - `audit/pages/AuditLogsPage`
  - Tests:
    - `src/test/{setup,fixtures,renderApp}`
    - `lib/permissions.test.ts`, `services/api.test.ts`, `app/routing.test.tsx`
    - `modules/auth/pages/LoginPage.test.tsx`, `modules/settings/components/RoleManagement.test.tsx`
    - `modules/users/lib/permissionMatrix.test.ts`, `modules/users/pages/UserManagement.test.tsx`
- **Docs:** `DECISIONS.md` (D-23 resolved, §D), `API_DESIGN.md` (§2 rewritten, §3.1, §3.5 note, §3.12), `DATABASE_DESIGN.md` (§9), `server/README.md` (bootstrap, auth, config), `client/README.md` (new), this file.

### Build and test results (2026-10-25)
- **Server:** `mvn clean verify` → BUILD SUCCESS. **76 tests, 0 failures, 0 errors, 0 skipped** (10 unit + 66 integration).
  - **Unit tests:**
    - `GlobalExceptionHandlerTest`: 6.
    - `EffectivePermissionsTest`: 4. Write does not grant sensitive actions; actions are module-bound; password policy; refresh-token hashing.
  - **Phase 2 regression:**
    - `ConstraintsIT`: 13.
    - `MigrationAndSeedIT`: 8. Now V1–V12, three active + four retired roles, 14 modules, 9 actions, templates.
    - `PersistenceIT`: 8.
  - **`AuthIT`: 12.**
    - Login sets the expected cookie attributes. The refresh token is never in the body and is stored hashed.
    - Wrong password and unknown user get the same error. Disabled user and lockout are handled.
    - Missing, malformed, foreign-key and expired JWTs are rejected; access tokens expire after 15 minutes.
    - Refresh rotation and reuse detection (family revoked, audited). The CSRF header is required.
    - Logout. Password change: old sessions and the old password stop working, and the audit has no secrets.
    - Forced password change. Deactivation ends sessions immediately.
  - **`RoleManagementIT`: 4.**
    - Exactly three roles, with user counts.
    - Super Admin rename: whitespace normalised, audited, assignments and permissions unchanged, new name visible in `/me`.
    - Admin and User cannot rename or list roles.
    - Rejected: code changes (by the API and by the DB trigger), empty names, case-insensitive duplicates, stale versions.
  - **`UserManagementIT`: 8.**
    - Create with template and forced password change; duplicate email; weak password.
    - Search, filters and the sort whitelist.
    - Default Admin and User are denied.
    - A delegated Admin is limited to User accounts and blocked on every escalation path.
    - Self demote/disable blocked. A role change resets permissions (audited).
    - Last active Super Admin protected; stale version rejected.
  - **`PermissionIT`: 6.**
    - User template.
    - No access, read-only and read-write are enforced on a live session. Write does not grant MANAGE_USERS.
    - Changes persist and are audited.
    - 10 invalid or escalating grants are rejected without side effects.
    - Super Admin matrix fixed; self-change refused.
    - SELF/TEAM deny, ALL allows.
  - **`InternalB2cSecurityIT`: 3.**
    - Three accounts never receive B2C name, price or cost (list, detail, search by B2C name): a User, a default Admin, and an account with full product rights plus INTERNAL_B2C read but without VIEW_INTERNAL_B2C.
    - Holders of the action and the Super Admin do receive them.
    - 401 without authentication; 403 without module read.
  - **`AuditAndBootstrapIT`: 4.**
    - Secret-looking keys are redacted.
    - Audit API access control and filters.
    - Bootstrap runs only without a Super Admin: password hashed, change forced, password not in the audit.
    - Bootstrap refuses weak passwords and existing emails, and never promotes.
- **Client:**
  - `npm test` → **39 tests in 7 files, all passing**:
    - Permission helpers and matrix dependency rules.
    - API client: a single refresh for concurrent 401s, session end when refresh is rejected, no tokens in web storage.
    - Routing: anonymous redirect, sidebar per permission, DB-driven labels, unauthorized page, forced password change, phase placeholders, B2C columns hidden/shown.
    - Login: validation, show/hide password, error codes, success.
    - Role management: table, rename, duplicate name, read-only.
    - User management: actions per permission, deactivation confirmation, matrix save payload, scope requirement, sensitive-grant confirmation, read-only matrix, role ceilings.
  - `npm run build` → success.
  - `npm run lint` → 0 errors.
- **Runtime checks:**
  - **Dev startup:** V12 applied and the app reported UP. The bootstrap warning was logged (no variables set).
  - **Log secrets:** the JWT secret is absent from the log. The DB password appears only inside `jdbc:postgresql`/`org.postgresql` strings, because it is the word "postgres".
  - **HTTP smoke test** through the Vite proxy against `partner_erp_test`:
    - login 200, with the HttpOnly cookie on `/api/v1/auth`
    - refresh without the header 403; refresh 200
    - `/me`, `/roles`, `/audit-logs`, `/products` all 200
    - logout, then refresh 401
  - **Headless-browser check** (agent-browser, test database):
    - A protected URL redirects to login, and signing in returns to it.
    - The users list, permission matrix and Role Management render in the IOTIQ design.
    - A permission change with the sensitive-grant confirmation was saved and audited.
    - The audit log page renders. Sign out returns to login, and protected routes then redirect there.
    - localStorage and sessionStorage stay empty, and the refresh cookie is not readable from JavaScript.

### Known issues / limitations
- SELF and TEAM scopes show no records until record ownership (D-30) and a team model exist. Business queries that apply scope arrive with each module (Phases 5–11).
- Login has account lockout but no per-IP rate limiting (planned for Phase 15 with the reverse proxy).
- There is no administrator-initiated password reset or "forgot password" flow; neither was specified.
- The Admin default template has no User Management access, so out of the box only a Super Admin manages users (as specified).
- The client bundle is 668 kB minified (210 kB gzip). Route-level code splitting is planned for Phase 4/15.
- ESLint reports 6 warnings and no errors: 5 fast-refresh export notices and 1 React Compiler notice about React Hook Form `watch`.
- Some Phase 0 client scaffold files are still empty and unused (e.g. `modules/auth/hooks/useLogin.ts`, `modules/auth/services/authService.ts`, other modules' components). They are left for their phases.
- Nothing is committed to git yet (no commit was requested).

### Pending decisions
- D-30 (record ownership) and a team model, needed before SELF/TEAM can show records.
- All other open decisions from D-04 to D-31 are unchanged.

### Next phase
Phase 4, frontend foundation, after your approval. Much of it was delivered here, so its remaining scope can be narrowed.

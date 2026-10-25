# IOTIQ Partner ERP — Implementation Progress

Statuses: `NOT_STARTED` · `IN_PROGRESS` · `BLOCKED` · `COMPLETED`

| Phase | Name | Status | Started | Completed |
|---|---|---|---|---|
| 0 | Source analysis & architecture | COMPLETED (approved 2026-10-24) | 2026-10-24 | 2026-10-24 |
| 1 | Backend foundation | COMPLETED (approved 2026-10-24) | 2026-10-24 | 2026-10-24 |
| 2 | Database schema & seed data | COMPLETED (approved 2026-10-25) | 2026-10-24 | 2026-10-24 |
| 3 | Authentication & authorization (incl. auth/admin screens) | COMPLETED (approved 2026-10-25) | 2026-10-25 | 2026-10-25 |
| 4 | Frontend foundation & design system | COMPLETED (approved 2026-10-25) | 2026-10-25 | 2026-10-25 |
| 5 | Partner management | COMPLETED (approved 2026-10-25) | 2026-10-25 | 2026-10-25 |
| 6 | Lead management | COMPLETED (approved with final changes; awaiting final approval) | 2026-10-25 | 2026-10-25 |
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
  - Nothing is committed to git yet (no commit was requested). *(Committed as `bdf0ec9` at the start of Phase 4.)*
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
  - Nothing is committed to git yet (no commit was requested). *(Committed as `bdf0ec9` at the start of Phase 4.)*
- **Pending decisions:**
  - D-23 (role→permission mapping) must be decided before Phase 3 can enforce authorization.
  - D-16 (partner/lead numbering), D-17 (product deactivate vs delete) and D-26 (soft delete) affect columns that were created nullable here.
  - All other decisions from D-04 to D-31 stay open, with source behaviour applying.
- **Next phase:** Phase 3, authentication and authorization, after approval.

---

## Phase 3: Authentication, authorization, role & user management

- **Status:** COMPLETED (approved 2026-10-25).
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
- The client bundle is 668 kB minified (210 kB gzip). Route-level code splitting is planned for Phase 4/15. *(Done in Phase 4.)*
- ESLint reports 6 warnings and no errors: 5 fast-refresh export notices and 1 React Compiler notice about React Hook Form `watch`.
- Some Phase 0 client scaffold files are still empty and unused (e.g. `modules/auth/hooks/useLogin.ts`, `modules/auth/services/authService.ts`, other modules' components). They are left for their phases.
- Nothing is committed to git yet (no commit was requested). *(Committed as `bdf0ec9` at the start of Phase 4.)*

### Pending decisions
- D-30 (record ownership) and a team model, needed before SELF/TEAM can show records.
- All other open decisions from D-04 to D-31 are unchanged.

### Next phase
Phase 4, frontend foundation, after your approval. Much of it was delivered here, so its remaining scope can be narrowed.

## Phase 4: Frontend foundation & design system

- **Status:** COMPLETED (approved 2026-10-25).
- **Start date:** 2026-10-25 · **Completed:** 2026-10-25
- **Decisions:** `docs/architecture/DECISIONS.md §E` (P4-01…P4-10).
- **Docs:** `docs/frontend/DESIGN_SYSTEM.md`, `docs/frontend/ARCHITECTURE.md`, `docs/frontend/COMPONENTS.md`, `client/README.md`.
- **Git:** Phase 3 checkpoint `bdf0ec9` committed before starting. Phase 4 is committed separately (`feat: complete phase 4 frontend foundation and design system`). Nothing was pushed.

### Completed tasks
1. **Design system.**
   - IOTIQ tokens (ivory `#F4EFE2`, paper `#FBF8EF`, forest `#11271C`, gold `#C39C67`) plus semantic, status, sidebar and chart tokens for light and dark.
   - Fonts: Bricolage Grotesque, Instrument Sans and IBM Plex Mono.
   - Radius, shadow and spacing conventions, print rules and reduced motion.
   - The legacy component CSS classes were removed.
2. **Themes.**
   - Light, Dark and System, persisted per device. A pre-paint script prevents a flash of the wrong theme, and the app follows the OS live while System is selected.
   - Print and PDF always use light tokens.
3. **Application shell.**
   - Sidebar: Dashboard, then Funnel, Sales, Master and Administration (DB module names, permission-filtered, no Internal B2C).
   - Desktop: collapsible with tooltips and persistence. Tablet: icon rail. Phone: drawer.
   - Top bar: breadcrumbs, theme menu, and an account menu with the role display name, Change password and Sign out.
   - Skip link and route error boundary.
4. **Components:** about 60 reusable components across `ui`, `forms`, `tables`, `data`, `feedback`, `layout`, `navigation` and `dashboard` (see COMPONENTS.md).
5. **DataTable:** server pagination and page size, server sorting (`aria-sort`), column visibility, skeleton, empty and error states with retry, row actions, and a focusable horizontal scroll region.
6. **Forms:**
   - RHF + Zod with shared schemas (email, password policy, decimal strings).
   - Server field-error mapping, error summary, accessible field wiring and an unsaved-changes guard.
   - Currency, percentage, number, date and password inputs, and a searchable select.
7. **Formatting:** INR with Indian grouping from decimal strings, without float conversion (`₹1,25,000.00`). Percentages (`18.00%`), dates, relative times, document numbers.
8. **Status badges** for user, partner, lead, estimate, invoice and payout, using backend states only. Derived states are marked.
9. **Dashboard components** (metric card, trend, chart container, activity, alert, summary). The dashboard shows real counts only. Business metrics show an explicit "Phase 12" state.
10. **Screens refined:**
    - Login (session-ended notice), Change password (live policy checklist).
    - Users: filters, sorting, columns.
    - Permission matrix: grouped, sticky module column, unsaved-changes guard.
    - Role Management (now `/roles`): description, immutable code, user count, rename dialog with preview, toast, live top-bar update.
    - Products: read-only; category filter and sorting; B2C columns only with `VIEW_INTERNAL_B2C`.
    - Audit logs: action, target, actor and date filters, and a details dialog with before/after.
11. **API client:**
    - `ApiRequestError.requestId`, shown as a support reference.
    - AbortSignal cancellation through TanStack Query, with cancelled requests never shown as errors.
    - Query-key factories, user-facing error descriptions.
    - The concurrent single-flight refresh is unchanged.
12. **Performance:** every route lazy (including the shell and login), stable vendor chunks.
13. **Error handling:** app-level and route-level error boundaries, a router error page, and recovery from failed chunk loads after a deploy ("A new version is available").

### Performance (production build, minified JS; gzip in brackets)

| | Before (Phase 3) | After (Phase 4) |
|---|---|---|
| JS files | 1 chunk, 668.3 kB (209.9) | 40 chunks, 825.6 kB total (274.6) |
| Largest chunk | 668.3 kB | 218.8 kB (`vendor-react`) |
| Initial JS: Login | 668.3 kB (209.9) | 735.5 kB (238.3) |
| Initial JS: Dashboard | 668.3 kB (209.9) | 622.7 kB (204.8) |
| Initial JS: Products | 668.3 kB (209.9) | 629.5 kB (208.0) |
| Initial JS: Roles / Audit / Permissions | 668.3 kB (209.9) | 642.5 / 643.1 / 636.3 kB (212.2 / 212.6 / 209.9) |
| Initial JS: Users | 668.3 kB (209.9) | 768.1 kB (251.6) |
| CSS | 14.4 kB | 40.6 kB (8.5) |

"Initial JS" is the entry plus every chunk the route imports statically, measured from the build output.
- **Honest summary:** total code grew by about 157 kB because of the design system (Radix menus, popovers, tabs and tooltips; the theme; the components). The vite chunk-size warning is gone.
- **Per route:**
  - Most routes load slightly less than before.
  - Login loads about 28 kB gzip more, because it pulls the forms chunk (zod plus react-hook-form).
  - Users loads about 42 kB gzip more (dialog, forms and dropdown menu).
- **Main gain:** caching. The vendor chunks (react, router, query, forms: 459 kB) don't change between application releases, and a page visited later downloads only its own chunk (4–17 kB).
- **Possible later work (Phase 15):** a lighter validation path on Login, or preloading the forms chunk.

### Build and test results (2026-10-25)
- **Client:**
  - `npm test` → **97 tests in 15 files, all passing.** The 58 new tests cover the 18 required areas:
    - Sidebar responsiveness (desktop, tablet rail, phone, live resize).
    - Collapse and expand with persistence, plus a rail-styling regression test.
    - Mobile drawer: navigation and Escape.
    - Theme switching and persistence, including an invalid saved value.
    - Protected routes and session expiry (`endSession` → login with notice).
    - Permission-aware navigation (groups, Administration, Role Management only, no Internal B2C), breadcrumbs.
    - Role rename updating the top bar.
    - Matrix behaviour (existing tests).
    - Form validation (schemas, decimal comparison, inline accessible errors, focus, server-error mapping).
    - DataTable: pagination, page size, windowing, loading, empty, error, sorting, columns, row actions.
    - Currency and number formatting.
    - Status badges.
    - Error boundaries (fallback, retry, reset on navigation, chunk errors, API error descriptions with request ID).
    - Lazy routes (every page is `React.lazy`; a lazy page renders behind the shell).
    - Internal B2C visibility (existing tests).
  - `npm run lint` → 0 errors, 4 warnings (fast-refresh notices on files that export a provider or context alongside components).
  - `npm run build` → success, no chunk over 500 kB.
- **Server:** `mvn clean verify` → **BUILD SUCCESS, 76 tests** (10 unit, 66 integration), 0 failures. The backend is unchanged in Phase 4.
- **Browser verification** (agent-browser; backend on `partner_erp_test`; integration-test fixture accounts; no production data):
  - **Sign-in flow:** a protected URL redirects to login, and signing in returns to it.
  - **Sidebar:** collapsed on desktop and persisted, with the active item highlighted.
  - **Theme:** Light → Dark persisted across reload. localStorage holds only `iotiq.theme` and `iotiq.sidebar.collapsed`. sessionStorage is empty, and no cookie is readable from JavaScript.
  - **Users, matrix, roles:** the Users list, the permission matrix (also at 390 px) and Role Management in dark mode all render correctly.
  - **Role rename:** the rename preview showed, the top bar updated to the new name immediately, and the name was then reverted.
  - **Products:** as Super Admin, the "Internal B2C view" and B2C columns show. The test seed has no B2C prices, so they show "—". As a USER-role fixture there are no B2C columns or names in the DOM, the sidebar shows only Dashboard and Products, and `/users` shows "You don't have access to this page".
  - **Audit:** the action filter works, and the details dialog shows before/after JSON with no password, token or hash.
  - **Mobile drawer** at 390 px: opens, navigates and closes.
  - **Sign out** returns to login, and protected URLs then redirect there.
  - **Print** from dark mode (PDF) renders a white page with the light palette and no navigation.
  - **Horizontal overflow** was checked at 1440, 1280, 1024, 768, 390 and 360 px on Dashboard, Users, Permissions, Roles, Products, Audit and Change password. Document width equals the viewport on every page after the fixes below; wide tables scroll inside their own region.
  - Screenshots were deleted afterwards, and the backend started for the check was stopped.
- **Defects found by browser verification and fixed:**
  1. **Collapsed rail lost its styling** (no active highlight, wrong item size). The tooltip trigger (Radix Slot) stringified NavLink's className function. The active state is now styled through `aria-current`, and a regression assertion was added.
  2. **Page-level horizontal scroll** on Users, Audit, Roles and Permissions at narrower widths. Absolutely positioned `sr-only` text inside the table escaped its non-positioned scroll container. The DataTable, matrix and tab scrollers are now `relative`.
  3. **Users table scrolled at 1440 px** because of long emails. The name column is capped and the email is truncated, with the full value as a tooltip.

### Responsive behaviour
| Width | Shell | Tables |
|---|---|---|
| 1440 / 1280 | Full sidebar (collapsible) | Fit, except very wide data |
| 1024 | Full sidebar (collapsible) | Users and Audit scroll inside their region |
| 768 | Icon rail | Scroll inside region |
| 390 / 360 | Drawer; breadcrumbs collapse to the current page | Scroll inside region; toolbars stack |

### Accessibility (WCAG 2.2 AA)
- **Contrast:** text, status and input-border colours were checked against their backgrounds in both themes (4.5:1 for text, 3:1 for control borders and focus rings).
- **Keyboard:** all interactive elements are keyboard-operable and use Radix focus management. Dialogs and the drawer trap focus and close on Escape. There is a skip link, visible focus rings and focusable scroll regions.
- **Screen readers:** landmarks (`nav "Main navigation"`, breadcrumb, `main`); `aria-current` on the active link; `aria-sort` on sortable headers; `aria-busy` and `role=status` while loading; `role=alert` for errors. Fields are wired with `aria-invalid`, `aria-describedby` and `aria-required`.
- **Status:** status is always conveyed as text, never by colour alone.
- **Touch:** targets are at least 36 px; navigation links are 40 px.
- **Motion:** reduced motion is respected.
- **Not done:** no automated axe audit or screen-reader session yet (planned for Phase 14).

### Known issues / limitations
- **Bundle:** Login and Users load more initial JS than the Phase 3 single bundle (see Performance).
- **Accessibility:** not yet audited with axe or a real screen reader (Phase 14).
- **Charts:** no chart library yet (Phase 12). Dashboard business metrics are intentionally absent.
- **Labels:** sidebar labels come from the DB module names ("Products / Price Sheet", "Partner Payouts"). The prompt's "Item & Price Sheet" is not used (P4-05).
- **Date filters:** use the native date input, so their display format follows the browser locale (for example mm/dd/yyyy in an en-US browser).
- **Browser fixtures:** browser checks used integration-test fixture accounts in `partner_erp_test`. Resetting that schema for a clean data set was blocked by the tool's permission policy, so the data reflects the last `mvn verify` run.
- **Lint:** 4 lint warnings remain (fast-refresh notices). They are harmless in production builds.
- **Dev server:** a Vite dev server from an earlier session was already running on port 5173 and was reused. It was left running.

### Next phase
Phase 5, partner management, after your approval.

## Phase 5: Partner management

- **Status:** COMPLETED. The conditional-approval items (GSTIN masking, browser verification, regression run, docs, Git checkpoint) are done; awaiting final approval.
- **Start date:** 2026-10-25 · **Completed:** 2026-10-25
- **Decisions:** `docs/architecture/DECISIONS.md §F` (P5-01…P5-14). **API:** `docs/architecture/API_DESIGN.md §3.3`.
- **Git:** committed as `feat: complete phase 5 partner management` on top of the Phase 4 commit `e709c7e`. Nothing was pushed.

### Completed tasks
1. **Backend.**
   - CRUD with optimistic locking, a status patch, and no delete (405, BR-P10).
   - Search (name, city, category, contact, phone), filters (status, category, tier, agreement), whitelisted sorting and pagination.
   - revenue12m (BR-P12): Σ net payments dated on or after today − 365 days, so refunds reduce it and future-dated payments count. Computed in one grouped query per page.
   - Calculated tier, and a performance endpoint (progress, remaining, fees unpaid/paid, leads, payout readiness).
   - Tier review preview and apply (§3.1), with recomputation under row locks and a stale check, plus tier history.
   - PAN, **GSTIN** and bank account masking behind `VIEW_PARTNER_SENSITIVE` (V13).
   - Format validation, and audit records that keep only changed keys with sensitive values masked.
2. **Frontend.**
   - The list page: search, four filters, assigned → calculated tier, revenue, agreement and leads.
   - The detail page: status and tier badges, BR-P6/P7/P8 and grace alerts, performance tiles, an accessible progress bar, profile, agreement and compliance cards, tier history, notes, and a linked-records note.
   - The create/edit page: three sections plus notes, zod rules mirroring the server, server field errors, an unsaved-changes guard, write-only PAN/GSTIN/bank handling, and tier and programme fields gated by approve.
   - The tier review dialog: bulk or single, select and apply, with a "Preview again" flow on 409.
   - Routes `/partners`, `/partners/new`, `/partners/:id` and `/partners/:id/edit`. Partners is live in the sidebar. The audit log has partner actions and a "Partners" target filter.

### Conditional-approval changes (2026-10-25)
1. **GSTIN masking (P5-02).**
   - The GSTIN embeds the PAN, so it is now masked under the existing `VIEW_PARTNER_SENSITIVE` action as `••` + its last 3 characters. Those are the entity number, `Z` and check character, none of them from the PAN. No new permission code was added.
   - Full values stay in the database and are shown to Super Admins and holders of the action.
   - Audit snapshots mask GSTIN. On update, GSTIN follows the null-keeps / ""-removes semantics, and a masked value sent back is rejected without being echoed. GSTIN and PAN are not searchable, and list rows carry neither. There are no exports yet; the statement CSV in Phase 11 must follow P5-02.
2. **Hardening found on the way.**
   - The integrity-violation handler no longer logs the driver message, whose "Failing row contains (…)" can include PAN, GSTIN or bank details (P5-14).
   - Change detection on update compares unmasked values. Before, a new PAN/GSTIN with the same visible tail was skipped by the audit and returned a stale version (P5-08).
3. **Regression tests.**
   - `PartnerApiIT.maskedGstinCannotBeUsedToReconstructThePan` checks that:
     - the detail, update, list, search, validation-error and audit outputs contain neither the full PAN/GSTIN nor the PAN's hidden first 6 characters;
     - the combined masks reveal only the PAN's last 4;
     - keep / replace (same tail) / remove all work.
   - `PartnerMapperTest` checks the mask positions.

### Browser verification (2026-10-25)
- **Setup:**
  - A separate backend on port 8091 against **`partner_erp_test`**, confirmed in the Flyway log line, and a separate Vite on 5181 proxying to it.
  - Accounts: integration-test fixture accounts only (Super Admin, Admin, Admin with Partners read + `VIEW_PARTNER_SENSITIVE`, User). No dev or production data was touched.
  - An unrelated server already running on 8080 and the Vite on 5173 were left alone.
  - Screenshots were deleted, and both servers and all browser sessions were stopped afterwards.
- **Verified:**
  - List: search by city and by category label; status, category, tier and agreement filters; sorting by city (both directions, with `aria-sort`) and by name; pagination summary.
  - Create: Super Admin, Architect, tier 3. Checked the empty-submit error summary and focus, the Architect warning, and the tier hint showing fee % and payout days. Lands on the detail page.
  - Edit: Admin. Tier and programme are disabled, inline PAN validation works, and a cleared PAN field keeps the stored value. Changing the agreement to Signed and the city persisted.
  - Status change: confirmation, then the BR-P6 "Agreement not signed" alert appears for an Active partner.
  - Masking:
    - Super Admin and the sensitive-grant Admin see full PAN, GSTIN and bank account.
    - The plain Admin sees `••234F`, `••1Z5` and `••9012`; the page text contains no full value or hidden PAN prefix. The edit form shows "On file: …" hints and Remove checkboxes.
  - Tier review:
    - The bulk preview (17 of 18 partners, the inactive one excluded) showed an upgrade and two grace periods; unticking one applied 2. The list and tier history show the upgrade, actor and revenue. Grace badge and alert shown.
    - Single-partner preview showed a one-step downgrade. After a concurrent tier edit in a second session, apply returned 409, with Apply disabled and "Preview again" fetching new figures.
  - Permissions:
    - The Admin has no "Run tier review". The read-only viewer has no create, edit, status or review actions and gets an access-denied page on `/partners/new` and `/edit`.
    - The User has no Partners in the navigation, and all four partner URLs show the access-denied page.
  - Layouts at 1440, 1024, 768 and 390 px: no horizontal page overflow on the list, detail, edit or new pages. Tables scroll inside their region. The form and the tier review dialog stack on phones.
- **Issues found and fixed:**
  1. After a successful save, the unsaved-changes guard blocked the redirect with "Leave without saving?". The guard is now off while submitting, and a regression assertion was added; it was confirmed to fail without the fix.
  2. A manual tier change kept the old grace flag, so a partner moved up by hand was immediately offered a step-down. A manual tier or programme change now clears grace (P5-13, new integration test).
  3. At 1440 px the row actions were clipped. The redundant "View" button was removed (the name is the link) and the search placeholder shortened.
  4. The mask alert showed a misplaced second lock icon, and the leads tile had a developer-facing "Phase 6" hint. Both removed.
  5. Dialogs without a description repeated the title as a hidden description, so screen readers announced it twice. The shared `Dialog` now omits it.
  6. The tier review rows repeated the grace wording. Shortened to "(unchanged)".
- **Note:** each `mvn verify` rebuilds the `partner_erp_test` schema (guarded to `*_test` databases), so browser sessions and data from before a test run disappear. This is expected.

### Database changes
- `V13__partner_sensitive_permission.sql`: adds the `PARTNERS` action `VIEW_PARTNER_SENSITIVE`. It is not granted to any template, and Super Admins hold it implicitly. No applied migration was modified, and no migration was added for the GSTIN change (it is response-level).

### Files
- **Server:**
  - `modules/partners/{controller/PartnerController, service/PartnerService, service/TierReviewService, service/TierRules, repository/PartnerRepository, repository/PartnerMetricsRepository, mapper/PartnerMapper, dto/*}`
  - `AuditActions` (partner actions), `common/exception/GlobalExceptionHandler` (safe integrity logging)
  - Tests: `unit/partners/{TierRulesTest, PartnerMapperTest}`, `integration/partners/PartnerApiIT`; `MigrationAndSeedIT` updated.
- **Client:**
  - `modules/partners/{types, services/partnerService, hooks/usePartners, components/PartnerForm, components/TierReviewDialog, components/TierCell, pages/PartnersPage, pages/PartnerDetailsPage, pages/PartnerFormPage, pages/Partners.test}`
  - `components/ui/Dialog.tsx`, `app/router.tsx`, `app/navigation.ts`, `lib/queryKeys.ts`, `lib/status.ts`, `utils/format.ts` (`businessToday`), `types/common.types.ts`, `modules/audit/pages/AuditLogsPage.tsx`, `test/fixtures.ts`.

### Build and test results (2026-10-25, after the conditional-approval changes)
- `mvn clean verify`: **117 tests, 0 failures, 0 errors, 0 skipped**.
  - 35 unit tests: `TierRulesTest` 23, `PartnerMapperTest` 2.
  - 82 integration tests: `PartnerApiIT` 16, including the GSTIN regression and the grace reset.
- `npm test`: **112 tests in 16 files, 0 failures**. 15 are partner tests, now covering GSTIN masking and write-only handling and the save-without-guard regression.
- `npm run lint`: 0 errors (the same 4 fast-refresh warnings as Phase 4).
- `npm run build`: OK. Route chunks: PartnersPage 6.1 kB, PartnerDetailsPage 10.6 kB, PartnerFormPage 11.8 kB (2.2 / 4.0 / 4.4 kB gzip).

### Known issues / limitations
- **Linked lists:** leads, estimates, invoices, payments and payouts on the detail page arrive with their phases. Lead counts and fee totals read the existing tables and are 0 until then.
- **Tier review:** manual only (D-07). There is no scheduled job.
- **Exports:** none in Phase 5. The partner statement CSV (Phase 11) must apply P5-02 masking.
- **Date input:** native, so its display format follows the browser locale.
- **Mobile:** filters stack one per row on phones (shared Toolbar behaviour), so the table starts lower on the screen.

### Next phase
Phase 6, lead management (approved 2026-10-25; see below).

## Phase 6: Lead management

- **Status:** COMPLETED. Approved on 2026-10-25 subject to the final changes below, which are done; awaiting final approval.
- **Start date:** 2026-10-25 · **Completed:** 2026-10-25
- **Decisions:** D-04, D-05, D-26 and D-30 accepted with the recommended options; `docs/architecture/DECISIONS.md §G` (P6-01…P6-12). **API:** `docs/architecture/API_DESIGN.md §3.4`.
- **Git:** committed as `feat: complete phase 6 lead management` on top of the Phase 5 commit `994047d`. Nothing was pushed.

### Final approval changes (2026-10-25)
1. **P6-06 approved:** the referring partner can change until the lead is acknowledged and never after, enforced by the API (field error on `partnerId`) and the form (read-only partner). No reassignment workflow. New tests: `LeadApiIT.thePartnerCanChangeBeforeAcknowledgement`, `Leads.test.tsx` "lets the partner change before acknowledgement".
2. **P6-13, closed leads:** Won and Lost leads can no longer be acknowledged directly. The API returns `422 LEAD_CLOSED` and changes nothing (no fee lock, protection, history or audit row); the list and detail pages show no Acknowledge action and explain that the lead must be reopened. Reopening through the existing approve-gated workflow makes it acknowledgeable. Regression tests: `LeadApiIT.wonAndLostLeadsMustBeReopenedBeforeAcknowledgement` and two `Leads.test.tsx` cases. Documented as an approved deviation from the source.

### Completed tasks
1. **Backend.**
   - CRUD with optimistic locking; search over client, city and partner name; stage, partner, conflict and SLA-breach filters; whitelisted sorting (newest first by default); pagination.
   - Conflict check on every save (BR-L6, D-05): last-10 phone digits, another partner, `protection_until >= today`, live leads only. Saved with `conflict = true` and the protecting partner's name.
   - Acknowledge (BR-L5, `LEADS:APPROVE`): locks the partner's current tier fee, protection = today + 90 days (setting), NEW → REGISTERED, row lock, `409` on a second call.
   - Stage rules (D-04): free, except leaving Won/Lost needs `LEADS:APPROVE`; history row for every change with an optional reason.
   - Soft delete (D-26, `LEADS:DELETE`, migration V14), refused while documents reference the lead.
   - `LeadSla` calculator (§4.1) and the matching SQL breach filter; history endpoint with actor names; meta endpoint (stages, project types, partners with fee rates, SLA targets).
   - Audit: `LEAD_CREATED`, `LEAD_UPDATED`, `LEAD_STAGE_CHANGED`, `LEAD_ACKNOWLEDGED`, `LEAD_DELETED`. Partner lead counts exclude deleted leads.
2. **Frontend.**
   - Leads list: the source columns (client/project with "Possible duplicate" pill, partner link, inline stage select, budget, registration with Acknowledge button or "N% locked · to date/expired", SLA pills), search, stage/partner/SLA/duplicate filters, Edit and Delete row actions.
   - Confirmations: acknowledge (source wording with fee rate, tier and protection days), reopen of a Won/Lost lead with an optional reason, delete with the "mark it Lost instead" rule.
   - Lead detail: registration, SLA block, client & project, stage history timeline, conflict and SLA alerts (§4.2), inline stage select.
   - Lead form (new/edit): client, registration and follow-up sections; partner select showing tier and fee; read-only partner after acknowledgement; read-only stage for closed leads without approve; reason field when the stage changes; conflict warning toast "Saved. Same phone is already protected for X."
   - Partner detail: the partner's leads with the same actions and a "New lead" button that pre-selects the partner (replaces the Phase 5 placeholder).
   - Routes `/leads`, `/leads/new`, `/leads/:id`, `/leads/:id/edit`; Leads is live in the sidebar; audit log has lead actions and a "Leads" target filter.

### Database changes
- `V14__lead_soft_delete.sql`: `leads.deleted_at`, `leads.deleted_by` (FK users) and two partial indexes on live leads. No applied migration was modified.

### Files
- **Server:**
  - `modules/leads/{controller/LeadController, service/LeadService, service/LeadSla, repository/LeadRepository, repository/LeadMetricsRepository, mapper/LeadMapper, dto/*, entity/Lead}`
  - `AuditActions` (lead actions), `PartnerRepository.findAllByOrderByNameAsc`, `PartnerMetricsRepository.leadCounts` (live leads only)
  - Tests: `unit/leads/LeadSlaTest`, `integration/leads/LeadApiIT`.
- **Client:**
  - `modules/leads/{types/lead.types, services/leadService, hooks/useLeads, utils/leadDisplay, components/LeadBadges, components/LeadDialogs, components/LeadForm, components/LeadTable, components/StageSelect, pages/LeadsPage, pages/LeadDetailsPage, pages/LeadFormPage, pages/Leads.test}`
  - `services/api.ts` (`apiDelete`), `lib/queryKeys.ts`, `app/router.tsx`, `app/navigation.ts`, `modules/partners/pages/PartnerDetailsPage.tsx`, `modules/audit/pages/AuditLogsPage.tsx`; routing tests now use Estimates as the not-yet-built example.

### Browser verification (2026-10-25)
- **Setup:** a separate backend on port 8091 against **`partner_erp_test`** (confirmed in the Flyway log: schema version 14), a separate Vite on 5181 proxying to it. Accounts: integration-test fixture accounts only (Super Admin, Admin, an Admin granted Leads approve + delete by the Super Admin, User). Test partners and leads tagged "P6 …" were created through the API; created/acknowledged times and one estimate were back-dated in the test DB to exercise the SLA clocks. The server on 8080 was left alone. Screenshots were deleted, and both test servers and all browser sessions were stopped afterwards.
- **Verified:**
  - List: the source columns, "Possible duplicate" pills, "8.00% locked · to <date>" registration text, SLA pills; the SLA-late filter returned exactly the late-acknowledgement lead and the late-estimate lead (first estimate 7 days after acknowledgement); search; stage, partner and duplicate filters.
  - Acknowledge (approver): confirmation "Acknowledge “Mehta Towers …” for Arc Builders …? The 8.00% (Registered) fee rate locks now and 90-day protection starts."; stage New → Registered; protection to 23 Jan 2027 (today + 90).
  - Reopen (approver): Won → Negotiation asked for confirmation and a reason; the detail history shows "Reopened: Won → Negotiation" with the reason and actor.
  - Delete of a lead with an estimate: refused with "This lead has estimates, invoices or payments, so it can't be deleted. Mark it Lost instead."
  - Create (Admin): empty submit shows "Client name is required."; "New lead" from the partner pre-selects the partner; a phone protected for another partner saved with the toast "Saved. Same phone is already protected for Kumar Electricals …" and the detail alert.
  - Permissions: the Admin sees no Acknowledge or Delete; after marking a lead Lost the Admin's inline select is disabled ("Reopening a Won or Lost lead needs Leads approve.") and the edit form shows the stage read-only. The User has no Leads in the navigation and gets the access-denied page on `/leads`.
  - Partner detail: the partner's leads table with inline actions and "New lead".
  - Layouts at 1440, 1024, 768 and 390 px on the list, detail, new and edit pages: no horizontal page overflow; the detail page stacks on phones.
- **Issues found and fixed during the phase:** the Units and Budget fields were not labelled for assistive technology (the `Controller` sat inside `FormField`, so the label's `id` landed on the Controller); fixed and covered by the form test. No further issues were found in the browser pass.

### Build and test results (2026-10-26, after the final approval changes)
- `mvn verify`: **146 tests, 0 failures, 0 errors, 0 skipped** (43 unit, including `LeadSlaTest` 8; 103 integration, including `LeadApiIT` 21). Run **without `clean`** because a Spring Boot dev server on port 8080 (not started by this phase) runs from `target/classes`; as instructed, it was neither stopped nor modified.
- `npm test`: **127 tests in 17 files, 0 failures** (`Leads.test.tsx` 15; `Partners.test.tsx` checks the new partner leads section).
- `npm run lint`: 0 errors (the same 4 fast-refresh warnings as before).
- `npm run build`: OK. Route chunks: LeadsPage 3.4 kB, LeadDetailsPage 6.7 kB, LeadFormPage 8.1 kB, shared LeadTable 3.0 kB (1.4 / 2.6 / 3.2 / 1.3 kB gzip).

### Known issues / limitations
- Approved deviations from the source: P6-06 (partner fixed after acknowledgement) and P6-13 (no acknowledgement of Won/Lost leads).
- Unacknowledged Won/Lost leads show "Ack ok", as in the source (their SLA clock stops).
- No partner reassignment workflow for acknowledged leads (out of Phase 6 scope).
- Automatic stage changes from estimates and invoices arrive with Phases 8–9; the "Estimate" row action with Phase 8; lead alerts on the dashboard with Phase 12.
- `lead_number` and client email wait for D-16, which stays open until those requirements are finalized.

### Next phase
Phase 7, product & price sheet, after your approval. Not started.

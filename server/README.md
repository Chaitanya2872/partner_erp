# IOTIQ Partner ERP: Server

Spring Boot modular monolith (Java 17, Spring Boot 4.1.1, Spring Security 7, PostgreSQL, Flyway).

## Prerequisites
- JDK 17 (`java -version`)
- Maven 3.9+
- PostgreSQL 16+ with a `partner_erp` database (and `partner_erp_test` for integration tests)

## Local setup
1. Copy `.env.example` to `.env` (git-ignored, read only by the `dev` and `test` profiles) and set:
   - `DB_PASSWORD`
   - `JWT_SECRET`: at least 32 random bytes, e.g. `openssl rand -base64 48`. Startup fails without it.
2. Run:
   ```
   mvn clean compile
   mvn spring-boot:run
   ```
   The `dev` profile is the default. Flyway applies migrations at startup.

## First Super Admin (one-time bootstrap)
No account is seeded and no password is hard-coded. The first Super Admin is created from environment variables **only while no Super Admin exists**:

1. Add to `server/.env` (or the process environment):
   ```
   BOOTSTRAP_SUPERADMIN_EMAIL=owner@yourcompany.example
   BOOTSTRAP_SUPERADMIN_PASSWORD=<12-128 chars, at least one letter and one digit>
   BOOTSTRAP_SUPERADMIN_NAME=Your Name
   ```
2. Start the server. The log shows `Created the bootstrap Super Admin account (o***@yourcompany.example)`; the password is never logged, and only its BCrypt hash is stored.
3. **Remove the three variables** from `.env`/the environment. They are ignored once a Super Admin exists, but should not linger.
4. Sign in. The account must change its password before anything else is allowed.

Startup refuses to continue (with a message that does not include the password) if the email is invalid, the password fails the policy, or the email already belongs to another account. Existing accounts are never promoted. If no Super Admin exists and the variables are absent, the server starts and logs a warning.

## Endpoints (dev)
| URL | Purpose |
|---|---|
| http://localhost:8080/actuator/health | Health (includes DB status in dev) |
| http://localhost:8080/actuator/health/liveness, /readiness | Probes |
| http://localhost:8080/actuator/info | Build info |
| http://localhost:8080/swagger-ui.html | OpenAPI UI (disabled in `prd`) |
| http://localhost:8080/v3/api-docs | OpenAPI JSON (disabled in `prd`) |

Public: `/api/v1/auth/login`, `/auth/refresh`, `/auth/logout`, health/info and API docs. Everything else under `/api/v1/**` needs a bearer access token and the module permission named in `docs/architecture/API_DESIGN.md`.

## Authentication in short
- `POST /api/v1/auth/login` returns a 15-minute access token in the body and sets the refresh cookie `iotiq_rt` (HttpOnly, SameSite=Strict, Path `/api/v1/auth`, 7 days, Secure outside dev).
- `POST /api/v1/auth/refresh` and `/logout` need the cookie and an `X-Requested-With` header. Refresh tokens rotate on every use; replaying an old one revokes the whole session family.
- Permissions are read from the database on every request, so changes, deactivation and password changes take effect immediately.

## Configuration
| Variable | Default (dev) | Notes |
|---|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/partner_erp` | required in `prd` |
| `DB_USERNAME` | `postgres` | required in `prd` |
| `DB_PASSWORD` | — | always required; never commit |
| `JWT_SECRET` | — | always required; ≥ 32 bytes; never commit. Changing it signs everyone out. |
| `REFRESH_COOKIE_SECURE` | `false` in dev, `true` otherwise | forced `true` in `prd` |
| `BOOTSTRAP_SUPERADMIN_EMAIL` / `_PASSWORD` / `_NAME` | — | one-time bootstrap, see above |
| `CORS_ORIGINS` | `http://localhost:5173` | comma-separated allowlist |
| `SERVER_PORT` | `8080` | |
| `SPRING_PROFILES_ACTIVE` | `dev` | `prd` for production (JSON logs, no Swagger, no health details) |

Fixed policy (in `application.yml`, `app.security`): access token 15 min, refresh token 7 days, lockout after 5 failed sign-ins for 15 min, passwords 12–128 characters.

## Conventions
- Responses use the envelope `{ success, data, meta, error, timestamp }`. Errors carry `code`, `fieldErrors` and `requestId` (also returned in the `X-Request-Id` header).
- Migrations are append-only once applied: add a new `V<n>__*.sql`, never edit an applied one.
- Package layout: `com.iotiq.partnererp.modules.<module>.{controller,dto,entity,mapper,repository,service}`.

## Tests
- `mvn test`: unit tests.
- `mvn clean verify`: unit + integration tests (`*IT`) against the local `partner_erp_test` database. The schema is rebuilt (Flyway clean + migrate) at context start; the cleaner refuses any database whose name does not end in `_test`. Integration tests use the real security filter chain through MockMvc and create their own accounts.

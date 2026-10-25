-- Phase 3: three system roles with editable display names, and per-user module permissions.
--
-- Replaces the Phase 2 proposal (6 roles, role -> permission-code mapping, which was never populated):
--   * roles.name becomes the immutable roles.code; display_name is editable; 4 proposal roles are retired
--     (active = false, kept for history) and USER is added.
--   * a user has exactly one role (users.role_id); the empty user_roles join table is dropped after backfill.
--   * module access (read/write/delete/export/approve + data scope) is stored per user in
--     user_module_permissions; role templates only seed new accounts.
--   * permissions becomes the catalogue of action permissions; the 29 proposal codes are retired, and
--     module-specific sensitive actions are granted per user in user_action_permissions.
--   * role_permissions (empty, superseded) is dropped.
-- No user gains privileges from this migration: existing users (none at the time of writing) keep
-- SUPER_ADMIN/ADMIN, any other role maps to USER, and no module permissions are granted to them.

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM role_permissions) THEN
        RAISE EXCEPTION 'role_permissions has rows; review them before applying V12';
    END IF;
END $$;

-- ---------------------------------------------------------------- roles
ALTER TABLE roles RENAME COLUMN name TO code;
ALTER TABLE roles RENAME CONSTRAINT ux_roles_name TO ux_roles_code;
ALTER TABLE roles
    ADD COLUMN display_name varchar(60),
    ADD COLUMN active       boolean     NOT NULL DEFAULT true,
    ADD COLUMN created_at   timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN updated_at   timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN updated_by   uuid REFERENCES users (id),
    ADD COLUMN version      bigint      NOT NULL DEFAULT 0;

UPDATE roles SET display_name = 'Super Admin',
                 description  = 'Full access to every module and action, including user, role and permission management'
 WHERE code = 'SUPER_ADMIN';
UPDATE roles SET display_name = 'Admin',
                 description  = 'Administrative user; module access is configured per account'
 WHERE code = 'ADMIN';
UPDATE roles SET display_name = initcap(replace(lower(code), '_', ' ')),
                 description  = 'Retired in V12 (three-role model); responsibilities are now granted as permissions',
                 active       = false
 WHERE code IN ('SALES_MANAGER', 'SALES_EXECUTIVE', 'FINANCE', 'PARTNER');
INSERT INTO roles (code, display_name, description)
VALUES ('USER', 'User', 'Standard user; module access is configured per account');

ALTER TABLE roles
    ALTER COLUMN display_name SET NOT NULL,
    ADD CONSTRAINT ck_roles_display_name CHECK (length(btrim(display_name)) >= 2);
-- Display names are unique among active roles (retired names may be reused).
CREATE UNIQUE INDEX ux_roles_display_name ON roles (lower(btrim(display_name))) WHERE active;
CREATE TRIGGER trg_roles_updated_at BEFORE UPDATE ON roles FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Role codes are referenced by security checks and must never change.
CREATE FUNCTION prevent_role_code_change() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF NEW.code IS DISTINCT FROM OLD.code THEN
        RAISE EXCEPTION 'Role codes are immutable (role %)', OLD.code USING ERRCODE = 'check_violation';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER trg_roles_code_immutable BEFORE UPDATE OF code ON roles
    FOR EACH ROW EXECUTE FUNCTION prevent_role_code_change();

-- ---------------------------------------------------------------- users: single role, session control
ALTER TABLE users
    ADD COLUMN role_id         uuid REFERENCES roles (id),
    ADD COLUMN session_version integer NOT NULL DEFAULT 0,
    ADD COLUMN locked_until    timestamptz;

UPDATE users u
   SET role_id = (SELECT r.id FROM roles r WHERE r.code = m.code)
  FROM (SELECT ur.user_id,
               CASE WHEN bool_or(r.code = 'SUPER_ADMIN') THEN 'SUPER_ADMIN'
                    WHEN bool_or(r.code = 'ADMIN') THEN 'ADMIN'
                    ELSE 'USER' END AS code
          FROM user_roles ur JOIN roles r ON r.id = ur.role_id
         GROUP BY ur.user_id) m
 WHERE m.user_id = u.id;
UPDATE users SET role_id = (SELECT id FROM roles WHERE code = 'USER') WHERE role_id IS NULL;

ALTER TABLE users ALTER COLUMN role_id SET NOT NULL;
CREATE INDEX ix_users_role ON users (role_id);
CREATE INDEX ix_users_status ON users (status);

DROP TABLE user_roles;
DROP TABLE role_permissions;

-- ---------------------------------------------------------------- modules
CREATE TABLE modules (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code             varchar(40)  NOT NULL CONSTRAINT ux_modules_code UNIQUE,
    display_name     varchar(80)  NOT NULL,
    description      varchar(255),
    sort_order       smallint     NOT NULL CONSTRAINT ux_modules_sort UNIQUE,
    supports_write   boolean      NOT NULL DEFAULT false,
    supports_delete  boolean      NOT NULL DEFAULT false,
    supports_export  boolean      NOT NULL DEFAULT false,
    supports_approve boolean      NOT NULL DEFAULT false,
    supports_scope   boolean      NOT NULL DEFAULT false,
    super_admin_only boolean      NOT NULL DEFAULT false   -- cannot be granted to ADMIN or USER accounts
);

INSERT INTO modules (code, display_name, description, sort_order,
                     supports_write, supports_delete, supports_export, supports_approve, supports_scope, super_admin_only)
VALUES ('DASHBOARD', 'Dashboard', 'KPIs, alerts and the partner funnel', 1, false, false, false, false, false, false),
       ('PARTNERS', 'Partners', 'Channel partners; Approve = apply tier reviews', 2, true, true, true, true, true, false),
       ('LEADS', 'Leads', 'Deal registration; Approve = acknowledge registrations', 3, true, true, true, true, true, false),
       ('PRODUCTS', 'Products / Price Sheet', 'Partner price sheet', 4, true, true, true, false, false, false),
       ('ESTIMATES', 'Estimates', 'Partner-copy estimates', 5, true, true, true, false, true, false),
       ('INVOICES', 'Invoices', 'Partner-copy invoices; Delete = cancel', 6, true, true, true, false, true, false),
       ('PAYMENTS', 'Payments', 'Payments received and refunds', 7, true, false, true, false, true, false),
       ('PAYOUTS', 'Partner Payouts', 'Referral fee accruals and payouts', 8, true, false, true, false, true, false),
       ('INTERNAL_B2C', 'Internal B2C', 'Internal B2C documents and price comparisons', 9, false, false, true, false, false, false),
       ('REPORTS', 'Reports', 'Operational and financial reports', 10, false, false, true, false, false, false),
       ('SETTINGS', 'Settings', 'Company profile and document defaults', 11, true, false, false, false, false, false),
       ('USER_MANAGEMENT', 'User Management', 'User accounts and their permissions', 12, true, false, false, false, false, false),
       ('ROLE_MANAGEMENT', 'Role Management', 'Role display names and descriptions', 13, true, false, false, false, false, true),
       ('AUDIT_LOGS', 'Audit Logs', 'Security and change history', 14, false, false, true, false, false, false);

-- ---------------------------------------------------------------- action permission catalogue
ALTER TABLE permissions
    ADD COLUMN display_name     varchar(80),
    ADD COLUMN kind             varchar(10) NOT NULL DEFAULT 'MODULE'
        CONSTRAINT ck_permissions_kind CHECK (kind IN ('GENERIC', 'MODULE')),
    ADD COLUMN module_id        uuid REFERENCES modules (id),
    ADD COLUMN active           boolean     NOT NULL DEFAULT true,
    ADD COLUMN super_admin_only boolean     NOT NULL DEFAULT false,
    ADD COLUMN sort_order       smallint;

UPDATE permissions
   SET active = false,
       display_name = code,
       description = description || ' (retired in V12; superseded by module permissions)';

-- GENERIC actions are per-module flags (user_module_permissions.can_*); MODULE actions are granted
-- individually (user_action_permissions) and belong to one module.
INSERT INTO permissions (code, display_name, description, kind, module_id, super_admin_only, sort_order)
VALUES ('DELETE', 'Delete', 'Delete (or cancel) records of a module', 'GENERIC', NULL, false, 1),
       ('EXPORT', 'Export', 'Export or download module data', 'GENERIC', NULL, false, 2),
       ('APPROVE', 'Approve', 'Approve sensitive module operations', 'GENERIC', NULL, false, 3),
       ('MARK_PAYOUT_PAID', 'Mark payout paid', 'Record UTR and mark partner payouts paid', 'MODULE',
        (SELECT id FROM modules WHERE code = 'PAYOUTS'), false, 4),
       ('REVERSE_PAYMENT', 'Reverse payment', 'Reverse or refund a recorded payment', 'MODULE',
        (SELECT id FROM modules WHERE code = 'PAYMENTS'), false, 5),
       ('VIEW_INTERNAL_B2C', 'View internal B2C prices', 'See B2C names, B2C prices and landed cost on the price sheet', 'MODULE',
        (SELECT id FROM modules WHERE code = 'PRODUCTS'), false, 6),
       ('MANAGE_USERS', 'Manage users', 'Activate and deactivate user accounts', 'MODULE',
        (SELECT id FROM modules WHERE code = 'USER_MANAGEMENT'), false, 7),
       ('MANAGE_PERMISSIONS', 'Manage permissions', 'Change user module and action permissions (Super Admin only in this version)', 'MODULE',
        (SELECT id FROM modules WHERE code = 'USER_MANAGEMENT'), true, 8),
       ('MANAGE_SETTINGS', 'Manage commercial settings', 'Change commercial policy settings (tiers, TDS, clawback, protection)', 'MODULE',
        (SELECT id FROM modules WHERE code = 'SETTINGS'), false, 9);

ALTER TABLE permissions
    ALTER COLUMN display_name SET NOT NULL,
    ADD CONSTRAINT ck_permissions_module CHECK ((kind = 'MODULE') = (module_id IS NOT NULL) OR NOT active);

-- ---------------------------------------------------------------- per-user permissions
-- A row means the user can at least read the module; no row = no access.
CREATE TABLE user_module_permissions (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    module_id   uuid        NOT NULL REFERENCES modules (id),
    can_read    boolean     NOT NULL DEFAULT true CONSTRAINT ck_ump_read CHECK (can_read),
    can_write   boolean     NOT NULL DEFAULT false,
    can_delete  boolean     NOT NULL DEFAULT false,
    can_export  boolean     NOT NULL DEFAULT false,
    can_approve boolean     NOT NULL DEFAULT false,
    data_scope  varchar(10) NOT NULL DEFAULT 'ALL' CONSTRAINT ck_ump_scope CHECK (data_scope IN ('SELF', 'TEAM', 'ALL')),
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    created_by  uuid REFERENCES users (id),
    updated_by  uuid REFERENCES users (id),
    CONSTRAINT ux_ump_user_module UNIQUE (user_id, module_id),
    CONSTRAINT ck_ump_delete_needs_write CHECK (NOT can_delete OR can_write)
);
CREATE INDEX ix_ump_module ON user_module_permissions (module_id);
CREATE TRIGGER trg_ump_updated_at BEFORE UPDATE ON user_module_permissions
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE user_action_permissions (
    user_id       uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    permission_id uuid        NOT NULL REFERENCES permissions (id),
    granted_at    timestamptz NOT NULL DEFAULT now(),
    granted_by    uuid REFERENCES users (id),
    PRIMARY KEY (user_id, permission_id)
);
CREATE INDEX ix_uap_permission ON user_action_permissions (permission_id);

-- ---------------------------------------------------------------- role templates (defaults for new accounts)
-- SUPER_ADMIN has no template: its access is implicit and does not depend on stored permissions.
CREATE TABLE role_permission_templates (
    role_id     uuid        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    module_id   uuid        NOT NULL REFERENCES modules (id),
    can_write   boolean     NOT NULL DEFAULT false,
    can_delete  boolean     NOT NULL DEFAULT false,
    can_export  boolean     NOT NULL DEFAULT false,
    can_approve boolean     NOT NULL DEFAULT false,
    data_scope  varchar(10) NOT NULL DEFAULT 'ALL' CONSTRAINT ck_rpt_scope CHECK (data_scope IN ('SELF', 'TEAM', 'ALL')),
    PRIMARY KEY (role_id, module_id),
    CONSTRAINT ck_rpt_delete_needs_write CHECK (NOT can_delete OR can_write)
);

INSERT INTO role_permission_templates (role_id, module_id, can_write)
SELECT r.id, m.id, t.can_write
  FROM (VALUES ('ADMIN', 'DASHBOARD', false),
               ('ADMIN', 'PARTNERS', true),
               ('ADMIN', 'LEADS', true),
               ('ADMIN', 'PRODUCTS', true),
               ('ADMIN', 'ESTIMATES', true),
               ('ADMIN', 'INVOICES', true),
               ('ADMIN', 'PAYMENTS', true),
               ('ADMIN', 'PAYOUTS', false),
               ('ADMIN', 'REPORTS', false),
               ('USER', 'DASHBOARD', false),
               ('USER', 'PRODUCTS', false)) AS t (role_code, module_code, can_write)
  JOIN roles r ON r.code = t.role_code
  JOIN modules m ON m.code = t.module_code;

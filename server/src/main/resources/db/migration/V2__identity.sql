-- Identity and access: users, roles, permissions, refresh tokens (ADR-07).
-- Role -> permission assignments are NOT seeded here: the permission matrix (DECISIONS D-23) awaits approval.

CREATE TABLE users (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    full_name            varchar(150) NOT NULL,
    email                varchar(254) NOT NULL,
    password_hash        varchar(100) NOT NULL,
    status               varchar(20)  NOT NULL DEFAULT 'ACTIVE'
        CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED')),
    must_change_password boolean      NOT NULL DEFAULT false,
    failed_login_count   integer      NOT NULL DEFAULT 0 CONSTRAINT ck_users_failed_login CHECK (failed_login_count >= 0),
    last_login_at        timestamptz,
    password_changed_at  timestamptz,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    created_by           uuid REFERENCES users (id),
    updated_by           uuid REFERENCES users (id),
    version              bigint       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));
CREATE TRIGGER trg_users_updated_at BEFORE UPDATE ON users FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE roles (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name        varchar(40)  NOT NULL CONSTRAINT ux_roles_name UNIQUE,
    description varchar(255)
);

CREATE TABLE permissions (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        varchar(60)  NOT NULL CONSTRAINT ux_permissions_code UNIQUE,
    description varchar(255)
);

CREATE TABLE user_roles (
    user_id uuid NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_id uuid NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);
CREATE INDEX ix_user_roles_role ON user_roles (role_id);

CREATE TABLE role_permissions (
    role_id       uuid NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_id uuid NOT NULL REFERENCES permissions (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);
CREATE INDEX ix_role_permissions_permission ON role_permissions (permission_id);

CREATE TABLE refresh_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  varchar(128) NOT NULL CONSTRAINT ux_refresh_tokens_hash UNIQUE,
    family_id   uuid         NOT NULL,
    expires_at  timestamptz  NOT NULL,
    revoked_at  timestamptz,
    replaced_by uuid REFERENCES refresh_tokens (id),
    created_at  timestamptz  NOT NULL DEFAULT now(),
    user_agent  varchar(255),
    ip_address  varchar(45)
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);

-- Roles proposed in the master prompt (production enhancement; no source equivalent).
INSERT INTO roles (name, description) VALUES
    ('SUPER_ADMIN',     'Full access including administrator management'),
    ('ADMIN',           'Administration of settings, users and all business data'),
    ('SALES_MANAGER',   'Manages partners, leads, products and sales documents'),
    ('SALES_EXECUTIVE', 'Registers leads and prepares estimates and invoices'),
    ('FINANCE',         'Payments, partner payouts, receivables and financial reports'),
    ('PARTNER',         'External partner user (only if a partner portal is approved, D-22)');

-- Permission catalogue from docs/architecture/API_DESIGN.md §2.
INSERT INTO permissions (code, description) VALUES
    ('PARTNER_READ',           'View partners'),
    ('PARTNER_WRITE',          'Create and edit partners'),
    ('PARTNER_SENSITIVE_READ', 'View unmasked PAN and bank details'),
    ('TIER_REVIEW',            'Run and apply partner tier reviews'),
    ('LEAD_READ',              'View leads'),
    ('LEAD_WRITE',             'Create and edit leads'),
    ('LEAD_ACK',               'Acknowledge lead registrations'),
    ('LEAD_REOPEN',            'Move a lead out of Won or Lost'),
    ('LEAD_DELETE',            'Delete leads'),
    ('PRODUCT_READ',           'View products'),
    ('PRODUCT_WRITE',          'Create and edit products'),
    ('PRODUCT_IMPORT',         'Import products from CSV'),
    ('B2C_VIEW',               'View internal B2C names, prices and documents'),
    ('B2C_PRICE_WRITE',        'Edit B2C prices and landed cost'),
    ('DOC_READ',               'View estimates and invoices'),
    ('DOC_WRITE',              'Create and edit estimates and invoices'),
    ('DOC_FEE_OVERRIDE',       'Override the referral fee percentage on documents'),
    ('INVOICE_CANCEL',         'Cancel invoices'),
    ('PAYMENT_READ',           'View payments'),
    ('PAYMENT_WRITE',          'Record payments'),
    ('PAYMENT_REVERSE',        'Reverse payments'),
    ('PAYOUT_READ',            'View partner payouts'),
    ('PAYOUT_PAY',             'Mark partner payouts paid'),
    ('PAYOUT_REVERSE',         'Reverse partner payouts'),
    ('REPORT_READ',            'View reports'),
    ('SETTINGS_READ',          'View settings'),
    ('SETTINGS_WRITE',         'Edit settings'),
    ('USER_ADMIN',             'Manage users and roles'),
    ('AUDIT_READ',             'View audit logs');

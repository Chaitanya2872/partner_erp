-- Partner master (source collection "cps") and tier review history.

CREATE TABLE partners (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_code        varchar(20) CONSTRAINT ux_partners_code UNIQUE,  -- from ER; population pending D-16
    name                varchar(200) NOT NULL,
    category            varchar(40)  NOT NULL CONSTRAINT ck_partners_category CHECK (category IN (
                            'INTERIOR_DESIGNER', 'ARCHITECT', 'BUILDER', 'DEVELOPER', 'CONTRACTOR',
                            'ELECTRICAL_DEALER', 'KITCHEN_FURNITURE_SHOWROOM', 'SOCIETY_REAL_ESTATE_APP', 'OTHER')),
    status              varchar(20)  NOT NULL DEFAULT 'PROSPECT'
        CONSTRAINT ck_partners_status CHECK (status IN ('PROSPECT', 'ONBOARDING', 'ACTIVE', 'INACTIVE')),
    contact_name        varchar(150),
    phone               varchar(30),
    email               varchar(254),
    city                varchar(100),
    tier_id             uuid         NOT NULL REFERENCES partner_tiers (id),
    strategic_programme boolean      NOT NULL DEFAULT false,
    downgrade_grace     boolean      NOT NULL DEFAULT false,
    last_tier_review_on date,
    agreement_status    varchar(20)  NOT NULL DEFAULT 'NOT_SENT'
        CONSTRAINT ck_partners_agreement CHECK (agreement_status IN ('NOT_SENT', 'SENT', 'SIGNED')),
    agreement_version   varchar(20)  DEFAULT 'v2.1',
    agreement_date      date,
    pan                 varchar(10),
    gstin               varchar(15),
    licence_no          varchar(100),
    bank_name           varchar(150),
    bank_account        varchar(34),
    ifsc                varchar(11),
    notes               text,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES users (id),
    updated_by          uuid REFERENCES users (id),
    version             bigint       NOT NULL DEFAULT 0
);
CREATE INDEX ix_partners_name ON partners (lower(name));
CREATE INDEX ix_partners_status ON partners (status);
CREATE INDEX ix_partners_tier ON partners (tier_id);
CREATE TRIGGER trg_partners_updated_at BEFORE UPDATE ON partners FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE partner_tier_reviews (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    partner_id  uuid           NOT NULL REFERENCES partners (id) ON DELETE CASCADE,
    from_level  smallint       NOT NULL CONSTRAINT ck_ptr_from CHECK (from_level BETWEEN 1 AND 4),
    to_level    smallint       NOT NULL CONSTRAINT ck_ptr_to CHECK (to_level BETWEEN 1 AND 4),
    action      varchar(20)    NOT NULL
        CONSTRAINT ck_ptr_action CHECK (action IN ('UPGRADE', 'DOWNGRADE', 'GRACE', 'RECOVERED', 'AUTO_UPGRADE')),
    revenue_12m numeric(14, 2) NOT NULL,
    programme   boolean        NOT NULL DEFAULT false,
    actor_id    uuid REFERENCES users (id),
    payment_id  uuid,          -- set for AUTO_UPGRADE; FK added in V9 once payments exists
    reviewed_at timestamptz    NOT NULL DEFAULT now()
);
CREATE INDEX ix_ptr_partner ON partner_tier_reviews (partner_id, reviewed_at DESC);

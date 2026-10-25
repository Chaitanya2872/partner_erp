-- Company settings (single row) and the four partner tiers, seeded with the source defaults
-- (DEF and DEFTIERS in docs/reference/IOTIQ_Partner_ERP_original.html; BUSINESS_RULES.md §2).

CREATE TABLE company_settings (
    id                     uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    singleton              boolean       NOT NULL DEFAULT true
        CONSTRAINT ux_company_settings_singleton UNIQUE
        CONSTRAINT ck_company_settings_singleton CHECK (singleton),
    company_name           varchar(200)  NOT NULL,
    address                text,
    gstin                  varchar(15),
    phone                  varchar(30),
    email                  varchar(254),
    terms                  text,
    lead_protection_days   integer       NOT NULL CONSTRAINT ck_cs_protect CHECK (lead_protection_days >= 0),
    clawback_days          integer       NOT NULL CONSTRAINT ck_cs_clawback CHECK (clawback_days >= 0),
    tds_percent            numeric(6, 3) NOT NULL CONSTRAINT ck_cs_tds_pct CHECK (tds_percent BETWEEN 0 AND 100),
    tds_threshold          numeric(14, 2) NOT NULL CONSTRAINT ck_cs_tds_threshold CHECK (tds_threshold >= 0),
    acknowledgement_hours  integer       NOT NULL CONSTRAINT ck_cs_ack CHECK (acknowledgement_hours >= 0),
    estimate_sla_days      integer       NOT NULL CONSTRAINT ck_cs_est_sla CHECK (estimate_sla_days >= 0),
    estimate_validity_days integer       NOT NULL CONSTRAINT ck_cs_valid CHECK (estimate_validity_days >= 0),
    invoice_due_days       integer       NOT NULL CONSTRAINT ck_cs_due CHECK (invoice_due_days >= 0),
    default_gst_percent    numeric(6, 3) NOT NULL CONSTRAINT ck_cs_gst CHECK (default_gst_percent BETWEEN 0 AND 100),
    created_at             timestamptz   NOT NULL DEFAULT now(),
    updated_at             timestamptz   NOT NULL DEFAULT now(),
    created_by             uuid REFERENCES users (id),
    updated_by             uuid REFERENCES users (id),
    version                bigint        NOT NULL DEFAULT 0
);
CREATE TRIGGER trg_company_settings_updated_at BEFORE UPDATE ON company_settings
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO company_settings (company_name, address, gstin, phone, email, terms,
                              lead_protection_days, clawback_days, tds_percent, tds_threshold,
                              acknowledgement_hours, estimate_sla_days, estimate_validity_days,
                              invoice_due_days, default_gst_percent)
VALUES ('IOTIQ',
        '6th Floor, Pardha''s Picasa, Durgam Cheruvu Road, Kavuri Hills, Madhapur, Hyderabad, Telangana - 500081',
        NULL,
        '8121234162',
        'gowtham.k@iotiq.co.in',
        'Quote valid for the period shown. Prices exclude site civil, wiring and back-box work unless listed. Payment: as agreed in the order confirmation.',
        90, 90, 2, 20000, 48, 5, 15, 15, 18);

CREATE TABLE partner_tiers (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tier_level           smallint       NOT NULL CONSTRAINT ux_partner_tiers_level UNIQUE
        CONSTRAINT ck_partner_tiers_level CHECK (tier_level BETWEEN 1 AND 4),
    name                 varchar(60)    NOT NULL,
    commission_percent   numeric(6, 3)  NOT NULL CONSTRAINT ck_pt_commission CHECK (commission_percent BETWEEN 0 AND 100),
    minimum_revenue      numeric(14, 2) NOT NULL CONSTRAINT ck_pt_min_revenue CHECK (minimum_revenue >= 0),
    payout_business_days smallint       NOT NULL CONSTRAINT ck_pt_payout_days CHECK (payout_business_days >= 0),
    created_at           timestamptz    NOT NULL DEFAULT now(),
    updated_at           timestamptz    NOT NULL DEFAULT now(),
    created_by           uuid REFERENCES users (id),
    updated_by           uuid REFERENCES users (id),
    version              bigint         NOT NULL DEFAULT 0
);
CREATE TRIGGER trg_partner_tiers_updated_at BEFORE UPDATE ON partner_tiers
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

INSERT INTO partner_tiers (tier_level, name, commission_percent, minimum_revenue, payout_business_days) VALUES
    (1, 'Registered', 8,  0,         15),
    (2, 'Preferred',  10, 1000000,   12),
    (3, 'Elite',      12, 3000000,   10),
    (4, 'Strategic',  15, 10000000,  7);

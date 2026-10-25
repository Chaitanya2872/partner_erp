-- Leads / deal registration (source collection "leads") and stage history.

CREATE TABLE leads (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_number         varchar(20) CONSTRAINT ux_leads_number UNIQUE,   -- from ER; population pending D-16
    partner_id          uuid         NOT NULL REFERENCES partners (id),
    client_name         varchar(200) NOT NULL,
    client_phone        varchar(30),
    -- Last ten digits of the phone, used by the duplicate/conflict check (source digits()).
    client_phone_digits varchar(10) GENERATED ALWAYS AS
                            (right(regexp_replace(coalesce(client_phone, ''), '\D', '', 'g'), 10)) STORED,
    client_email        varchar(254),                                    -- from ER; pending D-16
    city                varchar(150),
    project_type        varchar(30)  NOT NULL CONSTRAINT ck_leads_project_type CHECK (project_type IN (
                            'VILLA', 'APARTMENT', 'MULTI_UNIT_PROJECT', 'OFFICE', 'RETROFIT_RENOVATION', 'OTHER')),
    units               integer      CONSTRAINT ck_leads_units CHECK (units >= 0),
    budget              numeric(14, 2) CONSTRAINT ck_leads_budget CHECK (budget >= 0),
    stage               varchar(20)  NOT NULL DEFAULT 'NEW' CONSTRAINT ck_leads_stage CHECK (stage IN (
                            'NEW', 'REGISTERED', 'PRESALES', 'SITE_VISIT', 'ESTIMATE_SENT', 'NEGOTIATION', 'WON', 'LOST')),
    presales_owner      varchar(150),
    expected_close      date,
    source              varchar(200),
    notes               text,
    acknowledged_at     timestamptz,
    acknowledged_by     uuid REFERENCES users (id),
    locked_fee_percent  numeric(6, 3) CONSTRAINT ck_leads_fee CHECK (locked_fee_percent BETWEEN 0 AND 100),
    protection_until    date,
    conflict            boolean      NOT NULL DEFAULT false,
    created_at          timestamptz  NOT NULL DEFAULT now(),
    updated_at          timestamptz  NOT NULL DEFAULT now(),
    created_by          uuid REFERENCES users (id),
    updated_by          uuid REFERENCES users (id),
    version             bigint       NOT NULL DEFAULT 0,
    -- Acknowledging always locks the fee and starts protection together (source ackLead).
    CONSTRAINT ck_leads_ack_complete CHECK (
        acknowledged_at IS NULL OR (locked_fee_percent IS NOT NULL AND protection_until IS NOT NULL))
);
CREATE INDEX ix_leads_partner ON leads (partner_id);
CREATE INDEX ix_leads_stage ON leads (stage);
CREATE INDEX ix_leads_phone_protection ON leads (client_phone_digits, protection_until);
CREATE INDEX ix_leads_created ON leads (created_at DESC);
CREATE TRIGGER trg_leads_updated_at BEFORE UPDATE ON leads FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE lead_stage_history (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    lead_id     uuid        NOT NULL REFERENCES leads (id) ON DELETE CASCADE,
    event       varchar(20) NOT NULL
        CONSTRAINT ck_lsh_event CHECK (event IN ('CREATED', 'STAGE_CHANGED', 'ACKNOWLEDGED', 'AUTO_STAGE')),
    from_stage  varchar(20),
    to_stage    varchar(20),
    reason      varchar(500),
    actor_id    uuid REFERENCES users (id),
    occurred_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX ix_lsh_lead ON lead_stage_history (lead_id, occurred_at);

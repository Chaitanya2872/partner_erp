-- Append-only audit trail. Sensitive values (PAN, bank account, passwords, tokens) are redacted by the
-- application before they are written here.

CREATE TABLE audit_logs (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid REFERENCES users (id) ON DELETE SET NULL,
    entity_type varchar(60)  NOT NULL,
    entity_id   uuid,
    action      varchar(40)  NOT NULL,
    old_values  jsonb,
    new_values  jsonb,
    ip_address  varchar(45),
    request_id  varchar(64),
    created_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX ix_audit_entity ON audit_logs (entity_type, entity_id);
CREATE INDEX ix_audit_created ON audit_logs (created_at DESC);
CREATE INDEX ix_audit_user ON audit_logs (user_id);

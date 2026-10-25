-- Phase 6 (D-26): leads are soft-deleted so stage history and audit references stay intact.
-- A lead with estimates, invoices, payments or internal documents cannot be deleted at all (mark it Lost instead).

ALTER TABLE leads
    ADD COLUMN deleted_at timestamptz,
    ADD COLUMN deleted_by uuid REFERENCES users (id);

-- Lists, searches and the conflict check only ever read live leads.
CREATE INDEX ix_leads_live_created ON leads (created_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX ix_leads_live_phone ON leads (client_phone_digits, protection_until) WHERE deleted_at IS NULL;

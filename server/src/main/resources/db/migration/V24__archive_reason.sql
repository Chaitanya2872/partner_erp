-- Archive with a reason: leads and estimates are still only ever soft-deleted (nothing is physically removed),
-- but every new archive now says why. Archived rows can be listed, read and restored.

-- Backfilling must not touch updated_at of rows that were archived long ago.
ALTER TABLE leads DISABLE TRIGGER trg_leads_updated_at;
ALTER TABLE estimates DISABLE TRIGGER trg_estimates_updated_at;

ALTER TABLE leads ADD COLUMN delete_reason varchar(500);
ALTER TABLE estimates ADD COLUMN delete_reason varchar(500);

UPDATE leads SET delete_reason = 'Deleted before reasons were required' WHERE deleted_at IS NOT NULL;
UPDATE estimates SET delete_reason = 'Deleted before reasons were required' WHERE deleted_at IS NOT NULL;

ALTER TABLE leads ENABLE TRIGGER trg_leads_updated_at;
ALTER TABLE estimates ENABLE TRIGGER trg_estimates_updated_at;

-- An archived row always carries a non-blank reason; a live row carries none.
ALTER TABLE leads ADD CONSTRAINT ck_leads_delete_reason
    CHECK ((deleted_at IS NULL AND delete_reason IS NULL)
        OR (deleted_at IS NOT NULL AND coalesce(length(btrim(delete_reason)), 0) > 0));
ALTER TABLE estimates ADD CONSTRAINT ck_estimates_delete_reason
    CHECK ((deleted_at IS NULL AND delete_reason IS NULL)
        OR (deleted_at IS NOT NULL AND coalesce(length(btrim(delete_reason)), 0) > 0));

CREATE INDEX ix_leads_archived ON leads (deleted_at DESC) WHERE deleted_at IS NOT NULL;
CREATE INDEX ix_estimates_archived ON estimates (deleted_at DESC) WHERE deleted_at IS NOT NULL;

-- The lead history records the archive and the restore.
ALTER TABLE lead_stage_history DROP CONSTRAINT ck_lsh_event;
ALTER TABLE lead_stage_history ADD CONSTRAINT ck_lsh_event
    CHECK (event IN ('CREATED', 'STAGE_CHANGED', 'ACKNOWLEDGED', 'AUTO_STAGE', 'ARCHIVED', 'RESTORED'));

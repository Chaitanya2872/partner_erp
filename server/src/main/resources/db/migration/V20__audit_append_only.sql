-- Phase 13: the audit trail becomes append-only in the database too (V10 relied on the application).
-- Rows can't be deleted, truncated or changed. The only update allowed is the one the users foreign key performs
-- (ON DELETE SET NULL on user_id), with every other column unchanged; accounts are deactivated, not deleted, so it
-- doesn't happen in normal use. V1-V19 are unchanged.

CREATE OR REPLACE FUNCTION audit_logs_guard() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF NEW.user_id IS NULL AND OLD.user_id IS NOT NULL
            AND NEW.id = OLD.id
            AND NEW.entity_type = OLD.entity_type
            AND NEW.entity_id IS NOT DISTINCT FROM OLD.entity_id
            AND NEW.action = OLD.action
            AND NEW.old_values IS NOT DISTINCT FROM OLD.old_values
            AND NEW.new_values IS NOT DISTINCT FROM OLD.new_values
            AND NEW.ip_address IS NOT DISTINCT FROM OLD.ip_address
            AND NEW.request_id IS NOT DISTINCT FROM OLD.request_id
            AND NEW.created_at = OLD.created_at THEN
            RETURN NEW;
        END IF;
    END IF;
    RAISE EXCEPTION 'The audit log is append-only: entries can''t be changed or deleted'
        USING ERRCODE = 'check_violation';
END;
$$;

CREATE TRIGGER trg_audit_logs_guard BEFORE UPDATE OR DELETE ON audit_logs
    FOR EACH ROW EXECUTE FUNCTION audit_logs_guard();
CREATE TRIGGER trg_audit_logs_no_truncate BEFORE TRUNCATE ON audit_logs
    FOR EACH STATEMENT EXECUTE FUNCTION audit_logs_guard();

-- The viewer filters by action (module filter, action filter) within a date range, newest first.
CREATE INDEX ix_audit_action_created ON audit_logs (action, created_at DESC);

-- Baseline: extensions and shared helpers used by later migrations.
-- Business tables are created from V2 onwards (Phase 2, see docs/architecture/DATABASE_DESIGN.md §6).

-- gen_random_uuid() for UUID primary keys (built into PG 13+, pgcrypto kept for digest/crypt helpers).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- Keeps updated_at current on every UPDATE; attached per table in later migrations.
CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

-- V12 — Property detail page fields (spec: SPEC.md capability B2)
--
-- Three additions the redesigned mobile detail page renders:
--   1. possession_status — "Ready to Move" / "Under Construction" / "New Launch"
--   2. parking_count     — the mockup shows a count ("1 Car Parking"), not a yes/no
--   3. ref_seq           — backs the human-readable reference code (PF000100000)
--
-- NOTE ON VERSIONING: the shelved feature/dtcp-plot-layouts branch carries its own
-- V11__add_plot_layouts.sql. If that branch is ever revived it must renumber to V16+.

-- ── 1. Possession status ─────────────────────────────────────
-- Nullable and deliberately NOT backfilled: neither available_from nor
-- age_of_property can tell us whether a listing is ready to move without
-- guessing, and a guessed possession status is a claim the seller never made.
-- Existing rows stay NULL and the detail page hides the row.
ALTER TABLE properties ADD COLUMN possession_status VARCHAR(24);

-- ── 2. Parking count ─────────────────────────────────────────
-- parking_available stays: search filters and the post wizard both use it, and
-- it is the only parking signal on every existing row. The count refines it.
-- Display rule (client): parking_count ?? (parking_available ? 1 : 0).
ALTER TABLE properties ADD COLUMN parking_count SMALLINT;

ALTER TABLE properties ADD CONSTRAINT chk_parking_count_sane
    CHECK (parking_count IS NULL OR (parking_count >= 0 AND parking_count <= 20));

-- ── 3. Reference code sequence ───────────────────────────────
-- Buyers quote a code over the phone; a UUID is unusable for that.
-- The display code is derived in the mapper as 'PF' + zero-padded ref_seq.
--
-- Starts at 100000 rather than 1 so the code does not publish our exact listing
-- count — PF000100000 for the first listing instead of PF000000001.
-- A sequence (not a MAX+1 read) means concurrent inserts cannot collide.
CREATE SEQUENCE property_ref_seq START WITH 100000 INCREMENT BY 1;

ALTER TABLE properties ADD COLUMN ref_seq BIGINT;

-- Backfill every existing row before the NOT NULL goes on.
UPDATE properties SET ref_seq = nextval('property_ref_seq') WHERE ref_seq IS NULL;

ALTER TABLE properties ALTER COLUMN ref_seq SET NOT NULL;
ALTER TABLE properties ALTER COLUMN ref_seq SET DEFAULT nextval('property_ref_seq');
ALTER TABLE properties ADD CONSTRAINT uq_properties_ref_seq UNIQUE (ref_seq);

-- The column default is load-bearing: the entity maps ref_seq with Hibernate's
-- @Generated(INSERT), so the INSERT omits the column and Hibernate reads the
-- generated value back. Dropping this default would insert NULL and fail.

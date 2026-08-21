-- V17 — make the redesigned filter screen usable on existing data.
--
-- Two backfills and a set of indexes. No new columns: possession_status,
-- listed_by, facing, is_verified, parking_* and the amenities join table all
-- already exist (V2/V6/V12) — what was missing was the search layer, not the
-- schema.
--
-- NOTE: enum-valued columns are VARCHAR since V4, so the new
-- PropertyType.WAREHOUSE and the Facing search vocabulary need no DDL here.

-- ─────────────────────────────────────────────────────────────
-- 1. Possession status
-- ─────────────────────────────────────────────────────────────
-- V12 added the column and deliberately left existing rows NULL. That makes a
-- "Ready to Move" filter hide every pre-V12 listing, which reads as "no
-- results" rather than "no data". Everything listed so far is an existing,
-- occupiable property, so READY_TO_MOVE is the correct default.
UPDATE properties
   SET possession_status = 'READY_TO_MOVE'
 WHERE possession_status IS NULL;

-- ─────────────────────────────────────────────────────────────
-- 2. Facing
-- ─────────────────────────────────────────────────────────────
-- Free text written by the post wizard ('East', 'south east', ...). Search
-- matches upper(facing) so casing is already handled, but normalising to a
-- single canonical Title Case keeps the detail screens consistent. Deliberately
-- NOT upper-cased: this value is rendered verbatim to users.
UPDATE properties
   SET facing = initcap(trim(facing))
 WHERE facing IS NOT NULL
   AND facing <> initcap(trim(facing));

-- ─────────────────────────────────────────────────────────────
-- 3. Indexes for the new filter predicates
-- ─────────────────────────────────────────────────────────────
-- V2 indexed owner/locality/status/(listing_type,property_type)/price/bedrooms.
-- These are the newly filterable columns with enough cardinality to matter.
CREATE INDEX IF NOT EXISTS idx_properties_possession  ON properties (possession_status);
CREATE INDEX IF NOT EXISTS idx_properties_listed_by   ON properties (listed_by);

-- Partial: the filter only ever asks for verified = TRUE, same shape as V2's
-- is_featured index.
CREATE INDEX IF NOT EXISTS idx_properties_verified
    ON properties (is_verified) WHERE is_verified = TRUE;

-- ─────────────────────────────────────────────────────────────
-- 4. Map viewport (bounding-box) queries
-- ─────────────────────────────────────────────────────────────
-- "Search this area" filters latitude BETWEEN ? AND ? AND longitude BETWEEN ?
-- AND ?. A composite index serves that better than two single-column ones.
-- Partial, because rows without coordinates can never appear on the map.
CREATE INDEX IF NOT EXISTS idx_properties_lat_lng
    ON properties (latitude, longitude)
    WHERE latitude IS NOT NULL AND longitude IS NOT NULL;

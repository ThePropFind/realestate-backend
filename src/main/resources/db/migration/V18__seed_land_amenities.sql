-- V18 — Amenities for land and farm listings
--
-- The amenity master (V2 + V14) only ever described flats: lifts, clubhouses,
-- piped gas. A plot or farmland seller therefore reached the Features step of
-- the post wizard and found nothing that applied to them, so the whole step was
-- dead weight on exactly the listings that need the most detail.
--
-- These carry category = 'land' so the client can show them only for
-- PLOT / AGRICULTURAL_LAND listings and hide the building amenities there.
-- Water source, electricity, fencing, compound wall and corner-plot already
-- have dedicated columns on properties — they are deliberately NOT duplicated
-- here.
--
-- Idempotent on name so a re-run (or a partially-seeded environment) cannot
-- create duplicates — amenities have no unique constraint on name.

INSERT INTO amenities (name, category, icon_key)
SELECT v.name, v.category, v.icon_key
FROM (VALUES
    ('Farm House',      'land', 'farm-house'),
    ('Storage Shed',    'land', 'storage-shed'),
    ('Drip Irrigation', 'land', 'drip-irrigation'),
    ('Farm Road Access','land', 'farm-road'),
    ('Cattle Shed',     'land', 'cattle-shed'),
    ('Water Tank',      'land', 'water-tank')
) AS v(name, category, icon_key)
WHERE NOT EXISTS (SELECT 1 FROM amenities a WHERE a.name = v.name);

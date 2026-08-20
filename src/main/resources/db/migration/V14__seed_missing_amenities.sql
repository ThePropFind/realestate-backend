-- V14 — Amenities the detail-page mockup shows but V2 never seeded
-- (spec: SPEC.md capability B4)
--
-- V2 seeded 20 amenities. Three of the eight in the mockup's amenity grid were
-- missing entirely, so a seller could not tick them and the grid could never
-- render them. icon_key follows the existing kebab-case convention and is what
-- the mobile client maps to an Ionicons name.
--
-- Idempotent on name so a re-run (or a partially-seeded environment) cannot
-- create duplicates — amenities have no unique constraint on name.

INSERT INTO amenities (name, category, icon_key)
SELECT v.name, v.category, v.icon_key
FROM (VALUES
    ('24x7 Water Supply', 'utilities', 'water'),
    ('Vastu Compliant',   'utilities', 'vastu'),
    ('Drainage',          'utilities', 'drainage')
) AS v(name, category, icon_key)
WHERE NOT EXISTS (SELECT 1 FROM amenities a WHERE a.name = v.name);

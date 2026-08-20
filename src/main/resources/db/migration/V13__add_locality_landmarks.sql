-- V13 — Nearby places, moved server-side (spec: SPEC.md capability B3)
--
-- These landmarks previously lived in realestate-mobile/src/lib/landmarks.ts as a
-- hardcoded client table, which meant the web app could never show the same data
-- and the two would drift. Google Places Nearby Search is the "real" answer but
-- costs ~$17 per 1k requests, so the curated set moves to the server instead:
-- free, one source of truth, and swappable for Places later without a client change.
--
-- Distances are human-readable strings measured from the locality centre. They are
-- realistic but NOT surveyed — the column is a label, deliberately not a number, so
-- nothing downstream can present them as precise measurements.

CREATE TABLE locality_landmarks (
    id             UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    -- NULL locality_id marks the fallback set for a city, used when a locality
    -- has no curated rows of its own.
    locality_id    UUID REFERENCES localities(id) ON DELETE CASCADE,
    -- city_id is what scopes the fallback. Without it the fallback is global and
    -- a Chennai listing in an uncurated locality is served Coimbatore landmarks —
    -- a factual error the detail page would present to a buyer as fact.
    city_id        UUID NOT NULL REFERENCES cities(id) ON DELETE CASCADE,
    name           VARCHAR(120) NOT NULL,
    kind           VARCHAR(20)  NOT NULL,
    distance_label VARCHAR(20)  NOT NULL,
    sort_order     SMALLINT     NOT NULL DEFAULT 0
);

CREATE INDEX idx_locality_landmarks_locality ON locality_landmarks (locality_id, sort_order);
CREATE INDEX idx_locality_landmarks_city_fallback
    ON locality_landmarks (city_id, sort_order) WHERE locality_id IS NULL;

-- kind drives the icon on the client; keep it a closed set so an unknown value
-- can never reach the UI without a migration.
ALTER TABLE locality_landmarks ADD CONSTRAINT chk_landmark_kind
    CHECK (kind IN ('HOSPITAL', 'SCHOOL', 'MALL', 'TRANSPORT', 'FOOD', 'PARK', 'TECH'));

-- ── Per-locality curated sets (Coimbatore) ───────────────────
-- Joined on city slug as well as locality slug: locality slugs are not globally
-- unique (anna-nagar exists in both Chennai and Madurai).
INSERT INTO locality_landmarks (locality_id, city_id, name, kind, distance_label, sort_order)
SELECT l.id, c.id, v.name, v.kind, v.distance_label, v.sort_order
FROM (VALUES
    ('rs-puram',       'Cross Cut Road',           'MALL',      '450 m',  0),
    ('rs-puram',       'PSG Hospitals',            'HOSPITAL',  '3.5 km', 1),
    ('rs-puram',       'Stanes School',            'SCHOOL',    '1.1 km', 2),
    ('rs-puram',       'Race Course',              'PARK',      '900 m',  3),
    ('rs-puram',       'Gandhipuram Bus Stand',    'TRANSPORT', '3.0 km', 4),
    ('rs-puram',       'Annapoorna Restaurant',    'FOOD',      '800 m',  5),

    ('saibaba-colony', 'GRD Tower',                'MALL',      '750 m',  0),
    ('saibaba-colony', 'KMCH Hospital',            'HOSPITAL',  '1.6 km', 1),
    ('saibaba-colony', 'CMS College',              'SCHOOL',    '1.0 km', 2),
    ('saibaba-colony', 'NSR Road Market',          'MALL',      '500 m',  3),
    ('saibaba-colony', 'Brookefields Mall',        'MALL',      '5.5 km', 4),
    ('saibaba-colony', 'Coimbatore Junction',      'TRANSPORT', '5.0 km', 5),

    ('peelamedu',      'PSG College of Tech',      'SCHOOL',    '600 m',  0),
    ('peelamedu',      'Hindustan College',        'SCHOOL',    '1.4 km', 1),
    ('peelamedu',      'PSG Hospitals',            'HOSPITAL',  '1.2 km', 2),
    ('peelamedu',      'Fun Republic Mall',        'MALL',      '2.5 km', 3),
    ('peelamedu',      'Singanallur Bus Stand',    'TRANSPORT', '3.0 km', 4),
    ('peelamedu',      'Coimbatore Intl. Airport', 'TRANSPORT', '4.5 km', 5),

    ('gandhipuram',    'Gandhipuram Bus Stand',    'TRANSPORT', '300 m',  0),
    ('gandhipuram',    '100 Feet Road',            'MALL',      '500 m',  1),
    ('gandhipuram',    'Lakshmi Hospital',         'HOSPITAL',  '900 m',  2),
    ('gandhipuram',    'Coimbatore Junction',      'TRANSPORT', '2.5 km', 3),
    ('gandhipuram',    'CMS College',              'SCHOOL',    '2.2 km', 4),
    ('gandhipuram',    'Sree Annapoorna Sweets',   'FOOD',      '600 m',  5),

    ('singanallur',    'Singanallur Lake',         'PARK',      '700 m',  0),
    ('singanallur',    'Singanallur Bus Stand',    'TRANSPORT', '500 m',  1),
    ('singanallur',    'Coimbatore Intl. Airport', 'TRANSPORT', '3.0 km', 2),
    ('singanallur',    'PSG IMSR',                 'HOSPITAL',  '4.5 km', 3),
    ('singanallur',    'Hindustan College',        'SCHOOL',    '4.0 km', 4),
    ('singanallur',    'Fun Republic Mall',        'MALL',      '4.2 km', 5),

    ('hopes-college',  'Hope College Junction',    'TRANSPORT', '300 m',  0),
    ('hopes-college',  'PSG College of Tech',      'SCHOOL',    '1.1 km', 1),
    ('hopes-college',  'PSG Hospitals',            'HOSPITAL',  '1.4 km', 2),
    ('hopes-college',  'Fun Republic Mall',        'MALL',      '2.0 km', 3),
    ('hopes-college',  'Coimbatore Intl. Airport', 'TRANSPORT', '5.5 km', 4),
    ('hopes-college',  'Avinashi Road Market',     'MALL',      '600 m',  5),

    ('tidel-park',     'Tidel Park Coimbatore',    'TECH',      '350 m',  0),
    ('tidel-park',     'KGiSL Campus',             'TECH',      '1.0 km', 1),
    ('tidel-park',     'KMCH Hospital',            'HOSPITAL',  '6.0 km', 2),
    ('tidel-park',     'Brookefields Mall',        'MALL',      '6.5 km', 3),
    ('tidel-park',     'Coimbatore Intl. Airport', 'TRANSPORT', '7.5 km', 4),
    ('tidel-park',     'Hopes College Junction',   'TRANSPORT', '5.0 km', 5),

    ('ramanathapuram', 'Ramanathapuram Bus Stop',  'TRANSPORT', '400 m',  0),
    ('ramanathapuram', 'Sankara Eye Hospital',     'HOSPITAL',  '1.8 km', 1),
    ('ramanathapuram', 'Suguna PIP School',        'SCHOOL',    '1.2 km', 2),
    ('ramanathapuram', 'Brookefields Mall',        'MALL',      '3.5 km', 3),
    ('ramanathapuram', 'Coimbatore Junction',      'TRANSPORT', '2.8 km', 4),
    ('ramanathapuram', 'Trichy Road',              'MALL',      '500 m',  5)
) AS v(locality_slug, name, kind, distance_label, sort_order)
JOIN localities l ON l.slug = v.locality_slug
JOIN cities     c ON c.id   = l.city_id AND c.slug = 'coimbatore';

-- ── Per-city fallback (locality_id IS NULL) ──────────────────
-- Served when a listing's locality has no curated rows, so the section is never
-- empty for a Coimbatore listing in an uncurated locality.
--
-- Scoped to Coimbatore on purpose: a city with no fallback rows returns nothing
-- rather than borrowing another city's landmarks. An empty section is a gap;
-- the wrong city's landmarks is a lie. Seed a fallback set per city as each
-- city is activated.
INSERT INTO locality_landmarks (locality_id, city_id, name, kind, distance_label, sort_order)
SELECT NULL, c.id, v.name, v.kind, v.distance_label, v.sort_order
FROM (VALUES
    ('Brookefields Mall',        'MALL',      '5.5 km',  0),
    ('Coimbatore Junction',      'TRANSPORT', '6.0 km',  1),
    ('PSG Hospitals',            'HOSPITAL',  '4.8 km',  2),
    ('Coimbatore Intl. Airport', 'TRANSPORT', '12.0 km', 3)
) AS v(name, kind, distance_label, sort_order)
CROSS JOIN cities c
WHERE c.slug = 'coimbatore';

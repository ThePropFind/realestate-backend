-- Which kind of plot a PLOT listing is: residential, commercial or industrial.
--
-- Fixes regression #91. The property_type enum has a single PLOT value, so the
-- distinction lived only in the post wizard's client state and was never sent.
-- Editing a listing therefore had nothing to read it back from and reopened
-- every plot as "Residential Plot" — a seller correcting a typo on a
-- commercial plot silently re-filed it as residential.
--
-- Nullable, and stored as VARCHAR like every other enum here (V4 converted the
-- native Postgres enums away): NULL is correct and permanent for the ~all
-- listings that are not plots, and for the plots posted before this column
-- existed, whose use genuinely is not known. Clients must read NULL as
-- "unspecified" and not substitute a default.
ALTER TABLE properties
    ADD COLUMN IF NOT EXISTS plot_use VARCHAR(20);

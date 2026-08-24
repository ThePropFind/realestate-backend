-- V19 — Walkthrough video on a listing
--
-- One video per property, not a gallery: sellers shoot a single walkthrough, and
-- a second column beats a table we would only ever read one row from. If
-- multi-video ever lands, this becomes property_videos and the column is dropped
-- in that migration.
--
-- Stores the public URL only. The bytes live in the same object store as the
-- images (public bucket in prod, /uploads in dev) — videos are marketing
-- material, not the private documents in property_documents.

ALTER TABLE properties ADD COLUMN IF NOT EXISTS video_url VARCHAR(500);

COMMENT ON COLUMN properties.video_url IS
    'Public URL of the listing walkthrough video. NULL when the seller uploaded none.';

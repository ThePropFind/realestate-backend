-- Postal code for a listing's address. Nullable and intentionally NOT backfilled:
-- a locality does not map 1:1 to a PIN, so any guessed value would be wrong data.
ALTER TABLE properties ADD COLUMN pincode VARCHAR(6);

-- Indian PIN codes are six digits and never start with 0.
ALTER TABLE properties ADD CONSTRAINT chk_pincode_format
    CHECK (pincode IS NULL OR pincode ~ '^[1-9][0-9]{5}$');

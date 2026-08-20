-- V15 — Listing reports (spec: SPEC.md capability B6)
--
-- Buyers flag a listing from the detail page's Safety & reporting section:
-- fraud, already sold, wrong information, duplicate, offensive content.
-- Guests may report too — requiring a login to flag a scam listing would mean
-- the listings most worth flagging get flagged least. Abuse is bounded by the
-- existing per-IP inquiry rate limit (5/hr) rather than by an account wall.
--
-- Admin moderation UI is deliberately out of scope: rows land here and are
-- read by hand until the volume justifies a screen.

CREATE TABLE property_reports (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    property_id      UUID         NOT NULL REFERENCES properties(id) ON DELETE CASCADE,
    -- NULL for a guest report. SET NULL rather than CASCADE on delete: a report
    -- outlives the account that filed it, otherwise a bad actor could erase the
    -- reports against listings by deleting the reporting account.
    reporter_user_id UUID                  REFERENCES users(id)      ON DELETE SET NULL,

    -- FRAUD_OR_SCAM | ALREADY_SOLD_OR_RENTED | INCORRECT_INFO
    -- | DUPLICATE_LISTING | OFFENSIVE_CONTENT | OTHER
    reason           VARCHAR(32)  NOT NULL,
    details          TEXT,

    -- OPEN | REVIEWING | ACTIONED | DISMISSED
    status           VARCHAR(20)  NOT NULL DEFAULT 'OPEN',

    created_at       TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- The moderation queue reads by status and works oldest-first; the property
-- index backs "how many times has this listing been flagged".
CREATE INDEX idx_property_reports_property ON property_reports (property_id);
CREATE INDEX idx_property_reports_status   ON property_reports (status, created_at);

CREATE TRIGGER trg_property_reports_updated_at
    BEFORE UPDATE ON property_reports
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

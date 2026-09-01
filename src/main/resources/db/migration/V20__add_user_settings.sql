-- Per-user notification preference for the Settings screen.
--
-- Exactly ONE column, deliberately. The app sends three emails
-- (EmailService): the verification OTP, the password-reset OTP and the
-- "someone enquired about your listing" notice. The first two are security
-- mail and must not be opt-out-able, so they get no preference. That leaves
-- the inquiry notice as the only thing a user can honestly be given a switch
-- for — a Settings screen with four toggles would be three-quarters fiction.
-- Add a column here when a new non-transactional email actually ships.
--
-- NOT NULL DEFAULT TRUE: every existing user already receives these, so
-- defaulting to FALSE would silently unsubscribe the whole table.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS notify_email_inquiries BOOLEAN NOT NULL DEFAULT TRUE;

-- E-mails are normalized to lower case by the application; make the database enforce it too.
-- Normalize any existing rows first so the constraints can be added.
UPDATE users SET email = lower(btrim(email));
UPDATE group_invitations SET email = lower(btrim(email));

ALTER TABLE users
    ADD CONSTRAINT chk_users_email_lowercase CHECK (email = lower(email));
ALTER TABLE group_invitations
    ADD CONSTRAINT chk_group_invitations_email_lowercase CHECK (email = lower(email));

-- Lists are read by group, newest first. These indexes serve that ordering directly and
-- make the plain group_id indexes from V2 redundant (group_id is their leading column).
DROP INDEX idx_expenses_group_id;
DROP INDEX idx_payments_group_id;

CREATE INDEX idx_expenses_group_id_created_at
    ON expenses (group_id, created_at DESC, id DESC);
CREATE INDEX idx_payments_group_id_paid_at
    ON payments (group_id, paid_at DESC, id DESC);

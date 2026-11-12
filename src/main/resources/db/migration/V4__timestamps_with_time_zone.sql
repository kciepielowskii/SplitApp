-- Store points in time as TIMESTAMPTZ instead of zone-less TIMESTAMP.
-- Existing values are interpreted as UTC.

ALTER TABLE users
ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE expense_group
ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE group_members
ALTER COLUMN joined_at TYPE TIMESTAMPTZ USING joined_at AT TIME ZONE 'UTC';

ALTER TABLE expenses
ALTER COLUMN created_at TYPE TIMESTAMPTZ USING created_at AT TIME ZONE 'UTC';

ALTER TABLE payments
ALTER COLUMN paid_at TYPE TIMESTAMPTZ USING paid_at AT TIME ZONE 'UTC';
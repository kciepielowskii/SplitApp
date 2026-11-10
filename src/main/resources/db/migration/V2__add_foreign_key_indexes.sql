-- Postgres does not index foreign key columns automatically.
-- group_members(group_id) and expense_splits(expense_id) are already covered
-- by the leading column of their UNIQUE constraints.

CREATE INDEX idx_expense_group_user_id ON expense_group (user_id);
CREATE INDEX idx_group_members_user_id ON group_members (user_id);
CREATE INDEX idx_expenses_group_id ON expenses (group_id);
CREATE INDEX idx_expenses_paid_by_id ON expenses (paid_by_id);
CREATE INDEX idx_expense_splits_user_id ON expense_splits (user_id);
CREATE INDEX idx_payments_group_id ON payments (group_id);
CREATE INDEX idx_payments_sender_id ON payments (sender_id);
CREATE INDEX idx_payments_receiver_id ON payments (receiver_id);
-- Make the creator column distinct from group_members.user_id.
ALTER TABLE expense_group RENAME COLUMN user_id TO created_by_id;
ALTER INDEX idx_expense_group_user_id RENAME TO idx_expense_group_created_by_id;
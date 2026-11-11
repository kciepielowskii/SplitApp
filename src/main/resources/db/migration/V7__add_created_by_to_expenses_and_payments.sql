ALTER TABLE expenses ADD COLUMN created_by_id BIGINT;
UPDATE expenses SET created_by_id = paid_by_id;
ALTER TABLE expenses ALTER COLUMN created_by_id SET NOT NULL;
ALTER TABLE expenses
    ADD CONSTRAINT fk_expenses_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);
CREATE INDEX idx_expenses_created_by_id ON expenses (created_by_id);

ALTER TABLE payments ADD COLUMN created_by_id BIGINT;
UPDATE payments SET created_by_id = sender_id;
ALTER TABLE payments ALTER COLUMN created_by_id SET NOT NULL;
ALTER TABLE payments
    ADD CONSTRAINT fk_payments_created_by FOREIGN KEY (created_by_id) REFERENCES users (id);
CREATE INDEX idx_payments_created_by_id ON payments (created_by_id);

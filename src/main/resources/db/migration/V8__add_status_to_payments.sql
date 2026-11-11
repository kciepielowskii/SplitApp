-- existing payments were already counted in balances, so they stay confirmed
ALTER TABLE payments ADD COLUMN status varchar(20) NOT NULL DEFAULT 'CONFIRMED';
ALTER TABLE payments ALTER COLUMN status DROP DEFAULT;
ALTER TABLE payments
    ADD CONSTRAINT chk_payments_status CHECK (status IN ('PENDING', 'CONFIRMED'));

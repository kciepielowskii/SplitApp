-- A payment must go between two different users.
ALTER TABLE payments
    ADD CONSTRAINT chk_payment_sender_receiver
        CHECK (sender_id <> receiver_id);
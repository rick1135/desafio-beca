ALTER TABLE transactions
    ADD COLUMN correction_reason VARCHAR(255);

CREATE UNIQUE INDEX uk_transactions_original_transaction_id
    ON transactions (original_transaction_id);

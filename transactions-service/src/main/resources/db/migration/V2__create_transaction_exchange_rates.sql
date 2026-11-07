CREATE TABLE transaction_exchange_rates (
    transaction_id BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    currency VARCHAR(3) NOT NULL,
    selling_rate NUMERIC(19, 8) NOT NULL,
    rate_date DATE NOT NULL,
    quoted_at TIMESTAMP NOT NULL,
    bulletin_type VARCHAR(40) NOT NULL,
    CONSTRAINT uq_transaction_exchange_currency UNIQUE (transaction_id, currency)
);

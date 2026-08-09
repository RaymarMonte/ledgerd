-- one is to one relationship with accounts. Separated since balances is
-- truncated when projection is rebuild.
CREATE TABLE balances (
    account_id VARCHAR(20) PRIMARY KEY,
    balance BIGINT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT FK_account_id FOREIGN KEY (account_id) REFERENCES accounts(id)
);
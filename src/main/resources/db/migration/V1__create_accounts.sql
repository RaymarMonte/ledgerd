CREATE TABLE accounts (
    id VARCHAR(20) PRIMARY KEY,
    account_type VARCHAR(20) NOT NULL CHECK (account_type IN ('asset', 'liability', 'revenue', 'expense', 'equity')),
    currency CHAR(3) NOT NULL, -- USD, PHP, EUR
    balance_floor BIGINT,
    status VARCHAR(20) NOT NULL CHECK (status IN ('open', 'closed')),
    created_at TIMESTAMPTZ NOT NULL
);
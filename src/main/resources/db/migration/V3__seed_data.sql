INSERT INTO accounts (id, account_type, currency, balance_floor, status, created_at)
VALUES 
    ('ACCT-0001', 'asset', 'USD', 0, 'open', now()),
    ('ACCT-0002', 'asset', 'USD', 0, 'open', now()),
    ('EXTERNAL', 'liability', 'USD', NULL, 'open', now());

INSERT INTO balances (account_id, balance, updated_at)
VALUES
    ('ACCT-0001', 0, now()),
    ('ACCT-0002', 0, now()),
    ('EXTERNAL', 0, now());
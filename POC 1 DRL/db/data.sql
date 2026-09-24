-- Sample data
--   psql -U postgres -d ruleengine -f db/data.sql

-- Limits for USER-001 (not blocked)
INSERT INTO user_limit (user_id, transaction_mode, dr_cr_type, limit_amount) VALUES
    ('USER-001', 'TRANSFER', 'DR', 100),
    ('USER-001', 'TRANSFER', 'CR', 2323),
    ('USER-001', 'CREDIT',   'DR', 12323),
    ('USER-001', 'CREDIT',   'CR', 123213);

-- Limits for USER-002 (blocked). Present so the request reaches the rules;
-- the blocked-user rule fires first and stops evaluation.
INSERT INTO user_limit (user_id, transaction_mode, dr_cr_type, limit_amount) VALUES
    ('USER-002', 'TRANSFER', 'DR', 100),
    ('USER-002', 'TRANSFER', 'CR', 2323),
    ('USER-002', 'CREDIT',   'DR', 12323),
    ('USER-002', 'CREDIT',   'CR', 123213);

-- Products
INSERT INTO product (source_account, product_type, frequency, dr_res) VALUES
    ('100001', 'DEPOSIT', 'MONTHLY', 1),   -- debit-restricted
    ('200001', 'SAVINGS', 'MONTHLY', 0);   -- not restricted

-- Global reference data: blocked users (loaded once at startup)
INSERT INTO user_block (user_id, blocked) VALUES
    ('USER-001', FALSE),
    ('USER-002', TRUE);

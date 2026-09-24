-- Sample data
--   psql -U postgres -d ruleengine -f db/data.sql

-- USER-001: four (mode x DR/CR) limits
INSERT INTO user_limit (user_id, transaction_mode, dr_cr_type, limit_amount) VALUES
    ('USER-001', 'TRANSFER', 'DR', 100),
    ('USER-001', 'TRANSFER', 'CR', 2323),
    ('USER-001', 'CREDIT',   'DR', 12323),
    ('USER-001', 'CREDIT',   'CR', 123213);

-- Products
INSERT INTO product (source_account, product_type, frequency, dr_res) VALUES
    ('100001', 'DEPOSIT', 'MONTHLY', 1),   -- debit-restricted
    ('200001', 'SAVINGS', 'MONTHLY', 0);   -- not restricted

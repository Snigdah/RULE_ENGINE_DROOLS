-- =============================================================================
--  Business dummy data (PostgreSQL) - copy/paste into psql / pgAdmin.
--  Only the re_* tables this POC owns. Flows, groups, FIELDS and rules are all
--  seeded via the Admin API now (seed.ps1 / seed.sh) - not here.
--    psql -U postgres -d ruleengine -f db/business-data.sql
-- =============================================================================

-- users (kyc): USER-003 fails KYC; USER-002 is blocked (below)
INSERT INTO re_user (id, user_id, kyc_verified, full_name) VALUES (nextval('re_user_seq'), 'USER-001', TRUE,  'Ayesha Rahman');
INSERT INTO re_user (id, user_id, kyc_verified, full_name) VALUES (nextval('re_user_seq'), 'USER-002', TRUE,  'Blocked Bob');
INSERT INTO re_user (id, user_id, kyc_verified, full_name) VALUES (nextval('re_user_seq'), 'USER-003', FALSE, 'Unverified Uma');
INSERT INTO re_user (id, user_id, kyc_verified, full_name) VALUES (nextval('re_user_seq'), 'USER-004', TRUE,  'Low-score Lee');

-- block list (USER-002 blocked)
INSERT INTO re_user_block (id, user_id, blocked) VALUES (nextval('re_user_block_seq'), 'USER-002', TRUE);

-- transfer: per-user limits
INSERT INTO re_user_limit (id, user_id, transaction_mode, dr_cr_type, limit_amount) VALUES (nextval('re_user_limit_seq'), 'USER-001', 'ONLINE', 'D', 10000);
INSERT INTO re_user_limit (id, user_id, transaction_mode, dr_cr_type, limit_amount) VALUES (nextval('re_user_limit_seq'), 'USER-002', 'ONLINE', 'D', 10000);
INSERT INTO re_user_limit (id, user_id, transaction_mode, dr_cr_type, limit_amount) VALUES (nextval('re_user_limit_seq'), 'USER-003', 'ONLINE', 'D', 10000);
INSERT INTO re_user_limit (id, user_id, transaction_mode, dr_cr_type, limit_amount) VALUES (nextval('re_user_limit_seq'), 'USER-004', 'ONLINE', 'D', 10000);

-- transfer: accounts (100001 debit-restricted, 200001 not)
INSERT INTO re_product (id, source_account, dr_res) VALUES (nextval('re_product_seq'), '100001', 1);
INSERT INTO re_product (id, source_account, dr_res) VALUES (nextval('re_product_seq'), '200001', 0);

-- loan: credit scores (USER-004 fails, < 600)
INSERT INTO re_customer (id, user_id, credit_score, full_name) VALUES (nextval('re_customer_seq'), 'USER-001', 700, 'Ayesha Rahman');
INSERT INTO re_customer (id, user_id, credit_score, full_name) VALUES (nextval('re_customer_seq'), 'USER-002', 700, 'Blocked Bob');
INSERT INTO re_customer (id, user_id, credit_score, full_name) VALUES (nextval('re_customer_seq'), 'USER-004', 550, 'Low-score Lee');

-- closure: accounts (ACC-901 outstanding balance; ACC-902 dormant; ACC-903 frozen)
INSERT INTO re_account (id, account_no, balance, status) VALUES (nextval('re_account_seq'), 'ACC-900', 0,       'ACTIVE');
INSERT INTO re_account (id, account_no, balance, status) VALUES (nextval('re_account_seq'), 'ACC-901', 1500.00, 'ACTIVE');
INSERT INTO re_account (id, account_no, balance, status) VALUES (nextval('re_account_seq'), 'ACC-902', 0,       'DORMANT');
INSERT INTO re_account (id, account_no, balance, status) VALUES (nextval('re_account_seq'), 'ACC-903', 0,       'FROZEN');

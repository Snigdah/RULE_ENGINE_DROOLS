-- =============================================================================
--  Business dummy data (PostgreSQL) - copy/paste into psql.
--  Tables owned by this POC. Rules/flows/mappings are loaded via the admin API.
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

-- closure: accounts (ACC-901 has an outstanding balance)
INSERT INTO re_account (id, account_no, balance, status) VALUES (nextval('re_account_seq'), 'ACC-900', 0,       'ACTIVE');
INSERT INTO re_account (id, account_no, balance, status) VALUES (nextval('re_account_seq'), 'ACC-901', 1500.00, 'ACTIVE');

-- field catalog (library table, but no POST endpoint - seed it here)
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), 'user.kycVerified', 'COMMON', 'KYC Verified', 'BOOLEAN', 'user.kycVerified', '==', 'Yes (true), No (false)');
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), 'transaction.amount', 'TRANSFER_TRANSACTION', 'Transaction Amount', 'NUMBER', 'transaction.amount', '>,<,>=,<=,==', NULL);
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), 'transaction.currency', 'TRANSFER_TRANSACTION', 'Currency', 'ENUM', 'transaction.currency', '==,!=', 'BDT,USD,EUR,GBP');
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), 'product.drRes', 'TRANSFER_TRANSACTION', 'Debit Restricted', 'BOOLEAN', 'product.drRes', '==', 'Yes (1), No (0)');
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), 'userLimit.limit', 'TRANSFER_TRANSACTION', 'User Limit', 'NUMBER', 'userLimit.limit', '>,<,>=,<=', NULL);
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), 'loan.amount', 'LOAN_APPLICATION', 'Loan Amount', 'NUMBER', 'loan.amount', '>,<,>=,<=', NULL);
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), 'customer.creditScore', 'LOAN_APPLICATION', 'Credit Score', 'NUMBER', 'customer.creditScore', '>,<,>=,<=', NULL);
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), 'account.balance', 'ACCOUNT_CLOSURE', 'Account Balance', 'NUMBER', 'account.balance', '>,<,>=,<=,==,!=', NULL);

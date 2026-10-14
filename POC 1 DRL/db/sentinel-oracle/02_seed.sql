-- =============================================================================
--  Rule Engine approval POC - seed rows (Oracle, SENTINEL schema)
-- =============================================================================
--  Run AFTER 01_create.sql. Inserts:
--    1. POC business rows into the RE_* tables (same data as ../business-data.sql)
--    2. ONE row in META_FUNCTION_SELECTOR_MAP for function 101022 (the rule engine's
--       approval function, mapped in Centrino to workflow 40 "ruleEngine")
--
--  Flows, groups, fields and rules are NOT seeded here - run ../../seed.ps1 against
--  the rule engine with approval switched OFF (see README.md, step 3).
--  Everything inserted here is removed by 99_drop.sql. Plain SQL - runs in a PL/SQL
--  Developer SQL window or SQL*Plus.
-- =============================================================================

-- users (kyc): USER-003 fails KYC; USER-002 is blocked (below)
INSERT INTO RE_USER (ID, USER_ID, KYC_VERIFIED, FULL_NAME) VALUES (RE_USER_SEQ.NEXTVAL, 'USER-001', 1, 'Ayesha Rahman');
INSERT INTO RE_USER (ID, USER_ID, KYC_VERIFIED, FULL_NAME) VALUES (RE_USER_SEQ.NEXTVAL, 'USER-002', 1, 'Blocked Bob');
INSERT INTO RE_USER (ID, USER_ID, KYC_VERIFIED, FULL_NAME) VALUES (RE_USER_SEQ.NEXTVAL, 'USER-003', 0, 'Unverified Uma');
INSERT INTO RE_USER (ID, USER_ID, KYC_VERIFIED, FULL_NAME) VALUES (RE_USER_SEQ.NEXTVAL, 'USER-004', 1, 'Low-score Lee');

-- block list (USER-002 blocked)
INSERT INTO RE_USER_BLOCK (ID, USER_ID, BLOCKED) VALUES (RE_USER_BLOCK_SEQ.NEXTVAL, 'USER-002', 1);

-- transfer: per-user limits
INSERT INTO RE_USER_LIMIT (ID, USER_ID, TRANSACTION_MODE, DR_CR_TYPE, LIMIT_AMOUNT) VALUES (RE_USER_LIMIT_SEQ.NEXTVAL, 'USER-001', 'ONLINE', 'D', 10000);
INSERT INTO RE_USER_LIMIT (ID, USER_ID, TRANSACTION_MODE, DR_CR_TYPE, LIMIT_AMOUNT) VALUES (RE_USER_LIMIT_SEQ.NEXTVAL, 'USER-002', 'ONLINE', 'D', 10000);
INSERT INTO RE_USER_LIMIT (ID, USER_ID, TRANSACTION_MODE, DR_CR_TYPE, LIMIT_AMOUNT) VALUES (RE_USER_LIMIT_SEQ.NEXTVAL, 'USER-003', 'ONLINE', 'D', 10000);
INSERT INTO RE_USER_LIMIT (ID, USER_ID, TRANSACTION_MODE, DR_CR_TYPE, LIMIT_AMOUNT) VALUES (RE_USER_LIMIT_SEQ.NEXTVAL, 'USER-004', 'ONLINE', 'D', 10000);

-- transfer: accounts (100001 debit-restricted, 200001 not)
INSERT INTO RE_PRODUCT (ID, SOURCE_ACCOUNT, DR_RES) VALUES (RE_PRODUCT_SEQ.NEXTVAL, '100001', 1);
INSERT INTO RE_PRODUCT (ID, SOURCE_ACCOUNT, DR_RES) VALUES (RE_PRODUCT_SEQ.NEXTVAL, '200001', 0);

-- loan: credit scores (USER-004 fails, < 600)
INSERT INTO RE_CUSTOMER (ID, USER_ID, CREDIT_SCORE, FULL_NAME) VALUES (RE_CUSTOMER_SEQ.NEXTVAL, 'USER-001', 700, 'Ayesha Rahman');
INSERT INTO RE_CUSTOMER (ID, USER_ID, CREDIT_SCORE, FULL_NAME) VALUES (RE_CUSTOMER_SEQ.NEXTVAL, 'USER-002', 700, 'Blocked Bob');
INSERT INTO RE_CUSTOMER (ID, USER_ID, CREDIT_SCORE, FULL_NAME) VALUES (RE_CUSTOMER_SEQ.NEXTVAL, 'USER-004', 550, 'Low-score Lee');

-- closure: accounts (ACC-901 outstanding balance; ACC-902 dormant; ACC-903 frozen)
INSERT INTO RE_ACCOUNT (ID, ACCOUNT_NO, BALANCE, STATUS) VALUES (RE_ACCOUNT_SEQ.NEXTVAL, 'ACC-900', 0,       'ACTIVE');
INSERT INTO RE_ACCOUNT (ID, ACCOUNT_NO, BALANCE, STATUS) VALUES (RE_ACCOUNT_SEQ.NEXTVAL, 'ACC-901', 1500.00, 'ACTIVE');
INSERT INTO RE_ACCOUNT (ID, ACCOUNT_NO, BALANCE, STATUS) VALUES (RE_ACCOUNT_SEQ.NEXTVAL, 'ACC-902', 0,       'DORMANT');
INSERT INTO RE_ACCOUNT (ID, ACCOUNT_NO, BALANCE, STATUS) VALUES (RE_ACCOUNT_SEQ.NEXTVAL, 'ACC-903', 0,       'FROZEN');

-- approval behaviour for function 101022:
--   EXECUTE_STATUS = 1            -> final approval applies the change to RULE_* tables
--   IS_ASYNC_POST_APP_PROCESS = 0 -> no inbox messages (post-approval is also switched
--                                    off in the rule engine's config)
INSERT INTO META_FUNCTION_SELECTOR_MAP (FUNCTION_ID, FUNCTION_NAME, SELECTOR, EXECUTE_STATUS, IS_ASYNC_POST_APP_PROCESS)
VALUES (101022, 'Rule Engine Changes', 'rule-engine', 1, 0);

COMMIT;

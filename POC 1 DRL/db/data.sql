-- Sample business data. Rules and flow routing are uploaded via /admin, not seeded here.
--   psql -U postgres -d ruleengine -f db/data.sql

INSERT INTO re_user_limit (id, user_id, transaction_mode, dr_cr_type, limit_amount)
    VALUES (nextval('re_user_limit_seq'), 'USER-001', 'ONLINE', 'D', 10000);
INSERT INTO re_user_limit (id, user_id, transaction_mode, dr_cr_type, limit_amount)
    VALUES (nextval('re_user_limit_seq'), 'USER-002', 'ONLINE', 'D', 10000);

INSERT INTO re_product (id, source_account, dr_res)
    VALUES (nextval('re_product_seq'), '100001', 1);
INSERT INTO re_product (id, source_account, dr_res)
    VALUES (nextval('re_product_seq'), '200001', 0);

INSERT INTO re_user_block (id, user_id, blocked)
    VALUES (nextval('re_user_block_seq'), 'USER-002', TRUE);

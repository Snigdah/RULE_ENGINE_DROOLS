-- =============================================================================
--  TRANSFER flow — extra builder-palette fields for the VELOCITY & COMPLIANCE
--  rules. Catalog has no POST endpoint, so seed it here. Run against the live DB
--  BEFORE posting the two rules (the generator reads data_type to quote ENUMs).
--    psql -U postgres -d ruleengine -f db/transfer-extra-catalog.sql
-- =============================================================================
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES
  (nextval('rule_field_catalog_seq'), 'transaction.channel', 'TRANSFER_TRANSACTION', 'Channel', 'ENUM', 'transaction.channel', '==,!=', 'ATM,POS,ONLINE,BRANCH,AGENT');
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES
  (nextval('rule_field_catalog_seq'), 'transaction.country', 'TRANSFER_TRANSACTION', 'Destination Country', 'ENUM', 'transaction.country', '==,!=', 'BD,US,UK,AE,SG,OTHER');
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES
  (nextval('rule_field_catalog_seq'), 'transaction.dailyTxnCount', 'TRANSFER_TRANSACTION', 'Daily Txn Count', 'NUMBER', 'transaction.dailyTxnCount', '>,<,>=,<=,==', NULL);

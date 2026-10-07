-- =============================================================================
--  Seed data for the 3-flow POC (PostgreSQL). Run after schema.sql (or after the
--  app has created the tables with ddl-auto=update).
--    psql -U postgres -d ruleengine -f db/data.sql
-- =============================================================================

-- ---- request class -> flow --------------------------------------------------
INSERT INTO request_flow_map (id, fact_type, flow_name) VALUES
    (nextval('request_flow_map_seq'), 'com.example.droolspoc.model.ValidationContext', 'TRANSFER_TRANSACTION');
INSERT INTO request_flow_map (id, fact_type, flow_name) VALUES
    (nextval('request_flow_map_seq'), 'com.example.droolspoc.model.LoanValidationContext', 'LOAN_APPLICATION');
INSERT INTO request_flow_map (id, fact_type, flow_name) VALUES
    (nextval('request_flow_map_seq'), 'com.example.droolspoc.model.ClosureValidationContext', 'ACCOUNT_CLOSURE');

-- ---- flow -> ordered agenda groups (COMMON first, then the flow's own) ------
INSERT INTO flow_group (id, flow_name, agenda_group, order_no) VALUES (nextval('flow_group_seq'), 'TRANSFER_TRANSACTION', 'COMMON',   1);
INSERT INTO flow_group (id, flow_name, agenda_group, order_no) VALUES (nextval('flow_group_seq'), 'TRANSFER_TRANSACTION', 'TRANSFER', 2);
INSERT INTO flow_group (id, flow_name, agenda_group, order_no) VALUES (nextval('flow_group_seq'), 'LOAN_APPLICATION',     'COMMON',   1);
INSERT INTO flow_group (id, flow_name, agenda_group, order_no) VALUES (nextval('flow_group_seq'), 'LOAN_APPLICATION',     'LOAN',     2);
INSERT INTO flow_group (id, flow_name, agenda_group, order_no) VALUES (nextval('flow_group_seq'), 'ACCOUNT_CLOSURE',      'COMMON',   1);
INSERT INTO flow_group (id, flow_name, agenda_group, order_no) VALUES (nextval('flow_group_seq'), 'ACCOUNT_CLOSURE',      'CLOSURE',  2);

-- ---- field catalog (builder palette), scoped by flow ------------------------
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES
    (nextval('rule_field_catalog_seq'), 'user.kycVerified', 'COMMON', 'KYC Verified', 'BOOLEAN', 'user.kycVerified', '==', 'Yes (true), No (false)');
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES
    (nextval('rule_field_catalog_seq'), 'transaction.amount', 'TRANSFER_TRANSACTION', 'Transaction Amount', 'NUMBER', 'transaction.amount', '>,<,>=,<=,==', NULL);
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES
    (nextval('rule_field_catalog_seq'), 'transaction.currency', 'TRANSFER_TRANSACTION', 'Currency', 'ENUM', 'transaction.currency', '==,!=', 'BDT,USD,EUR,GBP');
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES
    (nextval('rule_field_catalog_seq'), 'product.drRes', 'TRANSFER_TRANSACTION', 'Debit Restricted', 'BOOLEAN', 'product.drRes', '==', 'Yes (1), No (0)');
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES
    (nextval('rule_field_catalog_seq'), 'userLimit.limit', 'TRANSFER_TRANSACTION', 'User Limit', 'NUMBER', 'userLimit.limit', '>,<,>=,<=', NULL);
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES
    (nextval('rule_field_catalog_seq'), 'loan.amount', 'LOAN_APPLICATION', 'Loan Amount', 'NUMBER', 'loan.amount', '>,<,>=,<=', NULL);
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES
    (nextval('rule_field_catalog_seq'), 'customer.creditScore', 'LOAN_APPLICATION', 'Credit Score', 'NUMBER', 'customer.creditScore', '>,<,>=,<=', NULL);
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values) VALUES
    (nextval('rule_field_catalog_seq'), 'account.balance', 'ACCOUNT_CLOSURE', 'Account Balance', 'NUMBER', 'account.balance', '>,<,>=,<=,==,!=', NULL);

-- ============================ RULES =========================================

-- COMMON / shared: blocked user (hand-written DRL, matches the RuleContext base) ----
INSERT INTO rule_definition (id, rule_name, agenda_group, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at)
VALUES (nextval('rule_definition_seq'), 'Block transaction for a blocked user', 'COMMON', 'DRL', NULL,
'package com.example.droolspoc.rules;

import com.example.droolspoc.model.RuleContext;
import com.example.droolspoc.context.data.BlockedUsers;

global leads.ruleengine.core.context.GlobalContext globalContext;

rule "Block transaction for a blocked user"
    agenda-group "COMMON"
    salience 100
    when
        $ctx : RuleContext()
        eval(globalContext.get(BlockedUsers.class).isBlocked($ctx.getUserId()))
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Blocked: user is blocked");
        drools.halt();
end',
1, 'ACTIVE', TRUE, 'system', now());

-- COMMON / shared: KYC not verified (hand-written DRL, halts on block) ----
INSERT INTO rule_definition (id, rule_name, agenda_group, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at)
VALUES (nextval('rule_definition_seq'), 'Block if KYC not verified', 'COMMON', 'DRL', NULL,
'package com.example.droolspoc.rules;

import com.example.droolspoc.model.RuleContext;

rule "Block if KYC not verified"
    agenda-group "COMMON"
    salience 90
    when
        $ctx : RuleContext(user != null, user.kycVerified == false)
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Blocked: KYC not verified");
        drools.halt();
end',
1, 'ACTIVE', TRUE, 'system', now());

-- TRANSFER: over-limit debit-restricted BDT (BUILDER) ----
INSERT INTO rule_definition (id, rule_name, agenda_group, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at)
VALUES (nextval('rule_definition_seq'), 'Block over-limit debit-restricted BDT transaction', 'TRANSFER', 'BUILDER',
'{"ruleName":"Block over-limit debit-restricted BDT transaction","flow":"TRANSFER_TRANSACTION","agendaGroup":"TRANSFER","salience":10,"match":"all","conditions":[{"field":"transaction.currency","operator":"==","value":"BDT"},{"field":"product.drRes","operator":"==","value":1},{"field":"transaction.amount","operator":">","valueField":"userLimit.limit"}],"then":{"decision":"block","message":"Blocked: amount exceeds limit on a debit-restricted BDT account"}}',
'package com.example.droolspoc.model;

rule "Block over-limit debit-restricted BDT transaction"
    agenda-group "TRANSFER"
    salience 10
    when
        $ctx : com.example.droolspoc.model.ValidationContext(
            transaction.currency == "BDT",
            product.drRes == 1,
            transaction.amount > userLimit.limit
        )
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Blocked: amount exceeds limit on a debit-restricted BDT account");
end',
1, 'ACTIVE', TRUE, 'farhat', now());

-- LOAN: below credit-score threshold (BUILDER) ----
INSERT INTO rule_definition (id, rule_name, agenda_group, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at)
VALUES (nextval('rule_definition_seq'), 'Block loan below credit-score threshold', 'LOAN', 'BUILDER',
'{"ruleName":"Block loan below credit-score threshold","flow":"LOAN_APPLICATION","agendaGroup":"LOAN","salience":10,"match":"all","conditions":[{"field":"customer.creditScore","operator":"<","value":600}],"then":{"decision":"block","message":"Blocked: credit score below 600"}}',
'package com.example.droolspoc.model;

rule "Block loan below credit-score threshold"
    agenda-group "LOAN"
    salience 10
    when
        $ctx : com.example.droolspoc.model.LoanValidationContext(
            customer.creditScore < 600
        )
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Blocked: credit score below 600");
end',
1, 'ACTIVE', TRUE, 'farhat', now());

-- CLOSURE: outstanding balance (BUILDER) ----
INSERT INTO rule_definition (id, rule_name, agenda_group, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at)
VALUES (nextval('rule_definition_seq'), 'Block closure with outstanding balance', 'CLOSURE', 'BUILDER',
'{"ruleName":"Block closure with outstanding balance","flow":"ACCOUNT_CLOSURE","agendaGroup":"CLOSURE","salience":10,"match":"all","conditions":[{"field":"account.balance","operator":">","value":0}],"then":{"decision":"block","message":"Blocked: account has an outstanding balance"}}',
'package com.example.droolspoc.model;

rule "Block closure with outstanding balance"
    agenda-group "CLOSURE"
    salience 10
    when
        $ctx : com.example.droolspoc.model.ClosureValidationContext(
            account.balance > 0
        )
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Blocked: account has an outstanding balance");
end',
1, 'ACTIVE', TRUE, 'farhat', now());

-- ============================ BUSINESS DEMO DATA ============================
-- users (kyc): USER-003 fails KYC; USER-002 is blocked (below)
INSERT INTO re_user (id, user_id, kyc_verified, full_name) VALUES (nextval('re_user_seq'), 'USER-001', TRUE,  'Ayesha Rahman');
INSERT INTO re_user (id, user_id, kyc_verified, full_name) VALUES (nextval('re_user_seq'), 'USER-002', TRUE,  'Blocked Bob');
INSERT INTO re_user (id, user_id, kyc_verified, full_name) VALUES (nextval('re_user_seq'), 'USER-003', FALSE, 'Unverified Uma');
INSERT INTO re_user (id, user_id, kyc_verified, full_name) VALUES (nextval('re_user_seq'), 'USER-004', TRUE,  'Low-score Lee');

-- block list
INSERT INTO re_user_block (id, user_id, blocked) VALUES (nextval('re_user_block_seq'), 'USER-002', TRUE);

-- transfer: limits + accounts
INSERT INTO re_user_limit (id, user_id, transaction_mode, dr_cr_type, limit_amount) VALUES (nextval('re_user_limit_seq'), 'USER-001', 'ONLINE', 'D', 10000);
INSERT INTO re_user_limit (id, user_id, transaction_mode, dr_cr_type, limit_amount) VALUES (nextval('re_user_limit_seq'), 'USER-002', 'ONLINE', 'D', 10000);
INSERT INTO re_user_limit (id, user_id, transaction_mode, dr_cr_type, limit_amount) VALUES (nextval('re_user_limit_seq'), 'USER-003', 'ONLINE', 'D', 10000);
INSERT INTO re_user_limit (id, user_id, transaction_mode, dr_cr_type, limit_amount) VALUES (nextval('re_user_limit_seq'), 'USER-004', 'ONLINE', 'D', 10000);
INSERT INTO re_product (id, source_account, dr_res) VALUES (nextval('re_product_seq'), '100001', 1);   -- debit-restricted
INSERT INTO re_product (id, source_account, dr_res) VALUES (nextval('re_product_seq'), '200001', 0);   -- not restricted

-- loan: credit scores (USER-004 fails, < 600)
INSERT INTO re_customer (id, user_id, credit_score, full_name) VALUES (nextval('re_customer_seq'), 'USER-001', 700, 'Ayesha Rahman');
INSERT INTO re_customer (id, user_id, credit_score, full_name) VALUES (nextval('re_customer_seq'), 'USER-002', 700, 'Blocked Bob');
INSERT INTO re_customer (id, user_id, credit_score, full_name) VALUES (nextval('re_customer_seq'), 'USER-004', 550, 'Low-score Lee');

-- closure: accounts (ACC-901 has an outstanding balance)
INSERT INTO re_account (id, account_no, balance, status) VALUES (nextval('re_account_seq'), 'ACC-900', 0,       'ACTIVE');
INSERT INTO re_account (id, account_no, balance, status) VALUES (nextval('re_account_seq'), 'ACC-901', 1500.00, 'ACTIVE');

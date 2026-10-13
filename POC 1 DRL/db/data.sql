-- =============================================================================
--  Seed data for the 3-flow POC (PostgreSQL) - 4-table design (rule_flow / rule_group /
--  rule_definition / rule_field_catalog). Run after the app has created the tables with
--  ddl-auto=update (or after schema.sql).
--    psql -U postgres -d ruleengine -f db/data.sql
--
--  Group ids are resolved by sub-select, so this script does not depend on sequence values.
--  Builder rules carry both source_json (editable) and the generated drl_text; the compiled
--  rule name is qualified FLOW.GROUP::name so duplicate display names stay unique in Drools.
-- =============================================================================

-- ---- rule_flow : flow master + router (request_class is the routing key) -----
INSERT INTO rule_flow (id, flow_code, request_class, fact_type, label, active) VALUES (nextval('rule_flow_seq'), 'TRANSFER_TRANSACTION', 'com.example.droolspoc.dto.TransactionRequest', 'com.example.droolspoc.model.ValidationContext', 'Transfer Transaction', TRUE);
INSERT INTO rule_flow (id, flow_code, request_class, fact_type, label, active) VALUES (nextval('rule_flow_seq'), 'LOAN_APPLICATION', 'com.example.droolspoc.dto.LoanRequest', 'com.example.droolspoc.model.LoanValidationContext', 'Loan Application', TRUE);
INSERT INTO rule_flow (id, flow_code, request_class, fact_type, label, active) VALUES (nextval('rule_flow_seq'), 'ACCOUNT_CLOSURE', 'com.example.droolspoc.dto.AccountCloseRequest', 'com.example.droolspoc.model.ClosureValidationContext', 'Account Closure', TRUE);

-- ---- rule_group : COMMON is shared (flow_id NULL); each flow's own groups follow --
-- active=TRUE: a group can be toggled off later (inactive groups are skipped at runtime).
INSERT INTO rule_group (id, flow_id, group_code, order_no, active) VALUES (nextval('rule_group_seq'), NULL, 'COMMON', 1, TRUE);
INSERT INTO rule_group (id, flow_id, group_code, order_no, active) VALUES (nextval('rule_group_seq'), (SELECT id FROM rule_flow WHERE flow_code='TRANSFER_TRANSACTION'), 'TRANSFER', 2, TRUE);
INSERT INTO rule_group (id, flow_id, group_code, order_no, active) VALUES (nextval('rule_group_seq'), (SELECT id FROM rule_flow WHERE flow_code='TRANSFER_TRANSACTION'), 'VELOCITY', 3, TRUE);
INSERT INTO rule_group (id, flow_id, group_code, order_no, active) VALUES (nextval('rule_group_seq'), (SELECT id FROM rule_flow WHERE flow_code='TRANSFER_TRANSACTION'), 'COMPLIANCE', 4, TRUE);
INSERT INTO rule_group (id, flow_id, group_code, order_no, active) VALUES (nextval('rule_group_seq'), (SELECT id FROM rule_flow WHERE flow_code='LOAN_APPLICATION'), 'LOAN', 2, TRUE);
INSERT INTO rule_group (id, flow_id, group_code, order_no, active) VALUES (nextval('rule_group_seq'), (SELECT id FROM rule_flow WHERE flow_code='ACCOUNT_CLOSURE'), 'CLOSURE', 2, TRUE);
INSERT INTO rule_group (id, flow_id, group_code, order_no, active) VALUES (nextval('rule_group_seq'), (SELECT id FROM rule_flow WHERE flow_code='ACCOUNT_CLOSURE'), 'COMPLIANCE', 3, TRUE);

-- ---- rule_field_catalog : builder palette (flow_id NULL = shared) ------------
INSERT INTO rule_field_catalog (id, flow_id, field_key, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), NULL, 'user.kycVerified', 'KYC Verified', 'BOOLEAN', 'user.kycVerified', '==', 'Yes (true), No (false)');
INSERT INTO rule_field_catalog (id, flow_id, field_key, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), (SELECT id FROM rule_flow WHERE flow_code='TRANSFER_TRANSACTION'), 'transaction.amount', 'Transaction Amount', 'NUMBER', 'transaction.amount', '> < >= <= ==', NULL);
INSERT INTO rule_field_catalog (id, flow_id, field_key, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), (SELECT id FROM rule_flow WHERE flow_code='TRANSFER_TRANSACTION'), 'transaction.currency', 'Currency', 'ENUM', 'transaction.currency', '== !=', 'BDT, USD, EUR, GBP');
INSERT INTO rule_field_catalog (id, flow_id, field_key, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), (SELECT id FROM rule_flow WHERE flow_code='TRANSFER_TRANSACTION'), 'product.drRes', 'Debit Restricted', 'BOOLEAN', 'product.drRes', '==', 'Yes (1), No (0)');
INSERT INTO rule_field_catalog (id, flow_id, field_key, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), (SELECT id FROM rule_flow WHERE flow_code='TRANSFER_TRANSACTION'), 'userLimit.limit', 'User Limit', 'NUMBER', 'userLimit.limit', '> < >= <=', NULL);
INSERT INTO rule_field_catalog (id, flow_id, field_key, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), (SELECT id FROM rule_flow WHERE flow_code='TRANSFER_TRANSACTION'), 'transaction.channel', 'Channel', 'ENUM', 'transaction.channel', '== !=', 'ATM, POS, ONLINE, BRANCH, AGENT');
INSERT INTO rule_field_catalog (id, flow_id, field_key, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), (SELECT id FROM rule_flow WHERE flow_code='TRANSFER_TRANSACTION'), 'transaction.country', 'Destination Country', 'ENUM', 'transaction.country', '== !=', 'BD, US, UK, AE, SG, OTHER');
INSERT INTO rule_field_catalog (id, flow_id, field_key, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), (SELECT id FROM rule_flow WHERE flow_code='TRANSFER_TRANSACTION'), 'transaction.dailyTxnCount', 'Daily Txn Count', 'NUMBER', 'transaction.dailyTxnCount', '> < >= <= ==', NULL);
INSERT INTO rule_field_catalog (id, flow_id, field_key, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), (SELECT id FROM rule_flow WHERE flow_code='LOAN_APPLICATION'), 'loan.amount', 'Loan Amount', 'NUMBER', 'loan.amount', '> < >= <=', NULL);
INSERT INTO rule_field_catalog (id, flow_id, field_key, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), (SELECT id FROM rule_flow WHERE flow_code='LOAN_APPLICATION'), 'customer.creditScore', 'Credit Score', 'NUMBER', 'customer.creditScore', '> < >= <=', NULL);
INSERT INTO rule_field_catalog (id, flow_id, field_key, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), (SELECT id FROM rule_flow WHERE flow_code='ACCOUNT_CLOSURE'), 'account.balance', 'Account Balance', 'NUMBER', 'account.balance', '> < >= <= == !=', NULL);
INSERT INTO rule_field_catalog (id, flow_id, field_key, label, data_type, drl_path, allowed_ops, allowed_values) VALUES (nextval('rule_field_catalog_seq'), (SELECT id FROM rule_flow WHERE flow_code='ACCOUNT_CLOSURE'), 'account.status', 'Account Status', 'ENUM', 'account.status', '== !=', 'ACTIVE, DORMANT, FROZEN, CLOSED');

-- ============================ RULES =========================================
-- #1  Block transaction for a blocked user  (group: COMMON, DRL)
INSERT INTO rule_definition (id, group_id, rule_name, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at) VALUES
(nextval('rule_definition_seq'), (SELECT id FROM rule_group WHERE group_code='COMMON' AND flow_id IS NULL), 'Block transaction for a blocked user', 'DRL', NULL, 'package com.example.droolspoc.rules;

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
end', 1, 'ACTIVE', TRUE, 'farhat', now());

-- #2  Block if KYC not verified  (group: COMMON, DRL)
INSERT INTO rule_definition (id, group_id, rule_name, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at) VALUES
(nextval('rule_definition_seq'), (SELECT id FROM rule_group WHERE group_code='COMMON' AND flow_id IS NULL), 'Block if KYC not verified', 'DRL', NULL, 'package com.example.droolspoc.rules;

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
end', 1, 'ACTIVE', TRUE, 'farhat', now());

-- #3  Block over-limit debit-restricted BDT transaction  (group: TRANSFER / TRANSFER_TRANSACTION, BUILDER)
INSERT INTO rule_definition (id, group_id, rule_name, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at) VALUES
(nextval('rule_definition_seq'), (SELECT id FROM rule_group WHERE group_code='TRANSFER' AND flow_id=(SELECT id FROM rule_flow WHERE flow_code='TRANSFER_TRANSACTION')), 'Block over-limit debit-restricted BDT transaction', 'BUILDER', '{"ruleName": "Block over-limit debit-restricted BDT transaction", "flow": "TRANSFER_TRANSACTION", "agendaGroup": "TRANSFER", "salience": 10, "conditionTree": {"type": "GROUP", "operator": "AND", "children": [{"type": "CONDITION", "field": "transaction.currency", "operator": "==", "value": "BDT"}, {"type": "CONDITION", "field": "product.drRes", "operator": "==", "value": 1}, {"type": "CONDITION", "field": "transaction.amount", "operator": ">", "valueField": "userLimit.limit"}]}, "then": {"decision": "block", "message": "Blocked: amount exceeds limit on a debit-restricted BDT account"}}', 'package com.example.droolspoc.model;

rule "TRANSFER_TRANSACTION.TRANSFER::Block over-limit debit-restricted BDT transaction"
    agenda-group "TRANSFER"
    salience 10
    when
        $ctx : com.example.droolspoc.model.ValidationContext(
            (transaction.currency == "BDT" && product.drRes == 1 && transaction.amount > userLimit.limit)
        )
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Blocked: amount exceeds limit on a debit-restricted BDT account");
end', 1, 'ACTIVE', TRUE, 'farhat', now());

-- #4  High-value or cross-border transfer  (group: TRANSFER / TRANSFER_TRANSACTION, BUILDER)
INSERT INTO rule_definition (id, group_id, rule_name, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at) VALUES
(nextval('rule_definition_seq'), (SELECT id FROM rule_group WHERE group_code='TRANSFER' AND flow_id=(SELECT id FROM rule_flow WHERE flow_code='TRANSFER_TRANSACTION')), 'High-value or cross-border transfer', 'BUILDER', '{"ruleName": "High-value or cross-border transfer", "flow": "TRANSFER_TRANSACTION", "agendaGroup": "TRANSFER", "salience": 20, "conditionTree": {"type": "GROUP", "operator": "OR", "children": [{"type": "GROUP", "operator": "AND", "children": [{"type": "CONDITION", "field": "transaction.channel", "operator": "==", "value": "AGENT"}, {"type": "CONDITION", "field": "transaction.amount", "operator": ">", "value": 50000}]}, {"type": "GROUP", "operator": "AND", "children": [{"type": "CONDITION", "field": "transaction.country", "operator": "==", "value": "OTHER"}, {"type": "CONDITION", "field": "transaction.currency", "operator": "!=", "value": "BDT"}]}]}, "then": {"decision": "block", "message": "Blocked: high-value agent transfer, or cross-border non-BDT transfer"}}', 'package com.example.droolspoc.model;

rule "TRANSFER_TRANSACTION.TRANSFER::High-value or cross-border transfer"
    agenda-group "TRANSFER"
    salience 20
    when
        $ctx : com.example.droolspoc.model.ValidationContext(
            ((transaction.channel == "AGENT" && transaction.amount > 50000) || (transaction.country == "OTHER" && transaction.currency != "BDT"))
        )
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Blocked: high-value agent transfer, or cross-border non-BDT transfer");
end', 1, 'ACTIVE', TRUE, 'farhat', now());

-- #5  Block rapid high-value agent transfers  (group: VELOCITY / TRANSFER_TRANSACTION, BUILDER)
INSERT INTO rule_definition (id, group_id, rule_name, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at) VALUES
(nextval('rule_definition_seq'), (SELECT id FROM rule_group WHERE group_code='VELOCITY' AND flow_id=(SELECT id FROM rule_flow WHERE flow_code='TRANSFER_TRANSACTION')), 'Block rapid high-value agent transfers', 'BUILDER', '{"ruleName": "Block rapid high-value agent transfers", "flow": "TRANSFER_TRANSACTION", "agendaGroup": "VELOCITY", "salience": 20, "conditionTree": {"type": "GROUP", "operator": "AND", "children": [{"type": "CONDITION", "field": "transaction.channel", "operator": "==", "value": "AGENT"}, {"type": "CONDITION", "field": "transaction.amount", "operator": ">", "value": 50000}, {"type": "CONDITION", "field": "transaction.dailyTxnCount", "operator": ">=", "value": 5}]}, "then": {"decision": "block", "message": "Blocked: too many high-value agent transfers today"}}', 'package com.example.droolspoc.model;

rule "TRANSFER_TRANSACTION.VELOCITY::Block rapid high-value agent transfers"
    agenda-group "VELOCITY"
    salience 20
    when
        $ctx : com.example.droolspoc.model.ValidationContext(
            (transaction.channel == "AGENT" && transaction.amount > 50000 && transaction.dailyTxnCount >= 5)
        )
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Blocked: too many high-value agent transfers today");
end', 1, 'ACTIVE', TRUE, 'farhat', now());

-- #6  Layered channel & geo screen  (group: VELOCITY / TRANSFER_TRANSACTION, BUILDER)
INSERT INTO rule_definition (id, group_id, rule_name, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at) VALUES
(nextval('rule_definition_seq'), (SELECT id FROM rule_group WHERE group_code='VELOCITY' AND flow_id=(SELECT id FROM rule_flow WHERE flow_code='TRANSFER_TRANSACTION')), 'Layered channel & geo screen', 'BUILDER', '{"ruleName": "Layered channel & geo screen", "flow": "TRANSFER_TRANSACTION", "agendaGroup": "VELOCITY", "salience": 20, "conditionTree": {"type": "GROUP", "operator": "AND", "children": [{"type": "GROUP", "operator": "AND", "children": [{"type": "CONDITION", "field": "transaction.amount", "operator": ">", "value": 10000}, {"type": "GROUP", "operator": "OR", "children": [{"type": "CONDITION", "field": "transaction.channel", "operator": "==", "value": "ONLINE"}, {"type": "CONDITION", "field": "transaction.channel", "operator": "==", "value": "AGENT"}]}]}, {"type": "GROUP", "operator": "AND", "children": [{"type": "GROUP", "operator": "OR", "children": [{"type": "CONDITION", "field": "transaction.country", "operator": "==", "value": "US"}, {"type": "CONDITION", "field": "transaction.country", "operator": "==", "value": "OTHER"}]}, {"type": "CONDITION", "field": "transaction.currency", "operator": "!=", "value": "BDT"}]}]}, "then": {"decision": "block", "message": "Blocked: layered channel & geo screening"}}', 'package com.example.droolspoc.model;

rule "TRANSFER_TRANSACTION.VELOCITY::Layered channel & geo screen"
    agenda-group "VELOCITY"
    salience 20
    when
        $ctx : com.example.droolspoc.model.ValidationContext(
            ((transaction.amount > 10000 && (transaction.channel == "ONLINE" || transaction.channel == "AGENT")) && ((transaction.country == "US" || transaction.country == "OTHER") && transaction.currency != "BDT"))
        )
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Blocked: layered channel & geo screening");
end', 1, 'ACTIVE', TRUE, 'farhat', now());

-- #7  Block high-value cross-border non-BDT transfer  (group: COMPLIANCE / TRANSFER_TRANSACTION, BUILDER)
INSERT INTO rule_definition (id, group_id, rule_name, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at) VALUES
(nextval('rule_definition_seq'), (SELECT id FROM rule_group WHERE group_code='COMPLIANCE' AND flow_id=(SELECT id FROM rule_flow WHERE flow_code='TRANSFER_TRANSACTION')), 'Block high-value cross-border non-BDT transfer', 'BUILDER', '{"ruleName": "Block high-value cross-border non-BDT transfer", "flow": "TRANSFER_TRANSACTION", "agendaGroup": "COMPLIANCE", "salience": 20, "conditionTree": {"type": "GROUP", "operator": "AND", "children": [{"type": "CONDITION", "field": "transaction.currency", "operator": "!=", "value": "BDT"}, {"type": "CONDITION", "field": "transaction.amount", "operator": ">=", "value": 100000}, {"type": "CONDITION", "field": "transaction.country", "operator": "==", "value": "OTHER"}]}, "then": {"decision": "block", "message": "Blocked: cross-border high-value transfer needs manual review"}}', 'package com.example.droolspoc.model;

rule "TRANSFER_TRANSACTION.COMPLIANCE::Block high-value cross-border non-BDT transfer"
    agenda-group "COMPLIANCE"
    salience 20
    when
        $ctx : com.example.droolspoc.model.ValidationContext(
            (transaction.currency != "BDT" && transaction.amount >= 100000 && transaction.country == "OTHER")
        )
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Blocked: cross-border high-value transfer needs manual review");
end', 1, 'ACTIVE', TRUE, 'farhat', now());

-- #8  Deep-nested compliance check  (group: COMPLIANCE / TRANSFER_TRANSACTION, BUILDER)
INSERT INTO rule_definition (id, group_id, rule_name, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at) VALUES
(nextval('rule_definition_seq'), (SELECT id FROM rule_group WHERE group_code='COMPLIANCE' AND flow_id=(SELECT id FROM rule_flow WHERE flow_code='TRANSFER_TRANSACTION')), 'Deep-nested compliance check', 'BUILDER', '{"ruleName": "Deep-nested compliance check", "flow": "TRANSFER_TRANSACTION", "agendaGroup": "COMPLIANCE", "salience": 20, "conditionTree": {"type": "GROUP", "operator": "AND", "children": [{"type": "GROUP", "operator": "OR", "children": [{"type": "GROUP", "operator": "AND", "children": [{"type": "GROUP", "operator": "OR", "children": [{"type": "CONDITION", "field": "transaction.amount", "operator": ">", "value": 5000}, {"type": "CONDITION", "field": "transaction.currency", "operator": "==", "value": "USD"}]}, {"type": "CONDITION", "field": "product.drRes", "operator": "==", "value": 1}]}, {"type": "CONDITION", "field": "transaction.dailyTxnCount", "operator": ">=", "value": 10}]}, {"type": "GROUP", "operator": "OR", "children": [{"type": "CONDITION", "field": "userLimit.limit", "operator": ">=", "value": 100000}, {"type": "GROUP", "operator": "AND", "children": [{"type": "CONDITION", "field": "transaction.amount", "operator": ">", "valueField": "userLimit.limit"}, {"type": "CONDITION", "field": "transaction.dailyTxnCount", "operator": ">=", "value": 5}]}]}]}, "then": {"decision": "block", "message": "Blocked: deep-nested compliance condition met"}}', 'package com.example.droolspoc.model;

rule "TRANSFER_TRANSACTION.COMPLIANCE::Deep-nested compliance check"
    agenda-group "COMPLIANCE"
    salience 20
    when
        $ctx : com.example.droolspoc.model.ValidationContext(
            ((((transaction.amount > 5000 || transaction.currency == "USD") && product.drRes == 1) || transaction.dailyTxnCount >= 10) && (userLimit.limit >= 100000 || (transaction.amount > userLimit.limit && transaction.dailyTxnCount >= 5)))
        )
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Blocked: deep-nested compliance condition met");
end', 1, 'ACTIVE', TRUE, 'farhat', now());

-- #9  Block loan below credit-score threshold  (group: LOAN / LOAN_APPLICATION, BUILDER)
INSERT INTO rule_definition (id, group_id, rule_name, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at) VALUES
(nextval('rule_definition_seq'), (SELECT id FROM rule_group WHERE group_code='LOAN' AND flow_id=(SELECT id FROM rule_flow WHERE flow_code='LOAN_APPLICATION')), 'Block loan below credit-score threshold', 'BUILDER', '{"ruleName": "Block loan below credit-score threshold", "flow": "LOAN_APPLICATION", "agendaGroup": "LOAN", "salience": 10, "conditionTree": {"type": "GROUP", "operator": "AND", "children": [{"type": "CONDITION", "field": "customer.creditScore", "operator": "<", "value": 600}]}, "then": {"decision": "block", "message": "Blocked: credit score below 600"}}', 'package com.example.droolspoc.model;

rule "LOAN_APPLICATION.LOAN::Block loan below credit-score threshold"
    agenda-group "LOAN"
    salience 10
    when
        $ctx : com.example.droolspoc.model.LoanValidationContext(
            (customer.creditScore < 600)
        )
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Blocked: credit score below 600");
end', 1, 'ACTIVE', TRUE, 'farhat', now());

-- #10  Block closure with outstanding balance  (group: CLOSURE / ACCOUNT_CLOSURE, BUILDER)
INSERT INTO rule_definition (id, group_id, rule_name, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at) VALUES
(nextval('rule_definition_seq'), (SELECT id FROM rule_group WHERE group_code='CLOSURE' AND flow_id=(SELECT id FROM rule_flow WHERE flow_code='ACCOUNT_CLOSURE')), 'Block closure with outstanding balance', 'BUILDER', '{"ruleName": "Block closure with outstanding balance", "flow": "ACCOUNT_CLOSURE", "agendaGroup": "CLOSURE", "salience": 10, "conditionTree": {"type": "GROUP", "operator": "AND", "children": [{"type": "CONDITION", "field": "account.balance", "operator": ">", "value": 0}]}, "then": {"decision": "block", "message": "Blocked: account has an outstanding balance"}}', 'package com.example.droolspoc.model;

rule "ACCOUNT_CLOSURE.CLOSURE::Block closure with outstanding balance"
    agenda-group "CLOSURE"
    salience 10
    when
        $ctx : com.example.droolspoc.model.ClosureValidationContext(
            (account.balance > 0)
        )
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Blocked: account has an outstanding balance");
end', 1, 'ACTIVE', TRUE, 'farhat', now());

-- #11  Closure compliance - dormant review  (group: COMPLIANCE / ACCOUNT_CLOSURE, BUILDER)
INSERT INTO rule_definition (id, group_id, rule_name, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at) VALUES
(nextval('rule_definition_seq'), (SELECT id FROM rule_group WHERE group_code='COMPLIANCE' AND flow_id=(SELECT id FROM rule_flow WHERE flow_code='ACCOUNT_CLOSURE')), 'Closure compliance - dormant review', 'BUILDER', '{"ruleName": "Closure compliance - dormant review", "flow": "ACCOUNT_CLOSURE", "agendaGroup": "COMPLIANCE", "salience": 20, "conditionTree": {"type": "GROUP", "operator": "AND", "children": [{"type": "CONDITION", "field": "account.status", "operator": "==", "value": "DORMANT"}]}, "then": {"decision": "block", "message": "Blocked: dormant account closure needs compliance review"}}', 'package com.example.droolspoc.model;

rule "ACCOUNT_CLOSURE.COMPLIANCE::Closure compliance - dormant review"
    agenda-group "COMPLIANCE"
    salience 20
    when
        $ctx : com.example.droolspoc.model.ClosureValidationContext(
            (account.status == "DORMANT")
        )
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Blocked: dormant account closure needs compliance review");
end', 1, 'ACTIVE', TRUE, 'farhat', now());

-- #12  Closure compliance - frozen block  (group: COMPLIANCE / ACCOUNT_CLOSURE, BUILDER)
INSERT INTO rule_definition (id, group_id, rule_name, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at) VALUES
(nextval('rule_definition_seq'), (SELECT id FROM rule_group WHERE group_code='COMPLIANCE' AND flow_id=(SELECT id FROM rule_flow WHERE flow_code='ACCOUNT_CLOSURE')), 'Closure compliance - frozen block', 'BUILDER', '{"ruleName": "Closure compliance - frozen block", "flow": "ACCOUNT_CLOSURE", "agendaGroup": "COMPLIANCE", "salience": 20, "conditionTree": {"type": "GROUP", "operator": "AND", "children": [{"type": "CONDITION", "field": "account.status", "operator": "==", "value": "FROZEN"}]}, "then": {"decision": "block", "message": "Blocked: a frozen account cannot be closed"}}', 'package com.example.droolspoc.model;

rule "ACCOUNT_CLOSURE.COMPLIANCE::Closure compliance - frozen block"
    agenda-group "COMPLIANCE"
    salience 20
    when
        $ctx : com.example.droolspoc.model.ClosureValidationContext(
            (account.status == "FROZEN")
        )
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Blocked: a frozen account cannot be closed");
end', 1, 'ACTIVE', TRUE, 'farhat', now());

-- ============================ BUSINESS DATA =================================
-- users (USER-002 is blocked, USER-003 is KYC-unverified, USER-004 has a low credit score)
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

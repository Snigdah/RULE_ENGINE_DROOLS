-- Closure COMPLIANCE demo — same group name as transfer, DIFFERENT rules (scoped to closures).
-- Run against the ruleengine DB. Safe to re-run. RESTART the backend after (SQL bypasses the cache).

BEGIN;

-- clean previous runs
DELETE FROM rule_definition WHERE rule_name IN ('Closure compliance - dormant review','Closure compliance - frozen block');
DELETE FROM flow_group WHERE flow_name='ACCOUNT_CLOSURE' AND agenda_group='COMPLIANCE';
DELETE FROM rule_field_catalog WHERE field_key='account.status' AND flow_name='ACCOUNT_CLOSURE';
DELETE FROM re_account WHERE account_no IN ('ACC-902','ACC-903');

-- 1) expose account.status to the closure flow (normal column — plain SQL)
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values)
VALUES (nextval('rule_field_catalog_seq'), 'account.status', 'ACCOUNT_CLOSURE', 'Account Status', 'ENUM', 'account.status', '==,!=', 'ACTIVE,DORMANT,FROZEN,CLOSED');

-- 2) test accounts (balance 0 so they pass the CLOSURE balance>0 rule and reach COMPLIANCE)
INSERT INTO re_account (id, account_no, balance, status) VALUES (nextval('re_account_seq'), 'ACC-902', 0, 'DORMANT');
INSERT INTO re_account (id, account_no, balance, status) VALUES (nextval('re_account_seq'), 'ACC-903', 0, 'FROZEN');

-- 3) add COMPLIANCE as the closure flow's 3rd stage: COMMON -> CLOSURE -> COMPLIANCE
INSERT INTO flow_group (id, flow_name, agenda_group, order_no, fact_type) VALUES (nextval('flow_group_seq'), 'ACCOUNT_CLOSURE', 'COMPLIANCE', 3, 'com.example.droolspoc.model.ClosureValidationContext');

-- Closure compliance - dormant review
INSERT INTO rule_definition (id, rule_name, agenda_group, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at)
VALUES (nextval('rule_definition_seq'), 'Closure compliance - dormant review', 'COMPLIANCE', 'BUILDER',
  lo_from_bytea(0, convert_to('{"ruleName":"Closure compliance - dormant review","flow":"ACCOUNT_CLOSURE","agendaGroup":"COMPLIANCE","salience":20,"conditionTree":{"type":"GROUP","operator":"AND","children":[{"type":"CONDITION","field":"account.status","operator":"==","value":"DORMANT"}]},"then":{"decision":"block","message":"Blocked: dormant account closure needs compliance review"}}', 'UTF8')),
  lo_from_bytea(0, convert_to('package com.example.droolspoc.model;

rule "Closure compliance - dormant review"
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
end', 'UTF8')),
  1, 'ACTIVE', TRUE, 'farhat', now());

-- Closure compliance - frozen block
INSERT INTO rule_definition (id, rule_name, agenda_group, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at)
VALUES (nextval('rule_definition_seq'), 'Closure compliance - frozen block', 'COMPLIANCE', 'BUILDER',
  lo_from_bytea(0, convert_to('{"ruleName":"Closure compliance - frozen block","flow":"ACCOUNT_CLOSURE","agendaGroup":"COMPLIANCE","salience":20,"conditionTree":{"type":"GROUP","operator":"AND","children":[{"type":"CONDITION","field":"account.status","operator":"==","value":"FROZEN"}]},"then":{"decision":"block","message":"Blocked: a frozen account cannot be closed"}}', 'UTF8')),
  lo_from_bytea(0, convert_to('package com.example.droolspoc.model;

rule "Closure compliance - frozen block"
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
end', 'UTF8')),
  1, 'ACTIVE', TRUE, 'farhat', now());

COMMIT;
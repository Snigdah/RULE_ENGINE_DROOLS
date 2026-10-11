-- Nested-tree rules in their real groups (TRANSFER / VELOCITY / COMPLIANCE).
-- oid @Lob columns -> lo_from_bytea. Safe to re-run. RESTART backend after (SQL bypasses the cache).

BEGIN;

-- remove the old throwaway DEMO rows + DEMO group, and any earlier copy of these rules
DELETE FROM rule_definition WHERE rule_name IN
  ('Demo 1 - rapid OR cross-border','Demo 2 - layered channel/geo','Demo 3 - deep nested',
   'High-value or cross-border transfer','Layered channel & geo screen','Deep-nested compliance check');
DELETE FROM flow_group WHERE flow_name='TRANSFER_TRANSACTION' AND agenda_group='DEMO';

-- (TRANSFER / VELOCITY / COMPLIANCE groups already exist on the transfer flow — no flow_group insert needed)

-- High-value or cross-border transfer  (TRANSFER)
INSERT INTO rule_definition (id, rule_name, agenda_group, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at)
VALUES (nextval('rule_definition_seq'), 'High-value or cross-border transfer', 'TRANSFER', 'BUILDER',
  lo_from_bytea(0, convert_to('{"ruleName":"High-value or cross-border transfer","flow":"TRANSFER_TRANSACTION","agendaGroup":"TRANSFER","salience":20,"conditionTree":{"type":"GROUP","operator":"OR","children":[{"type":"GROUP","operator":"AND","children":[{"type":"CONDITION","field":"transaction.channel","operator":"==","value":"AGENT"},{"type":"CONDITION","field":"transaction.amount","operator":">","value":50000}]},{"type":"GROUP","operator":"AND","children":[{"type":"CONDITION","field":"transaction.country","operator":"==","value":"OTHER"},{"type":"CONDITION","field":"transaction.currency","operator":"!=","value":"BDT"}]}]},"then":{"decision":"block","message":"Blocked: high-value agent transfer, or cross-border non-BDT transfer"}}', 'UTF8')),
  lo_from_bytea(0, convert_to('package com.example.droolspoc.model;

rule "High-value or cross-border transfer"
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
end', 'UTF8')),
  1, 'ACTIVE', TRUE, 'farhat', now());

-- Layered channel & geo screen  (VELOCITY)
INSERT INTO rule_definition (id, rule_name, agenda_group, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at)
VALUES (nextval('rule_definition_seq'), 'Layered channel & geo screen', 'VELOCITY', 'BUILDER',
  lo_from_bytea(0, convert_to('{"ruleName":"Layered channel & geo screen","flow":"TRANSFER_TRANSACTION","agendaGroup":"VELOCITY","salience":20,"conditionTree":{"type":"GROUP","operator":"AND","children":[{"type":"GROUP","operator":"AND","children":[{"type":"CONDITION","field":"transaction.amount","operator":">","value":10000},{"type":"GROUP","operator":"OR","children":[{"type":"CONDITION","field":"transaction.channel","operator":"==","value":"ONLINE"},{"type":"CONDITION","field":"transaction.channel","operator":"==","value":"AGENT"}]}]},{"type":"GROUP","operator":"AND","children":[{"type":"GROUP","operator":"OR","children":[{"type":"CONDITION","field":"transaction.country","operator":"==","value":"US"},{"type":"CONDITION","field":"transaction.country","operator":"==","value":"OTHER"}]},{"type":"CONDITION","field":"transaction.currency","operator":"!=","value":"BDT"}]}]},"then":{"decision":"block","message":"Blocked: layered channel & geo screening"}}', 'UTF8')),
  lo_from_bytea(0, convert_to('package com.example.droolspoc.model;

rule "Layered channel & geo screen"
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
end', 'UTF8')),
  1, 'ACTIVE', TRUE, 'farhat', now());

-- Deep-nested compliance check  (COMPLIANCE)
INSERT INTO rule_definition (id, rule_name, agenda_group, source_type, source_json, drl_text, version_no, status, active, created_by, updated_at)
VALUES (nextval('rule_definition_seq'), 'Deep-nested compliance check', 'COMPLIANCE', 'BUILDER',
  lo_from_bytea(0, convert_to('{"ruleName":"Deep-nested compliance check","flow":"TRANSFER_TRANSACTION","agendaGroup":"COMPLIANCE","salience":20,"conditionTree":{"type":"GROUP","operator":"AND","children":[{"type":"GROUP","operator":"OR","children":[{"type":"GROUP","operator":"AND","children":[{"type":"GROUP","operator":"OR","children":[{"type":"CONDITION","field":"transaction.amount","operator":">","value":5000},{"type":"CONDITION","field":"transaction.currency","operator":"==","value":"USD"}]},{"type":"CONDITION","field":"product.drRes","operator":"==","value":1}]},{"type":"CONDITION","field":"transaction.dailyTxnCount","operator":">=","value":10}]},{"type":"GROUP","operator":"OR","children":[{"type":"CONDITION","field":"userLimit.limit","operator":">=","value":100000},{"type":"GROUP","operator":"AND","children":[{"type":"CONDITION","field":"transaction.amount","operator":">","valueField":"userLimit.limit"},{"type":"CONDITION","field":"transaction.dailyTxnCount","operator":">=","value":5}]}]}]},"then":{"decision":"block","message":"Blocked: deep-nested compliance condition met"}}', 'UTF8')),
  lo_from_bytea(0, convert_to('package com.example.droolspoc.model;

rule "Deep-nested compliance check"
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
end', 'UTF8')),
  1, 'ACTIVE', TRUE, 'farhat', now());

COMMIT;
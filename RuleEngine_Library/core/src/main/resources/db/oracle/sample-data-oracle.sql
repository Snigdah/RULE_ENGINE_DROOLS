-- =============================================================================
--  Rule Engine library - SAMPLE / DEMO data (Oracle)
-- =============================================================================
--  Illustrative rows for the design walkthrough: 3 request types, 4 rules
--  (2 shared in COMMON), and 7 catalog fields. Values use the Centrino demo
--  package (com.leads.admin.ruleenginepoc). Run AFTER schema-oracle.sql.
--
--  This is NOT production data - it shows how the four tables relate.
--  Run in SQL*Plus / SQLcl. PKs use the sequences so nothing collides later.
-- =============================================================================
SET DEFINE OFF;
SET SQLBLANKLINES ON;

-- ---- REQUEST_FLOW_MAP : request class -> flow -------------------------------
INSERT INTO REQUEST_FLOW_MAP (ID, FACT_TYPE, FLOW_NAME) VALUES
    (REQUEST_FLOW_MAP_SEQ.NEXTVAL, 'com.leads.admin.ruleenginepoc.model.ValidationContext', 'TRANSFER_TRANSACTION');
INSERT INTO REQUEST_FLOW_MAP (ID, FACT_TYPE, FLOW_NAME) VALUES
    (REQUEST_FLOW_MAP_SEQ.NEXTVAL, 'com.leads.admin.ruleenginepoc.model.LoanValidationContext', 'LOAN_APPLICATION');
INSERT INTO REQUEST_FLOW_MAP (ID, FACT_TYPE, FLOW_NAME) VALUES
    (REQUEST_FLOW_MAP_SEQ.NEXTVAL, 'com.leads.admin.ruleenginepoc.model.ClosureValidationContext', 'ACCOUNT_CLOSURE');

-- ---- FLOW_GROUP : every flow runs COMMON first, then its own group ----------
INSERT INTO FLOW_GROUP (ID, FLOW_NAME, AGENDA_GROUP, ORDER_NO) VALUES (FLOW_GROUP_SEQ.NEXTVAL, 'TRANSFER_TRANSACTION', 'COMMON',   1);
INSERT INTO FLOW_GROUP (ID, FLOW_NAME, AGENDA_GROUP, ORDER_NO) VALUES (FLOW_GROUP_SEQ.NEXTVAL, 'TRANSFER_TRANSACTION', 'TRANSFER', 2);
INSERT INTO FLOW_GROUP (ID, FLOW_NAME, AGENDA_GROUP, ORDER_NO) VALUES (FLOW_GROUP_SEQ.NEXTVAL, 'LOAN_APPLICATION',     'COMMON',   1);
INSERT INTO FLOW_GROUP (ID, FLOW_NAME, AGENDA_GROUP, ORDER_NO) VALUES (FLOW_GROUP_SEQ.NEXTVAL, 'LOAN_APPLICATION',     'LOAN',     2);
INSERT INTO FLOW_GROUP (ID, FLOW_NAME, AGENDA_GROUP, ORDER_NO) VALUES (FLOW_GROUP_SEQ.NEXTVAL, 'ACCOUNT_CLOSURE',      'COMMON',   1);
INSERT INTO FLOW_GROUP (ID, FLOW_NAME, AGENDA_GROUP, ORDER_NO) VALUES (FLOW_GROUP_SEQ.NEXTVAL, 'ACCOUNT_CLOSURE',      'CLOSURE',  2);

-- ---- RULE_FIELD_CATALOG : builder palette (COMMON shows in every flow) ------
INSERT INTO RULE_FIELD_CATALOG (ID, FIELD_KEY, FLOW_NAME, LABEL, DATA_TYPE, DRL_PATH, ALLOWED_OPS, ALLOWED_VALUES) VALUES
    (RULE_FIELD_CATALOG_SEQ.NEXTVAL, 'user.kycVerified', 'COMMON', 'KYC Verified', 'BOOLEAN', 'user.kycVerified', '==', 'Yes (true), No (false)');
INSERT INTO RULE_FIELD_CATALOG (ID, FIELD_KEY, FLOW_NAME, LABEL, DATA_TYPE, DRL_PATH, ALLOWED_OPS, ALLOWED_VALUES) VALUES
    (RULE_FIELD_CATALOG_SEQ.NEXTVAL, 'transaction.amount', 'TRANSFER_TRANSACTION', 'Transaction Amount', 'NUMBER', 'transaction.amount', '>,<,>=,<=,==', NULL);
INSERT INTO RULE_FIELD_CATALOG (ID, FIELD_KEY, FLOW_NAME, LABEL, DATA_TYPE, DRL_PATH, ALLOWED_OPS, ALLOWED_VALUES) VALUES
    (RULE_FIELD_CATALOG_SEQ.NEXTVAL, 'transaction.currency', 'TRANSFER_TRANSACTION', 'Currency', 'ENUM', 'transaction.currency', '==,!=', 'BDT,USD,EUR,GBP');
INSERT INTO RULE_FIELD_CATALOG (ID, FIELD_KEY, FLOW_NAME, LABEL, DATA_TYPE, DRL_PATH, ALLOWED_OPS, ALLOWED_VALUES) VALUES
    (RULE_FIELD_CATALOG_SEQ.NEXTVAL, 'product.drRes', 'TRANSFER_TRANSACTION', 'Debit Restricted', 'BOOLEAN', 'product.drRes', '==', 'Yes (1), No (0)');
INSERT INTO RULE_FIELD_CATALOG (ID, FIELD_KEY, FLOW_NAME, LABEL, DATA_TYPE, DRL_PATH, ALLOWED_OPS, ALLOWED_VALUES) VALUES
    (RULE_FIELD_CATALOG_SEQ.NEXTVAL, 'userLimit.limit', 'TRANSFER_TRANSACTION', 'User Limit', 'NUMBER', 'userLimit.limit', '>,<,>=,<=', NULL);
INSERT INTO RULE_FIELD_CATALOG (ID, FIELD_KEY, FLOW_NAME, LABEL, DATA_TYPE, DRL_PATH, ALLOWED_OPS, ALLOWED_VALUES) VALUES
    (RULE_FIELD_CATALOG_SEQ.NEXTVAL, 'loan.amount', 'LOAN_APPLICATION', 'Loan Amount', 'NUMBER', 'loan.amount', '>,<,>=,<=', NULL);
INSERT INTO RULE_FIELD_CATALOG (ID, FIELD_KEY, FLOW_NAME, LABEL, DATA_TYPE, DRL_PATH, ALLOWED_OPS, ALLOWED_VALUES) VALUES
    (RULE_FIELD_CATALOG_SEQ.NEXTVAL, 'customer.creditScore', 'LOAN_APPLICATION', 'Credit Score', 'NUMBER', 'customer.creditScore', '>,<,>=,<=', NULL);

-- ---- RULE_DEFINITION : 4 rules (101 & 103 are shared, both in COMMON) -------

-- Rule 101 - Block blocked user (COMMON, hand-written DRL: uses eval + a global) --
INSERT INTO RULE_DEFINITION (ID, RULE_NAME, AGENDA_GROUP, SOURCE_TYPE, SOURCE_JSON, DRL_TEXT, VERSION_NO, STATUS, ACTIVE, CREATED_BY, UPDATED_AT)
VALUES (RULE_DEFINITION_SEQ.NEXTVAL, 'Block transaction for a blocked user', 'COMMON', 'DRL', NULL,
'package com.leads.admin.ruleenginepoc.rules;

import com.leads.admin.ruleenginepoc.model.ValidationContext;
import com.leads.admin.ruleenginepoc.reference.BlockedUsers;

global leads.ruleengine.core.context.GlobalContext globalContext;

rule "Block transaction for a blocked user"
    agenda-group "COMMON"
    salience 100
    when
        $ctx : ValidationContext()
        eval(globalContext.get(BlockedUsers.class).isBlocked($ctx.getTransaction().getUserId()))
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Blocked: user is blocked");
        drools.halt();
end',
1, 'ACTIVE', 1, 'system', SYSTIMESTAMP);

-- Rule 103 - Block if KYC not verified (COMMON, BUILDER) ----------------------
INSERT INTO RULE_DEFINITION (ID, RULE_NAME, AGENDA_GROUP, SOURCE_TYPE, SOURCE_JSON, DRL_TEXT, VERSION_NO, STATUS, ACTIVE, CREATED_BY, UPDATED_AT)
VALUES (RULE_DEFINITION_SEQ.NEXTVAL, 'Block if KYC not verified', 'COMMON', 'BUILDER',
'{"ruleName":"Block if KYC not verified","flow":"TRANSFER_TRANSACTION","agendaGroup":"COMMON","salience":90,"match":"all","conditions":[{"field":"user.kycVerified","operator":"==","value":false}],"then":{"decision":"block","message":"KYC not verified"}}',
'package com.leads.admin.ruleenginepoc.rules;

import com.leads.admin.ruleenginepoc.model.ValidationContext;

rule "Block if KYC not verified"
    agenda-group "COMMON"
    salience 90
    when
        $ctx : ValidationContext(
            user.kycVerified == false
        )
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("KYC not verified");
end',
1, 'ACTIVE', 1, 'farhat', SYSTIMESTAMP);

-- Rule 102 - Over-limit debit-restricted BDT (TRANSFER, BUILDER) --------------
INSERT INTO RULE_DEFINITION (ID, RULE_NAME, AGENDA_GROUP, SOURCE_TYPE, SOURCE_JSON, DRL_TEXT, VERSION_NO, STATUS, ACTIVE, CREATED_BY, UPDATED_AT)
VALUES (RULE_DEFINITION_SEQ.NEXTVAL, 'Over-limit debit-restricted BDT', 'TRANSFER', 'BUILDER',
'{"ruleName":"Over-limit debit-restricted BDT","flow":"TRANSFER_TRANSACTION","agendaGroup":"TRANSFER","salience":10,"match":"all","conditions":[{"field":"transaction.currency","operator":"==","value":"BDT"},{"field":"product.drRes","operator":"==","value":1},{"field":"transaction.amount","operator":">","valueField":"userLimit.limit"}],"then":{"decision":"block","message":"Amount exceeds limit on a debit-restricted BDT account"}}',
'package com.leads.admin.ruleenginepoc.rules;

import com.leads.admin.ruleenginepoc.model.ValidationContext;

rule "Over-limit debit-restricted BDT"
    agenda-group "TRANSFER"
    salience 10
    when
        $ctx : ValidationContext(
            transaction.currency == "BDT",
            product.drRes == 1,
            transaction.amount > userLimit.limit
        )
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Amount exceeds limit on a debit-restricted BDT account");
end',
1, 'ACTIVE', 1, 'farhat', SYSTIMESTAMP);

-- Rule 104 - Loan exceeds eligibility (LOAN, BUILDER) ------------------------
INSERT INTO RULE_DEFINITION (ID, RULE_NAME, AGENDA_GROUP, SOURCE_TYPE, SOURCE_JSON, DRL_TEXT, VERSION_NO, STATUS, ACTIVE, CREATED_BY, UPDATED_AT)
VALUES (RULE_DEFINITION_SEQ.NEXTVAL, 'Loan exceeds eligibility', 'LOAN', 'BUILDER',
'{"ruleName":"Loan exceeds eligibility","flow":"LOAN_APPLICATION","agendaGroup":"LOAN","salience":10,"match":"all","conditions":[{"field":"customer.creditScore","operator":"<","value":600}],"then":{"decision":"block","message":"Credit score below 600"}}',
'package com.leads.admin.ruleenginepoc.rules;

import com.leads.admin.ruleenginepoc.model.LoanValidationContext;

rule "Loan exceeds eligibility"
    agenda-group "LOAN"
    salience 10
    when
        $ctx : LoanValidationContext(
            customer.creditScore < 600
        )
    then
        $ctx.setValid(false);
        $ctx.setPermissionDenied(true);
        $ctx.setValidationMessage("Credit score below 600");
end',
1, 'ACTIVE', 1, 'farhat', SYSTIMESTAMP);

COMMIT;

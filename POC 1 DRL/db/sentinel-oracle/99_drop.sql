-- =============================================================================
--  Rule Engine approval POC - REMOVE everything this POC added to the SENTINEL schema
-- =============================================================================
--  Undoes 01_create.sql and 02_seed.sql, plus the approval records that testing
--  wrote into the shared TREG_* tables. Every DELETE on a shared table is scoped to
--  FUNCTION_ID 101022 (or to the RULE_* tables) so Sentinel's own rows are untouched.
--
--  Safe to re-run: missing objects are skipped. Plain SQL - runs in a PL/SQL Developer
--  SQL window or SQL*Plus.
-- =============================================================================

-- ---- 1. Approval records created while testing (function 101022 only) -------
DELETE FROM TREG_APPROVAL_TABLES_CLOB
 WHERE SET_ID IN (SELECT SET_ID FROM TREG_APPROVAL WHERE FUNCTION_ID = 101022);
DELETE FROM TREG_APPROVAL_TABLES_VAL
 WHERE SET_ID IN (SELECT SET_ID FROM TREG_APPROVAL WHERE FUNCTION_ID = 101022);
DELETE FROM TREG_APPROVAL_TABLES
 WHERE SET_ID IN (SELECT SET_ID FROM TREG_APPROVAL WHERE FUNCTION_ID = 101022);
DELETE FROM TREG_APPROVAL_QUEUE
 WHERE SET_ID IN (SELECT SET_ID FROM TREG_APPROVAL WHERE FUNCTION_ID = 101022);
DELETE FROM TREG_APPROVAL_DELETE_LOG
 WHERE ROOT_TABLE_NM IN ('RULE_GROUP', 'RULE_DEFINITION');
DELETE FROM TREG_APPROVAL WHERE FUNCTION_ID = 101022;

-- ---- 2. The selector row from 02_seed.sql ------------------------------------
DELETE FROM META_FUNCTION_SELECTOR_MAP WHERE FUNCTION_ID = 101022;

COMMIT;

-- ---- 3. Tables and sequences from 01_create.sql (children first) ------------
BEGIN
    FOR t IN (SELECT column_value AS name FROM TABLE(sys.odcivarchar2list(
                  'RULE_DEFINITION', 'RULE_FIELD_CATALOG', 'RULE_GROUP', 'RULE_FLOW',
                  'RE_USER', 'RE_USER_LIMIT', 'RE_PRODUCT', 'RE_USER_BLOCK', 'RE_CUSTOMER', 'RE_ACCOUNT')))
    LOOP
        BEGIN
            EXECUTE IMMEDIATE 'DROP TABLE ' || t.name || ' PURGE';
            DBMS_OUTPUT.PUT_LINE('dropped table ' || t.name);
        EXCEPTION WHEN OTHERS THEN
            IF SQLCODE != -942 THEN RAISE; END IF;   -- ORA-00942: already gone
        END;
    END LOOP;

    FOR s IN (SELECT column_value AS name FROM TABLE(sys.odcivarchar2list(
                  'RULE_FLOW_SEQ', 'RULE_GROUP_SEQ', 'RULE_DEFINITION_SEQ', 'RULE_FIELD_CATALOG_SEQ',
                  'RE_USER_SEQ', 'RE_USER_LIMIT_SEQ', 'RE_PRODUCT_SEQ', 'RE_USER_BLOCK_SEQ',
                  'RE_CUSTOMER_SEQ', 'RE_ACCOUNT_SEQ')))
    LOOP
        BEGIN
            EXECUTE IMMEDIATE 'DROP SEQUENCE ' || s.name;
            DBMS_OUTPUT.PUT_LINE('dropped sequence ' || s.name);
        EXCEPTION WHEN OTHERS THEN
            IF SQLCODE != -2289 THEN RAISE; END IF;  -- ORA-02289: already gone
        END;
    END LOOP;
END;
/
-- Centrino workflow 40 / function map 541 must be removed by the product owner (see README.md).

-- =============================================================================
--  Rule-engine CLEANUP (PostgreSQL / pgAdmin)
--  Wipes the FOUR library tables so you can re-seed flows / groups / fields / rules
--  through the Admin API (seed.ps1 / seed.sh, or the curl steps in seed-via-api.md).
--  Your business data (the re_* tables) is NOT touched.
--
--  Database: ruleengine   ·   run as the app's DB user (e.g. postgres)
-- =============================================================================

-- 0) (optional) see which rule-engine tables exist right now
SELECT table_name
FROM information_schema.tables
WHERE table_schema = 'public'
  AND table_name IN ('rule_flow','rule_group','rule_definition','rule_field_catalog',
                     'flow_group','request_flow_map')
ORDER BY table_name;


-- ============================ OPTION A — fresh rebuild (recommended) ==========
--  Drops the rule-engine tables + sequences (new AND legacy names). Safe to run
--  even if some don't exist. Business re_* tables are left alone.
--
--  AFTER running this:
--    1. RESTART the POC app  → Hibernate (ddl-auto=update) recreates the 4 tables
--       in the new 3.0.0 shape (empty).
--    2. Run seed.ps1 / seed.sh (or the curl steps) → seeds everything via the API.
-- -----------------------------------------------------------------------------

DROP TABLE IF EXISTS rule_definition    CASCADE;
DROP TABLE IF EXISTS rule_group         CASCADE;
DROP TABLE IF EXISTS rule_field_catalog CASCADE;
DROP TABLE IF EXISTS rule_flow          CASCADE;

-- legacy tables from the old 2.0.0 design (harmless if they don't exist)
DROP TABLE IF EXISTS flow_group         CASCADE;
DROP TABLE IF EXISTS request_flow_map   CASCADE;

DROP SEQUENCE IF EXISTS rule_definition_seq;
DROP SEQUENCE IF EXISTS rule_group_seq;
DROP SEQUENCE IF EXISTS rule_field_catalog_seq;
DROP SEQUENCE IF EXISTS rule_flow_seq;
DROP SEQUENCE IF EXISTS flow_group_seq;
DROP SEQUENCE IF EXISTS request_flow_map_seq;


-- ============================ OPTION B — just empty the data (no restart) =====
--  Use this INSTEAD of Option A only when the 4 tables are ALREADY in the new
--  3.0.0 shape and you just want them cleared. No app restart needed — seed via
--  the API right after. (Uncomment the lines below to use.)
-- -----------------------------------------------------------------------------

-- TRUNCATE rule_definition, rule_group, rule_flow, rule_field_catalog RESTART IDENTITY CASCADE;
-- ALTER SEQUENCE rule_flow_seq          RESTART WITH 1;
-- ALTER SEQUENCE rule_group_seq         RESTART WITH 1;
-- ALTER SEQUENCE rule_definition_seq    RESTART WITH 1;
-- ALTER SEQUENCE rule_field_catalog_seq RESTART WITH 1;


-- ============================ verify empty ====================================
-- Run after Option B (after Option A the tables don't exist until the app restarts):
-- SELECT 'rule_flow' t, count(*) FROM rule_flow
-- UNION ALL SELECT 'rule_group', count(*) FROM rule_group
-- UNION ALL SELECT 'rule_definition', count(*) FROM rule_definition
-- UNION ALL SELECT 'rule_field_catalog', count(*) FROM rule_field_catalog;

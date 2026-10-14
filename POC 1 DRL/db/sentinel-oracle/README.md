# Rule engine approval test - Sentinel DB objects

Dummy objects for testing maker-checker approval of rule-engine changes against the
**Sentinel** Oracle schema (`sentinel@192.168.20.77:1521/microcubepdb`).
Nothing here alters an existing Sentinel or `TREG_*` table - only new objects and rows.

## Scripts

| Script | What it does |
|---|---|
| `01_create.sql` | Creates the tables and sequences below. Aborts if any name already exists. |
| `02_seed.sql` | Business rows for the `RE_*` tables + one `META_FUNCTION_SELECTOR_MAP` row (101022). |
| `99_drop.sql` | Removes everything below, plus the approval records testing created (function 101022 only). |

## Objects created (all removed by `99_drop.sql`)

**Tables:** `RULE_FLOW`, `RULE_GROUP`, `RULE_DEFINITION`, `RULE_FIELD_CATALOG`,
`RE_USER`, `RE_USER_LIMIT`, `RE_PRODUCT`, `RE_USER_BLOCK`, `RE_CUSTOMER`, `RE_ACCOUNT`
(plus their PK/UK/FK/check constraints and indexes `IX_RULE_GROUP_FLOW`,
`IX_RULE_DEFINITION_GROUP`, `IX_RULE_FIELD_FLOW`).

**Sequences:** `RULE_FLOW_SEQ`, `RULE_GROUP_SEQ`, `RULE_DEFINITION_SEQ`, `RULE_FIELD_CATALOG_SEQ`,
`RE_USER_SEQ`, `RE_USER_LIMIT_SEQ`, `RE_PRODUCT_SEQ`, `RE_USER_BLOCK_SEQ`, `RE_CUSTOMER_SEQ`,
`RE_ACCOUNT_SEQ`.

**Rows in existing tables:**
- `META_FUNCTION_SELECTOR_MAP`: `FUNCTION_ID = 101022` (`EXECUTE_STATUS = 1`, `IS_ASYNC_POST_APP_PROCESS = 0`, selector `rule-engine`).
- Written by changeboard while testing, all with `FUNCTION_ID = 101022`: `TREG_APPROVAL`,
  `TREG_APPROVAL_QUEUE`, `TREG_APPROVAL_TABLES`, `TREG_APPROVAL_TABLES_VAL`,
  `TREG_APPROVAL_TABLES_CLOB`; and `TREG_APPROVAL_DELETE_LOG` rows for `RULE_GROUP` / `RULE_DEFINITION`.

## Outside this DB schema (product owner removes these in Centrino)

| Item | Id |
|---|---|
| Workflow `ruleEngine` (1 level, role-based) | 40 |
| Workflow level detail | 54 |
| Role map (`rule-engine` role) | 73 |
| Function map (function 101022 -> workflow 40) | 541 |

Approver for the test: **saikat1** (role `rule-engine`). Maker: **afrida1**.

## Run order

1. `@01_create.sql` then `@02_seed.sql` (as `sentinel`).
2. Start the rule engine with approval **off** once, to seed flows/groups/rules through the API:
   ```
   set RULE_ENGINE_APPROVAL=false
   mvn spring-boot:run -Dspring-boot.run.profiles=sentinel
   powershell -ExecutionPolicy Bypass -File seed.ps1
   ```
3. Restart with approval **on** (the default for the `sentinel` profile):
   ```
   mvn spring-boot:run -Dspring-boot.run.profiles=sentinel
   ```
4. Clean up later: `@99_drop.sql`, and ask the product owner to remove the Centrino items above.

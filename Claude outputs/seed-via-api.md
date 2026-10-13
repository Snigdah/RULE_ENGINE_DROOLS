# Rule Engine POC — Seed via Admin API (curl)

Seeds the **library** tables for the exact current scenario:
3 flows, transfer with 4 groups (COMMON → TRANSFER → VELOCITY → COMPLIANCE), 7 rules, 11 catalog fields.
Domain: `com.example.droolspoc` · Engine base URL: `http://localhost:8080`

> Windows: use `curl.exe` (not the PowerShell `curl` alias). Git Bash / cmd are fine.

## Which tables can be seeded by API?

| Table | API | How |
|---|---|---|
| REQUEST_FLOW_MAP | `POST /admin/flows/mappings` | curl (below) |
| FLOW_GROUP | `POST /admin/flows` | curl (below) |
| RULE_DEFINITION | `POST /admin/rules` | curl (below) |
| RULE_FIELD_CATALOG | **none (read-only)** | **SQL only** (section 1) |

**Run order:** 1) catalog SQL → 2) flows → 3) mappings → 4) rules.
Flows must exist *before* builder rules (the DRL generator reads the flow's FACT_TYPE from FLOW_GROUP). Mappings route request classes; catalog must exist first so ENUM values are quoted correctly. Backend must be running with the model classes (incl. `channel`/`country`/`dailyTxnCount`).

---

## 1) RULE_FIELD_CATALOG — SQL only (no API)

```sql
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values)
VALUES (nextval('rule_field_catalog_seq'), 'user.kycVerified', 'COMMON', 'KYC Verified', 'BOOLEAN', 'user.kycVerified', '==', 'Yes (true), No (false)');
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values)
VALUES (nextval('rule_field_catalog_seq'), 'transaction.amount', 'TRANSFER_TRANSACTION', 'Transaction Amount', 'NUMBER', 'transaction.amount', '>,<,>=,<=,==', NULL);
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values)
VALUES (nextval('rule_field_catalog_seq'), 'transaction.currency', 'TRANSFER_TRANSACTION', 'Currency', 'ENUM', 'transaction.currency', '==,!=', 'BDT,USD,EUR,GBP');
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values)
VALUES (nextval('rule_field_catalog_seq'), 'product.drRes', 'TRANSFER_TRANSACTION', 'Debit Restricted', 'BOOLEAN', 'product.drRes', '==', 'Yes (1), No (0)');
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values)
VALUES (nextval('rule_field_catalog_seq'), 'userLimit.limit', 'TRANSFER_TRANSACTION', 'User Limit', 'NUMBER', 'userLimit.limit', '>,<,>=,<=', NULL);
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values)
VALUES (nextval('rule_field_catalog_seq'), 'transaction.channel', 'TRANSFER_TRANSACTION', 'Channel', 'ENUM', 'transaction.channel', '==,!=', 'ATM,POS,ONLINE,BRANCH,AGENT');
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values)
VALUES (nextval('rule_field_catalog_seq'), 'transaction.country', 'TRANSFER_TRANSACTION', 'Destination Country', 'ENUM', 'transaction.country', '==,!=', 'BD,US,UK,AE,SG,OTHER');
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values)
VALUES (nextval('rule_field_catalog_seq'), 'transaction.dailyTxnCount', 'TRANSFER_TRANSACTION', 'Daily Txn Count', 'NUMBER', 'transaction.dailyTxnCount', '>,<,>=,<=,==', NULL);
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values)
VALUES (nextval('rule_field_catalog_seq'), 'loan.amount', 'LOAN_APPLICATION', 'Loan Amount', 'NUMBER', 'loan.amount', '>,<,>=,<=', NULL);
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values)
VALUES (nextval('rule_field_catalog_seq'), 'customer.creditScore', 'LOAN_APPLICATION', 'Credit Score', 'NUMBER', 'customer.creditScore', '>,<,>=,<=', NULL);
INSERT INTO rule_field_catalog (id, field_key, flow_name, label, data_type, drl_path, allowed_ops, allowed_values)
VALUES (nextval('rule_field_catalog_seq'), 'account.balance', 'ACCOUNT_CLOSURE', 'Account Balance', 'NUMBER', 'account.balance', '>,<,>=,<=,==,!=', NULL);
```

_(Oracle: swap `nextval('rule_field_catalog_seq')` → `RULE_FIELD_CATALOG_SEQ.NEXTVAL`.)_

---

## 2) FLOW_GROUP — define each flow's ordered groups

```bash
curl.exe -X POST http://localhost:8080/admin/flows -H "Content-Type: application/json" -d "{\"flowName\": \"TRANSFER_TRANSACTION\", \"factType\": \"com.example.droolspoc.model.ValidationContext\", \"groups\": [\"COMMON\", \"TRANSFER\", \"VELOCITY\", \"COMPLIANCE\"]}"
```

```bash
curl.exe -X POST http://localhost:8080/admin/flows -H "Content-Type: application/json" -d "{\"flowName\": \"LOAN_APPLICATION\", \"factType\": \"com.example.droolspoc.model.LoanValidationContext\", \"groups\": [\"COMMON\", \"LOAN\"]}"
```

```bash
curl.exe -X POST http://localhost:8080/admin/flows -H "Content-Type: application/json" -d "{\"flowName\": \"ACCOUNT_CLOSURE\", \"factType\": \"com.example.droolspoc.model.ClosureValidationContext\", \"groups\": [\"COMMON\", \"CLOSURE\"]}"
```

---

## 3) REQUEST_FLOW_MAP — map each context class to its flow

```bash
curl.exe -X POST http://localhost:8080/admin/flows/mappings -H "Content-Type: application/json" -d "{\"requestType\": \"com.example.droolspoc.dto.TransactionRequest\", \"flowName\": \"TRANSFER_TRANSACTION\"}"
```

```bash
curl.exe -X POST http://localhost:8080/admin/flows/mappings -H "Content-Type: application/json" -d "{\"requestType\": \"com.example.droolspoc.dto.LoanRequest\", \"flowName\": \"LOAN_APPLICATION\"}"
```

```bash
curl.exe -X POST http://localhost:8080/admin/flows/mappings -H "Content-Type: application/json" -d "{\"requestType\": \"com.example.droolspoc.dto.AccountCloseRequest\", \"flowName\": \"ACCOUNT_CLOSURE\"}"
```

---

## 4) RULE_DEFINITION — 2 hand-written DRL + 5 builder rules

### DRL rules (COMMON)

**Block transaction for a blocked user**
```bash
curl.exe -X POST http://localhost:8080/admin/rules -H "Content-Type: application/json" -d "{\"ruleName\": \"Block transaction for a blocked user\", \"agendaGroup\": \"COMMON\", \"sourceType\": \"DRL\", \"drl\": \"package com.example.droolspoc.rules;\n\nimport com.example.droolspoc.model.RuleContext;\nimport com.example.droolspoc.context.data.BlockedUsers;\n\nglobal leads.ruleengine.core.context.GlobalContext globalContext;\n\nrule \\"Block transaction for a blocked user\\"\n    agenda-group \\"COMMON\\"\n    salience 100\n    when\n        $ctx : RuleContext()\n        eval(globalContext.get(BlockedUsers.class).isBlocked($ctx.getUserId()))\n    then\n        $ctx.setValid(false);\n        $ctx.setPermissionDenied(true);\n        $ctx.setValidationMessage(\\"Blocked: user is blocked\\");\n        drools.halt();\nend\", \"createdBy\": \"farhat\"}"
```

**Block if KYC not verified**
```bash
curl.exe -X POST http://localhost:8080/admin/rules -H "Content-Type: application/json" -d "{\"ruleName\": \"Block if KYC not verified\", \"agendaGroup\": \"COMMON\", \"sourceType\": \"DRL\", \"drl\": \"package com.example.droolspoc.rules;\n\nimport com.example.droolspoc.model.RuleContext;\n\nrule \\"Block if KYC not verified\\"\n    agenda-group \\"COMMON\\"\n    salience 90\n    when\n        $ctx : RuleContext(user != null, user.kycVerified == false)\n    then\n        $ctx.setValid(false);\n        $ctx.setPermissionDenied(true);\n        $ctx.setValidationMessage(\\"Blocked: KYC not verified\\");\n        drools.halt();\nend\", \"createdBy\": \"farhat\"}"
```

### Builder rules

**Block over-limit debit-restricted BDT transaction**  (TRANSFER)
```bash
curl.exe -X POST http://localhost:8080/admin/rules -H "Content-Type: application/json" -d "{\"ruleName\": \"Block over-limit debit-restricted BDT transaction\", \"agendaGroup\": \"TRANSFER\", \"sourceType\": \"BUILDER\", \"sourceJson\": \"{\\"ruleName\\": \\"Block over-limit debit-restricted BDT transaction\\", \\"flow\\": \\"TRANSFER_TRANSACTION\\", \\"agendaGroup\\": \\"TRANSFER\\", \\"salience\\": 10, \\"conditionTree\\": {\\"type\\": \\"GROUP\\", \\"operator\\": \\"AND\\", \\"children\\": [{\\"type\\": \\"CONDITION\\", \\"field\\": \\"transaction.currency\\", \\"operator\\": \\"==\\", \\"value\\": \\"BDT\\"}, {\\"type\\": \\"CONDITION\\", \\"field\\": \\"product.drRes\\", \\"operator\\": \\"==\\", \\"value\\": 1}, {\\"type\\": \\"CONDITION\\", \\"field\\": \\"transaction.amount\\", \\"operator\\": \\">\\", \\"valueField\\": \\"userLimit.limit\\"}]}, \\"then\\": {\\"decision\\": \\"block\\", \\"message\\": \\"Blocked: amount exceeds limit on a debit-restricted BDT account\\"}}\", \"createdBy\": \"farhat\"}"
```

**Block loan below credit-score threshold**  (LOAN)
```bash
curl.exe -X POST http://localhost:8080/admin/rules -H "Content-Type: application/json" -d "{\"ruleName\": \"Block loan below credit-score threshold\", \"agendaGroup\": \"LOAN\", \"sourceType\": \"BUILDER\", \"sourceJson\": \"{\\"ruleName\\": \\"Block loan below credit-score threshold\\", \\"flow\\": \\"LOAN_APPLICATION\\", \\"agendaGroup\\": \\"LOAN\\", \\"salience\\": 10, \\"conditionTree\\": {\\"type\\": \\"GROUP\\", \\"operator\\": \\"AND\\", \\"children\\": [{\\"type\\": \\"CONDITION\\", \\"field\\": \\"customer.creditScore\\", \\"operator\\": \\"<\\", \\"value\\": 600}]}, \\"then\\": {\\"decision\\": \\"block\\", \\"message\\": \\"Blocked: credit score below 600\\"}}\", \"createdBy\": \"farhat\"}"
```

**Block closure with outstanding balance**  (CLOSURE)
```bash
curl.exe -X POST http://localhost:8080/admin/rules -H "Content-Type: application/json" -d "{\"ruleName\": \"Block closure with outstanding balance\", \"agendaGroup\": \"CLOSURE\", \"sourceType\": \"BUILDER\", \"sourceJson\": \"{\\"ruleName\\": \\"Block closure with outstanding balance\\", \\"flow\\": \\"ACCOUNT_CLOSURE\\", \\"agendaGroup\\": \\"CLOSURE\\", \\"salience\\": 10, \\"conditionTree\\": {\\"type\\": \\"GROUP\\", \\"operator\\": \\"AND\\", \\"children\\": [{\\"type\\": \\"CONDITION\\", \\"field\\": \\"account.balance\\", \\"operator\\": \\">\\", \\"value\\": 0}]}, \\"then\\": {\\"decision\\": \\"block\\", \\"message\\": \\"Blocked: account has an outstanding balance\\"}}\", \"createdBy\": \"farhat\"}"
```

**Block rapid high-value agent transfers**  (VELOCITY)
```bash
curl.exe -X POST http://localhost:8080/admin/rules -H "Content-Type: application/json" -d "{\"ruleName\": \"Block rapid high-value agent transfers\", \"agendaGroup\": \"VELOCITY\", \"sourceType\": \"BUILDER\", \"sourceJson\": \"{\\"ruleName\\": \\"Block rapid high-value agent transfers\\", \\"flow\\": \\"TRANSFER_TRANSACTION\\", \\"agendaGroup\\": \\"VELOCITY\\", \\"salience\\": 20, \\"conditionTree\\": {\\"type\\": \\"GROUP\\", \\"operator\\": \\"AND\\", \\"children\\": [{\\"type\\": \\"CONDITION\\", \\"field\\": \\"transaction.channel\\", \\"operator\\": \\"==\\", \\"value\\": \\"AGENT\\"}, {\\"type\\": \\"CONDITION\\", \\"field\\": \\"transaction.amount\\", \\"operator\\": \\">\\", \\"value\\": 50000}, {\\"type\\": \\"CONDITION\\", \\"field\\": \\"transaction.dailyTxnCount\\", \\"operator\\": \\">=\\", \\"value\\": 5}]}, \\"then\\": {\\"decision\\": \\"block\\", \\"message\\": \\"Blocked: too many high-value agent transfers today\\"}}\", \"createdBy\": \"farhat\"}"
```

**Block high-value cross-border non-BDT transfer**  (COMPLIANCE)
```bash
curl.exe -X POST http://localhost:8080/admin/rules -H "Content-Type: application/json" -d "{\"ruleName\": \"Block high-value cross-border non-BDT transfer\", \"agendaGroup\": \"COMPLIANCE\", \"sourceType\": \"BUILDER\", \"sourceJson\": \"{\\"ruleName\\": \\"Block high-value cross-border non-BDT transfer\\", \\"flow\\": \\"TRANSFER_TRANSACTION\\", \\"agendaGroup\\": \\"COMPLIANCE\\", \\"salience\\": 20, \\"conditionTree\\": {\\"type\\": \\"GROUP\\", \\"operator\\": \\"AND\\", \\"children\\": [{\\"type\\": \\"CONDITION\\", \\"field\\": \\"transaction.currency\\", \\"operator\\": \\"!=\\", \\"value\\": \\"BDT\\"}, {\\"type\\": \\"CONDITION\\", \\"field\\": \\"transaction.amount\\", \\"operator\\": \\">=\\", \\"value\\": 100000}, {\\"type\\": \\"CONDITION\\", \\"field\\": \\"transaction.country\\", \\"operator\\": \\"==\\", \\"value\\": \\"OTHER\\"}]}, \\"then\\": {\\"decision\\": \\"block\\", \\"message\\": \\"Blocked: cross-border high-value transfer needs manual review\\"}}\", \"createdBy\": \"farhat\"}"
```

---

## Note — business tables are NOT library tables
`re_user`, `re_user_block`, `re_user_limit`, `re_product`, `re_customer`, `re_account` belong to the POC service, not the rule-engine library — they have no admin API and are seeded by your own SQL (`db/business-data.sql`). The rules above only *read* them at request time.

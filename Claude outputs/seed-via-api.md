# Rule Engine POC — Seed & Test via the Admin API (curl)

Brings up the exact scenario (3 flows, shared **COMMON**, transfer with COMMON → TRANSFER → VELOCITY → COMPLIANCE, loan, closure) **through HTTP**, then exercises it with the business endpoints and the active/inactive toggles.

- Engine base URL: `http://localhost:8080`
- Domain package: `com.example.droolspoc`
- Library: `com.leads:rule-engine:3.0.0` (4-table design)

> **Windows:** use `curl.exe` (not the PowerShell `curl` alias). Git Bash / cmd are fine.
> **Prereq:** the app is running and `db/business-data.sql` is loaded (the `re_*` tables). No rule-engine rows yet.

---

## Fastest: one script (no SQL)

Everything below is also packaged as a runnable script that drives the whole import over the Admin API.

```powershell
# Windows PowerShell
.\seed.ps1            # import the full scenario
.\seed.ps1 -Reset     # delete anything there first, then import
.\seed.ps1 -Test      # import, then run the validation scenarios and print PASS/BLOCK
```

```bash
# Linux / macOS / Git Bash (needs curl + jq)
./seed.sh             # import
RESET=1 ./seed.sh      # delete first, then import
TEST=1 ./seed.sh       # import, then run scenarios
BASE=http://host:8080 ./seed.sh
```

The rest of this page is the same thing done by hand, step by step, so you can see each call.

---

## What seeds via API vs. SQL

Everything is now API-creatable — no SQL bootstrap needed.

| Table | Endpoint |
|---|---|
| `rule_flow` | `POST /admin/flows` |
| `rule_group` (a flow's own) | `POST /admin/flows/{flowCode}/groups`, or the `groups` list on `POST /admin/flows` |
| `rule_group` (**shared COMMON**, `flow_id NULL`) | `POST /admin/groups/shared` |
| `rule_definition` | `POST /admin/groups/{groupId}/rules` |
| `rule_field_catalog` | `POST /admin/fields` (blank `flowCode` = shared) |

**Run order:** 1) create flows → 2) shared COMMON group + fields → 3) read group ids → 4) create rules → 5) test. (`psql -f db/data.sql` still works as an all-SQL alternative.)

**Clean slate:** re-importing onto existing data? Use `-Reset` / `RESET=1`, or truncate: `TRUNCATE rule_definition, rule_group, rule_flow, rule_field_catalog RESTART IDENTITY CASCADE;`

---

## 1) Create the 3 flows (API)

`POST /admin/flows` creates the `rule_flow` master (with its `requestClass` + `factType`) and, from the `groups` list, that flow's own groups in order.

```bash
curl -X POST http://localhost:8080/admin/flows -H "Content-Type: application/json" -d '{
  "flowCode": "TRANSFER_TRANSACTION",
  "requestClass": "com.example.droolspoc.dto.TransactionRequest",
  "factType": "com.example.droolspoc.model.ValidationContext",
  "label": "Transfer Transaction",
  "groups": ["TRANSFER", "VELOCITY", "COMPLIANCE"]
}'

curl -X POST http://localhost:8080/admin/flows -H "Content-Type: application/json" -d '{
  "flowCode": "LOAN_APPLICATION",
  "requestClass": "com.example.droolspoc.dto.LoanRequest",
  "factType": "com.example.droolspoc.model.LoanValidationContext",
  "label": "Loan Application",
  "groups": ["LOAN"]
}'

curl -X POST http://localhost:8080/admin/flows -H "Content-Type: application/json" -d '{
  "flowCode": "ACCOUNT_CLOSURE",
  "requestClass": "com.example.droolspoc.dto.AccountCloseRequest",
  "factType": "com.example.droolspoc.model.ClosureValidationContext",
  "label": "Account Closure",
  "groups": ["CLOSURE", "COMPLIANCE"]
}'
```

> Note: `ACCOUNT_CLOSURE` gets its **own** `COMPLIANCE` group — a different row from `TRANSFER_TRANSACTION`'s `COMPLIANCE`. Same name, different group, different rules. That is the whole point of the redesign.

---

## 2) Shared COMMON group + fields (API)

The shared COMMON group (`flow_id NULL`) and the catalog fields now have create endpoints — no SQL.

```bash
# shared COMMON group - runs first for every flow (reloads routing automatically)
curl -X POST http://localhost:8080/admin/groups/shared -H "Content-Type: application/json" \
  -d '{ "groupCode":"COMMON", "orderNo":1 }'

# a SHARED field - omit flowCode entirely
curl -X POST http://localhost:8080/admin/fields -H "Content-Type: application/json" \
  -d '{ "fieldKey":"user.kycVerified","label":"KYC Verified","dataType":"BOOLEAN","drlPath":"user.kycVerified","allowedOps":"==","allowedValues":"Yes (true), No (false)" }'

# a flow-scoped field - set flowCode
curl -X POST http://localhost:8080/admin/fields -H "Content-Type: application/json" \
  -d '{ "flowCode":"TRANSFER_TRANSACTION","fieldKey":"transaction.amount","label":"Transaction Amount","dataType":"NUMBER","drlPath":"transaction.amount","allowedOps":"> < >= <= ==" }'

curl -X POST http://localhost:8080/admin/fields -H "Content-Type: application/json" \
  -d '{ "flowCode":"TRANSFER_TRANSACTION","fieldKey":"transaction.currency","label":"Currency","dataType":"ENUM","drlPath":"transaction.currency","allowedOps":"== !=","allowedValues":"BDT, USD, EUR, GBP" }'
```

That's the pattern — the full set of 12 fields is in `seed.ps1` / `seed.sh` (loan has `loan.amount`, `customer.creditScore`; closure has `account.balance`, `account.status`; transfer also has `product.drRes`, `userLimit.limit`, `transaction.channel`, `transaction.country`, `transaction.dailyTxnCount`).

Fields can be removed too: `DELETE /admin/fields/{id}`.

---

## 3) Read the group ids (API)

Rules attach to a group **by id**, so fetch them. Shared groups (COMMON) are listed first.

```bash
curl http://localhost:8080/admin/flows/TRANSFER_TRANSACTION/groups
```

```json
[
  { "id": 1, "groupCode": "COMMON",     "orderNo": 1, "shared": true,  "active": true },
  { "id": 2, "groupCode": "TRANSFER",   "orderNo": 2, "shared": false, "active": true },
  { "id": 3, "groupCode": "VELOCITY",   "orderNo": 3, "shared": false, "active": true },
  { "id": 4, "groupCode": "COMPLIANCE", "orderNo": 4, "shared": false, "active": true }
]
```

Note the ids you get back (yours may differ). Below, replace `{COMMON_ID}`, `{TRANSFER_ID}`, `{COMPLIANCE_ID}`, etc. with the real values. `GET /admin/flows/ACCOUNT_CLOSURE/groups` gives the **closure** COMPLIANCE id (a different number).

---

## 4) Create rules (API)

### 4a. The two shared COMMON rules (hand-written DRL)

DRL rules go in the shared COMMON group. `sourceType: "DRL"`, `drl` carries the whole rule (note the `\n` escaping).

```bash
curl -X POST http://localhost:8080/admin/groups/{COMMON_ID}/rules -H "Content-Type: application/json" -d '{
  "ruleName": "Block transaction for a blocked user",
  "sourceType": "DRL",
  "createdBy": "farhat",
  "drl": "package com.example.droolspoc.rules;\n\nimport com.example.droolspoc.model.RuleContext;\nimport com.example.droolspoc.context.data.BlockedUsers;\n\nglobal leads.ruleengine.core.context.GlobalContext globalContext;\n\nrule \"Block transaction for a blocked user\"\n    agenda-group \"COMMON\"\n    salience 100\n    when\n        $ctx : RuleContext()\n        eval(globalContext.get(BlockedUsers.class).isBlocked($ctx.getUserId()))\n    then\n        $ctx.setValid(false);\n        $ctx.setPermissionDenied(true);\n        $ctx.setValidationMessage(\"Blocked: user is blocked\");\n        drools.halt();\nend"
}'

curl -X POST http://localhost:8080/admin/groups/{COMMON_ID}/rules -H "Content-Type: application/json" -d '{
  "ruleName": "Block if KYC not verified",
  "sourceType": "DRL",
  "createdBy": "farhat",
  "drl": "package com.example.droolspoc.rules;\n\nimport com.example.droolspoc.model.RuleContext;\n\nrule \"Block if KYC not verified\"\n    agenda-group \"COMMON\"\n    salience 90\n    when\n        $ctx : RuleContext(user != null, user.kycVerified == false)\n    then\n        $ctx.setValid(false);\n        $ctx.setPermissionDenied(true);\n        $ctx.setValidationMessage(\"Blocked: KYC not verified\");\n        drools.halt();\nend"
}'
```

### 4b. A builder rule (optional preview, then save)

Builder rules go in a **flow** group (they need a fact type). `sourceType: "BUILDER"`, `sourceJson` is the builder tree as a JSON **string**. The server generates the DRL from it (fact type from the group's flow, agenda-group from the group's code, a `FLOW.GROUP::name` qualifier for uniqueness).

Preview first — generates + compile-checks, saves nothing:

```bash
curl -X POST http://localhost:8080/admin/rules/preview -H "Content-Type: application/json" -d '{
  "ruleName": "Block over-limit debit-restricted BDT transaction",
  "flow": "TRANSFER_TRANSACTION",
  "agendaGroup": "TRANSFER",
  "salience": 10,
  "conditionTree": { "type":"GROUP","operator":"AND","children":[
    {"type":"CONDITION","field":"transaction.currency","operator":"==","value":"BDT"},
    {"type":"CONDITION","field":"product.drRes","operator":"==","value":1},
    {"type":"CONDITION","field":"transaction.amount","operator":">","valueField":"userLimit.limit"} ] },
  "then": { "decision":"block","message":"Blocked: amount exceeds limit on a debit-restricted BDT account" }
}'
# -> { "drl": "...", "valid": true, "error": null }
```

Save it into the TRANSFER group (`sourceJson` is the same object, as a string):

```bash
curl -X POST http://localhost:8080/admin/groups/{TRANSFER_ID}/rules -H "Content-Type: application/json" -d '{
  "ruleName": "Block over-limit debit-restricted BDT transaction",
  "sourceType": "BUILDER",
  "createdBy": "farhat",
  "sourceJson": "{\"ruleName\":\"Block over-limit debit-restricted BDT transaction\",\"flow\":\"TRANSFER_TRANSACTION\",\"agendaGroup\":\"TRANSFER\",\"salience\":10,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.currency\",\"operator\":\"==\",\"value\":\"BDT\"},{\"type\":\"CONDITION\",\"field\":\"product.drRes\",\"operator\":\"==\",\"value\":1},{\"type\":\"CONDITION\",\"field\":\"transaction.amount\",\"operator\":\">\",\"valueField\":\"userLimit.limit\"}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: amount exceeds limit on a debit-restricted BDT account\"}}"
}'
```

A nested COMPLIANCE example (goes in `{COMPLIANCE_ID}` of the transfer flow):

```bash
curl -X POST http://localhost:8080/admin/groups/{COMPLIANCE_ID}/rules -H "Content-Type: application/json" -d '{
  "ruleName": "Block high-value cross-border non-BDT transfer",
  "sourceType": "BUILDER",
  "createdBy": "farhat",
  "sourceJson": "{\"ruleName\":\"Block high-value cross-border non-BDT transfer\",\"flow\":\"TRANSFER_TRANSACTION\",\"agendaGroup\":\"COMPLIANCE\",\"salience\":20,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.currency\",\"operator\":\"!=\",\"value\":\"BDT\"},{\"type\":\"CONDITION\",\"field\":\"transaction.amount\",\"operator\":\">=\",\"value\":100000},{\"type\":\"CONDITION\",\"field\":\"transaction.country\",\"operator\":\"==\",\"value\":\"OTHER\"}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: cross-border high-value transfer needs manual review\"}}"
}'
```

These two are just examples. **The complete set of all 12 rules (ready to paste) is in `seed-rules.md`** — COMMON ×2, TRANSFER ×2, VELOCITY ×2, COMPLIANCE/transfer ×2, LOAN ×1, CLOSURE ×1, COMPLIANCE/closure ×2. (Or run `seed.ps1` / `seed.sh`, which post all 12 and resolve the group ids for you.)

---

## 5) Test the scenarios (business endpoints)

The POC exposes three validate endpoints. Response is `{ "valid": bool, "permissionDenied": bool, "message": string }`.

### Transfer — `POST /api/transactions/validate`

```bash
# T1 blocked user -> blocked (COMMON)
curl -X POST http://localhost:8080/api/transactions/validate -H "Content-Type: application/json" -d '{ "userId":"USER-002","sourceAccount":"100001","currency":"BDT","amount":500 }'

# T2 KYC not verified -> blocked (COMMON)
curl -X POST http://localhost:8080/api/transactions/validate -H "Content-Type: application/json" -d '{ "userId":"USER-003","sourceAccount":"100001","currency":"BDT","amount":500 }'

# T3 over limit on debit-restricted BDT -> blocked (TRANSFER)
curl -X POST http://localhost:8080/api/transactions/validate -H "Content-Type: application/json" -d '{ "userId":"USER-001","sourceAccount":"100001","currency":"BDT","amount":15000 }'

# T4 within limit -> valid
curl -X POST http://localhost:8080/api/transactions/validate -H "Content-Type: application/json" -d '{ "userId":"USER-001","sourceAccount":"100001","currency":"BDT","amount":5000 }'

# T5 cross-border high-value -> blocked (COMPLIANCE)
curl -X POST http://localhost:8080/api/transactions/validate -H "Content-Type: application/json" -d '{ "userId":"USER-001","sourceAccount":"200001","currency":"USD","amount":150000,"country":"OTHER" }'
```

### Loan — `POST /api/loans/validate`

```bash
# L1 credit score < 600 -> blocked (LOAN)
curl -X POST http://localhost:8080/api/loans/validate -H "Content-Type: application/json" -d '{ "userId":"USER-004","amount":50000,"tenureMonths":12,"purpose":"Personal" }'

# L2 good score -> valid
curl -X POST http://localhost:8080/api/loans/validate -H "Content-Type: application/json" -d '{ "userId":"USER-001","amount":50000,"tenureMonths":12,"purpose":"Personal" }'

# L3 blocked user -> blocked (COMMON reused in the loan flow)
curl -X POST http://localhost:8080/api/loans/validate -H "Content-Type: application/json" -d '{ "userId":"USER-002","amount":50000,"tenureMonths":12,"purpose":"Personal" }'
```

### Account closure — `POST /api/account-closures/validate`

```bash
# C1 outstanding balance -> blocked (CLOSURE)
curl -X POST http://localhost:8080/api/account-closures/validate -H "Content-Type: application/json" -d '{ "userId":"USER-001","accountNo":"ACC-901","reason":"Customer request" }'

# C2 zero balance -> valid
curl -X POST http://localhost:8080/api/account-closures/validate -H "Content-Type: application/json" -d '{ "userId":"USER-001","accountNo":"ACC-900","reason":"Customer request" }'
```

---

## 6) Demonstrate active / inactive (the on/off toggle)

Inactive = kept, but skipped when a business request hits. The row and its rules stay.

```bash
# turn the transfer COMPLIANCE group OFF
curl -X PUT http://localhost:8080/admin/groups/{COMPLIANCE_ID}/active -H "Content-Type: application/json" -d '{ "active": false }'

# re-run T5 -> now VALID (COMPLIANCE no longer evaluated)
curl -X POST http://localhost:8080/api/transactions/validate -H "Content-Type: application/json" -d '{ "userId":"USER-001","sourceAccount":"200001","currency":"USD","amount":150000,"country":"OTHER" }'

# turn it back ON -> T5 blocks again
curl -X PUT http://localhost:8080/admin/groups/{COMPLIANCE_ID}/active -H "Content-Type: application/json" -d '{ "active": true }'
```

Same pattern per level:

```bash
# deactivate ONE rule (needs the rule id from GET /admin/groups/{id}/rules)
curl -X PUT http://localhost:8080/admin/rules/{RULE_ID}/active -H "Content-Type: application/json" -d '{ "active": false }'

# deactivate a WHOLE flow -> its requests run no rules at all (pass through)
curl -X PUT http://localhost:8080/admin/flows/LOAN_APPLICATION/active -H "Content-Type: application/json" -d '{ "active": false }'
```

To remove something for good (not just disable), use the DELETE endpoints instead — see the API reference PDF.

---

## Troubleshooting

- **`No flow mapped for request class '...'`** — the request class isn't registered. Check `requestClass` on the flow (`GET /admin/flows`).
- **Builder save 400 `No fact type` / `sourceJson required`** — the group's flow has no `factType`, or you posted a BUILDER rule into the shared COMMON group (COMMON takes DRL only).
- **Builder rule fires wrong / unknown field** — the field isn't in `rule_field_catalog` for that flow; add it (step 2) and re-save.
- **Rule saved but nothing changes** — reload isn't needed (every write reloads), but if you edited the DB directly, call `POST /admin/flows/reload`.
- **409 on delete** — the flow/group still has children; delete them first or add `?force=true`.

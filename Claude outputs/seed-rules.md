# Rule Engine POC - all 12 rules (curl)

The complete rule set for the 3-flow scenario, to seed **by hand** over the Admin API. (The
`seed.ps1` / `seed.sh` scripts do all of this for you and resolve the ids automatically - use
those unless you specifically want to run each call.)

## First: get the real group ids

Group ids are assigned by the DB sequence, not fixed 1-7. Fetch them and substitute the
placeholders below:

```bash
curl http://localhost:8080/admin/flows/TRANSFER_TRANSACTION/groups   # -> COMMON, TRANSFER, VELOCITY, COMPLIANCE
curl http://localhost:8080/admin/flows/LOAN_APPLICATION/groups       # -> COMMON, LOAN
curl http://localhost:8080/admin/flows/ACCOUNT_CLOSURE/groups        # -> COMMON, CLOSURE, COMPLIANCE
```

| Placeholder | Group | Flow |
|---|---|---|
| `{COMMON_ID}` | COMMON (shared) | all flows |
| `{TRANSFER_ID}` | TRANSFER | TRANSFER_TRANSACTION |
| `{VELOCITY_ID}` | VELOCITY | TRANSFER_TRANSACTION |
| `{TR_COMPLIANCE_ID}` | COMPLIANCE | TRANSFER_TRANSACTION |
| `{LOAN_ID}` | LOAN | LOAN_APPLICATION |
| `{CLOSURE_ID}` | CLOSURE | ACCOUNT_CLOSURE |
| `{CL_COMPLIANCE_ID}` | COMPLIANCE | ACCOUNT_CLOSURE (a DIFFERENT group from the transfer one) |

> The two COMPLIANCE groups are different rows with different ids - that is the point of the design.
> On Windows use `curl.exe`.

---

## COMMON (shared) - 2 rules, hand-written DRL

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

## TRANSFER - 2 rules

```bash
curl -X POST http://localhost:8080/admin/groups/{TRANSFER_ID}/rules -H "Content-Type: application/json" -d '{
  "ruleName": "Block over-limit debit-restricted BDT transaction",
  "sourceType": "BUILDER",
  "createdBy": "farhat",
  "sourceJson": "{\"ruleName\":\"Block over-limit debit-restricted BDT transaction\",\"flow\":\"TRANSFER_TRANSACTION\",\"agendaGroup\":\"TRANSFER\",\"salience\":10,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.currency\",\"operator\":\"==\",\"value\":\"BDT\"},{\"type\":\"CONDITION\",\"field\":\"product.drRes\",\"operator\":\"==\",\"value\":1},{\"type\":\"CONDITION\",\"field\":\"transaction.amount\",\"operator\":\">\",\"valueField\":\"userLimit.limit\"}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: amount exceeds limit on a debit-restricted BDT account\"}}"
}'

curl -X POST http://localhost:8080/admin/groups/{TRANSFER_ID}/rules -H "Content-Type: application/json" -d '{
  "ruleName": "High-value or cross-border transfer",
  "sourceType": "BUILDER",
  "createdBy": "farhat",
  "sourceJson": "{\"ruleName\":\"High-value or cross-border transfer\",\"flow\":\"TRANSFER_TRANSACTION\",\"agendaGroup\":\"TRANSFER\",\"salience\":20,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"OR\",\"children\":[{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.channel\",\"operator\":\"==\",\"value\":\"AGENT\"},{\"type\":\"CONDITION\",\"field\":\"transaction.amount\",\"operator\":\">\",\"value\":50000}]},{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.country\",\"operator\":\"==\",\"value\":\"OTHER\"},{\"type\":\"CONDITION\",\"field\":\"transaction.currency\",\"operator\":\"!=\",\"value\":\"BDT\"}]}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: high-value agent transfer, or cross-border non-BDT transfer\"}}"
}'
```

## VELOCITY - 2 rules

```bash
curl -X POST http://localhost:8080/admin/groups/{VELOCITY_ID}/rules -H "Content-Type: application/json" -d '{
  "ruleName": "Block rapid high-value agent transfers",
  "sourceType": "BUILDER",
  "createdBy": "farhat",
  "sourceJson": "{\"ruleName\":\"Block rapid high-value agent transfers\",\"flow\":\"TRANSFER_TRANSACTION\",\"agendaGroup\":\"VELOCITY\",\"salience\":20,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.channel\",\"operator\":\"==\",\"value\":\"AGENT\"},{\"type\":\"CONDITION\",\"field\":\"transaction.amount\",\"operator\":\">\",\"value\":50000},{\"type\":\"CONDITION\",\"field\":\"transaction.dailyTxnCount\",\"operator\":\">=\",\"value\":5}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: too many high-value agent transfers today\"}}"
}'

curl -X POST http://localhost:8080/admin/groups/{VELOCITY_ID}/rules -H "Content-Type: application/json" -d '{
  "ruleName": "Layered channel & geo screen",
  "sourceType": "BUILDER",
  "createdBy": "farhat",
  "sourceJson": "{\"ruleName\":\"Layered channel & geo screen\",\"flow\":\"TRANSFER_TRANSACTION\",\"agendaGroup\":\"VELOCITY\",\"salience\":20,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.amount\",\"operator\":\">\",\"value\":10000},{\"type\":\"GROUP\",\"operator\":\"OR\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.channel\",\"operator\":\"==\",\"value\":\"ONLINE\"},{\"type\":\"CONDITION\",\"field\":\"transaction.channel\",\"operator\":\"==\",\"value\":\"AGENT\"}]}]},{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"GROUP\",\"operator\":\"OR\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.country\",\"operator\":\"==\",\"value\":\"US\"},{\"type\":\"CONDITION\",\"field\":\"transaction.country\",\"operator\":\"==\",\"value\":\"OTHER\"}]},{\"type\":\"CONDITION\",\"field\":\"transaction.currency\",\"operator\":\"!=\",\"value\":\"BDT\"}]}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: layered channel & geo screening\"}}"
}'
```

## COMPLIANCE (transfer) - 2 rules

```bash
curl -X POST http://localhost:8080/admin/groups/{TR_COMPLIANCE_ID}/rules -H "Content-Type: application/json" -d '{
  "ruleName": "Block high-value cross-border non-BDT transfer",
  "sourceType": "BUILDER",
  "createdBy": "farhat",
  "sourceJson": "{\"ruleName\":\"Block high-value cross-border non-BDT transfer\",\"flow\":\"TRANSFER_TRANSACTION\",\"agendaGroup\":\"COMPLIANCE\",\"salience\":20,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.currency\",\"operator\":\"!=\",\"value\":\"BDT\"},{\"type\":\"CONDITION\",\"field\":\"transaction.amount\",\"operator\":\">=\",\"value\":100000},{\"type\":\"CONDITION\",\"field\":\"transaction.country\",\"operator\":\"==\",\"value\":\"OTHER\"}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: cross-border high-value transfer needs manual review\"}}"
}'

curl -X POST http://localhost:8080/admin/groups/{TR_COMPLIANCE_ID}/rules -H "Content-Type: application/json" -d '{
  "ruleName": "Deep-nested compliance check",
  "sourceType": "BUILDER",
  "createdBy": "farhat",
  "sourceJson": "{\"ruleName\":\"Deep-nested compliance check\",\"flow\":\"TRANSFER_TRANSACTION\",\"agendaGroup\":\"COMPLIANCE\",\"salience\":20,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"GROUP\",\"operator\":\"OR\",\"children\":[{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"GROUP\",\"operator\":\"OR\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.amount\",\"operator\":\">\",\"value\":5000},{\"type\":\"CONDITION\",\"field\":\"transaction.currency\",\"operator\":\"==\",\"value\":\"USD\"}]},{\"type\":\"CONDITION\",\"field\":\"product.drRes\",\"operator\":\"==\",\"value\":1}]},{\"type\":\"CONDITION\",\"field\":\"transaction.dailyTxnCount\",\"operator\":\">=\",\"value\":10}]},{\"type\":\"GROUP\",\"operator\":\"OR\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"userLimit.limit\",\"operator\":\">=\",\"value\":100000},{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.amount\",\"operator\":\">\",\"valueField\":\"userLimit.limit\"},{\"type\":\"CONDITION\",\"field\":\"transaction.dailyTxnCount\",\"operator\":\">=\",\"value\":5}]}]}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: deep-nested compliance condition met\"}}"
}'
```

## LOAN - 1 rule

```bash
curl -X POST http://localhost:8080/admin/groups/{LOAN_ID}/rules -H "Content-Type: application/json" -d '{
  "ruleName": "Block loan below credit-score threshold",
  "sourceType": "BUILDER",
  "createdBy": "farhat",
  "sourceJson": "{\"ruleName\":\"Block loan below credit-score threshold\",\"flow\":\"LOAN_APPLICATION\",\"agendaGroup\":\"LOAN\",\"salience\":10,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"customer.creditScore\",\"operator\":\"<\",\"value\":600}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: credit score below 600\"}}"
}'
```

## CLOSURE - 1 rule

```bash
curl -X POST http://localhost:8080/admin/groups/{CLOSURE_ID}/rules -H "Content-Type: application/json" -d '{
  "ruleName": "Block closure with outstanding balance",
  "sourceType": "BUILDER",
  "createdBy": "farhat",
  "sourceJson": "{\"ruleName\":\"Block closure with outstanding balance\",\"flow\":\"ACCOUNT_CLOSURE\",\"agendaGroup\":\"CLOSURE\",\"salience\":10,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"account.balance\",\"operator\":\">\",\"value\":0}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: account has an outstanding balance\"}}"
}'
```

## COMPLIANCE (closure) - 2 rules

```bash
curl -X POST http://localhost:8080/admin/groups/{CL_COMPLIANCE_ID}/rules -H "Content-Type: application/json" -d '{
  "ruleName": "Closure compliance - dormant review",
  "sourceType": "BUILDER",
  "createdBy": "farhat",
  "sourceJson": "{\"ruleName\":\"Closure compliance - dormant review\",\"flow\":\"ACCOUNT_CLOSURE\",\"agendaGroup\":\"COMPLIANCE\",\"salience\":20,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"account.status\",\"operator\":\"==\",\"value\":\"DORMANT\"}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: dormant account closure needs compliance review\"}}"
}'

curl -X POST http://localhost:8080/admin/groups/{CL_COMPLIANCE_ID}/rules -H "Content-Type: application/json" -d '{
  "ruleName": "Closure compliance - frozen block",
  "sourceType": "BUILDER",
  "createdBy": "farhat",
  "sourceJson": "{\"ruleName\":\"Closure compliance - frozen block\",\"flow\":\"ACCOUNT_CLOSURE\",\"agendaGroup\":\"COMPLIANCE\",\"salience\":20,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"account.status\",\"operator\":\"==\",\"value\":\"FROZEN\"}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: a frozen account cannot be closed\"}}"
}'
```

---

## Verify

```bash
curl http://localhost:8080/admin/groups/{TR_COMPLIANCE_ID}/rules   # 2 rules
curl http://localhost:8080/admin/groups/{CL_COMPLIANCE_ID}/rules   # 2 DIFFERENT rules, same group name
```

That's all 12 (2 COMMON + 4 transfer + 1 loan + 3 closure). Now run the scenarios in
`seed-via-api.md` section 5.

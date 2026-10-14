#!/usr/bin/env bash
# Rule Engine POC - full seed via the Admin API (no SQL needed). Requires: curl, jq.
#   ./seed.sh            # seed
#   RESET=1 ./seed.sh    # delete existing, then seed
#   TEST=1 ./seed.sh     # seed, then run scenarios
#   BASE=http://host:8080 ./seed.sh
set -euo pipefail
BASE="${BASE:-http://localhost:8080}"
post(){ curl -sS -X POST "$BASE$1" -H "Content-Type: application/json" -d "$2" >/dev/null; }
getj(){ curl -sS "$BASE$1"; }
del(){ curl -sS -X DELETE "$BASE$1" >/dev/null 2>&1 || true; }

if [ "${RESET:-0}" = "1" ]; then
  echo "Reset (delete existing)..."
  flows="$(getj /admin/flows || echo '[]')"
  fc1="$(echo "$flows" | jq -r '.[0].flowCode // empty')"
  if [ -n "$fc1" ]; then
    getj "/admin/flows/$fc1/fields" | jq -r '.[] | select(.flow==null) | .id' | while read -r id; do del "/admin/fields/$id"; done
  fi
  echo "$flows" | jq -r '.[].groups[] | select(.shared) | .id' | sort -u | while read -r id; do del "/admin/groups/$id?force=true"; done
  echo "$flows" | jq -r '.[].flowCode' | while read -r fc; do del "/admin/flows/$fc?force=true"; done
fi

echo "1/5 Flows..."

post /admin/flows '{"flowCode":"TRANSFER_TRANSACTION","requestClass":"com.example.droolspoc.dto.TransactionRequest","factType":"com.example.droolspoc.model.ValidationContext","label":"Transfer Transaction","groups":["TRANSFER","VELOCITY","COMPLIANCE"]}'
post /admin/flows '{"flowCode":"LOAN_APPLICATION","requestClass":"com.example.droolspoc.dto.LoanRequest","factType":"com.example.droolspoc.model.LoanValidationContext","label":"Loan Application","groups":["LOAN"]}'
post /admin/flows '{"flowCode":"ACCOUNT_CLOSURE","requestClass":"com.example.droolspoc.dto.AccountCloseRequest","factType":"com.example.droolspoc.model.ClosureValidationContext","label":"Account Closure","groups":["CLOSURE","COMPLIANCE"]}'

echo "2/5 Shared COMMON group..."
post /admin/groups/shared '{"groupCode":"COMMON","orderNo":1}'

echo "3/5 Fields..."
post /admin/fields '{"fieldKey":"user.kycVerified","label":"KYC Verified","dataType":"BOOLEAN","drlPath":"user.kycVerified","allowedOps":"==","allowedValues":"Yes (true), No (false)"}'
post /admin/fields '{"fieldKey":"transaction.amount","label":"Transaction Amount","dataType":"NUMBER","drlPath":"transaction.amount","allowedOps":"> < >= <= ==","flowCode":"TRANSFER_TRANSACTION"}'
post /admin/fields '{"fieldKey":"transaction.currency","label":"Currency","dataType":"ENUM","drlPath":"transaction.currency","allowedOps":"== !=","flowCode":"TRANSFER_TRANSACTION","allowedValues":"BDT, USD, EUR, GBP"}'
post /admin/fields '{"fieldKey":"product.drRes","label":"Debit Restricted","dataType":"BOOLEAN","drlPath":"product.drRes","allowedOps":"==","flowCode":"TRANSFER_TRANSACTION","allowedValues":"Yes (1), No (0)"}'
post /admin/fields '{"fieldKey":"userLimit.limit","label":"User Limit","dataType":"NUMBER","drlPath":"userLimit.limit","allowedOps":"> < >= <=","flowCode":"TRANSFER_TRANSACTION"}'
post /admin/fields '{"fieldKey":"transaction.channel","label":"Channel","dataType":"ENUM","drlPath":"transaction.channel","allowedOps":"== !=","flowCode":"TRANSFER_TRANSACTION","allowedValues":"ATM, POS, ONLINE, BRANCH, AGENT"}'
post /admin/fields '{"fieldKey":"transaction.country","label":"Destination Country","dataType":"ENUM","drlPath":"transaction.country","allowedOps":"== !=","flowCode":"TRANSFER_TRANSACTION","allowedValues":"BD, US, UK, AE, SG, OTHER"}'
post /admin/fields '{"fieldKey":"transaction.dailyTxnCount","label":"Daily Txn Count","dataType":"NUMBER","drlPath":"transaction.dailyTxnCount","allowedOps":"> < >= <= ==","flowCode":"TRANSFER_TRANSACTION"}'
post /admin/fields '{"fieldKey":"loan.amount","label":"Loan Amount","dataType":"NUMBER","drlPath":"loan.amount","allowedOps":"> < >= <=","flowCode":"LOAN_APPLICATION"}'
post /admin/fields '{"fieldKey":"customer.creditScore","label":"Credit Score","dataType":"NUMBER","drlPath":"customer.creditScore","allowedOps":"> < >= <=","flowCode":"LOAN_APPLICATION"}'
post /admin/fields '{"fieldKey":"account.balance","label":"Account Balance","dataType":"NUMBER","drlPath":"account.balance","allowedOps":"> < >= <= == !=","flowCode":"ACCOUNT_CLOSURE"}'
post /admin/fields '{"fieldKey":"account.status","label":"Account Status","dataType":"ENUM","drlPath":"account.status","allowedOps":"== !=","flowCode":"ACCOUNT_CLOSURE","allowedValues":"ACTIVE, DORMANT, FROZEN, CLOSED"}'

echo "4/5 Resolving group ids..."
TG="$(getj /admin/flows/TRANSFER_TRANSACTION/groups)"
common=$(echo "$TG" | jq -r '.[] | select(.shared) | .id' | head -1)
transfer=$(echo "$TG" | jq -r '.[] | select(.groupCode=="TRANSFER") | .id')
velocity=$(echo "$TG" | jq -r '.[] | select(.groupCode=="VELOCITY") | .id')
complianceT=$(echo "$TG" | jq -r '.[] | select(.groupCode=="COMPLIANCE") | .id')
loan=$(getj /admin/flows/LOAN_APPLICATION/groups | jq -r '.[] | select(.groupCode=="LOAN") | .id')
CG="$(getj /admin/flows/ACCOUNT_CLOSURE/groups)"
closure=$(echo "$CG" | jq -r '.[] | select(.groupCode=="CLOSURE") | .id')
complianceC=$(echo "$CG" | jq -r '.[] | select(.groupCode=="COMPLIANCE") | .id')

echo "5/5 Rules..."
read -r -d '' DRL1 <<'DRLEOF' || true
package com.example.droolspoc.rules;

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
end
DRLEOF
post "/admin/groups/$common/rules" "$(jq -n --arg n "Block transaction for a blocked user" --arg d "$DRL1" '{ruleName:$n,sourceType:"DRL",createdBy:"farhat",drl:$d}')"
read -r -d '' DRL2 <<'DRLEOF' || true
package com.example.droolspoc.rules;

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
end
DRLEOF
post "/admin/groups/$common/rules" "$(jq -n --arg n "Block if KYC not verified" --arg d "$DRL2" '{ruleName:$n,sourceType:"DRL",createdBy:"farhat",drl:$d}')"
post "/admin/groups/$transfer/rules" "$(jq -n --arg n "Block over-limit debit-restricted BDT transaction" --arg j "{\"ruleName\":\"Block over-limit debit-restricted BDT transaction\",\"flow\":\"TRANSFER_TRANSACTION\",\"agendaGroup\":\"TRANSFER\",\"salience\":10,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.currency\",\"operator\":\"==\",\"value\":\"BDT\"},{\"type\":\"CONDITION\",\"field\":\"product.drRes\",\"operator\":\"==\",\"value\":1},{\"type\":\"CONDITION\",\"field\":\"transaction.amount\",\"operator\":\">\",\"valueField\":\"userLimit.limit\"}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: amount exceeds limit on a debit-restricted BDT account\"}}" '{ruleName:$n,sourceType:"BUILDER",createdBy:"farhat",sourceJson:$j}')"
post "/admin/groups/$transfer/rules" "$(jq -n --arg n "High-value or cross-border transfer" --arg j "{\"ruleName\":\"High-value or cross-border transfer\",\"flow\":\"TRANSFER_TRANSACTION\",\"agendaGroup\":\"TRANSFER\",\"salience\":20,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"OR\",\"children\":[{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.channel\",\"operator\":\"==\",\"value\":\"AGENT\"},{\"type\":\"CONDITION\",\"field\":\"transaction.amount\",\"operator\":\">\",\"value\":50000}]},{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.country\",\"operator\":\"==\",\"value\":\"OTHER\"},{\"type\":\"CONDITION\",\"field\":\"transaction.currency\",\"operator\":\"!=\",\"value\":\"BDT\"}]}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: high-value agent transfer, or cross-border non-BDT transfer\"}}" '{ruleName:$n,sourceType:"BUILDER",createdBy:"farhat",sourceJson:$j}')"
post "/admin/groups/$velocity/rules" "$(jq -n --arg n "Block rapid high-value agent transfers" --arg j "{\"ruleName\":\"Block rapid high-value agent transfers\",\"flow\":\"TRANSFER_TRANSACTION\",\"agendaGroup\":\"VELOCITY\",\"salience\":20,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.channel\",\"operator\":\"==\",\"value\":\"AGENT\"},{\"type\":\"CONDITION\",\"field\":\"transaction.amount\",\"operator\":\">\",\"value\":50000},{\"type\":\"CONDITION\",\"field\":\"transaction.dailyTxnCount\",\"operator\":\">=\",\"value\":5}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: too many high-value agent transfers today\"}}" '{ruleName:$n,sourceType:"BUILDER",createdBy:"farhat",sourceJson:$j}')"
post "/admin/groups/$velocity/rules" "$(jq -n --arg n "Layered channel & geo screen" --arg j "{\"ruleName\":\"Layered channel & geo screen\",\"flow\":\"TRANSFER_TRANSACTION\",\"agendaGroup\":\"VELOCITY\",\"salience\":20,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.amount\",\"operator\":\">\",\"value\":10000},{\"type\":\"GROUP\",\"operator\":\"OR\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.channel\",\"operator\":\"==\",\"value\":\"ONLINE\"},{\"type\":\"CONDITION\",\"field\":\"transaction.channel\",\"operator\":\"==\",\"value\":\"AGENT\"}]}]},{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"GROUP\",\"operator\":\"OR\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.country\",\"operator\":\"==\",\"value\":\"US\"},{\"type\":\"CONDITION\",\"field\":\"transaction.country\",\"operator\":\"==\",\"value\":\"OTHER\"}]},{\"type\":\"CONDITION\",\"field\":\"transaction.currency\",\"operator\":\"!=\",\"value\":\"BDT\"}]}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: layered channel & geo screening\"}}" '{ruleName:$n,sourceType:"BUILDER",createdBy:"farhat",sourceJson:$j}')"
post "/admin/groups/$complianceT/rules" "$(jq -n --arg n "Block high-value cross-border non-BDT transfer" --arg j "{\"ruleName\":\"Block high-value cross-border non-BDT transfer\",\"flow\":\"TRANSFER_TRANSACTION\",\"agendaGroup\":\"COMPLIANCE\",\"salience\":20,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.currency\",\"operator\":\"!=\",\"value\":\"BDT\"},{\"type\":\"CONDITION\",\"field\":\"transaction.amount\",\"operator\":\">=\",\"value\":100000},{\"type\":\"CONDITION\",\"field\":\"transaction.country\",\"operator\":\"==\",\"value\":\"OTHER\"}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: cross-border high-value transfer needs manual review\"}}" '{ruleName:$n,sourceType:"BUILDER",createdBy:"farhat",sourceJson:$j}')"
post "/admin/groups/$complianceT/rules" "$(jq -n --arg n "Deep-nested compliance check" --arg j "{\"ruleName\":\"Deep-nested compliance check\",\"flow\":\"TRANSFER_TRANSACTION\",\"agendaGroup\":\"COMPLIANCE\",\"salience\":20,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"GROUP\",\"operator\":\"OR\",\"children\":[{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"GROUP\",\"operator\":\"OR\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.amount\",\"operator\":\">\",\"value\":5000},{\"type\":\"CONDITION\",\"field\":\"transaction.currency\",\"operator\":\"==\",\"value\":\"USD\"}]},{\"type\":\"CONDITION\",\"field\":\"product.drRes\",\"operator\":\"==\",\"value\":1}]},{\"type\":\"CONDITION\",\"field\":\"transaction.dailyTxnCount\",\"operator\":\">=\",\"value\":10}]},{\"type\":\"GROUP\",\"operator\":\"OR\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"userLimit.limit\",\"operator\":\">=\",\"value\":100000},{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"transaction.amount\",\"operator\":\">\",\"valueField\":\"userLimit.limit\"},{\"type\":\"CONDITION\",\"field\":\"transaction.dailyTxnCount\",\"operator\":\">=\",\"value\":5}]}]}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: deep-nested compliance condition met\"}}" '{ruleName:$n,sourceType:"BUILDER",createdBy:"farhat",sourceJson:$j}')"
post "/admin/groups/$loan/rules" "$(jq -n --arg n "Block loan below credit-score threshold" --arg j "{\"ruleName\":\"Block loan below credit-score threshold\",\"flow\":\"LOAN_APPLICATION\",\"agendaGroup\":\"LOAN\",\"salience\":10,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"customer.creditScore\",\"operator\":\"<\",\"value\":600}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: credit score below 600\"}}" '{ruleName:$n,sourceType:"BUILDER",createdBy:"farhat",sourceJson:$j}')"
post "/admin/groups/$closure/rules" "$(jq -n --arg n "Block closure with outstanding balance" --arg j "{\"ruleName\":\"Block closure with outstanding balance\",\"flow\":\"ACCOUNT_CLOSURE\",\"agendaGroup\":\"CLOSURE\",\"salience\":10,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"account.balance\",\"operator\":\">\",\"value\":0}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: account has an outstanding balance\"}}" '{ruleName:$n,sourceType:"BUILDER",createdBy:"farhat",sourceJson:$j}')"
post "/admin/groups/$complianceC/rules" "$(jq -n --arg n "Closure compliance - dormant review" --arg j "{\"ruleName\":\"Closure compliance - dormant review\",\"flow\":\"ACCOUNT_CLOSURE\",\"agendaGroup\":\"COMPLIANCE\",\"salience\":20,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"account.status\",\"operator\":\"==\",\"value\":\"DORMANT\"}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: dormant account closure needs compliance review\"}}" '{ruleName:$n,sourceType:"BUILDER",createdBy:"farhat",sourceJson:$j}')"
post "/admin/groups/$complianceC/rules" "$(jq -n --arg n "Closure compliance - frozen block" --arg j "{\"ruleName\":\"Closure compliance - frozen block\",\"flow\":\"ACCOUNT_CLOSURE\",\"agendaGroup\":\"COMPLIANCE\",\"salience\":20,\"conditionTree\":{\"type\":\"GROUP\",\"operator\":\"AND\",\"children\":[{\"type\":\"CONDITION\",\"field\":\"account.status\",\"operator\":\"==\",\"value\":\"FROZEN\"}]},\"then\":{\"decision\":\"block\",\"message\":\"Blocked: a frozen account cannot be closed\"}}" '{ruleName:$n,sourceType:"BUILDER",createdBy:"farhat",sourceJson:$j}')"

echo "Seed complete."

if [ "${TEST:-0}" = "1" ]; then
  echo; echo "Running scenarios..."
  v(){ r=$(curl -sS -X POST "$BASE$2" -H "Content-Type: application/json" -d "$3"); \
       printf '  %-42s %s  %s\n' "$1" "$(echo "$r"|jq -r 'if .valid then "VALID  " else "BLOCKED" end')" "$(echo "$r"|jq -r '.message')"; }
  v "T1 blocked user"          /api/transactions/validate '{"userId":"USER-002","transactionMode":"ONLINE","debitCredit":"D","sourceAccount":"100001","currency":"BDT","amount":500}'
  v "T3 over limit (dr-rest.)" /api/transactions/validate '{"userId":"USER-001","transactionMode":"ONLINE","debitCredit":"D","sourceAccount":"100001","currency":"BDT","amount":15000}'
  v "T4 within limit"          /api/transactions/validate '{"userId":"USER-001","transactionMode":"ONLINE","debitCredit":"D","sourceAccount":"100001","currency":"BDT","amount":5000}'
  v "T5 cross-border high-val" /api/transactions/validate '{"userId":"USER-001","transactionMode":"ONLINE","debitCredit":"D","sourceAccount":"200001","currency":"USD","amount":150000,"country":"OTHER"}'
  v "L1 credit score < 600"    /api/loans/validate        '{"userId":"USER-004","amount":50000,"tenureMonths":12,"purpose":"Personal"}'
  v "C1 outstanding balance"   /api/account-closures/validate '{"userId":"USER-001","accountNo":"ACC-901","reason":"x"}'
fi

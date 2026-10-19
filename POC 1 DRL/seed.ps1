<#
  Rule Engine POC - full seed via the Admin API (no SQL, no jq). THIS is the Windows script.
  (Do NOT double-click it - that just opens Notepad. Run it in PowerShell.)

  HOW TO RUN (PowerShell, in the folder that contains this file):
    powershell -ExecutionPolicy Bypass -File .\seed.ps1 -Test
  or, from an open PowerShell prompt:
    Set-ExecutionPolicy -Scope Process Bypass -Force
    .\seed.ps1 -Test

  Switches:
    (none)      seed everything (safe to re-run - existing rows are skipped/updated)
    -Reset      delete existing flows/shared groups/shared fields first, then seed
    -Test       after seeding, run the validation scenarios and print the result
    -Base URL   point at another host (default http://localhost:8080)

  Prereq: the POC app is running, and business-data.sql is loaded (the re_* tables).
  Linux/macOS/Git Bash users: use seed.sh instead (needs curl + jq).
#>
param([string]$Base="http://localhost:8080",[switch]$Reset,[switch]$Test)
$ErrorActionPreference="Stop"
function PostJ($p,$o){ Invoke-RestMethod -Method Post -Uri "$Base$p" -ContentType "application/json" -Body ($o|ConvertTo-Json -Depth 30) }
function PostTry($p,$o){ try { PostJ $p $o | Out-Null } catch { Write-Host "   (already there, skipped)" -ForegroundColor DarkGray } }
function GetJ($p){ Invoke-RestMethod -Method Get -Uri "$Base$p" }
function DelJ($p){ try { Invoke-RestMethod -Method Delete -Uri "$Base$p" | Out-Null } catch {} }

if($Reset){
  Write-Host "Reset (delete existing)..." -ForegroundColor Yellow
  $flows=@(); try { $flows=@(GetJ "/admin/flows") } catch {}
  if($flows.Count -gt 0){
    foreach($fld in (GetJ "/admin/flows/$($flows[0].flowCode)/fields")){ if($null -eq $fld.flow){ DelJ "/admin/fields/$($fld.id)" } }
  }
  $shared=@(); foreach($f in $flows){ foreach($g in $f.groups){ if($g.shared){ $shared+=$g.id } } }
  foreach($f in $flows){ DelJ "/admin/flows/$($f.flowCode)?force=true" }
  foreach($id in ($shared|Select-Object -Unique)){ DelJ "/admin/groups/$id?force=true" }
}

Write-Host "1/5 Flows..." -ForegroundColor Cyan
PostJ "/admin/flows" @{ flowCode="TRANSFER_TRANSACTION"; requestClass="com.example.droolspoc.dto.TransactionRequest"; factType="com.example.droolspoc.model.ValidationContext"; label="Transfer Transaction"; groups=@("TRANSFER", "VELOCITY", "COMPLIANCE") } | Out-Null
PostJ "/admin/flows" @{ flowCode="LOAN_APPLICATION"; requestClass="com.example.droolspoc.dto.LoanRequest"; factType="com.example.droolspoc.model.LoanValidationContext"; label="Loan Application"; groups=@("LOAN") } | Out-Null
PostJ "/admin/flows" @{ flowCode="ACCOUNT_CLOSURE"; requestClass="com.example.droolspoc.dto.AccountCloseRequest"; factType="com.example.droolspoc.model.ClosureValidationContext"; label="Account Closure"; groups=@("CLOSURE", "COMPLIANCE") } | Out-Null

Write-Host "2/5 Shared COMMON group..." -ForegroundColor Cyan
PostTry "/admin/groups/shared" @{ groupCode="COMMON"; orderNo=1 }

Write-Host "3/5 Fields..." -ForegroundColor Cyan
PostTry "/admin/fields" @{ fieldKey="user.kycVerified"; label="KYC Verified"; dataType="BOOLEAN"; drlPath="user.kycVerified"; allowedOps="=="; allowedValues="Yes (true), No (false)" }
PostTry "/admin/fields" @{ flowCode="TRANSFER_TRANSACTION"; fieldKey="transaction.amount"; label="Transaction Amount"; dataType="NUMBER"; drlPath="transaction.amount"; allowedOps="> < >= <= =="; allowedValues=$null }
PostTry "/admin/fields" @{ flowCode="TRANSFER_TRANSACTION"; fieldKey="transaction.currency"; label="Currency"; dataType="ENUM"; drlPath="transaction.currency"; allowedOps="== !="; allowedValues="BDT, USD, EUR, GBP" }
PostTry "/admin/fields" @{ flowCode="TRANSFER_TRANSACTION"; fieldKey="product.drRes"; label="Debit Restricted"; dataType="BOOLEAN"; drlPath="product.drRes"; allowedOps="=="; allowedValues="Yes (1), No (0)" }
PostTry "/admin/fields" @{ flowCode="TRANSFER_TRANSACTION"; fieldKey="userLimit.limit"; label="User Limit"; dataType="NUMBER"; drlPath="userLimit.limit"; allowedOps="> < >= <="; allowedValues=$null }
PostTry "/admin/fields" @{ flowCode="TRANSFER_TRANSACTION"; fieldKey="transaction.channel"; label="Channel"; dataType="ENUM"; drlPath="transaction.channel"; allowedOps="== !="; allowedValues="ATM, POS, ONLINE, BRANCH, AGENT" }
PostTry "/admin/fields" @{ flowCode="TRANSFER_TRANSACTION"; fieldKey="transaction.country"; label="Destination Country"; dataType="ENUM"; drlPath="transaction.country"; allowedOps="== !="; allowedValues="BD, US, UK, AE, SG, OTHER" }
PostTry "/admin/fields" @{ flowCode="TRANSFER_TRANSACTION"; fieldKey="transaction.dailyTxnCount"; label="Daily Txn Count"; dataType="NUMBER"; drlPath="transaction.dailyTxnCount"; allowedOps="> < >= <= =="; allowedValues=$null }
PostTry "/admin/fields" @{ flowCode="LOAN_APPLICATION"; fieldKey="loan.amount"; label="Loan Amount"; dataType="NUMBER"; drlPath="loan.amount"; allowedOps="> < >= <="; allowedValues=$null }
PostTry "/admin/fields" @{ flowCode="LOAN_APPLICATION"; fieldKey="customer.creditScore"; label="Credit Score"; dataType="NUMBER"; drlPath="customer.creditScore"; allowedOps="> < >= <="; allowedValues=$null }
PostTry "/admin/fields" @{ flowCode="ACCOUNT_CLOSURE"; fieldKey="account.balance"; label="Account Balance"; dataType="NUMBER"; drlPath="account.balance"; allowedOps="> < >= <= == !="; allowedValues=$null }
PostTry "/admin/fields" @{ flowCode="ACCOUNT_CLOSURE"; fieldKey="account.status"; label="Account Status"; dataType="ENUM"; drlPath="account.status"; allowedOps="== !="; allowedValues="ACTIVE, DORMANT, FROZEN, CLOSED" }

Write-Host "4/5 Resolving group ids..." -ForegroundColor Cyan
$tg=GetJ "/admin/flows/TRANSFER_TRANSACTION/groups"
$common=($tg|Where-Object{$_.shared}|Select-Object -First 1).id
$transfer=($tg|Where-Object{$_.groupCode -eq "TRANSFER"}).id
$velocity=($tg|Where-Object{$_.groupCode -eq "VELOCITY"}).id
$complianceT=($tg|Where-Object{$_.groupCode -eq "COMPLIANCE"}).id
$loan=((GetJ "/admin/flows/LOAN_APPLICATION/groups")|Where-Object{$_.groupCode -eq "LOAN"}).id
$cg=GetJ "/admin/flows/ACCOUNT_CLOSURE/groups"
$closure=($cg|Where-Object{$_.groupCode -eq "CLOSURE"}).id
$complianceC=($cg|Where-Object{$_.groupCode -eq "COMPLIANCE"}).id

Write-Host "5/5 Rules..." -ForegroundColor Cyan
PostJ "/admin/groups/$transfer/rules" @{ ruleName="Block over-limit debit-restricted BDT transaction"; sourceType="BUILDER"; createdBy="farhat"; sourceJson='{"ruleName":"Block over-limit debit-restricted BDT transaction","flow":"TRANSFER_TRANSACTION","agendaGroup":"TRANSFER","salience":10,"conditionTree":{"type":"GROUP","operator":"AND","children":[{"type":"CONDITION","field":"transaction.currency","operator":"==","value":"BDT"},{"type":"CONDITION","field":"product.drRes","operator":"==","value":1},{"type":"CONDITION","field":"transaction.amount","operator":">","valueField":"userLimit.limit"}]},"then":{"decision":"block","message":"Blocked: amount exceeds limit on a debit-restricted BDT account"}}' } | Out-Null
PostJ "/admin/groups/$transfer/rules" @{ ruleName="High-value or cross-border transfer"; sourceType="BUILDER"; createdBy="farhat"; sourceJson='{"ruleName":"High-value or cross-border transfer","flow":"TRANSFER_TRANSACTION","agendaGroup":"TRANSFER","salience":20,"conditionTree":{"type":"GROUP","operator":"OR","children":[{"type":"GROUP","operator":"AND","children":[{"type":"CONDITION","field":"transaction.channel","operator":"==","value":"AGENT"},{"type":"CONDITION","field":"transaction.amount","operator":">","value":50000}]},{"type":"GROUP","operator":"AND","children":[{"type":"CONDITION","field":"transaction.country","operator":"==","value":"OTHER"},{"type":"CONDITION","field":"transaction.currency","operator":"!=","value":"BDT"}]}]},"then":{"decision":"block","message":"Blocked: high-value agent transfer, or cross-border non-BDT transfer"}}' } | Out-Null
PostJ "/admin/groups/$velocity/rules" @{ ruleName="Block rapid high-value agent transfers"; sourceType="BUILDER"; createdBy="farhat"; sourceJson='{"ruleName":"Block rapid high-value agent transfers","flow":"TRANSFER_TRANSACTION","agendaGroup":"VELOCITY","salience":20,"conditionTree":{"type":"GROUP","operator":"AND","children":[{"type":"CONDITION","field":"transaction.channel","operator":"==","value":"AGENT"},{"type":"CONDITION","field":"transaction.amount","operator":">","value":50000},{"type":"CONDITION","field":"transaction.dailyTxnCount","operator":">=","value":5}]},"then":{"decision":"block","message":"Blocked: too many high-value agent transfers today"}}' } | Out-Null
PostJ "/admin/groups/$velocity/rules" @{ ruleName="Layered channel & geo screen"; sourceType="BUILDER"; createdBy="farhat"; sourceJson='{"ruleName":"Layered channel & geo screen","flow":"TRANSFER_TRANSACTION","agendaGroup":"VELOCITY","salience":20,"conditionTree":{"type":"GROUP","operator":"AND","children":[{"type":"GROUP","operator":"AND","children":[{"type":"CONDITION","field":"transaction.amount","operator":">","value":10000},{"type":"GROUP","operator":"OR","children":[{"type":"CONDITION","field":"transaction.channel","operator":"==","value":"ONLINE"},{"type":"CONDITION","field":"transaction.channel","operator":"==","value":"AGENT"}]}]},{"type":"GROUP","operator":"AND","children":[{"type":"GROUP","operator":"OR","children":[{"type":"CONDITION","field":"transaction.country","operator":"==","value":"US"},{"type":"CONDITION","field":"transaction.country","operator":"==","value":"OTHER"}]},{"type":"CONDITION","field":"transaction.currency","operator":"!=","value":"BDT"}]}]},"then":{"decision":"block","message":"Blocked: layered channel & geo screening"}}' } | Out-Null
PostJ "/admin/groups/$complianceT/rules" @{ ruleName="Block high-value cross-border non-BDT transfer"; sourceType="BUILDER"; createdBy="farhat"; sourceJson='{"ruleName":"Block high-value cross-border non-BDT transfer","flow":"TRANSFER_TRANSACTION","agendaGroup":"COMPLIANCE","salience":20,"conditionTree":{"type":"GROUP","operator":"AND","children":[{"type":"CONDITION","field":"transaction.currency","operator":"!=","value":"BDT"},{"type":"CONDITION","field":"transaction.amount","operator":">=","value":100000},{"type":"CONDITION","field":"transaction.country","operator":"==","value":"OTHER"}]},"then":{"decision":"block","message":"Blocked: cross-border high-value transfer needs manual review"}}' } | Out-Null
PostJ "/admin/groups/$complianceT/rules" @{ ruleName="Deep-nested compliance check"; sourceType="BUILDER"; createdBy="farhat"; sourceJson='{"ruleName":"Deep-nested compliance check","flow":"TRANSFER_TRANSACTION","agendaGroup":"COMPLIANCE","salience":20,"conditionTree":{"type":"GROUP","operator":"AND","children":[{"type":"GROUP","operator":"OR","children":[{"type":"GROUP","operator":"AND","children":[{"type":"GROUP","operator":"OR","children":[{"type":"CONDITION","field":"transaction.amount","operator":">","value":5000},{"type":"CONDITION","field":"transaction.currency","operator":"==","value":"USD"}]},{"type":"CONDITION","field":"product.drRes","operator":"==","value":1}]},{"type":"CONDITION","field":"transaction.dailyTxnCount","operator":">=","value":10}]},{"type":"GROUP","operator":"OR","children":[{"type":"CONDITION","field":"userLimit.limit","operator":">=","value":100000},{"type":"GROUP","operator":"AND","children":[{"type":"CONDITION","field":"transaction.amount","operator":">","valueField":"userLimit.limit"},{"type":"CONDITION","field":"transaction.dailyTxnCount","operator":">=","value":5}]}]}]},"then":{"decision":"block","message":"Blocked: deep-nested compliance condition met"}}' } | Out-Null
PostJ "/admin/groups/$loan/rules" @{ ruleName="Block loan below credit-score threshold"; sourceType="BUILDER"; createdBy="farhat"; sourceJson='{"ruleName":"Block loan below credit-score threshold","flow":"LOAN_APPLICATION","agendaGroup":"LOAN","salience":10,"conditionTree":{"type":"GROUP","operator":"AND","children":[{"type":"CONDITION","field":"customer.creditScore","operator":"<","value":600}]},"then":{"decision":"block","message":"Blocked: credit score below 600"}}' } | Out-Null
PostJ "/admin/groups/$closure/rules" @{ ruleName="Block closure with outstanding balance"; sourceType="BUILDER"; createdBy="farhat"; sourceJson='{"ruleName":"Block closure with outstanding balance","flow":"ACCOUNT_CLOSURE","agendaGroup":"CLOSURE","salience":10,"conditionTree":{"type":"GROUP","operator":"AND","children":[{"type":"CONDITION","field":"account.balance","operator":">","value":0}]},"then":{"decision":"block","message":"Blocked: account has an outstanding balance"}}' } | Out-Null
PostJ "/admin/groups/$complianceC/rules" @{ ruleName="Closure compliance - dormant review"; sourceType="BUILDER"; createdBy="farhat"; sourceJson='{"ruleName":"Closure compliance - dormant review","flow":"ACCOUNT_CLOSURE","agendaGroup":"COMPLIANCE","salience":20,"conditionTree":{"type":"GROUP","operator":"AND","children":[{"type":"CONDITION","field":"account.status","operator":"==","value":"DORMANT"}]},"then":{"decision":"block","message":"Blocked: dormant account closure needs compliance review"}}' } | Out-Null
PostJ "/admin/groups/$complianceC/rules" @{ ruleName="Closure compliance - frozen block"; sourceType="BUILDER"; createdBy="farhat"; sourceJson='{"ruleName":"Closure compliance - frozen block","flow":"ACCOUNT_CLOSURE","agendaGroup":"COMPLIANCE","salience":20,"conditionTree":{"type":"GROUP","operator":"AND","children":[{"type":"CONDITION","field":"account.status","operator":"==","value":"FROZEN"}]},"then":{"decision":"block","message":"Blocked: a frozen account cannot be closed"}}' } | Out-Null

Write-Host "Seed complete." -ForegroundColor Green

if($Test){
  Write-Host "`nRunning scenarios..." -ForegroundColor Cyan
  function V($label,$path,$body){
    $r=Invoke-RestMethod -Method Post -Uri "$Base$path" -ContentType "application/json" -Body $body
    $tag= if($r.valid){"VALID  "}else{"BLOCKED"}
    Write-Host ("  {0,-42} {1}  {2}" -f $label,$tag,$r.message)
  }
  V "T3 over limit (dr-rest.)" "/api/transactions/validate" '{"userId":"USER-001","transactionMode":"ONLINE","debitCredit":"D","sourceAccount":"100001","currency":"BDT","amount":15000}'
  V "T4 within limit"          "/api/transactions/validate" '{"userId":"USER-001","transactionMode":"ONLINE","debitCredit":"D","sourceAccount":"100001","currency":"BDT","amount":5000}'
  V "T5 cross-border high-val" "/api/transactions/validate" '{"userId":"USER-001","transactionMode":"ONLINE","debitCredit":"D","sourceAccount":"200001","currency":"USD","amount":150000,"country":"OTHER"}'
  V "L1 credit score < 600"    "/api/loans/validate"        '{"userId":"USER-004","amount":50000,"tenureMonths":12,"purpose":"Personal"}'
  V "L2 good score"            "/api/loans/validate"        '{"userId":"USER-001","amount":50000,"tenureMonths":12,"purpose":"Personal"}'
  V "C1 outstanding balance"   "/api/account-closures/validate" '{"userId":"USER-001","accountNo":"ACC-901","reason":"x"}'
  V "C2 zero balance"          "/api/account-closures/validate" '{"userId":"USER-001","accountNo":"ACC-900","reason":"x"}'
}

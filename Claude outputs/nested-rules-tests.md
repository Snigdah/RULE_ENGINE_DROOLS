# Nested rules — test in the Test Console

Three nested-logic rules now live in the **real** transfer groups (no more DEMO group). User `USER-001` is clean; account `100001` = debit-restricted (drRes=1), `200001` = normal; every limit = 10000. Each BLOCK case is crafted so only its own rule fires.

Endpoint: `POST /api/transactions/validate` (this is what the Test Console calls).


---

## High-value or cross-border transfer  — group `TRANSFER`

Logic: `(channel==AGENT AND amount>50000) OR (country==OTHER AND currency!=BDT)`

**BLOCK**

```bash
curl.exe -X POST http://localhost:8080/api/transactions/validate -H "Content-Type: application/json" -d "{\"userId\": \"USER-001\", \"transactionMode\": \"TRANSFER\", \"debitCredit\": \"DR\", \"sourceAccount\": \"200001\", \"currency\": \"USD\", \"amount\": 1000, \"sourceBranch\": \"001\", \"destinationAccount\": \"999\", \"exchangeRate\": 1, \"remarks\": \"test\", \"channel\": \"ONLINE\", \"country\": \"OTHER\", \"dailyTxnCount\": 1}"
```

**ALLOW**

```bash
curl.exe -X POST http://localhost:8080/api/transactions/validate -H "Content-Type: application/json" -d "{\"userId\": \"USER-001\", \"transactionMode\": \"TRANSFER\", \"debitCredit\": \"DR\", \"sourceAccount\": \"200001\", \"currency\": \"BDT\", \"amount\": 1000, \"sourceBranch\": \"001\", \"destinationAccount\": \"999\", \"exchangeRate\": 1, \"remarks\": \"test\", \"channel\": \"ONLINE\", \"country\": \"BD\", \"dailyTxnCount\": 1}"
```


---

## Layered channel & geo screen  — group `VELOCITY`

Logic: `amount>10000 AND (ONLINE or AGENT) AND ((US or OTHER) AND currency!=BDT)`

**BLOCK**

```bash
curl.exe -X POST http://localhost:8080/api/transactions/validate -H "Content-Type: application/json" -d "{\"userId\": \"USER-001\", \"transactionMode\": \"TRANSFER\", \"debitCredit\": \"DR\", \"sourceAccount\": \"200001\", \"currency\": \"USD\", \"amount\": 20000, \"sourceBranch\": \"001\", \"destinationAccount\": \"999\", \"exchangeRate\": 1, \"remarks\": \"test\", \"channel\": \"ONLINE\", \"country\": \"US\", \"dailyTxnCount\": 1}"
```

**ALLOW**

```bash
curl.exe -X POST http://localhost:8080/api/transactions/validate -H "Content-Type: application/json" -d "{\"userId\": \"USER-001\", \"transactionMode\": \"TRANSFER\", \"debitCredit\": \"DR\", \"sourceAccount\": \"200001\", \"currency\": \"BDT\", \"amount\": 20000, \"sourceBranch\": \"001\", \"destinationAccount\": \"999\", \"exchangeRate\": 1, \"remarks\": \"test\", \"channel\": \"ONLINE\", \"country\": \"BD\", \"dailyTxnCount\": 1}"
```


---

## Deep-nested compliance check  — group `COMPLIANCE`

Logic: `(((amount>5000 OR USD) AND drRes) OR dailyTxnCount>=10) AND (limit>=100000 OR (amount>limit AND dailyTxnCount>=5))`

**BLOCK**

```bash
curl.exe -X POST http://localhost:8080/api/transactions/validate -H "Content-Type: application/json" -d "{\"userId\": \"USER-001\", \"transactionMode\": \"TRANSFER\", \"debitCredit\": \"DR\", \"sourceAccount\": \"100001\", \"currency\": \"USD\", \"amount\": 20000, \"sourceBranch\": \"001\", \"destinationAccount\": \"999\", \"exchangeRate\": 1, \"remarks\": \"test\", \"channel\": \"ONLINE\", \"country\": \"BD\", \"dailyTxnCount\": 10}"
```

**ALLOW**

```bash
curl.exe -X POST http://localhost:8080/api/transactions/validate -H "Content-Type: application/json" -d "{\"userId\": \"USER-001\", \"transactionMode\": \"TRANSFER\", \"debitCredit\": \"DR\", \"sourceAccount\": \"200001\", \"currency\": \"BDT\", \"amount\": 1000, \"sourceBranch\": \"001\", \"destinationAccount\": \"999\", \"exchangeRate\": 1, \"remarks\": \"test\", \"channel\": \"ONLINE\", \"country\": \"BD\", \"dailyTxnCount\": 1}"
```

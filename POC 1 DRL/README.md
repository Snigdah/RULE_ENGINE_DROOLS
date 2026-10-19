# Drools Rule-Engine POC — 3 flows

Spring Boot 3.5 + Java 21 + PostgreSQL. This app does **not** embed Drools — it is a
business consumer of `com.leads:rule-engine` (the sibling `RuleEngine_Library`, **0.0.2**).

It demonstrates the full design: **one engine, three flows** (transfer, loan, account-closure),
each flow with its own **builder** rules. The shared **COMMON** group still exists but is empty
(the blocked-user and KYC DRL rules were removed), so services call
`ruleEngine.execute(context, XRequest.class)` without a global context.

## How the reuse works

Every flow's context extends a shared base, `RuleContext` (holds `userId`, `user`, and the
decision result). A rule placed in the shared COMMON group would be written against `RuleContext`
and fire for any flow; the flow-specific rules match the concrete context.

```
request class ──▶ flow ──▶ ordered groups ──▶ rules
TransactionRequest ▶ TRANSFER_TRANSACTION ▶ [COMMON, TRANSFER] ▶ over-limit, high-value, velocity, compliance
LoanRequest        ▶ LOAN_APPLICATION     ▶ [COMMON, LOAN]     ▶ credit-score
AccountCloseRequest▶ ACCOUNT_CLOSURE       ▶ [COMMON, CLOSURE]  ▶ outstanding-balance
```

## Rules (stored in `rule_definition`)

| Rule | Group | Source |
|------|-------|--------|
| Over-limit debit-restricted BDT | TRANSFER | BUILDER (JSON → DRL) |
| Loan below credit score | LOAN | BUILDER |
| Closure with outstanding balance | CLOSURE | BUILDER |

Builder rules store both `source_json` (editable) and the generated `drl_text` (what runs).
The engine only ever executes `drl_text`.

## Run

```bash
# 1. install the library (once), if not already in your ~/.m2
mvn -f "../RuleEngine_Library/core/pom.xml" install

# 2. database
createdb -U postgres ruleengine
psql -U postgres -d ruleengine -f db/schema.sql   # or let ddl-auto=update create tables
psql -U postgres -d ruleengine -f db/data.sql     # rules + routing + demo rows

# 3. run
mvn clean spring-boot:run
```

## Endpoints
- `POST /api/transactions/validate` — transfer
- `POST /api/loans/validate` — loan
- `POST /api/account-closures/validate` — account closure
- `GET /admin/rules`, `GET /admin/rules/fields?flow=…`, `POST /admin/rules` — library
- `GET/POST/DELETE /admin/flows` and `/admin/flows/mappings` — library

## Test personas (seeded)

| user | kyc | blocked | credit | note |
|------|-----|---------|--------|------|
| USER-001 | yes | no | 700 | the "happy" user |
| USER-002 | yes | **yes** | 700 | blocked flag in data only (no rule uses it now) |
| USER-003 | **no** | no | — | KYC flag in data only (no rule uses it now) |
| USER-004 | yes | no | **550** | fails the loan credit-score rule |

Accounts: `100001` debit-restricted, `200001` not. `ACC-901` has a balance, `ACC-900` is zero.

See `test-requests.http` for ready-to-run calls (T1–T5 transfer, L1–L3 loan, C1–C3 closure).

## Tests
```bash
mvn test
```

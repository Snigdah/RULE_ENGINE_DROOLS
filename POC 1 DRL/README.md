# Drools Transaction POC

Spring Boot 3.5 + Java 21 + Maven + Drools + PostgreSQL.

The engine validates a transaction using **context building**: the request
carries lookup keys, the service loads `UserLimit` and `Product` from the DB,
wraps everything in one `ValidationContext` fact, and fires the rules against
it. DB lookups happen in the service (never inside a rule).

## Requirements
- JDK 21, Maven 3.9+
- PostgreSQL running locally

## 1. Database
```bash
createdb -U postgres ruleengine
psql -U postgres -d ruleengine -f db/schema.sql
psql -U postgres -d ruleengine -f db/data.sql
```
Datasource is configured in `src/main/resources/application.yml`
(url `jdbc:postgresql://localhost:5432/ruleengine`, user/pass `postgres`).
`ddl-auto` is `validate` — Hibernate checks the entities against your
hand-created tables and never auto-creates them.

## 2. Run
```bash
mvn clean spring-boot:run
```

## The rule
A transaction is **blocked** only when all three hold:
- `currency == "BDT"`
- product `drRes == 1` (debit-restricted)
- `amount > userLimit.limit`

Otherwise it is allowed. Sample data limits for `USER-001`:

| transactionMode | DR/CR | limit  |
|-----------------|-------|--------|
| TRANSFER        | DR    | 100    |
| TRANSFER        | CR    | 2323   |
| CREDIT          | DR    | 12323  |
| CREDIT          | CR    | 123213 |

## Global context (startup)

`user_block` rows are loaded once at startup by `GlobalContextLoader`
(`ApplicationRunner`) into `GlobalContext` (in-memory, immutable for the POC).
The service exposes it to each session as a Drools `global`, and the
**blocked-user rule lives in `transaction-rules.drl`** - so an admin can turn
the block off by deleting that rule from the file, no code change or redeploy.

A blocked user gets:
```json
{"valid":false,"permissionDenied":true,"message":"Blocked: user is blocked"}
```

Notes:
- The blocked-user rule has the highest salience and calls `drools.halt()`,
  so it wins over the limit rule.
- Context (limit/product) is still loaded before the rules fire, so a blocked
  user needs valid context rows or the request returns 422 first. The seed
  gives USER-002 limits for this reason.
- Blocked status is cached at startup; a newly blocked user is not picked up
  until restart. For production, refresh it (scheduled reload / TTL / eviction).

## Rule grouping (agenda groups + decision flow)

Rules are tagged with Drools `agenda-group` so a request only fires the groups
it needs, not the whole rule set:

- `COMMON`   - cross-cutting gates (e.g. blocked user), run for every flow
- `TRANSFER` - transfer-specific rules

A flow name maps to an ordered list of groups inside RuleExecutionService
(`"TRANSFER_TRANSACTION" -> [COMMON, TRANSFER]`). `RuleExecutionService` is the
only class that touches `KieSession`/agenda groups: it focuses the groups in
reverse (focus is a LIFO stack) so the first group in the flow (`COMMON`) fires
first and gates the rest. The API/service just names the flow.

To add a use case later: add rules under a new `agenda-group`, add one line
to the FLOWS map in RuleExecutionService, done. (The flow->groups map can move to the DB when there
are several use cases.)

## Endpoints
- `POST /api/transactions/validate`
- `GET  /actuator/health`

## Try it
See `test-requests.http` (IntelliJ HTTP client). Example — blocked:
```bash
curl -X POST http://localhost:8080/api/transactions/validate \
  -H "Content-Type: application/json" \
  -d "{\"userId\":\"USER-001\",\"transactionMode\":\"TRANSFER\",\"debitCredit\":\"DR\",\"sourceAccount\":\"100001\",\"currency\":\"BDT\",\"amount\":500}"
```
```json
{"valid":false,"permissionDenied":true,"message":"Blocked: amount exceeds limit on a debit-restricted BDT account"}
```

Responses: `200` with `{valid, permissionDenied, message}`;
`400` on bad input; `422` when a limit/product row is missing.

## Design
- `dto/TransactionRequest` / `dto/TransactionResponse` - API contract
- `model/Transaction`      - internal fact (holds result fields)
- `model/UserLimit`, `model/Product` - JPA entities + context facts
- `model/ValidationContext` - the single fact the rule reads
- `repository/*`           - the two DB lookups
- `service/TransactionRuleService` - context building + rule firing
- `exception/ContextNotFoundException` + handler -> 422
- `resources/rules/transaction-rules.drl` - the rule

## Tests
```bash
mvn test
```
- `TransactionRuleServiceTest` - rule logic with real KieContainer + mocked repos (no DB)
- `TransactionControllerTest`  - web layer, mocked service (no DB)

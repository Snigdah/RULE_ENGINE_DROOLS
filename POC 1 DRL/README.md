# Transaction Validation POC (DMN)

Spring Boot 3.5 + Java 21 + Maven + DMN (kie-dmn) + PostgreSQL.

Flow: **Controller -> Service -> build context (DB) -> DMN decision -> response.**
The decision lives in `src/main/resources/dmn/transaction-decision.dmn`.

## Business logic (2 rules, in the DMN)
Blocked when:
1. the user is blocked (`user_block` row with blocked = true), OR
2. currency = BDT AND product debit-restricted (drRes = 1) AND amount > userLimit.

Otherwise allowed.

## Context building
`buildContext` loads `UserLimit` (by user + mode + DR/CR) and `Product` (by
source account) in parallel on virtual threads; a missing row -> HTTP 422.
The blocked flag is a simple per-request lookup on `user_block`.

## Requirements
- JDK 21, Maven 3.9+
- PostgreSQL

## Run
```bash
createdb -U postgres ruleengine
# tables auto-create on startup (ddl-auto: update)
mvn spring-boot:run
psql -U postgres -d ruleengine -f db/data.sql
```

## Endpoints
- `POST /api/transactions/validate`
- `GET  /actuator/health`

## Try it
```bash
curl -X POST http://localhost:8080/api/transactions/validate \
  -H "Content-Type: application/json" \
  -d "{\"userId\":\"USER-001\",\"transactionMode\":\"TRANSFER\",\"debitCredit\":\"DR\",\"sourceAccount\":\"100001\",\"currency\":\"BDT\",\"amount\":500}"
```

## Design
- `dto/*`                 - request / response
- `model/*`              - entities (UserLimit, Product, UserBlock), Transaction (input), ValidationContext
- `repository/*`         - DB lookups
- `config/DmnConfig`     - builds the DMNRuntime from the .dmn
- `config/ConcurrencyConfig` - virtual-thread executor
- `service/DmnDecisionService` - inputs -> DMN -> response
- `service/TransactionRuleService` - build context + blocked lookup + decision
- `resources/dmn/transaction-decision.dmn` - the decision

## Tests
```bash
mvn test
```

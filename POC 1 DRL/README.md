# Drools Transaction POC

Spring Boot 3.5 + Java 21 + PostgreSQL. This app does **not** embed Drools.
It is a business consumer of `com.leads:rule-engine` (the sibling `RuleEngine_Library`).

The service loads `UserLimit` and `Product`, wraps them in one `ValidationContext`
fact, and calls:

```java
ruleEngine.execute(context, TransactionRequest.class, globalContext);
```

The library compiles DRL from `rule_file`, picks the flow from `request_flow_map`
and `flow_group`, and mutates the fact. This service reads `valid`,
`permissionDenied`, and the message off that fact.

## Requirements
- JDK 21, Maven 3.9+
- PostgreSQL running locally
- The rule-engine library installed into the local Maven repo (once):

```bash
mvn -f "../RuleEngine_Library/core/pom.xml" install
```

## 1. Database
```bash
createdb -U postgres ruleengine
psql -U postgres -d ruleengine -f db/schema.sql
psql -U postgres -d ruleengine -f db/data.sql
```
Datasource is in `src/main/resources/application.yml`
(`jdbc:postgresql://localhost:5432/ruleengine`, user `postgres`).
`ddl-auto` is `update`. Hibernate also creates these tables from the entities
if you skip the scripts. Seed data still has to be loaded.

Library tables: `rule_file`, `flow_group`, `request_flow_map`.
Business tables: `re_user_limit`, `re_product`, `re_user_block`.

## 2. Run
```bash
mvn clean spring-boot:run
```

On a fresh database there are no rules and no flow mapping. Upload them before
calling validate (see `test-requests.http`):

1. `POST /admin/rules` with `rules/transaction-rules.drl`
2. `POST /admin/flows` — flow `TRANSFER_TRANSACTION`, groups `COMMON` then `TRANSFER`
3. `POST /admin/flows/mappings` — request class
   `com.example.droolspoc.dto.TransactionRequest`

## The rule
A transaction is **blocked** when the user is in `re_user_block`, or when all
three hold:

- `currency == "BDT"`
- product `drRes == 1` (debit-restricted)
- `amount > userLimit.limit`

Otherwise it is allowed. Seed data:

| user / account | meaning |
|----------------|---------|
| USER-001       | limit 10000, ONLINE / D, not blocked |
| USER-002       | limit 10000, blocked |
| 100001         | debit-restricted (`dr_res = 1`) |
| 200001         | not restricted |

Blocked users are loaded at startup by `BlockedUsersLoader` into the library
`GlobalContext`. `POST /admin/global-context/reload` picks up new block rows
without a restart.

## Endpoints
- `POST /api/transactions/validate` — this service
- `GET/POST/DELETE /admin/rules` — library
- `GET/POST/DELETE /admin/flows` and `/admin/flows/mappings` — library
- `POST /admin/global-context/reload` — library
- `GET /actuator/health`

Responses: `200` with `{valid, permissionDenied, message}`;
`400` on bad input or a missing flow mapping;
`422` when a limit or product row is missing.

## Tests
```bash
mvn test
```
- `TransactionRuleServiceTest` — context building and the library `execute` call (no DB, no Drools)
- `TransactionControllerTest` — web layer, mocked service

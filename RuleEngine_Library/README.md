# Rule Engine Starter (`com.leads:rule-engine`)

A shared Spring Boot starter that gives any Microcube service a Drools rule engine with
**runtime rule authoring** (Visual Rule Builder JSON *or* hand-written DRL) and **DB-driven
flow routing** — no engine code copied per service. Same house pattern as the AuthZ library:
add the dependency, configure YAML, done.

Business microservices run on **Oracle** (PL/SQL). The library ships its own entities; each
service points them at its own Oracle schema.

---

## 1. Add the dependency

```xml
<dependency>
    <groupId>com.leads</groupId>
    <artifactId>rule-engine</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

Publish it to Nexus first from this repo:

```bash
cd core
mvn clean deploy
```

> **Spring Boot version:** this library targets **Boot 3.5.10**. The consuming service must
> be on the same 3.5.x line. (AuthZ is on Boot 4.0.2 — if a service uses both libraries they
> must share one Boot line; ask before mixing.)

## 2. Configure (service `application.yaml`)

```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@//db-host:1521/ORCLPDB1
    username: MYSVC
    password: ${DB_PASSWORD}          # externalise — never inline in banking
    driver-class-name: oracle.jdbc.OracleDriver
  jpa:
    hibernate:
      ddl-auto: none                  # DBA runs schema-oracle.sql; app does not touch DDL
                                      # (use 'update' in dev to auto-create the tables)

rule-engine:
  admin:
    enabled: true                     # expose /admin/rules + /admin/flows (default true)
```

The service supplies the **Oracle JDBC driver** (`ojdbc`) — the library ships none. Hibernate
auto-detects the Oracle dialect from the live connection.

### Schema

Four tables: `RULE_DEFINITION` (the rules — stores `SOURCE_JSON` + `DRL_TEXT`),
`RULE_FIELD_CATALOG` (the builder palette), `FLOW_GROUP` (flow → ordered agenda groups) and
`REQUEST_FLOW_MAP` (request class → flow). A BUILDER rule's fact/context class is stored in `REQUEST_FLOW_MAP.FACT_TYPE` (set it in the schema seed or via `POST /admin/flows/mappings` with `factType`) — no YAML.

- **Dev:** `ddl-auto: update` auto-creates all four.
- **Prod (banking):** DBA runs `core/src/main/resources/db/oracle/schema-oracle.sql` once, then
  `ddl-auto: none`. `sample-data-oracle.sql` has demo rows (3 request types, 4 rules, 7 fields).

## 3. What each service still writes (business-specific)

The library is business-agnostic. Per service you write:

- your **request DTO** (e.g. `TransactionRequest`)
- your **fact/context** class + a `buildContext(...)` that does your DB lookups
- your business **entities/repos** (their own tables)
- any **`GlobalReferenceLoader`** beans (reference data your rules read as a `global`)
- your **rules** — authored at runtime as builder JSON or hand-written DRL via the admin API
- (for BUILDER rules) setters on your context the generated DRL calls: `setValid(boolean)`,
  `setPermissionDenied(boolean)`, `setValidationMessage(String)`

Then call the engine:

```java
ruleExecutionService.execute(context, TransactionRequest.class);
```

`execute(Object fact, Class<?> requestType)` — the fact is your context; the request class
selects the flow. The rules mutate the fact in place; read the result off it afterwards.

## 4. Onboard a new request type (all runtime, no redeploy)

```bash
# a1) save a BUILDER rule (the UI posts this — engine generates the DRL from the JSON)
curl -X POST http://localhost:8080/admin/rules \
  -H "Content-Type: application/json" \
  -d '{"ruleName":"Over-limit debit-restricted BDT","agendaGroup":"TRANSFER","sourceType":"BUILDER",
       "sourceJson":"{\"flow\":\"TRANSFER_TRANSACTION\",\"agendaGroup\":\"TRANSFER\",\"salience\":10,\"match\":\"all\",\"conditions\":[{\"field\":\"transaction.amount\",\"operator\":\">\",\"valueField\":\"userLimit.limit\"}],\"then\":{\"decision\":\"block\",\"message\":\"Over limit\"}}"}'

# a2) save a hand-written DRL rule (e.g. one using eval + a global)
curl -X POST http://localhost:8080/admin/rules \
  -H "Content-Type: application/json" \
  -d '{"ruleName":"Block blocked user","agendaGroup":"COMMON","sourceType":"DRL","drl":"package ...\nrule \"...\" ... end"}'

# b) define the flow (ordered agenda groups)
curl -X POST http://localhost:8080/admin/flows \
  -H "Content-Type: application/json" \
  -d '{"flowName":"TRANSFER_TRANSACTION","groups":["COMMON","TRANSFER"]}'

# c) map your request class to the flow
curl -X POST http://localhost:8080/admin/flows/mappings \
  -H "Content-Type: application/json" \
  -d '{"requestClass":"com.example.dto.TransactionRequest","flowName":"TRANSFER_TRANSACTION"}'
```

Inspect / manage:

```bash
curl http://localhost:8080/admin/rules                       # list stored rules
curl "http://localhost:8080/admin/rules/fields?flow=TRANSFER_TRANSACTION"  # builder palette for a flow
curl -X POST http://localhost:8080/admin/rules/preview -H "Content-Type: application/json" \
  -d '{"flow":"TRANSFER_TRANSACTION","agendaGroup":"TRANSFER","salience":10,"match":"all","conditions":[{"field":"transaction.amount","operator":">","valueField":"userLimit.limit"}],"then":{"decision":"block","message":"Over limit"}}'   # generated DRL + compiles? (no save)
curl http://localhost:8080/admin/flows/overview              # mappings + flows in one view
curl -X DELETE "http://localhost:8080/admin/rules/Block%20blocked%20user"
curl -X DELETE "http://localhost:8080/admin/flows/mappings?requestClass=com.example.dto.TransactionRequest"
```

A rule whose DRL does not compile is **rejected with 400** and the running engine is untouched
(validate-before-swap).

---

## Module layout

```
core/
 └─ src/main/java/leads/ruleengine/core/
    ├─ config/       RuleEngineAutoConfiguration, RuleEngineProperties
    ├─ service/      RuleExecutionService, RuleBaseProvider, DecisionFlowResolver,
    │                RuleAdminService, RuleDrlGenerator, FlowAdminService
    ├─ controller/   RuleAdminController, FlowAdminController   (gated by rule-engine.admin.enabled)
    ├─ model/        RuleDefinition, RuleFieldCatalog, FlowGroup, RequestFlowMap  (Oracle: CLOB + SEQUENCE)
    ├─ repository/   the four Spring Data repos
    ├─ context/      GlobalContext, GlobalReferenceLoader, GlobalContextLoader
    ├─ dto/          RuleDefinitionRequest, BuilderRuleSpec, FlowDefinitionRequest, RequestMappingRequest
    └─ exception/    RuleCompilationException, FlowNotConfiguredException,
                     RuleEngineAdminExceptionHandler (scoped to admin controllers only)
```

Auto-wired via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

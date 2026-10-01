# Rule Engine Starter (`com.leads:rule-engine`)

A shared Spring Boot starter that gives any Microcube service a Drools rule engine with
**runtime rule upload** and **DB-driven flow routing** — no engine code copied per service.
Same house pattern as the AuthZ library: add the dependency, configure YAML, done.

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
      ddl-auto: validate              # DBA runs schema-oracle.sql; app only validates
                                      # (use 'update' in dev to auto-create the 3 tables)

rule-engine:
  admin:
    enabled: true                     # expose /admin/rules + /admin/flows (default true)
```

The service supplies the **Oracle JDBC driver** (`ojdbc`) — the library ships none. Hibernate
auto-detects the Oracle dialect from the live connection.

### Schema

- **Dev:** `ddl-auto: update` auto-creates `RULE_FILE`, `FLOW_GROUP`, `REQUEST_FLOW_MAP`.
- **Prod (banking):** DBA runs `core/src/main/resources/db/oracle/schema-oracle.sql` once, then
  `ddl-auto: validate`.

## 3. What each service still writes (business-specific)

The library is business-agnostic. Per service you write:

- your **request DTO** (e.g. `TransactionRequest`)
- your **fact/context** class + a `buildContext(...)` that does your DB lookups
- your business **entities/repos** (their own tables)
- any **`GlobalReferenceLoader`** beans (reference data your rules read as a `global`)
- your **DRL** files (uploaded at runtime via the admin API)

Then call the engine:

```java
ruleExecutionService.execute(context, TransactionRequest.class);
```

`execute(Object fact, Class<?> requestType)` — the fact is your context; the request class
selects the flow. The rules mutate the fact in place; read the result off it afterwards.

## 4. Onboard a new request type (all runtime, no redeploy)

```bash
# a) upload a DRL
curl -X POST http://localhost:8080/admin/rules \
  -H "Content-Type: application/json" \
  -d '{"fileName":"transaction-rules.drl","drl":"package ...\nrule \"...\" ... end"}'

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
curl http://localhost:8080/admin/rules                       # list stored DRLs
curl http://localhost:8080/admin/flows/overview              # mappings + flows in one view
curl -X DELETE http://localhost:8080/admin/rules/transaction-rules.drl
curl -X DELETE "http://localhost:8080/admin/flows/mappings?requestClass=com.example.dto.TransactionRequest"
```

A DRL that does not compile is **rejected with 400** and the running engine is untouched
(validate-before-swap).

---

## Module layout

```
core/
 └─ src/main/java/leads/ruleengine/core/
    ├─ config/       RuleEngineAutoConfiguration, RuleEngineProperties
    ├─ service/      RuleExecutionService, RuleBaseProvider, DecisionFlowResolver,
    │                RuleAdminService, FlowAdminService
    ├─ controller/   RuleAdminController, FlowAdminController   (gated by rule-engine.admin.enabled)
    ├─ model/        RuleFile, FlowGroup, RequestFlowMap        (Oracle: CLOB + SEQUENCE)
    ├─ repository/   the three Spring Data repos
    ├─ context/      GlobalContext, GlobalReferenceLoader, GlobalContextLoader
    ├─ dto/          RuleUploadRequest, FlowDefinitionRequest, RequestMappingRequest
    └─ exception/    RuleCompilationException, FlowNotConfiguredException,
                     RuleEngineAdminExceptionHandler (scoped to admin controllers only)
```

Auto-wired via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

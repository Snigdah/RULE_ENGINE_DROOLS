# Drools Transaction POC

Spring Boot 3.5 + Java 21 + Maven. Compiles the DRL rules under
`resources/rules/` into a Drools `KieContainer` at startup and validates
transactions over REST.

## Requirements
- JDK 21
- Maven 3.9+

## Build & run
```bash
mvn clean spring-boot:run
```

## Endpoints
- `POST /api/transactions/validate` - run the rules on a transaction
- `GET  /actuator/health`           - health / readiness probe

## Try it
DEVIR over 5000 -> permission denied:
```bash
curl -X POST http://localhost:8080/api/transactions/validate \
  -H "Content-Type: application/json" \
  -d "{\"transferMode\":\"DEVIR\",\"amount\":6000}"
```
Response:
```json
{"valid":false,"permissionDenied":true,
 "message":"Permission denied: DEVIR transaction amount cannot be greater than 5000"}
```

Normal transfer over 500 -> flagged but valid:
```bash
curl -X POST http://localhost:8080/api/transactions/validate \
  -H "Content-Type: application/json" \
  -d "{\"transferMode\":\"NORMAL\",\"amount\":800}"
```

Invalid input (missing amount) -> 400:
```json
{"timestamp":"...","status":400,"error":"Validation failed",
 "fieldErrors":{"amount":"amount is required"}}
```

## Design notes
- `dto/TransactionRequest.java`  - API input; Bean Validation on required fields
- `dto/TransactionResponse.java` - API output; the rule outcome only
- `model/Transaction.java`       - internal Drools fact (mutated by the rules); never exposed at the API
- `config/DroolsConfig.java`     - builds the KieContainer via the public KieResources API
- `service/TransactionRuleService.java` - maps request -> fact, fires rules in a fresh KieSession (disposed per call), returns response
- `controller/TransactionController.java` - `@Valid` request, delegates to the service
- `controller/GlobalExceptionHandler.java` - clean 400 on validation failure
- `resources/rules/transaction-rules.drl` - the rules

## Tests
```bash
mvn test
```
- `TransactionRuleServiceTest` - rule logic
- `TransactionControllerTest`  - endpoint happy path + validation 400

## Notes
- Confirm the exact patch versions resolve (spring-boot 3.5.10, drools 10.2.0);
  bump to the latest patch if either fails to download.
- Drools 10 requires Java 17+, satisfied by Java 21.

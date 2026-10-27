# FinSecure Pay – Transaction Validation API

A Spring Boot service that checks whether a payment transaction is valid before it is processed.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/health` | Liveness check; returns status and app version |
| POST | `/validate` | Validates a transaction: `200` if valid, `422` with reasons if not |
| GET | `/actuator/health/liveness` | Kubernetes liveness probe |
| GET | `/actuator/health/readiness` | Kubernetes readiness probe |
| GET | `/actuator/prometheus` | Metrics for Prometheus |

## Validation rules

1. `fromAccount` and `toAccount` are required
2. Account IDs must match `ACC` + 5–10 digits (e.g. `ACC12345`)
3. Sender and receiver cannot be the same account
4. Amount must be greater than 0
5. Amount cannot exceed the configured maximum (default 10,000)
6. Amount can have at most 2 decimal places
7. Currency must be in the configured list (default SGD, USD, EUR, MYR)

Rules 5 and 7 are configurable through environment variables (`MAX_AMOUNT`, `ALLOWED_CURRENCIES`), which the Kubernetes ConfigMap sets.

## Prerequisites

- Java 21 (e.g. Eclipse Temurin): check with `java -version`
- Maven 3.9+: check with `mvn -version`

## Build and test

```bash
mvn clean verify
```

Test reports are written to `target/surefire-reports/`. The runnable jar is `target/app.jar`.

## Run locally

```bash
mvn spring-boot:run
# or
java -jar target/app.jar
```

## Try it

```bash
curl http://localhost:8080/health

# Valid transaction -> 200
curl -i -X POST http://localhost:8080/validate \
  -H "Content-Type: application/json" \
  -d '{"fromAccount":"ACC12345","toAccount":"ACC67890","amount":250.00,"currency":"SGD"}'

# Invalid transaction -> 422 with reasons
curl -i -X POST http://localhost:8080/validate \
  -H "Content-Type: application/json" \
  -d '{"fromAccount":"ACC12345","toAccount":"ACC12345","amount":-5,"currency":"JPY"}'
```

On Windows PowerShell, use `curl.exe` instead of `curl`, and escape the inner quotes or use `Invoke-RestMethod`.

## Project layout

```
src/main/java/com/finsecure/validator/
  FinSecureApplication.java          app entry point
  controller/TransactionController   HTTP endpoints, log masking
  service/TransactionValidator       business rules
  model/TransactionRequest           request JSON
  model/ValidationResult             response JSON
src/test/java/...                    JUnit 5 tests (rules + endpoints)
```

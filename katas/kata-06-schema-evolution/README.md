# Kata 6 — The Schema Evolution

**Difficulty:** 5/5

## Context

Your order fulfillment workflow has been running in production for six months. There are currently 500 running workflow executions (long-running orders with approval steps). You need to deploy a new version that adds a fraud check step between payment authorization and inventory reservation. Running workflows must not break.

## Challenge

1. Add a new `fraudCheck(order, payment)` activity between payment and inventory
2. Use `Workflow.getVersion()` so running workflows replay correctly
3. Add a new required field (`riskScore`) to `PaymentResult` without breaking deserialization of existing results
4. Deploy with zero downtime

## Constraints

- Existing running workflows must continue without interruption
- New workflows must execute fraudCheck
- Old DTO payloads (without `riskScore`) must deserialize successfully

## Try it

```bash
mvn spring-boot:run
curl -X POST 'localhost:8086/api/orders?orderId=ord-1&amount=42'
```

After implementing the version branch, run a few orders. Then simulate "old running workflows" by:
1. Comment out your `getVersion` branch (revert to pre-change code).
2. Start a workflow (this represents an in-flight pre-deployment run).
3. Restore the `getVersion` branch.
4. Run more workflows. The old one still completes correctly thanks to `DEFAULT_VERSION`.

## Solution outline

```java
int v = Workflow.getVersion(
        "add-fraud-check",
        Workflow.DEFAULT_VERSION,
        1);

if (v >= 1) {
    if (!activities.fraudCheck(orderId, payment)) {
        throw ApplicationFailure.newNonRetryableFailure("Fraud", "FraudDetected");
    }
}
```

Backwards-compatible DTOs use `@JsonIgnoreProperties(ignoreUnknown = true)` + new fields with default values — see `PaymentResult.java`.

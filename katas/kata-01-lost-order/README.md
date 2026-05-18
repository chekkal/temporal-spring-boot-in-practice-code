# Kata 1 — The Lost Order

**Difficulty:** 2/5

## Context (from the book)

A customer places an order, but the system crashes between payment authorization and inventory reservation. The customer is charged, but their order is never fulfilled. The support team has to manually reconcile the payment and resend the order. This happens three times a week.

## Challenge

Implement a basic `OrderFulfillmentWorkflow` with three activities:

1. `authorizePayment(orderId)` — returns a `PaymentConfirmation`
2. `reserveInventory(orderId, items)` — returns a `ReservationId`
3. `sendConfirmation(orderId, email)` — sends an email notification

The workflow must guarantee that if any step fails, the customer is not left in an inconsistent state. If `reserveInventory` fails, the payment authorization must be voided.

## Constraints

- Use the Temporal Java SDK with Spring Boot
- Activities are Spring `@Component` beans
- Workflow id must be the order id (for deduplication)
- 30s activity timeout, max 3 retries

## Where to start

Open `src/main/java/com/example/kata01/OrderFulfillmentWorkflowImpl.java` and follow the TODO. The scaffolding (Spring app, `TemporalConfig`, controller, in-memory activity impl) is already wired.

## Run

```bash
# from the repo root, start Temporal once
cd ../../order-platform/docker && docker compose up -d
# Then
cd ../../katas/kata-01-lost-order && mvn spring-boot:run
# Hit the endpoint:
curl -X POST localhost:8081/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"ord-1","items":["sku-a","sku-b"],"customerEmail":"a@b.c"}'
# Force compensation:
curl -X POST localhost:8081/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"ord-2","items":["fail-x"],"customerEmail":"a@b.c"}'
```

## Solution outline (from the book)

1. Try-catch in `fulfill`: catch `Exception` from `reserveInventory`, call `voidAuthorization`, rethrow.
2. Or use `Saga` + `addCompensation(activities::voidAuthorization, authId)` and `saga.compensate()` in the catch.

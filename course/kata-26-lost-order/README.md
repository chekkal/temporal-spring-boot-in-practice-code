# Lecture 26 — Kata: The Lost Order

**Difficulty:** 2/5 — basic workflow with compensation

## Scenario

Orders go through three steps: charge the payment, reserve inventory, create the shipment. When the
application crashes right after the charge, inventory is never reserved and nothing ships. The customer
has been charged $99.00 for nothing, support finds out from a ticket hours or days later, and the refund
is manual. It happens every few weeks at peak traffic.

## Challenge

Implement `OrderFulfillmentWorkflow` so that a customer can never be charged for an order that will
not ship.

| Requirement    | Detail                                                                  |
|----------------|-------------------------------------------------------------------------|
| 3 activities   | `chargePayment()`, `reserveInventory()`, `createShipment()`             |
| Compensation   | If a step fails, undo every completed step in reverse order             |
| Workflow ID    | `"order-" + orderId` (business key, gives deduplication)                |
| Timeout        | 30 s `StartToCloseTimeout` per activity                                 |
| Retries        | 3 attempts per activity                                                 |
| Activities     | Spring `@Component` beans                                               |
| Query          | `@QueryMethod getStatus()` returns the current `OrderStatus`            |

| Step | Forward                       | Compensation                          |
|------|-------------------------------|---------------------------------------|
| 1    | `payment.chargePayment`       | `payment.refundPayment(paymentId)`    |
| 2    | `inventory.reserveInventory`  | `inventory.releaseInventory(request)` |
| 3    | `shipping.createShipment`     | none (last step)                      |

`OrderStatus` moves through `STARTED → CHARGING_PAYMENT → RESERVING_INVENTORY → CREATING_SHIPMENT →
COMPLETED`, or `… → COMPENSATING → FAILED`. A failed order is **returned** as `OrderResult.failed(...)`;
the workflow itself completes normally.

## Constraints

- Plain Temporal Java SDK with Spring Boot (`TemporalConfig` wires the client, worker and activities).
- Catch `ActivityFailure` in the workflow, not `Exception`.
- Keep the compensations in a `List<Runnable>` and run them in reverse.

## Where to start

Open `starter/src/main/java/com/example/course/kata26/OrderFulfillmentWorkflowImpl.java` and follow the
TODO in `process()`. Everything else (interfaces, DTOs, activity beans with in-memory fakes, controller,
`TemporalConfig`) is already written.

## Check your work with the tests

The starter ships with the same tests as the solution. They run against Temporal's in-process test
server, so you need neither Docker nor a running Temporal server. They are skipped by default because
they fail until the kata is solved:

```bash
# from the repo root
mvn -f course/kata-26-lost-order/starter/pom.xml test -DskipTests=false
```

What they check: the happy path, each failure point with the exact compensations in the exact order,
the 3-attempt retry, the `getStatus()` query while the shipment step is running, the idempotent
`chargePayment`, and the REST controller end to end through a `@SpringBootTest`.

## Run the app

```bash
# 1. Start Temporal once (from the repo root)
cd order-platform/docker && docker compose up -d && cd ../..
#    or: temporal server start-dev

# 2. Run the starter (or the solution) on port 8097
mvn -f course/kata-26-lost-order/starter/pom.xml spring-boot:run
```

### Failure triggers

The activity fakes fail on purpose when the request asks them to:

| Trigger                                   | What fails                  | Expected compensations            |
|-------------------------------------------|-----------------------------|-----------------------------------|
| `"paymentMethod": "declined-card"`        | `chargePayment` (3 attempts) | none                             |
| an item named `"out-of-stock"`            | `reserveInventory` (3 attempts) | `refundPayment`               |
| `orderId` containing `carrier-down`       | `createShipment` (3 attempts) | `releaseInventory`, then `refundPayment` |

Retries use the default back-off (1 s, then 2 s), so a failing order takes about 3 seconds to finish.

### curl

```bash
# Happy path
curl -X POST localhost:8097/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"1001","customerEmail":"jane@example.com","amount":99.00,"paymentMethod":"visa","items":["sku-a","sku-b"]}'
curl localhost:8097/api/orders/order-1001/status     # "COMPLETED"
curl localhost:8097/api/orders/order-1001/result     # {"orderId":"1001","status":"COMPLETED",...}

# Same order again: same workflow id, Temporal refuses it → HTTP 409
curl -i -X POST localhost:8097/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"1001","customerEmail":"jane@example.com","amount":99.00,"paymentMethod":"visa","items":["sku-a","sku-b"]}'

# Payment declined: nothing to undo
curl -X POST localhost:8097/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"1002","customerEmail":"jane@example.com","amount":99.00,"paymentMethod":"declined-card","items":["sku-a"]}'
curl localhost:8097/api/orders/order-1002/result     # "status":"FAILED"

# Out of stock: payment is refunded
curl -X POST localhost:8097/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"1003","customerEmail":"jane@example.com","amount":99.00,"paymentMethod":"visa","items":["sku-a","out-of-stock"]}'
curl localhost:8097/api/orders/order-1003/result

# Carrier down: inventory released, then payment refunded
curl -X POST localhost:8097/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"carrier-down-1004","customerEmail":"jane@example.com","amount":99.00,"paymentMethod":"visa","items":["sku-a"]}'
curl localhost:8097/api/orders/order-carrier-down-1004/status   # "COMPENSATING" for a few seconds, then "FAILED"
```

`/result` returns HTTP 202 while the workflow is still running. Watch the application log for the
compensation order (`[inventory] released …` before `[payment] refunding …`), and open the Temporal UI
(http://localhost:8233, for both docker compose and `start-dev`) to see the event history.

## Solution

`solution/` holds the complete implementation from the lecture's solution slides. Try the kata first.

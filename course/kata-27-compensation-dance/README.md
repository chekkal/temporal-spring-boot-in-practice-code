# Lecture 27 — Kata: The Compensation Dance

**Difficulty:** 3/5 — full saga with five steps

## Scenario

A five-step fulfillment pipeline (validate, charge, reserve, ship, notify) is watched by a cron job that
runs every 30 minutes to find stuck orders. Detection takes up to 30 minutes, compensation is done by the
support team running scripts, nobody can tell which step failed without digging through logs, and
compensation is regularly partial: inventory released but the payment never refunded, or the reverse.

## Challenge

Replace the cron job with a `FullOrderSagaWorkflow`.

| Requirement          | Detail                                                                     |
|----------------------|----------------------------------------------------------------------------|
| 5 forward steps      | validate, charge, reserve, ship, notify                                    |
| 4 compensations      | `cancelShipment`, `releaseInventory`, `refundPayment`, `logValidationReversal` |
| Compensation order   | Strict reverse order of the completed steps                                |
| Status tracking      | `@QueryMethod getStatus()` returns `SagaStatus(currentStep, completedSteps, failureReason)` |
| Compensation retry   | Aggressive: 5 attempts, 500 ms initial interval (`compensationOpts`)       |
| Forward retry        | Conservative: 3 attempts, 1 s initial interval (`forwardOpts`)             |
| DoNotRetry (forward) | `PaymentDeclinedException`, `ValidationException`                          |

| Step | `recordStep(...)`      | Forward (`forward` stub)   | Compensation (`compensate` stub)   |
|------|------------------------|----------------------------|------------------------------------|
| 1    | `VALIDATING`           | `validateOrder`            | `logValidationReversal(request)`   |
| 2    | `CHARGING_PAYMENT`     | `chargePayment`            | `refundPayment(paymentId)`         |
| 3    | `RESERVING_INVENTORY`  | `reserveInventory`         | `releaseInventory(request)`        |
| 4    | `CREATING_SHIPMENT`    | `createShipment`           | `cancelShipment(trackingId)`       |
| 5    | `SENDING_NOTIFICATION` | `sendNotification`         | none (you cannot unsend an email)  |

On failure, `compensate()` must attempt **every** compensation even if one of them fails. If any
compensation still fails after its 5 attempts, `currentStep` becomes `COMPENSATION_PARTIAL` and
`alertOperations(request, errors)` is called; otherwise it becomes `COMPENSATION_COMPLETE`.

## Constraints

- Two `ActivityOptions` sets and two stubs on the same `OrderActivities` interface.
- Catch `ActivityFailure` around each compensation so one failure does not stop the others.
- `setDoNotRetry(...)` matches the failure **type** string. The activity fakes throw
  `ApplicationFailure.newFailure(message, "PaymentDeclinedException")` (and `"ValidationException"`)
  so the type matches the slide's names. A plain Java exception would carry its fully-qualified class
  name as the type (`com.example...PaymentDeclinedException`) and would not match.

## Where to start

Open `starter/src/main/java/com/example/course/kata27/FullOrderSagaWorkflowImpl.java`. The retry options,
the two stubs, the status fields, `getStatus()` and `recordStep()` are given. Implement `process()` and
`compensate()` following the two TODO blocks.

## Check your work with the tests

The starter has the solution's tests. They use Temporal's in-process test server (no Docker, no server;
retry back-offs are skipped). Skipped by default because they fail until the kata is solved:

```bash
# from the repo root
mvn -f course/kata-27-compensation-dance/starter/pom.xml test -DskipTests=false
```

They check the happy path; that validation and payment-declined failures run exactly one attempt;
the exact compensation sequence for a failure at every step; that a failing refund is attempted 5 times,
the remaining compensations still run, the status is `COMPENSATION_PARTIAL` and operations are alerted;
the query while a step is running; and the REST controller end to end through a `@SpringBootTest`.

## Run the app

```bash
# 1. Start Temporal once (from the repo root)
cd order-platform/docker && docker compose up -d && cd ../..
#    or: temporal server start-dev

# 2. Run the starter (or the solution) on port 8098
mvn -f course/kata-27-compensation-dance/starter/pom.xml spring-boot:run
```

### Failure triggers

| Trigger                                  | What fails                               | Expected compensations (in order)                                     | Final `currentStep`     |
|------------------------------------------|------------------------------------------|------------------------------------------------------------------------|-------------------------|
| `"items": []` or `"amount": 0`           | `validateOrder`, 1 attempt (DoNotRetry)  | none                                                                   | `COMPENSATION_COMPLETE` |
| `"paymentMethod": "declined-card"`       | `chargePayment`, 1 attempt (DoNotRetry)  | `logValidationReversal`                                                | `COMPENSATION_COMPLETE` |
| an item named `"out-of-stock"`           | `reserveInventory`, 3 attempts           | `refundPayment`, `logValidationReversal`                               | `COMPENSATION_COMPLETE` |
| `orderId` containing `carrier-down`      | `createShipment`, 3 attempts             | `releaseInventory`, `refundPayment`, `logValidationReversal`           | `COMPENSATION_COMPLETE` |
| `orderId` containing `email-down`        | `sendNotification`, 3 attempts           | `cancelShipment`, `releaseInventory`, `refundPayment`, `logValidationReversal` | `COMPENSATION_COMPLETE` |
| `orderId` containing `refund-fails` (combine with a later failure, e.g. `carrier-down-refund-fails-7`) | `refundPayment` fails all 5 attempts | the others still run, then `alertOperations` | `COMPENSATION_PARTIAL` |

Note on notifications: the speaker notes say a failed notification leaves the order complete, but the
slide code (reproduced here) calls `sendNotification` inside the same `try`, so a notification that fails
all 3 attempts triggers the full compensation. That is also the only path on which the registered
`cancelShipment` compensation can ever run. If you want the "order stays complete" behaviour, wrap
`sendNotification` in its own `try/catch (ActivityFailure e)`; the `email-down` test will then tell you
it no longer matches the lecture.

### curl

```bash
# Happy path
curl -X POST localhost:8098/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"2001","customerEmail":"jane@example.com","amount":149.00,"paymentMethod":"visa","items":["sku-a"]}'
curl localhost:8098/api/orders/order-2001/status
# {"currentStep":"COMPLETED","completedSteps":["VALIDATING","CHARGING_PAYMENT","RESERVING_INVENTORY","CREATING_SHIPMENT","SENDING_NOTIFICATION"],"failureReason":null}
curl localhost:8098/api/orders/order-2001/result

# Validation failure (no items): one attempt, nothing to undo
curl -X POST localhost:8098/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"2002","customerEmail":"jane@example.com","amount":149.00,"paymentMethod":"visa","items":[]}'
curl localhost:8098/api/orders/order-2002/status

# Payment declined: one attempt, validation reversed
curl -X POST localhost:8098/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"2003","customerEmail":"jane@example.com","amount":149.00,"paymentMethod":"declined-card","items":["sku-a"]}'
curl localhost:8098/api/orders/order-2003/status

# Out of stock
curl -X POST localhost:8098/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"2004","customerEmail":"jane@example.com","amount":149.00,"paymentMethod":"visa","items":["out-of-stock"]}'
curl localhost:8098/api/orders/order-2004/status

# Carrier down
curl -X POST localhost:8098/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"carrier-down-2005","customerEmail":"jane@example.com","amount":149.00,"paymentMethod":"visa","items":["sku-a"]}'
curl localhost:8098/api/orders/order-carrier-down-2005/status

# Email service down: all four compensations
curl -X POST localhost:8098/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"email-down-2006","customerEmail":"jane@example.com","amount":149.00,"paymentMethod":"visa","items":["sku-a"]}'
curl localhost:8098/api/orders/order-email-down-2006/status

# Carrier down AND the refund service is down: partial compensation + ops alert (takes ~10 s)
curl -X POST localhost:8098/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"carrier-down-refund-fails-2007","customerEmail":"jane@example.com","amount":149.00,"paymentMethod":"visa","items":["sku-a"]}'
curl localhost:8098/api/orders/order-carrier-down-refund-fails-2007/status   # "currentStep":"COMPENSATION_PARTIAL"
```

`/result` returns HTTP 202 while the workflow is still running; a second POST with the same `orderId`
returns 409. In the log, compensations appear as `[-4] …`, `[-3] …`, `[-2] …`, `[-1] …`, and a partial
compensation ends with `[OPS ALERT] …`.

## Solution

`solution/` holds the complete implementation from the lecture's solution slides. Try the kata first.

# Lecture 28 — Kata: The Approval Bottleneck

**Difficulty:** 3/5 — signal-based approval with timeout and escalation

## Scenario

Orders over $5000 need a manager's approval. Today they are written to the `orders` table as
`AWAITING_APPROVAL`, and a `@Scheduled` job polls the table every minute to find orders that have waited
more than 48 hours. That is 60 queries per hour per instance, up to a minute of delay on the timeout,
another scheduled job for the 24-hour escalation, and yet another query to answer "what is pending?".

## Challenge

Build an `ApprovalWorkflow` that waits for a human decision at zero cost.

| Requirement    | Detail                                                                   |
|----------------|--------------------------------------------------------------------------|
| Threshold      | Orders over $5000 need approval; $5000 and below are fulfilled directly  |
| Signal         | `@SignalMethod approveOrder(ApprovalDecision)`                           |
| Query          | `@QueryMethod getApprovalStatus()` returns `ApprovalStatus`              |
| First wait     | 24 hours for the initial approval                                        |
| Escalation     | No answer after 24 h → `notifyEscalation` (the manager's manager)        |
| Second wait    | 24 more hours after the escalation                                       |
| Auto-reject    | No answer after 48 h in total → `REJECTED_TIMEOUT`, `notifyRejection(request, "48h timeout")` |
| Approval REST  | `POST /api/orders/{workflowId}/approve` sends the signal                 |
| Processing     | Approved → `recordApproval`, then the standard fulfillment from Lecture 26 |

```java
public record ApprovalDecision(boolean approved, String approvedBy, String reason) {}
public record ApprovalStatus(String orderId, String phase, Instant waitingSince,
                             boolean escalated, ApprovalDecision decision) {}
```

`phase` values: `WAITING_APPROVAL`, `ESCALATED`, `APPROVED`, `REJECTED`, `REJECTED_TIMEOUT`, plus
`AUTO_APPROVED` for orders of $5000 or less, and `COMPLETED` / `FULFILLMENT_FAILED` once fulfillment
has finished.

## Constraints

- Use `Workflow.await(Duration, condition)`, never polling or `Thread.sleep`.
- Use `Workflow.currentTimeMillis()` for `waitingSince`, never `Instant.now()`.
- The signal handler only stores the decision; the `await` condition does the rest.

## Where to start

Open `starter/src/main/java/com/example/course/kata28/ApprovalWorkflowImpl.java`. The activity stubs,
the state fields, `getApprovalStatus()` and `executeOrderFulfillment()` (charge, reserve, ship with
compensation, as in Lecture 26) are given. Implement `processWithApproval()`, the `approveOrder()`
signal handler, and a `handleDecision()` helper, following the TODO.

## Check your work with the tests

The starter has the solution's tests. They use Temporal's in-process test server with time skipping, so
`testEnv.sleep(Duration.ofHours(25))` jumps a day ahead instantly. Skipped by default because they fail
until the kata is solved:

```bash
# from the repo root
mvn -f course/kata-28-approval-bottleneck/starter/pom.xml test -DskipTests=false
```

They cover: an order under $5000 (and exactly $5000) fulfilled without approval; approval within 24 h
with no escalation; no answer for 24 h → escalation, then approval; rejection by the manager; no answer
for 48 h → automatic `REJECTED_TIMEOUT`; approval followed by a fulfillment failure with compensation;
the query at each stage; and the REST endpoints end to end through a `@SpringBootTest`.

## Run the app

```bash
# 1. Start Temporal once (from the repo root)
cd order-platform/docker && docker compose up -d && cd ../..
#    or: temporal server start-dev

# 2. Run the starter (or the solution) on port 8099
mvn -f course/kata-28-approval-bottleneck/starter/pom.xml spring-boot:run
```

### curl

```bash
# Small order ($120): fulfilled at once, no approval
curl -X POST localhost:8099/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"3001","customerEmail":"jane@example.com","amount":120.00,"paymentMethod":"visa","items":["sku-a"]}'
curl localhost:8099/api/orders/order-3001/result            # "status":"COMPLETED"

# Big order ($7500): waits for a decision
curl -X POST localhost:8099/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"3002","customerEmail":"jane@example.com","amount":7500.00,"paymentMethod":"visa","items":["sku-a"]}'
curl localhost:8099/api/orders/order-3002/approval-status   # "phase":"WAITING_APPROVAL","waitingSince":"…"
curl localhost:8099/api/orders/order-3002/result            # HTTP 202, still running

# Approve it
curl -X POST localhost:8099/api/orders/order-3002/approve -H 'Content-Type: application/json' \
  -d '{"approved":true,"approvedBy":"alice","reason":"Known customer"}'
curl localhost:8099/api/orders/order-3002/approval-status   # "phase":"COMPLETED","decision":{…"approvedBy":"alice"…}
curl localhost:8099/api/orders/order-3002/result            # "status":"COMPLETED"

# Reject another one
curl -X POST localhost:8099/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"3003","customerEmail":"jane@example.com","amount":9000.00,"paymentMethod":"visa","items":["sku-a"]}'
curl -X POST localhost:8099/api/orders/order-3003/approve -H 'Content-Type: application/json' \
  -d '{"approved":false,"approvedBy":"alice","reason":"Budget exceeded"}'
curl localhost:8099/api/orders/order-3003/result            # "status":"REJECTED","message":"Budget exceeded"

# Approved, but fulfillment fails (out of stock): payment refunded, phase FULFILLMENT_FAILED
curl -X POST localhost:8099/api/orders -H 'Content-Type: application/json' \
  -d '{"orderId":"3004","customerEmail":"jane@example.com","amount":6000.00,"paymentMethod":"visa","items":["out-of-stock"]}'
curl -X POST localhost:8099/api/orders/order-3004/approve -H 'Content-Type: application/json' \
  -d '{"approved":true,"approvedBy":"alice","reason":"ok"}'
curl localhost:8099/api/orders/order-3004/approval-status
```

After approval, fulfillment uses the same failure triggers as Lecture 26: `"paymentMethod":"declined-card"`
fails the charge, an item named `"out-of-stock"` fails the reservation, and an `orderId` containing
`carrier-down` fails the shipment.

### Seeing the escalation and the timeout

On a real server the timers are real: escalation after 24 hours, rejection after 48. The tests cover
both with time skipping. To watch them with curl, temporarily change both `Duration.ofHours(24)` in
`ApprovalWorkflowImpl` to `Duration.ofMinutes(1)`, restart, place a big order and poll
`/approval-status`: `ESCALATED` after one minute, `REJECTED_TIMEOUT` after two. In the Temporal UI
(http://localhost:8233) the workflow shows a pending timer and no activity while it waits.

## Solution

`solution/` holds the complete implementation from the lecture's solution slides. Try the kata first.

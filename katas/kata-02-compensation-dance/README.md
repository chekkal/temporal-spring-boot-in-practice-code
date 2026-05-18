# Kata 2 — The Compensation Dance

**Difficulty:** 3/5

## Context

Your order fulfillment process has five steps, and failure at any step requires compensating all previously completed steps in reverse order. The team's current implementation uses database flags and a cron job to detect stuck orders, but the cron job itself has failed twice this month.

## Challenge

Implement a full saga pattern with these steps:

| Step | Action | Compensation |
|------|--------|-------------|
| 1 | `validateOrder()` | None |
| 2 | `authorizePayment()` | `voidPayment()` |
| 3 | `reserveInventory()` | `releaseInventory()` |
| 4 | `scheduleShipment()` | `cancelShipment()` |
| 5 | `notifyCustomer()` | None |

Compensations must run in reverse order. Each compensation gets its own retry policy.

## Constraints

- Each compensation must attempt **at least 5 retries** (compensations are critical)
- Use `Workflow.getLogger()` for progress logging
- Expose the current step via `@QueryMethod`

## Try it

```bash
curl -X POST localhost:8082/api/orders/ord-1                 # happy path
curl -X POST localhost:8082/api/orders/fail-step-3-x         # step 3 fails → compensate 2
curl -X POST localhost:8082/api/orders/fail-step-4-x         # step 4 fails → compensate 3, 2
curl    localhost:8082/api/orders/fail-step-4-x/status       # see current state via query
```

Watch the compensation order in the logs — `[-3] released ...` then `[-2] voided ...`.

## Solution outline

1. Maintain a `List<Runnable>` of registered compensations *or* use `io.temporal.workflow.Saga`.
2. After each successful forward activity, register its inverse.
3. In the catch block, set `compensating = true`, iterate compensations in reverse (or call `saga.compensate()`), and rethrow.
4. Use a second `ActivityStub` configured with `.setMaximumAttempts(5)` exclusively for compensations.

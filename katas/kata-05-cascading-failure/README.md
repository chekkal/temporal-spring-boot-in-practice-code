# Kata 5 — The Cascading Failure

**Difficulty:** 4/5

## Context

Your payment gateway occasionally has outages lasting 5-30 minutes. During those outages, all order fulfillment workflows fail at the payment step and trigger compensations — even though the orders are perfectly valid and would succeed if retried after the outage.

## Challenge

1. Retry payment with exponential backoff: 1s → 2s → 4s → 8s → ... up to 60s max
2. Up to **5** retry attempts on the primary
3. Distinguish retryable (GatewayTimeout, 503) from non-retryable (PaymentDeclined, InsufficientFunds)
4. Use heartbeating
5. Fall back to a **secondary** provider after the primary exhausts retries

## Constraints

- Non-retryable failures must fail immediately (`setDoNotRetry`)
- Retry strategy lives in `ActivityOptions` / `RetryOptions`, not in custom code
- Fallback logic lives in the **workflow**, not the activity

## Try it

```bash
# Happy path (succeeds on primary)
curl -X POST 'localhost:8085/api/pay?orderId=ok-1&amount=42'

# Primary fails transiently 3x then succeeds (no fallback needed)
curl -X POST 'localhost:8085/api/pay?orderId=flaky-1&amount=42'

# Primary always down → workflow falls back to secondary
curl -X POST 'localhost:8085/api/pay?orderId=always-down-1&amount=42'

# Card declined → fails fast, NO fallback
curl -X POST 'localhost:8085/api/pay?orderId=declined-1&amount=42'
```

## Solution outline

```java
try {
    return primary.charge(orderId, amount);
} catch (ActivityFailure af) {
    if (af.getCause() instanceof ApplicationFailure appF
            && "PaymentDeclined".equals(appF.getType())) {
        throw af; // business failure — don't fall back
    }
    return secondary.charge(orderId, amount); // gateway outage — try alternate provider
}
```

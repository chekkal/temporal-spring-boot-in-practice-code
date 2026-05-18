package com.example.kata05;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Demo failure model:
 *   - paymentMethod "declined"  → non-retryable PaymentDeclined
 *   - paymentMethod "always-down" → always throws retryable GatewayTimeout
 *   - paymentMethod "flaky"     → throws GatewayTimeout 3 times then succeeds
 *   - anything else             → succeeds
 */
@Component
public class PrimaryPaymentActivityImpl implements PrimaryPaymentActivity {

    private static final Logger log = LoggerFactory.getLogger(PrimaryPaymentActivityImpl.class);
    private final AtomicInteger flakyAttempts = new AtomicInteger();

    @Override
    public String charge(String orderId, double amount) {
        if (orderId.contains("declined")) {
            throw ApplicationFailure.newNonRetryableFailure(
                    "Card declined", "PaymentDeclined");
        }
        if (orderId.contains("always-down")) {
            throw ApplicationFailure.newFailure(
                    "Primary gateway 503", "GatewayTimeout");
        }
        if (orderId.contains("flaky")) {
            if (flakyAttempts.incrementAndGet() < 3) {
                throw ApplicationFailure.newFailure(
                        "Primary gateway transient timeout", "GatewayTimeout");
            }
        }
        String tx = "primary-" + UUID.randomUUID();
        log.info("primary charged order={} amount={} tx={}", orderId, amount, tx);
        return tx;
    }
}

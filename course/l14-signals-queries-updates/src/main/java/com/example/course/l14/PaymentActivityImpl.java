package com.example.course.l14;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** In-memory payment fake. */
@Component
public class PaymentActivityImpl implements PaymentActivity {

    private static final Logger log = LoggerFactory.getLogger(PaymentActivityImpl.class);

    private final ConcurrentMap<String, PaymentResult> charges = new ConcurrentHashMap<>();

    @Override
    public PaymentResult charge(Order order) {
        // Demo failure trigger: a payment method containing "decline" is rejected (not retried).
        if (order.getPaymentMethod() != null && order.getPaymentMethod().contains("decline")) {
            throw ApplicationFailure.newNonRetryableFailure(
                    "Payment declined for order " + order.getId(), "PaymentDeclined");
        }
        PaymentResult result = new PaymentResult("pay-" + UUID.randomUUID(), order.getTotal());
        charges.put(result.transactionId(), result);
        log.info("Charged order={} amount={} tx={}", order.getId(), order.getTotal(), result.transactionId());
        return result;
    }

    @Override
    public void refund(PaymentResult payment) {
        if (charges.remove(payment.transactionId()) != null) {
            log.info("Refunded tx={} amount={}", payment.transactionId(), payment.amount());
        } else {
            log.info("Refund for unknown/already-refunded tx={} (no-op)", payment.transactionId());
        }
    }
}

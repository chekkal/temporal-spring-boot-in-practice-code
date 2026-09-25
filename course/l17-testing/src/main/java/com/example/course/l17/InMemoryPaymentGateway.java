package com.example.course.l17;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** In-memory fake of the payment provider. */
@Component
public class InMemoryPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(InMemoryPaymentGateway.class);

    private final Set<String> charges = ConcurrentHashMap.newKeySet();

    @Override
    public ChargeResult charge(String paymentToken) {
        // Demo failure trigger: a token containing "decline" is declined.
        if (paymentToken != null && paymentToken.contains("decline")) {
            throw new IllegalArgumentException("Card declined: " + paymentToken);
        }
        String paymentId = "PAY-" + UUID.randomUUID();
        charges.add(paymentId);
        log.info("Charged token={} paymentId={}", paymentToken, paymentId);
        return new ChargeResult(paymentId);
    }

    @Override
    public void refund(String paymentId) {
        if (charges.remove(paymentId)) {
            log.info("Refunded paymentId={}", paymentId);
        } else {
            log.info("Refund for unknown/already-refunded paymentId={} (no-op)", paymentId);
        }
    }
}

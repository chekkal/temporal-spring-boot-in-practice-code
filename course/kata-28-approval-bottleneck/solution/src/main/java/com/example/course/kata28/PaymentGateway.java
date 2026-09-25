package com.example.course.kata28;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory fake payment provider.
 * Failure trigger: paymentMethod "declined-card" is always declined.
 */
@Component
public class PaymentGateway {

    public static final String DECLINED_CARD = "declined-card";

    private static final Logger log = LoggerFactory.getLogger(PaymentGateway.class);
    private final ConcurrentMap<String, BigDecimal> charges = new ConcurrentHashMap<>();

    public PaymentResult charge(BigDecimal amount, String paymentMethod) {
        if (DECLINED_CARD.equals(paymentMethod)) {
            throw new IllegalStateException("Card declined for payment method " + paymentMethod);
        }
        String paymentId = "pay-" + UUID.randomUUID();
        charges.put(paymentId, amount);
        log.info("gateway charged {} → {}", amount, paymentId);
        return new PaymentResult(paymentId, amount);
    }

    public void refund(String paymentId) {
        BigDecimal amount = charges.remove(paymentId);
        if (amount != null) {
            log.info("gateway refunded {} for {}", amount, paymentId);
        } else {
            log.info("gateway refund for {} ignored (unknown or already refunded)", paymentId);
        }
    }

    public boolean isCharged(String paymentId) {
        return charges.containsKey(paymentId);
    }
}

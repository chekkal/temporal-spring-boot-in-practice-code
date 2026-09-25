package com.example.course.kata28;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.stereotype.Component;

/** In-memory stand-in for a JPA repository. Keyed by orderId for the idempotency check. */
@Component
public class PaymentRepository {

    private final ConcurrentMap<String, Payment> byOrderId = new ConcurrentHashMap<>();

    public Optional<Payment> findByOrderId(String orderId) {
        return Optional.ofNullable(byOrderId.get(orderId));
    }

    public void save(Payment payment) {
        byOrderId.put(payment.orderId(), payment);
    }
}

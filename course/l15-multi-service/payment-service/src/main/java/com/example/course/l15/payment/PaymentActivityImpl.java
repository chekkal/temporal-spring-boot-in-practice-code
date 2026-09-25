package com.example.course.l15.payment;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.example.course.l15.api.Order;
import com.example.course.l15.api.PaymentActivity;
import com.example.course.l15.api.PaymentResult;
import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory stand-in for a payment gateway.
 *
 * Failure trigger: a paymentMethod containing "decline" (e.g. "card-decline") is rejected
 * with a non-retryable PaymentDeclined failure.
 */
@Component
public class PaymentActivityImpl implements PaymentActivity {

    private static final Logger log = LoggerFactory.getLogger(PaymentActivityImpl.class);

    public record Payment(String transactionId, String orderId, BigDecimal amount, String status) {}

    private final ConcurrentMap<String, Payment> payments = new ConcurrentHashMap<>();

    @Override
    public PaymentResult authorize(Order order) {
        String method = order.getPaymentMethod() == null ? "" : order.getPaymentMethod();
        if (method.contains("decline")) {
            log.info("declined order={} method={}", order.getId(), method);
            throw ApplicationFailure.newNonRetryableFailure(
                    "card declined for order " + order.getId(), "PaymentDeclined");
        }
        // Transaction id derived from the order id: a retried authorize returns the same result.
        String transactionId = "txn-" + order.getId();
        payments.putIfAbsent(transactionId,
                new Payment(transactionId, order.getId(), order.getAmount(), "AUTHORIZED"));
        log.info("authorized order={} txn={} amount={}", order.getId(), transactionId, order.getAmount());
        return new PaymentResult(transactionId, order.getId(), order.getAmount());
    }

    @Override
    public void refund(PaymentResult payment) {
        payments.computeIfPresent(payment.transactionId(),
                (id, p) -> new Payment(p.transactionId(), p.orderId(), p.amount(), "REFUNDED"));
        log.info("refunded order={} txn={}", payment.orderId(), payment.transactionId());
    }

    public Collection<Payment> payments() {
        return payments.values();
    }

    public Payment find(String transactionId) {
        return payments.get(transactionId);
    }
}

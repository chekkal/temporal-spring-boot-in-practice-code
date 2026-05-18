package com.example.order.activity;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.example.order.api.activity.PaymentActivity;
import com.example.order.api.model.Money;
import com.example.order.api.model.Order;
import com.example.order.api.model.PaymentResult;
import com.example.order.api.model.PaymentStatus;
import com.example.order.api.model.RefundResult;
import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory payment activity for the demo. A real implementation would call a payment
 * gateway (Stripe, Adyen, etc.) and persist records through a repository — see Chapter 14.
 */
@Component
public class PaymentActivityImpl implements PaymentActivity {

    private static final Logger log = LoggerFactory.getLogger(PaymentActivityImpl.class);

    private final ConcurrentMap<String, Money> authorizations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Money> captures = new ConcurrentHashMap<>();

    @Override
    public void validateOrder(Order order) {
        if (order == null || order.getId() == null || order.getId().isBlank()) {
            throw ApplicationFailure.newNonRetryableFailure("Order id is required", "ValidationError");
        }
        if (order.getTotal() == null || order.getTotal().getAmount().signum() <= 0) {
            throw ApplicationFailure.newNonRetryableFailure("Order total must be > 0", "ValidationError");
        }
        log.info("Validated order {}", order.getId());
    }

    @Override
    public PaymentResult authorize(Order order) {
        // Demo failure trigger: any payment method containing "decline" forces a non-retryable
        // failure so you can exercise the compensation path.
        if (order.getPaymentMethod() != null && order.getPaymentMethod().contains("decline")) {
            throw ApplicationFailure.newNonRetryableFailure(
                    "Payment declined by gateway", "PaymentDeclined");
        }
        String txId = "auth-" + UUID.randomUUID();
        authorizations.put(txId, order.getTotal());
        log.info("Authorized order={} amount={} tx={}", order.getId(), order.getTotal(), txId);
        return new PaymentResult(txId, order.getTotal(), PaymentStatus.AUTHORIZED);
    }

    @Override
    public PaymentResult capture(String authorizationId, Money amount) {
        if (authorizations.remove(authorizationId) == null) {
            throw ApplicationFailure.newNonRetryableFailure(
                    "Unknown authorization id: " + authorizationId, "UnknownAuthorization");
        }
        captures.put(authorizationId, amount);
        log.info("Captured authorization={} amount={}", authorizationId, amount);
        return new PaymentResult(authorizationId, amount, PaymentStatus.CAPTURED);
    }

    @Override
    public void voidAuthorization(String authorizationId) {
        if (authorizations.remove(authorizationId) != null) {
            log.info("Voided authorization={}", authorizationId);
        } else {
            log.info("Void called for unknown/already-voided authorization={} (idempotent no-op)", authorizationId);
        }
    }

    @Override
    public RefundResult refund(String transactionId, Money amount) {
        Money removed = captures.remove(transactionId);
        if (removed != null) {
            log.info("Refunded tx={} amount={}", transactionId, amount);
            return new RefundResult("refund-" + UUID.randomUUID(), PaymentStatus.REFUNDED);
        }
        log.info("Refund called for unknown/already-refunded tx={} (idempotent no-op)", transactionId);
        return new RefundResult(null, PaymentStatus.REFUNDED);
    }

    @Override
    public PaymentResult charge(Order order) {
        PaymentResult authorized = authorize(order);
        return capture(authorized.getTransactionId(), order.getTotal());
    }
}

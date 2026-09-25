package com.example.course.kata28;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PaymentActivityImpl implements PaymentActivity {

    private static final Logger log = LoggerFactory.getLogger(PaymentActivityImpl.class);

    @Autowired private PaymentGateway gateway;
    @Autowired private PaymentRepository repo;

    public PaymentActivityImpl() {
    }

    /** For tests that build the activity without Spring. */
    public PaymentActivityImpl(PaymentGateway gateway, PaymentRepository repo) {
        this.gateway = gateway;
        this.repo = repo;
    }

    @Override
    public PaymentResult chargePayment(OrderRequest request) {
        // Idempotency check (Layer 2): a retried activity must not charge twice.
        Optional<Payment> existing = repo.findByOrderId(request.getOrderId());
        if (existing.isPresent()) {
            log.info("[payment] order {} already charged, returning {}",
                    request.getOrderId(), existing.get().toResult().getPaymentId());
            return existing.get().toResult();
        }

        PaymentResult result = gateway.charge(request.getAmount(), request.getPaymentMethod());
        repo.save(new Payment(request.getOrderId(), result));
        log.info("[payment] charged order {} → {}", request.getOrderId(), result.getPaymentId());
        return result;
    }

    @Override
    public void refundPayment(String paymentId) {
        log.info("[payment] refunding {}", paymentId);
        gateway.refund(paymentId);
    }
}

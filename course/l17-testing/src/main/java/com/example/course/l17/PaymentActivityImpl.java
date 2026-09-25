package com.example.course.l17;

import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PaymentActivityImpl implements PaymentActivity {

    private static final Logger log = LoggerFactory.getLogger(PaymentActivityImpl.class);

    private final PaymentGateway paymentGateway;

    /** Used by the plain JUnit tests: {@code new PaymentActivityImpl()} as on the slides. */
    public PaymentActivityImpl() {
        this(new InMemoryPaymentGateway());
    }

    @Autowired
    public PaymentActivityImpl(PaymentGateway paymentGateway) {
        this.paymentGateway = paymentGateway;
    }

    @Override
    public PaymentResult charge(OrderRequest request) {
        try {
            ChargeResult charge = paymentGateway.charge(request.getPaymentToken());
            log.info("Charged item={} qty={} paymentId={}", request.getItemId(), request.getQuantity(), charge.paymentId());
            return new PaymentResult(charge.paymentId());
        } catch (IllegalArgumentException declined) {
            // A decline will not succeed on retry.
            throw ApplicationFailure.newNonRetryableFailure(declined.getMessage(), "PaymentDeclined");
        }
    }

    @Override
    public void refund(String paymentId) {
        paymentGateway.refund(paymentId);
    }
}

package com.example.course.l17;

import java.time.Duration;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

/**
 * charge → reserve stock → ship (physical products only).
 * If stock cannot be reserved (false or a failure) or shipping fails, the payment is refunded.
 */
public class OrderWorkflowImpl implements OrderWorkflow {

    // Replay-safe logger: does not log again while the workflow is being replayed.
    private static final Logger log = Workflow.getLogger(OrderWorkflowImpl.class);

    private static final ActivityOptions OPTIONS = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(10))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setMaximumAttempts(3)
                    .build())
            .build();

    private final PaymentActivity payment = Workflow.newActivityStub(PaymentActivity.class, OPTIONS);
    private final InventoryActivity inventory = Workflow.newActivityStub(InventoryActivity.class, OPTIONS);
    private final ShippingActivity shipping = Workflow.newActivityStub(ShippingActivity.class, OPTIONS);

    @Override
    public OrderResult processOrder(OrderRequest request) {
        log.info("Processing order item={} qty={} digital={}",
                request.getItemId(), request.getQuantity(), request.isDigital());

        PaymentResult paymentResult;
        try {
            paymentResult = payment.charge(request);
        } catch (ActivityFailure e) {
            log.warn("Payment failed for item={}: {}", request.getItemId(), e.getCause().getMessage());
            return new OrderResult(OrderStatus.PAYMENT_FAILED, null, null);
        }
        String paymentId = paymentResult.paymentId();

        boolean reserved;
        try {
            reserved = inventory.reserveStock(request);
        } catch (ActivityFailure e) {
            log.warn("Stock reservation failed for item={}: {}", request.getItemId(), e.getCause().getMessage());
            reserved = false;
        }
        if (!reserved) {
            log.info("Out of stock, refunding {}", paymentId);
            payment.refund(paymentId);
            return new OrderResult(OrderStatus.REFUNDED, paymentId, null);
        }

        String shipmentId = null;
        if (!request.isDigital()) {
            try {
                shipmentId = shipping.createShipment(request);
            } catch (ActivityFailure e) {
                log.warn("Shipping failed, refunding {}", paymentId);
                payment.refund(paymentId);
                return new OrderResult(OrderStatus.REFUNDED, paymentId, null);
            }
        } else {
            log.info("Digital product, skipping shipment");
        }

        log.info("Order completed paymentId={} shipmentId={}", paymentId, shipmentId);
        return new OrderResult(OrderStatus.COMPLETED, paymentId, shipmentId);
    }
}

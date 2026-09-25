package com.example.course.kata26;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

public class OrderFulfillmentWorkflowImpl implements OrderFulfillmentWorkflow {

    private static final Logger log = Workflow.getLogger(OrderFulfillmentWorkflowImpl.class);

    private OrderStatus status = OrderStatus.STARTED;
    private final List<Runnable> compensations = new ArrayList<>();

    private final ActivityOptions options = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(3).build())
            .build();

    private final PaymentActivity payment =
            Workflow.newActivityStub(PaymentActivity.class, options);
    private final InventoryActivity inventory =
            Workflow.newActivityStub(InventoryActivity.class, options);
    private final ShippingActivity shipping =
            Workflow.newActivityStub(ShippingActivity.class, options);

    @Override
    public OrderStatus getStatus() {
        return status;
    }

    @Override
    public OrderResult process(OrderRequest request) {
        // ┌─────────────────────────────────────────────────────────────────────┐
        // │ LECTURE 26 — KATA: THE LOST ORDER                                   │
        // ├─────────────────────────────────────────────────────────────────────┤
        // │ Never leave a customer charged for an order that will not ship.     │
        // │                                                                     │
        // │ Forward steps (update `status` before each one so getStatus() is    │
        // │ accurate while the step runs):                                      │
        // │   1. CHARGING_PAYMENT     payment.chargePayment(request)            │
        // │                           → PaymentResult                           │
        // │   2. RESERVING_INVENTORY  inventory.reserveInventory(request)       │
        // │   3. CREATING_SHIPMENT    shipping.createShipment(request)          │
        // │   then status = COMPLETED, return OrderResult.success(orderId)      │
        // │                                                                     │
        // │ After each successful step, add its undo to `compensations`:        │
        // │   step 1 → payment.refundPayment(paymentResult.getPaymentId())      │
        // │   step 2 → inventory.releaseInventory(request)                      │
        // │   step 3 is last, so it needs no compensation.                      │
        // │                                                                     │
        // │ Wrap the steps in try / catch (ActivityFailure e):                  │
        // │   status = COMPENSATING, run the compensations in REVERSE order     │
        // │   (Collections.reverse, then comp.run()), status = FAILED, and      │
        // │   return OrderResult.failed(orderId, e.getCause().getMessage()).    │
        // │                                                                     │
        // │ Hints:                                                              │
        // │   • Catch ActivityFailure, not Exception.                           │
        // │   • The activity stubs above already carry the 30s timeout and the  │
        // │     3-attempt retry policy; retries happen before the catch fires.  │
        // │   • Business failures are RETURNED as OrderResult.failed(...), the  │
        // │     workflow itself completes normally.                             │
        // └─────────────────────────────────────────────────────────────────────┘

        throw new UnsupportedOperationException(
                "Implement the workflow body — see TODO in OrderFulfillmentWorkflowImpl");
    }
}

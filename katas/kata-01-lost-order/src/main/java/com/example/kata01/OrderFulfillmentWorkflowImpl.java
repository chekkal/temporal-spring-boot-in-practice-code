package com.example.kata01;

import java.time.Duration;
import java.util.List;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

public class OrderFulfillmentWorkflowImpl implements OrderFulfillmentWorkflow {

    private final OrderActivities activities = Workflow.newActivityStub(
            OrderActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
                    .build());

    @Override
    public String fulfill(String orderId, List<String> items, String customerEmail) {
        // ┌─────────────────────────────────────────────────────────────────────┐
        // │ KATA 1 — THE LOST ORDER                                             │
        // ├─────────────────────────────────────────────────────────────────────┤
        // │ Guarantee that a customer is NEVER charged for an order that won't  │
        // │ be fulfilled. Steps:                                                │
        // │                                                                     │
        // │   1. activities.authorizePayment(orderId)  → PaymentConfirmation    │
        // │   2. activities.reserveInventory(orderId, items)  → reservationId   │
        // │   3. activities.sendConfirmation(orderId, customerEmail)            │
        // │                                                                     │
        // │ If step 2 throws, you MUST call voidAuthorization() before          │
        // │ propagating the failure. Step 3 failures need no compensation.      │
        // │                                                                     │
        // │ Hints:                                                              │
        // │   • Simple try-catch: catch in step 2, void the auth, rethrow.      │
        // │   • Or use io.temporal.workflow.Saga + saga.addCompensation(...).   │
        // │                                                                     │
        // │ Return the reservationId on success.                                │
        // └─────────────────────────────────────────────────────────────────────┘

        throw new UnsupportedOperationException(
                "Implement the workflow body — see TODO in OrderFulfillmentWorkflowImpl");
    }
}

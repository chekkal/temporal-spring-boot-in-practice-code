package com.example.kata02;

import java.time.Duration;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

public class OrderSagaWorkflowImpl implements OrderSagaWorkflow {

    /** Forward steps: normal retry policy (3 attempts). */
    private final SagaActivities forward = Workflow.newActivityStub(
            SagaActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
                    .build());

    /** Compensations: aggressive retry policy (5 attempts) — partial state is worse. */
    private final SagaActivities compensation = Workflow.newActivityStub(
            SagaActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(5).build())
                    .build());

    private volatile String currentStep = "INIT";
    private volatile boolean compensating = false;

    @Override
    public String fulfill(String orderId) {
        // ┌─────────────────────────────────────────────────────────────────────┐
        // │ KATA 2 — THE COMPENSATION DANCE                                     │
        // ├─────────────────────────────────────────────────────────────────────┤
        // │ Steps (forward) and compensations (reverse):                        │
        // │   1. validateOrder           — no compensation                      │
        // │   2. authorizePayment        ⇄ voidPayment                          │
        // │   3. reserveInventory        ⇄ releaseInventory                     │
        // │   4. scheduleShipment        ⇄ cancelShipment                       │
        // │   5. notifyCustomer          — no compensation                      │
        // │                                                                     │
        // │ Implement the saga. On any failure, run compensations in REVERSE    │
        // │ registration order, using the `compensation` stub (5 retries).      │
        // │                                                                     │
        // │ Track `currentStep` so the @QueryMethod reflects progress, and set  │
        // │ `compensating = true` once you enter the catch block.               │
        // │                                                                     │
        // │ Two valid approaches:                                               │
        // │   (a) Use io.temporal.workflow.Saga + saga.addCompensation(...)     │
        // │   (b) Maintain List<Runnable> compensations + iterate in reverse    │
        // │                                                                     │
        // │ Use Workflow.getLogger(...) for replay-safe logging of progress.    │
        // └─────────────────────────────────────────────────────────────────────┘

        throw new UnsupportedOperationException(
                "Implement the saga body — see TODO in OrderSagaWorkflowImpl");
    }

    @Override
    public String getStatus() {
        return currentStep + (compensating ? " (compensating)" : "");
    }
}

package com.example.course.kata27;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

public class FullOrderSagaWorkflowImpl implements FullOrderSagaWorkflow {

    private static final Logger log = Workflow.getLogger(FullOrderSagaWorkflowImpl.class);

    // Conservative retries for forward actions
    private final ActivityOptions forwardOpts =
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setDoNotRetry("PaymentDeclinedException",
                                    "ValidationException")
                            .build())
                    .build();

    // Aggressive retries for compensations
    private final ActivityOptions compensationOpts =
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(5)
                            .setInitialInterval(Duration.ofMillis(500))
                            .build())
                    .build();

    private String currentStep = "NOT_STARTED";
    private final List<String> completedSteps = new ArrayList<>();
    private final List<Runnable> compensations = new ArrayList<>();
    private String failureReason = null;

    // Separate stubs for forward and compensation actions
    private final OrderActivities forward =
            Workflow.newActivityStub(OrderActivities.class, forwardOpts);
    private final OrderActivities compensate =
            Workflow.newActivityStub(OrderActivities.class, compensationOpts);

    @Override
    public SagaStatus getStatus() {
        return new SagaStatus(
                currentStep, completedSteps, failureReason);
    }

    private void recordStep(String step) {
        currentStep = step;
        completedSteps.add(step);
    }

    @Override
    public OrderResult process(OrderRequest request) {
        // ┌─────────────────────────────────────────────────────────────────────┐
        // │ LECTURE 27 — KATA: THE COMPENSATION DANCE  (part 1: forward path)   │
        // ├─────────────────────────────────────────────────────────────────────┤
        // │ Call recordStep("<STEP>") BEFORE each forward activity, use the     │
        // │ `forward` stub, and after each success register the undo using the  │
        // │ `compensate` stub:                                                  │
        // │                                                                     │
        // │   step                  forward call            compensation        │
        // │   VALIDATING            validateOrder(request)  logValidationReversal(request)
        // │   CHARGING_PAYMENT      chargePayment(request)  refundPayment(paymentId)
        // │                         → PaymentResult                             │
        // │   RESERVING_INVENTORY   reserveInventory(req)   releaseInventory(request)
        // │   CREATING_SHIPMENT     createShipment(request) cancelShipment(trackingId)
        // │                         → ShipmentResult                            │
        // │   SENDING_NOTIFICATION  sendNotification(req)   none (can't unsend)  │
        // │                                                                     │
        // │ Success: currentStep = "COMPLETED", return OrderResult.success(id). │
        // │                                                                     │
        // │ catch (ActivityFailure e):                                          │
        // │   failureReason = e.getCause().getMessage();                        │
        // │   compensate(request);                                              │
        // │   return OrderResult.failed(orderId, failureReason);                │
        // │                                                                     │
        // │ The retry policies are already done for you (forwardOpts vs         │
        // │ compensationOpts). ValidationException and PaymentDeclinedException │
        // │ are on the forward DoNotRetry list, so they fail on attempt 1.      │
        // └─────────────────────────────────────────────────────────────────────┘

        throw new UnsupportedOperationException(
                "Implement the saga — see TODO in FullOrderSagaWorkflowImpl.process");
    }

    private void compensate(OrderRequest request) {
        // ┌─────────────────────────────────────────────────────────────────────┐
        // │ LECTURE 27 — KATA: THE COMPENSATION DANCE  (part 2: compensation)   │
        // ├─────────────────────────────────────────────────────────────────────┤
        // │ 1. currentStep = "COMPENSATING"                                     │
        // │ 2. Copy `compensations`, reverse the copy (strict reverse order).   │
        // │ 3. Run EVERY compensation. Wrap each comp.run() in its own          │
        // │    try / catch (ActivityFailure e) and collect                      │
        // │    e.getCause().getMessage() into a List<String> compensationErrors │
        // │    — one failed refund must not stop the inventory release.         │
        // │ 4. Errors?  currentStep = "COMPENSATION_PARTIAL" and call           │
        // │             forward.alertOperations(request, compensationErrors)    │
        // │    None?    currentStep = "COMPENSATION_COMPLETE"                   │
        // └─────────────────────────────────────────────────────────────────────┘

        throw new UnsupportedOperationException(
                "Implement compensation — see TODO in FullOrderSagaWorkflowImpl.compensate");
    }
}

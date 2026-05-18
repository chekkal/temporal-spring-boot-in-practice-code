package com.example.kata05;

import java.time.Duration;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

public class PaymentWorkflowImpl implements PaymentWorkflow {

    /**
     * Primary stub: aggressive retry (5 attempts) with exponential backoff 1s → 60s.
     * `setDoNotRetry("PaymentDeclined")` makes business failures fail fast.
     */
    private final PrimaryPaymentActivity primary = Workflow.newActivityStub(
            PrimaryPaymentActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setHeartbeatTimeout(Duration.ofSeconds(10))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2.0)
                            .setMaximumInterval(Duration.ofSeconds(60))
                            .setMaximumAttempts(5)
                            .setDoNotRetry("PaymentDeclined")
                            .build())
                    .build());

    /** Secondary stub: lighter retry. */
    private final SecondaryPaymentActivity secondary = Workflow.newActivityStub(
            SecondaryPaymentActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
                    .build());

    @Override
    public String pay(String orderId, double amount) {
        // ┌─────────────────────────────────────────────────────────────────────┐
        // │ KATA 5 — THE CASCADING FAILURE                                      │
        // ├─────────────────────────────────────────────────────────────────────┤
        // │ 1. Try primary.charge(...)                                          │
        // │ 2. If it fails with an ActivityFailure caused by an ApplicationFailure │
        // │    of type "PaymentDeclined" → rethrow (business failure, no retry, │
        // │    no fallback).                                                    │
        // │ 3. Otherwise (the primary exhausted retries on transient errors):   │
        // │    fall back to secondary.charge(...) and return that result.       │
        // │ 4. If secondary also fails, rethrow.                                │
        // │                                                                     │
        // │ Tips:                                                               │
        // │   - catch io.temporal.failure.ActivityFailure                       │
        // │   - cause is the ApplicationFailure: af.getCause() instanceof       │
        // │     ApplicationFailure appF; appF.getType().equals("PaymentDeclined")│
        // │   - return the transaction id from whichever activity succeeded.    │
        // └─────────────────────────────────────────────────────────────────────┘

        throw new UnsupportedOperationException(
                "Implement primary-then-secondary payment — see TODO in PaymentWorkflowImpl");
    }
}

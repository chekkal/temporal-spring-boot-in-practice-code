package com.example.kata06;

import java.time.Duration;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

public class OrderWorkflowImpl implements OrderWorkflow {

    private final OrderActivities a = Workflow.newActivityStub(
            OrderActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(30)).build());

    /** Use the same id ("add-fraud-check") forever — that's the contract Temporal replays against. */
    public static final String VERSION_ID = "add-fraud-check";
    public static final int VERSION_V1_FRAUD_CHECK = 1;

    @Override
    public String run(String orderId, double amount) {
        PaymentResult payment = a.authorizePayment(orderId, amount);

        // ┌─────────────────────────────────────────────────────────────────────┐
        // │ KATA 6 — THE SCHEMA EVOLUTION                                       │
        // ├─────────────────────────────────────────────────────────────────────┤
        // │ You are deploying a new fraud-check step between payment and        │
        // │ inventory. There are 500 in-flight workflows from the old version   │
        // │ which were started before fraudCheck existed. They MUST replay      │
        // │ correctly through this exact code (Temporal does not version       │
        // │ workflow code; it replays your current code against old history).   │
        // │                                                                     │
        // │ Use Workflow.getVersion to branch:                                  │
        // │                                                                     │
        // │   int v = Workflow.getVersion(                                      │
        // │       VERSION_ID,                                                   │
        // │       Workflow.DEFAULT_VERSION,                                     │
        // │       VERSION_V1_FRAUD_CHECK);                                      │
        // │   if (v >= VERSION_V1_FRAUD_CHECK) {                                │
        // │     boolean ok = a.fraudCheck(orderId, payment);                    │
        // │     if (!ok) throw ApplicationFailure.newNonRetryableFailure(       │
        // │         "Flagged as fraud", "FraudDetected");                       │
        // │   }                                                                 │
        // │                                                                     │
        // │ Why this works:                                                     │
        // │   - Old runs replay → getVersion returns DEFAULT_VERSION (-1) →     │
        // │     fraudCheck is skipped → history matches the old execution.      │
        // │   - New runs → getVersion records version=1 in history → fraudCheck │
        // │     runs every replay.                                              │
        // │                                                                     │
        // │ Place getVersion at the EXACT point where the new step is inserted, │
        // │ not anywhere else in the method.                                    │
        // └─────────────────────────────────────────────────────────────────────┘

        // TODO: insert Workflow.getVersion(...) + branched call to fraudCheck here.

        String reservationId = a.reserveInventory(orderId);
        return a.ship(orderId);
    }
}

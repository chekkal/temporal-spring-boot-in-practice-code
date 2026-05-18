package com.example.kata04;

import java.time.Duration;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

public class ReconciliationWorkflowImpl implements ReconciliationWorkflow {

    private static final int BATCH_SIZE = 100;
    private static final int HISTORY_THRESHOLD = 5_000;

    private final ReconciliationActivities activities = Workflow.newActivityStub(
            ReconciliationActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(5))
                    .setRetryOptions(RetryOptions.newBuilder().setMaximumAttempts(3).build())
                    .build());

    private int processed = 0;
    private int failed = 0;
    private int currentOffset = 0;

    @Override
    public void run(int startingOffset) {
        this.currentOffset = startingOffset;

        // ┌─────────────────────────────────────────────────────────────────────┐
        // │ KATA 4 — THE MIDNIGHT MIGRATION                                     │
        // ├─────────────────────────────────────────────────────────────────────┤
        // │ Implement the reconciliation loop:                                  │
        // │                                                                     │
        // │   while (true) {                                                    │
        // │     batch = activities.fetchBatch(currentOffset, BATCH_SIZE);       │
        // │     if (batch.isEmpty()) break;                                     │
        // │                                                                     │
        // │     for (record : batch) {                                          │
        // │       try { activities.processRecord(record); processed++; }       │
        // │       catch (Exception e) { Workflow.getLogger(...).warn(...);      │
        // │                              failed++; }                            │
        // │     }                                                               │
        // │     currentOffset += batch.size();                                  │
        // │                                                                     │
        // │     // Continue-As-New if history is getting big                    │
        // │     if (Workflow.getInfo().getHistoryLength() > HISTORY_THRESHOLD) {│
        // │       Workflow.continueAsNew(currentOffset);   // doesn't return    │
        // │     }                                                               │
        // │   }                                                                 │
        // │                                                                     │
        // │   activities.sendSummary(processed, failed);                        │
        // └─────────────────────────────────────────────────────────────────────┘

        throw new UnsupportedOperationException(
                "Implement the reconciliation loop — see TODO in ReconciliationWorkflowImpl");
    }

    @Override
    public String getProgress() {
        return "offset=" + currentOffset + " processed=" + processed + " failed=" + failed;
    }
}

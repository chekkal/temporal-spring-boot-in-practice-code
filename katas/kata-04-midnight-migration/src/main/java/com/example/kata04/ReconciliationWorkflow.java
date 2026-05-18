package com.example.kata04;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ReconciliationWorkflow {

    /**
     * @param startingOffset where to resume from; continueAsNew passes the next offset.
     */
    @WorkflowMethod
    void run(int startingOffset);

    @QueryMethod
    String getProgress();
}

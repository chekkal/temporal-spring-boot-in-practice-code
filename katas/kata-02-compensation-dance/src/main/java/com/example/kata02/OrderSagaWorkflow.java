package com.example.kata02;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface OrderSagaWorkflow {

    @WorkflowMethod
    String fulfill(String orderId);

    /** Current step + whether the saga is compensating — required by the kata. */
    @QueryMethod
    String getStatus();
}

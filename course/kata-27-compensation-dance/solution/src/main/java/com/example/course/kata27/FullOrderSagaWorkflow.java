package com.example.course.kata27;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface FullOrderSagaWorkflow {
    @WorkflowMethod
    OrderResult process(OrderRequest request);

    @QueryMethod
    SagaStatus getStatus();
}

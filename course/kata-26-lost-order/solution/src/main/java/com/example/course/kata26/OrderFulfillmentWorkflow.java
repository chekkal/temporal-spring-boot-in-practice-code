package com.example.course.kata26;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface OrderFulfillmentWorkflow {
    @WorkflowMethod
    OrderResult process(OrderRequest request);

    @QueryMethod
    OrderStatus getStatus();
}

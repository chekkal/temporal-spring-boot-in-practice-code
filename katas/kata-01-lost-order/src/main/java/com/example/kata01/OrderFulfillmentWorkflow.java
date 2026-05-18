package com.example.kata01;

import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface OrderFulfillmentWorkflow {

    @WorkflowMethod
    String fulfill(String orderId, java.util.List<String> items, String customerEmail);
}

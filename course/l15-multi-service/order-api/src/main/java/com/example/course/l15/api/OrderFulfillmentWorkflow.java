package com.example.course.l15.api;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * The workflow contract. Implemented by order-service only; the other services never see
 * the implementation, only this interface (slide 5).
 */
@WorkflowInterface
public interface OrderFulfillmentWorkflow {

    @WorkflowMethod
    OrderResult fulfill(Order order);

    @QueryMethod
    OrderStatus getStatus();
}

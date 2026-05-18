package com.example.order.api.workflow;

import com.example.order.api.model.Order;
import com.example.order.api.model.OrderResult;
import com.example.order.api.model.OrderStatus;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Order-fulfillment saga (Chapter 13).
 *
 * Steps: validate → authorize payment → reserve inventory → schedule shipment → notify.
 * Failure at any step compensates all completed steps in reverse registration order.
 */
@WorkflowInterface
public interface OrderFulfillmentWorkflow {

    @WorkflowMethod
    OrderResult fulfill(Order order);

    /** Customer-initiated cancellation while the workflow is still running. */
    @SignalMethod
    void cancel(String reason);

    /** Read-only progress query for status dashboards (Chapter 17). */
    @QueryMethod
    OrderStatus getStatus();
}

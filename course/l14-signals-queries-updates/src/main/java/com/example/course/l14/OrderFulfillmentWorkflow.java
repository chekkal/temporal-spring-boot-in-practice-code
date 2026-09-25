package com.example.course.l14;

import java.util.List;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.UpdateMethod;
import io.temporal.workflow.UpdateValidatorMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface OrderFulfillmentWorkflow {

    @WorkflowMethod
    OrderResult fulfill(Order order);

    // --- Signals: async, fire-and-forget input (slide 3) ---

    @SignalMethod
    void updateShippingAddress(Address newAddress);

    @SignalMethod
    void cancelOrder(String reason);

    // --- Queries: synchronous, read-only (slide 5) ---

    @QueryMethod
    OrderStatus getStatus();

    @QueryMethod
    OrderDetails getDetails();

    // --- Update: synchronous mutation with a response (slide 6) ---

    @UpdateMethod
    UpdateOrderResult updateOrderItems(List<OrderItem> newItems);

    @UpdateValidatorMethod(updateName = "updateOrderItems")
    void validateUpdateOrderItems(List<OrderItem> newItems);
}

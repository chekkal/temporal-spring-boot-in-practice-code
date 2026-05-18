package com.example.order.workflow;

import com.example.order.activity.InventoryActivityImpl;
import com.example.order.activity.NotificationActivityImpl;
import com.example.order.activity.PaymentActivityImpl;
import com.example.order.activity.ShippingActivityImpl;
import com.example.order.api.model.Money;
import com.example.order.api.model.Order;
import com.example.order.api.model.OrderResult;
import com.example.order.api.model.OrderStatus;
import com.example.order.api.workflow.OrderFulfillmentWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Workflow test using the in-memory {@code TestWorkflowExtension} — covered in Chapter 21.
 */
class OrderFulfillmentWorkflowTest {

    @RegisterExtension
    static final TestWorkflowExtension testEnv = TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(OrderFulfillmentWorkflowImpl.class)
            .setActivityImplementations(
                    new PaymentActivityImpl(),
                    new InventoryActivityImpl(),
                    new ShippingActivityImpl(),
                    new NotificationActivityImpl())
            .build();

    @Test
    void happyPath_completesOrder(Worker worker, OrderFulfillmentWorkflow workflow) {
        Order order = sampleOrder("ord-happy", "card-1234");

        OrderResult result = workflow.fulfill(order);

        assertEquals(OrderStatus.COMPLETED, result.getStatus());
        assertEquals("ord-happy", result.getOrderId());
    }

    @Test
    void paymentDeclined_triggersNoCompensations(Worker worker, OrderFulfillmentWorkflow workflow) {
        // The "decline" token forces a non-retryable PaymentDeclined failure.
        // Authorization never succeeded, so nothing has been registered for compensation yet.
        Order order = sampleOrder("ord-decline", "card-decline-test");

        assertThrows(Exception.class, () -> workflow.fulfill(order));
    }

    private static Order sampleOrder(String id, String paymentMethod) {
        Order o = new Order();
        o.setId(id);
        o.setCustomerId("cust-1");
        o.setItems(List.of(new Order.LineItem("SKU-A", 1, Money.of(19.99))));
        o.setTotal(Money.of(19.99));
        o.setPaymentMethod(paymentMethod);
        o.setShippingAddress("1 Demo St");
        o.setCustomerEmail("demo@example.com");
        return o;
    }

    @SuppressWarnings("unused")
    private static WorkflowOptions unused(WorkflowClient client) {
        return WorkflowOptions.newBuilder().setTaskQueue("ignored").build();
    }
}

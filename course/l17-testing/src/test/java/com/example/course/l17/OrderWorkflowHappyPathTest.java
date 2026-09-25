package com.example.course.l17;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Slide 2 (TestWorkflowEnvironment setup) and slide 3 (happy path test).
 * Real activity implementations, in-process test server, no Temporal server needed.
 */
class OrderWorkflowHappyPathTest {

    // Slide 2
    @RegisterExtension
    static TestWorkflowExtension testWorkflow =
        TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(OrderWorkflowImpl.class)
            .setActivityImplementations(new PaymentActivityImpl(),
                                         new InventoryActivityImpl(),
                                         new ShippingActivityImpl())   // not on the slide; item-1 is shipped
            .build();

    // Slide 3
    @Test
    void orderWorkflow_completesSuccessfully(
            TestWorkflowEnvironment testEnv,
            WorkflowClient client,
            Worker worker) {

        OrderWorkflow workflow = client.newWorkflowStub(
            OrderWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(worker.getTaskQueue())
                .build());

        OrderResult result = workflow.processOrder(
            new OrderRequest("item-1", 2, "card-123"));

        assertEquals(OrderStatus.COMPLETED, result.getStatus());
        assertTrue(result.getPaymentId().startsWith("PAY-"));
        assertTrue(result.getShipmentId().startsWith("SHIP-"));
    }

    @Test
    void outOfStockItem_isRefunded_withRealActivities(WorkflowClient client, Worker worker) {
        OrderWorkflow workflow = client.newWorkflowStub(OrderWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue(worker.getTaskQueue()).build());

        // InMemoryInventoryService returns false for item ids starting with "oos-".
        OrderResult result = workflow.processOrder(new OrderRequest("oos-item", 1, "card-123"));

        assertEquals(OrderStatus.REFUNDED, result.getStatus());
    }

    @Test
    void declinedCard_failsPayment_withRealActivities(WorkflowClient client, Worker worker) {
        OrderWorkflow workflow = client.newWorkflowStub(OrderWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue(worker.getTaskQueue()).build());

        OrderResult result = workflow.processOrder(new OrderRequest("item-1", 1, "card-decline"));

        assertEquals(OrderStatus.PAYMENT_FAILED, result.getStatus());
    }
}

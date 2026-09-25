package com.example.course.l17;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.InOrder;

/**
 * Slide 5: every activity mocked, so only the workflow's orchestration logic is under test.
 *
 * setDoNotStart(true) lets each test register fresh mocks on the injected Worker before
 * starting the environment.
 */
class OrderWorkflowMockedActivitiesTest {

    @RegisterExtension
    static TestWorkflowExtension testWorkflow = TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(OrderWorkflowImpl.class)
            .setDoNotStart(true)
            .build();

    private PaymentActivity payment;
    private InventoryActivity inventory;
    private ShippingActivity shipping;

    @BeforeEach
    void mocks() {
        payment = mock(PaymentActivity.class);
        inventory = mock(InventoryActivity.class);
        shipping = mock(ShippingActivity.class);

        when(payment.charge(any())).thenReturn(new PaymentResult("PAY-1"));
        when(inventory.reserveStock(any())).thenReturn(true);
        when(shipping.createShipment(any())).thenReturn("SHIP-1");
    }

    private OrderWorkflow startWorker(TestWorkflowEnvironment testEnv, WorkflowClient client, Worker worker) {
        worker.registerActivitiesImplementations(payment, inventory, shipping);
        testEnv.start();
        return client.newWorkflowStub(OrderWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(worker.getTaskQueue()).build());
    }

    @Test
    void workflow_skipsShippingForDigitalProducts(TestWorkflowEnvironment testEnv, WorkflowClient client, Worker worker) {
        OrderWorkflow workflow = startWorker(testEnv, client, worker);

        OrderResult result = workflow.processOrder(new OrderRequest("ebook-1", 1, "card-123", true));

        assertEquals(OrderStatus.COMPLETED, result.getStatus());
        assertNull(result.getShipmentId());
        verify(shipping, never()).createShipment(any());
    }

    @Test
    void workflow_callsActivitiesInOrderForPhysicalProducts(TestWorkflowEnvironment testEnv, WorkflowClient client, Worker worker) {
        OrderWorkflow workflow = startWorker(testEnv, client, worker);

        OrderResult result = workflow.processOrder(new OrderRequest("item-1", 2, "card-123"));

        assertEquals("SHIP-1", result.getShipmentId());
        InOrder order = inOrder(payment, inventory, shipping);
        order.verify(payment).charge(any());
        order.verify(inventory).reserveStock(any());
        order.verify(shipping, times(1)).createShipment(any());
        verify(payment, never()).refund(any());
    }
}

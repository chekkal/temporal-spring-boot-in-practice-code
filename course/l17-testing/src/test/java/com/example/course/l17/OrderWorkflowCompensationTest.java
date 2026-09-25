package com.example.course.l17;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;

/**
 * Slide 4: testing compensation with Mockito mocks, building TestWorkflowEnvironment by hand
 * (no JUnit extension).
 */
class OrderWorkflowCompensationTest {

    @Test
    void orderWorkflow_refundsOnInventoryFailure() {
        // Mock the inventory activity to fail
        InventoryActivity inventoryMock = mock(InventoryActivity.class);
        when(inventoryMock.reserveStock(any()))
            .thenThrow(new RuntimeException("Out of stock"));

        PaymentActivity paymentMock = mock(PaymentActivity.class);
        when(paymentMock.charge(any()))
            .thenReturn(new PaymentResult("PAY-001"));

        TestWorkflowEnvironment testEnv = TestWorkflowEnvironment.newInstance();
        try {
            Worker worker = testEnv.newWorker("test-queue");
            worker.registerWorkflowImplementationTypes(OrderWorkflowImpl.class);
            worker.registerActivitiesImplementations(paymentMock, inventoryMock);
            testEnv.start();

            // Start the workflow and wait for it. reserveStock throws on every attempt; the
            // workflow's retry policy gives up after 3 attempts (the retry back-off is skipped
            // by the test server, so this still takes milliseconds).
            OrderWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(OrderWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue("test-queue").build());
            OrderResult result = workflow.processOrder(new OrderRequest("item-1", 2, "card-123"));

            assertEquals(OrderStatus.REFUNDED, result.getStatus());
            verify(paymentMock).refund("PAY-001");
        } finally {
            testEnv.close();
        }
    }

    @Test
    void orderWorkflow_refundsWhenReserveStockReturnsFalse() {
        // Same compensation, triggered by the boolean return instead of an exception.
        InventoryActivity inventoryMock = mock(InventoryActivity.class);
        when(inventoryMock.reserveStock(any())).thenReturn(false);
        PaymentActivity paymentMock = mock(PaymentActivity.class);
        when(paymentMock.charge(any())).thenReturn(new PaymentResult("PAY-002"));

        TestWorkflowEnvironment testEnv = TestWorkflowEnvironment.newInstance();
        try {
            Worker worker = testEnv.newWorker("test-queue");
            worker.registerWorkflowImplementationTypes(OrderWorkflowImpl.class);
            worker.registerActivitiesImplementations(paymentMock, inventoryMock);
            testEnv.start();

            OrderWorkflow workflow = testEnv.getWorkflowClient().newWorkflowStub(OrderWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue("test-queue").build());
            OrderResult result = workflow.processOrder(new OrderRequest("item-1", 2, "card-123"));

            assertEquals(OrderStatus.REFUNDED, result.getStatus());
            verify(paymentMock).refund("PAY-002");
        } finally {
            testEnv.close();
        }
    }
}

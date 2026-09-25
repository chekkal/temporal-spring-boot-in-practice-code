package com.example.course.l15.order;

import java.math.BigDecimal;
import java.util.List;

import com.example.course.l15.api.Order;
import com.example.course.l15.api.OrderFulfillmentWorkflow;
import com.example.course.l15.api.OrderResult;
import com.example.course.l15.api.OrderStatus;
import io.temporal.api.enums.v1.PendingActivityState;
import io.temporal.api.enums.v1.WorkflowExecutionStatus;
import io.temporal.api.workflow.v1.PendingActivityInfo;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionDescription;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Runs the order workflow against four workers, one per task queue, the same way the four
 * Spring Boot apps run in production: order-service hosts only the workflow, and each of
 * payment-service, inventory-service and shipping-service hosts only its own activity.
 */
@Timeout(60)
class OrderFulfillmentMultiServiceTest {

    private TestWorkflowEnvironment testEnv;
    private RecordingFakes fakes;

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        fakes = new RecordingFakes();

        // order-service: the workflow and nothing else, like TemporalConfig.orderWorker
        Worker orderWorker = testEnv.newWorker("order-service");
        orderWorker.registerWorkflowImplementationTypes(OrderFulfillmentWorkflowImpl.class);
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    private void startPaymentWorker() {
        testEnv.newWorker("payment-service").registerActivitiesImplementations(fakes.payment);
    }

    private void startInventoryWorker() {
        testEnv.newWorker("inventory-service").registerActivitiesImplementations(fakes.inventory);
    }

    private void startShippingWorker() {
        testEnv.newWorker("shipping-service").registerActivitiesImplementations(fakes.shipping);
    }

    private OrderFulfillmentWorkflow newWorkflow(String orderId) {
        return testEnv.getWorkflowClient().newWorkflowStub(OrderFulfillmentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("order-" + orderId)
                        .setTaskQueue("order-service")
                        .build());
    }

    private static Order order(String id, String sku, String paymentMethod, String address) {
        return new Order(id, "cust-1", List.of(new Order.LineItem(sku, 1)),
                new BigDecimal("49.90"), paymentMethod, address);
    }

    @Test
    void happyPath_eachStepRunsOnItsOwnServiceQueue() {
        startPaymentWorker();
        startInventoryWorker();
        startShippingWorker();
        testEnv.start();

        OrderFulfillmentWorkflow workflow = newWorkflow("ord-1");
        OrderResult result = workflow.fulfill(order("ord-1", "SKU-1", "card-ok", "1 Main St"));

        assertEquals(OrderStatus.COMPLETED, result.status());
        assertEquals("txn-ord-1", result.transactionId());
        assertEquals("res-ord-1", result.reservationId());
        assertEquals("TRK-ord-1", result.trackingNumber());
        assertEquals(List.of(
                "authorize@payment-service",
                "reserve@inventory-service",
                "schedule@shipping-service"), fakes.journal);
        assertEquals(OrderStatus.COMPLETED, workflow.getStatus());
    }

    @Test
    void inventoryFails_paymentIsRefundedOnThePaymentServiceQueue() {
        startPaymentWorker();
        startInventoryWorker();
        startShippingWorker();
        testEnv.start();

        OrderFulfillmentWorkflow workflow = newWorkflow("ord-2");
        WorkflowFailedException e = assertThrows(WorkflowFailedException.class,
                () -> workflow.fulfill(order("ord-2", "OOS-1", "card-ok", "1 Main St")));

        ActivityFailure activityFailure = assertInstanceOf(ActivityFailure.class, e.getCause());
        ApplicationFailure cause = assertInstanceOf(ApplicationFailure.class, activityFailure.getCause());
        assertEquals("OutOfStock", cause.getType());
        assertEquals(List.of(
                "authorize@payment-service",
                "reserve@inventory-service",
                "refund@payment-service"), fakes.journal);
        assertEquals(OrderStatus.FAILED, workflow.getStatus());
    }

    @Test
    void shippingFails_compensationsRunInReverseOrderAcrossServices() {
        startPaymentWorker();
        startInventoryWorker();
        startShippingWorker();
        testEnv.start();

        OrderFulfillmentWorkflow workflow = newWorkflow("ord-3");
        assertThrows(WorkflowFailedException.class,
                () -> workflow.fulfill(order("ord-3", "SKU-1", "card-ok", "Nowhere Land")));

        assertEquals(List.of(
                "authorize@payment-service",
                "reserve@inventory-service",
                "schedule@shipping-service",
                "release@inventory-service",   // last successful step undone first
                "refund@payment-service"), fakes.journal);
        assertEquals(OrderStatus.FAILED, workflow.getStatus());
    }

    @Test
    void paymentDeclined_nothingToCompensate() {
        startPaymentWorker();
        startInventoryWorker();
        startShippingWorker();
        testEnv.start();

        OrderFulfillmentWorkflow workflow = newWorkflow("ord-4");
        assertThrows(WorkflowFailedException.class,
                () -> workflow.fulfill(order("ord-4", "SKU-1", "card-decline", "1 Main St")));

        assertEquals(List.of("authorize@payment-service"), fakes.journal);
        assertEquals(OrderStatus.FAILED, workflow.getStatus());
    }

    /**
     * The point of the lecture: if inventory-service is down, the order does not fail. The
     * activity task sits on the "inventory-service" queue until a worker polls it.
     */
    @Test
    void inventoryServiceDown_orderWaitsAtInventoryStep_thenCompletesWhenItComesBack() throws Exception {
        startPaymentWorker();
        startShippingWorker();
        // no inventory-service worker
        testEnv.start();

        OrderFulfillmentWorkflow workflow = newWorkflow("ord-5");
        WorkflowClient.start(workflow::fulfill, order("ord-5", "SKU-1", "card-ok", "1 Main St"));
        WorkflowStub stub = WorkflowStub.fromTyped(workflow);

        awaitStatus(workflow, OrderStatus.RESERVING_INVENTORY);

        // Give it a few seconds of real time. Nothing times out and nothing fails: the task
        // waits on the queue, and StartToClose only starts counting once a worker picks it up.
        // (testEnv.sleep() is not used here: the test server does not skip time while an
        // activity task is waiting for a poller.)
        Thread.sleep(3_000);

        assertEquals(OrderStatus.RESERVING_INVENTORY, workflow.getStatus());
        WorkflowExecutionDescription description = stub.describe();
        assertEquals(WorkflowExecutionStatus.WORKFLOW_EXECUTION_STATUS_RUNNING, description.getStatus());
        PendingActivityInfo pending = description.getRawDescription().getPendingActivities(0);
        assertEquals("Reserve", pending.getActivityType().getName());
        assertEquals(PendingActivityState.PENDING_ACTIVITY_STATE_SCHEDULED, pending.getState());
        assertEquals(List.of("authorize@payment-service"), fakes.journal);

        // inventory-service comes back: a new process with its own WorkerFactory.
        WorkerFactory inventoryService = WorkerFactory.newInstance(testEnv.getWorkflowClient());
        try {
            inventoryService.newWorker("inventory-service")
                    .registerActivitiesImplementations(fakes.inventory);
            inventoryService.start();

            OrderResult result = stub.getResult(OrderResult.class);

            assertEquals(OrderStatus.COMPLETED, result.status());
            assertEquals(List.of(
                    "authorize@payment-service",
                    "reserve@inventory-service",
                    "schedule@shipping-service"), fakes.journal);
        } finally {
            inventoryService.shutdownNow();
        }
    }

    private static void awaitStatus(OrderFulfillmentWorkflow workflow, OrderStatus expected)
            throws InterruptedException {
        long deadline = System.currentTimeMillis() + 10_000;
        while (workflow.getStatus() != expected) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("status is " + workflow.getStatus() + ", expected " + expected);
            }
            Thread.sleep(50);
        }
    }
}

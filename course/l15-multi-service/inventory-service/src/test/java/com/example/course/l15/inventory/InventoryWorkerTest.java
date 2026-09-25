package com.example.course.l15.inventory;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import com.example.course.l15.api.InventoryActivity;
import com.example.course.l15.api.Order;
import com.example.course.l15.api.InventoryReservation;
import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the worker bean from TemporalConfig serves InventoryActivity on the "inventory-service" queue:
 * a caller workflow on a different queue routes the activity there with setTaskQueue.
 */
class InventoryWorkerTest {

    private final TestWorkflowEnvironment testEnv = TestWorkflowEnvironment.newInstance();

    @WorkflowInterface
    public interface CallerWorkflow {
        @WorkflowMethod
        InventoryReservation call(Order order);
    }

    public static class CallerWorkflowImpl implements CallerWorkflow {
        private final InventoryActivity activity = Workflow.newActivityStub(InventoryActivity.class,
                ActivityOptions.newBuilder()
                        .setTaskQueue("inventory-service")
                        .setStartToCloseTimeout(Duration.ofSeconds(30))
                        .build());

        @Override
        public InventoryReservation call(Order order) {
            return activity.reserve(order);
        }
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Test
    void workerBeanServesTheActivityOnItsOwnQueue() {
        InventoryActivityImpl impl = new InventoryActivityImpl();
        new TemporalConfig().inventoryWorker(testEnv.getWorkerFactory(), impl);
        testEnv.newWorker("caller").registerWorkflowImplementationTypes(CallerWorkflowImpl.class);
        testEnv.start();

        CallerWorkflow caller = testEnv.getWorkflowClient().newWorkflowStub(CallerWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue("caller").build());
        InventoryReservation result = caller.call(new Order("ord-1", "cust-1", List.of(new Order.LineItem("SKU-1", 1)),
                new BigDecimal("10.00"), "card-ok", "1 Main St"));

        assertEquals("ord-1", result.orderId());
        assertEquals(1, impl.reservations().size());
    }
}

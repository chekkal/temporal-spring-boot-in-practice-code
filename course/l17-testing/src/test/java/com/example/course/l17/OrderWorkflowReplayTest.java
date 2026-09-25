package com.example.course.l17;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import io.temporal.activity.ActivityOptions;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.common.WorkflowExecutionHistory;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.Worker;
import io.temporal.workflow.Workflow;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.time.Duration;

/**
 * Bonus (not on the lecture 17 slides): replay tests.
 *
 * A running workflow is rebuilt by replaying its event history through the *current* code.
 * If you change the workflow in a way that issues different commands (reorder activities,
 * add or remove one) the replay no longer matches and in-flight workflows break after deploy.
 * A replay test catches that in CI. Lecture 19 (versioning and safe workflow evolution) builds
 * on this; in a real project you would replay histories exported from production.
 */
class OrderWorkflowReplayTest {

    @RegisterExtension
    static TestWorkflowExtension testWorkflow = TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(OrderWorkflowImpl.class)
            .setActivityImplementations(new PaymentActivityImpl(),
                                         new InventoryActivityImpl(),
                                         new ShippingActivityImpl())
            .build();

    private static WorkflowExecutionHistory recordHistory(WorkflowClient client, Worker worker) throws Exception {
        OrderWorkflow workflow = client.newWorkflowStub(OrderWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("replay-order-1")
                        .setTaskQueue(worker.getTaskQueue())
                        .build());
        workflow.processOrder(new OrderRequest("item-1", 2, "card-123"));
        WorkflowExecutionHistory history = client.fetchHistory("replay-order-1");

        // Saved so you can open it, or replay it later against a changed workflow.
        Path out = Path.of("target", "order-workflow-history.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, history.toJson(true));
        return history;
    }

    @Test
    void currentCode_replaysRecordedHistory(WorkflowClient client, Worker worker) throws Exception {
        WorkflowExecutionHistory history = recordHistory(client, worker);

        // Throws if OrderWorkflowImpl is no longer compatible with the recorded history.
        WorkflowReplayer.replayWorkflowExecution(history, OrderWorkflowImpl.class);
    }

    @Test
    void reorderedActivities_failReplay(WorkflowClient client, Worker worker) throws Exception {
        WorkflowExecutionHistory history = recordHistory(client, worker);

        assertThatThrownBy(() -> WorkflowReplayer.replayWorkflowExecution(history, ReorderedOrderWorkflowImpl.class))
                .hasMessageContaining("NonDeterministicException");
    }

    /** A "new version" that reserves stock before charging: incompatible with running workflows. */
    public static class ReorderedOrderWorkflowImpl implements OrderWorkflow {

        private final ActivityOptions options = ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10)).build();
        private final PaymentActivity payment = Workflow.newActivityStub(PaymentActivity.class, options);
        private final InventoryActivity inventory = Workflow.newActivityStub(InventoryActivity.class, options);
        private final ShippingActivity shipping = Workflow.newActivityStub(ShippingActivity.class, options);

        @Override
        public OrderResult processOrder(OrderRequest request) {
            inventory.reserveStock(request);
            PaymentResult paid = payment.charge(request);
            String shipmentId = shipping.createShipment(request);
            return new OrderResult(OrderStatus.COMPLETED, paid.paymentId(), shipmentId);
        }
    }
}

package com.example.course.l15.order;

import java.util.Map;

import com.example.course.l15.api.Order;
import com.example.course.l15.api.OrderFulfillmentWorkflow;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final WorkflowClient client;

    public OrderController(WorkflowClient client) {
        this.client = client;
    }

    /** Starts the workflow and returns right away. The order is then driven by Temporal. */
    @PostMapping
    public ResponseEntity<Map<String, String>> place(@RequestBody Order order) {
        String workflowId = workflowId(order.getId());
        OrderFulfillmentWorkflow workflow = client.newWorkflowStub(
                OrderFulfillmentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        // Reject a second run even after the first has completed (lecture 23).
                        .setWorkflowIdReusePolicy(
                                WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                        .build());
        try {
            WorkflowClient.start(workflow::fulfill, order);
        } catch (WorkflowExecutionAlreadyStarted e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("orderId", order.getId(), "workflowId", workflowId,
                            "error", "order already submitted"));
        }
        return ResponseEntity.accepted()
                .body(Map.of("orderId", order.getId(), "workflowId", workflowId));
    }

    /** Runs the getStatus query against the workflow. */
    @GetMapping("/{orderId}/status")
    public Map<String, String> status(@PathVariable String orderId) {
        OrderFulfillmentWorkflow workflow =
                client.newWorkflowStub(OrderFulfillmentWorkflow.class, workflowId(orderId));
        return Map.of("orderId", orderId, "status", workflow.getStatus().name());
    }

    static String workflowId(String orderId) {
        return "order-" + orderId;
    }

    /** Unknown order id, or a signal sent to a workflow that has already finished. */
    @ExceptionHandler(WorkflowNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(WorkflowNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "workflowId", e.getExecution().getWorkflowId(),
                "error", "no such order, or its workflow has already finished"));
    }
}

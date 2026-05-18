package com.example.order.controller;

import java.util.Map;

import com.example.order.api.model.Order;
import com.example.order.api.model.OrderStatus;
import com.example.order.api.workflow.OrderFulfillmentWorkflow;
import com.example.order.config.TemporalConfig;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final WorkflowClient workflowClient;

    public OrderController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    /** Starts an OrderFulfillmentWorkflow asynchronously, keyed by the order id. */
    @PostMapping
    public ResponseEntity<Map<String, String>> place(@RequestBody Order order) {
        OrderFulfillmentWorkflow workflow = workflowClient.newWorkflowStub(
                OrderFulfillmentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("order-" + order.getId())
                        .setTaskQueue(TemporalConfig.ORDER_TASK_QUEUE)
                        .setWorkflowIdReusePolicy(
                                WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                        .build());

        WorkflowClient.start(workflow::fulfill, order);

        return ResponseEntity.accepted().body(Map.of(
                "orderId", order.getId(),
                "workflowId", "order-" + order.getId(),
                "status", "ACCEPTED"));
    }

    @GetMapping("/{orderId}/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String orderId) {
        OrderFulfillmentWorkflow workflow = workflowClient.newWorkflowStub(
                OrderFulfillmentWorkflow.class, "order-" + orderId);
        OrderStatus status = workflow.getStatus();
        return ResponseEntity.ok(Map.of("orderId", orderId, "status", status));
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<Void> cancel(@PathVariable String orderId,
                                       @RequestParam(defaultValue = "user-requested") String reason) {
        OrderFulfillmentWorkflow workflow = workflowClient.newWorkflowStub(
                OrderFulfillmentWorkflow.class, "order-" + orderId);
        workflow.cancel(reason);
        return ResponseEntity.accepted().build();
    }

    @ExceptionHandler(WorkflowExecutionAlreadyStarted.class)
    public ResponseEntity<Map<String, String>> handleDuplicate(WorkflowExecutionAlreadyStarted e) {
        return ResponseEntity.status(409).body(Map.of(
                "error", "workflow_id_in_use",
                "workflowId", e.getExecution().getWorkflowId(),
                "message", "An order with this id is already in flight or has been processed."));
    }
}

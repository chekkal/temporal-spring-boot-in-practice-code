package com.example.course.l17;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Small REST front end so the workflows the tests cover can also be run by hand. */
@RestController
@RequestMapping("/api")
public class OrderController {

    private final WorkflowClient workflowClient;

    public OrderController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    /** Runs processOrder and waits for the result (a few seconds at most). */
    @PostMapping("/orders")
    public ResponseEntity<OrderResult> placeOrder(@RequestBody OrderRequest request) {
        OrderWorkflow workflow = workflowClient.newWorkflowStub(OrderWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("order-" + UUID.randomUUID())
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        .build());
        return ResponseEntity.ok(workflow.processOrder(request));
    }

    @PostMapping("/approvals")
    public ResponseEntity<Map<String, String>> startApproval(@RequestBody ApprovalRequest request) {
        ApprovalWorkflow workflow = workflowClient.newWorkflowStub(ApprovalWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(approvalId(request.orderId()))
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        .build());
        WorkflowClient.start(workflow::startApproval, request);
        return ResponseEntity.accepted().body(Map.of("workflowId", approvalId(request.orderId())));
    }

    @PostMapping("/approvals/{orderId}/approve")
    public ResponseEntity<Void> approve(@PathVariable String orderId, @RequestBody Decision decision) {
        workflowClient.newWorkflowStub(ApprovalWorkflow.class, approvalId(orderId))
                .approve(decision.approver(), decision.comment());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/approvals/{orderId}/reject")
    public ResponseEntity<Void> reject(@PathVariable String orderId, @RequestBody Decision decision) {
        workflowClient.newWorkflowStub(ApprovalWorkflow.class, approvalId(orderId))
                .reject(decision.approver(), decision.comment());
        return ResponseEntity.accepted().build();
    }

    /** "PENDING" while the approval workflow is still waiting, otherwise its result. */
    @GetMapping("/approvals/{orderId}")
    public ResponseEntity<Map<String, String>> approvalResult(@PathVariable String orderId) {
        WorkflowStub stub = workflowClient.newUntypedWorkflowStub(approvalId(orderId));
        try {
            return ResponseEntity.ok(Map.of("result", stub.getResult(1, TimeUnit.SECONDS, String.class)));
        } catch (TimeoutException stillWaiting) {
            return ResponseEntity.ok(Map.of("result", "PENDING"));
        }
    }

    private static String approvalId(String orderId) {
        return "approval-" + orderId;
    }

    public record Decision(String approver, String comment) {}
}

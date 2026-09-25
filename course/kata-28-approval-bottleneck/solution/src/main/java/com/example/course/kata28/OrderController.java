package com.example.course.kata28;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired private WorkflowClient client;

    /** Starts ApprovalWorkflow. Workflow id = "order-" + orderId. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> placeOrder(@RequestBody OrderRequest request) {
        String workflowId = "order-" + request.getOrderId();
        ApprovalWorkflow workflow = client.newWorkflowStub(
                ApprovalWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        // Reject a second run even after the first has completed (lecture 23).
                        .setWorkflowIdReusePolicy(
                                WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                        .build());
        try {
            WorkflowClient.start(workflow::processWithApproval, request);
        } catch (WorkflowExecutionAlreadyStarted e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("workflowId", workflowId, "error", "order already exists"));
        }
        boolean approvalRequired = request.getAmount().compareTo(ApprovalWorkflowImpl.APPROVAL_THRESHOLD) > 0;
        return ResponseEntity.accepted()
                .body(Map.of("workflowId", workflowId, "approvalRequired", approvalRequired));
    }

    @PostMapping("/{workflowId}/approve")
    public ResponseEntity<String> approveOrder(
            @PathVariable String workflowId,
            @RequestBody ApprovalDecision decision) {

        ApprovalWorkflow workflow = client.newWorkflowStub(
                ApprovalWorkflow.class, workflowId);

        workflow.approveOrder(decision);

        return ResponseEntity.ok("Approval signal sent");
    }

    @GetMapping("/{workflowId}/approval-status")
    public ResponseEntity<ApprovalStatus> getApprovalStatus(
            @PathVariable String workflowId) {

        ApprovalWorkflow workflow = client.newWorkflowStub(
                ApprovalWorkflow.class, workflowId);

        return ResponseEntity.ok(workflow.getApprovalStatus());
    }

    /** The workflow's OrderResult, or 202 if it is still running (e.g. waiting for approval). */
    @GetMapping("/{workflowId}/result")
    public ResponseEntity<?> getResult(@PathVariable String workflowId) {
        WorkflowStub stub = client.newUntypedWorkflowStub(workflowId);
        try {
            return ResponseEntity.ok(stub.getResult(2, TimeUnit.SECONDS, OrderResult.class));
        } catch (TimeoutException e) {
            return ResponseEntity.accepted().body(Map.of("workflowId", workflowId, "status", "RUNNING"));
        }
    }

    /** Unknown order id, or a signal sent to a workflow that has already finished. */
    @ExceptionHandler(WorkflowNotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(WorkflowNotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "workflowId", e.getExecution().getWorkflowId(),
                "error", "no such order, or its workflow has already finished"));
    }
}

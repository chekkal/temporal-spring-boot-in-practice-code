package com.example.course.kata27;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
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

    /** Starts the workflow. Workflow id = "order-" + orderId (business key → deduplication). */
    @PostMapping
    public ResponseEntity<Map<String, String>> placeOrder(@RequestBody OrderRequest request) {
        String workflowId = "order-" + request.getOrderId();
        FullOrderSagaWorkflow workflow = client.newWorkflowStub(
                FullOrderSagaWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        // Reject a second run even after the first has completed (lecture 23).
                        .setWorkflowIdReusePolicy(
                                WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_REJECT_DUPLICATE)
                        .build());
        try {
            WorkflowClient.start(workflow::process, request);
        } catch (WorkflowExecutionAlreadyStarted e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("workflowId", workflowId, "error", "order already exists"));
        }
        return ResponseEntity.accepted().body(Map.of("workflowId", workflowId));
    }

    /** @QueryMethod getStatus() — currentStep, completedSteps, failureReason. Works while running and after completion. */
    @GetMapping("/{workflowId}/status")
    public ResponseEntity<SagaStatus> getStatus(@PathVariable String workflowId) {
        FullOrderSagaWorkflow workflow = client.newWorkflowStub(FullOrderSagaWorkflow.class, workflowId);
        return ResponseEntity.ok(workflow.getStatus());
    }

    /** The workflow's OrderResult, or 202 if it is still running. */
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

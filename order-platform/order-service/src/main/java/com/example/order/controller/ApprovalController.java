package com.example.order.controller;

import com.example.order.api.model.ApprovalRequest;
import com.example.order.api.model.ApprovalStatus;
import com.example.order.api.workflow.OrderApprovalWorkflow;
import io.temporal.client.WorkflowClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Delivers approve/reject signals to a running OrderApprovalWorkflow. The workflow
 * has been waiting (via Workflow.await) — possibly for hours or days — for these.
 */
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {

    private final WorkflowClient workflowClient;

    public ApprovalController(WorkflowClient workflowClient) {
        this.workflowClient = workflowClient;
    }

    @PostMapping("/{orderId}/approve")
    public ResponseEntity<Void> approve(@PathVariable String orderId,
                                        @RequestBody ApprovalRequest request) {
        OrderApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                OrderApprovalWorkflow.class, "approval-" + orderId);
        workflow.approve(request.getApprover(), request.getComment());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{orderId}/reject")
    public ResponseEntity<Void> reject(@PathVariable String orderId,
                                       @RequestBody ApprovalRequest request) {
        OrderApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                OrderApprovalWorkflow.class, "approval-" + orderId);
        workflow.reject(request.getApprover(), request.getComment());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{orderId}/status")
    public ResponseEntity<ApprovalStatus> status(@PathVariable String orderId) {
        OrderApprovalWorkflow workflow = workflowClient.newWorkflowStub(
                OrderApprovalWorkflow.class, "approval-" + orderId);
        return ResponseEntity.ok(workflow.getApprovalStatus());
    }
}

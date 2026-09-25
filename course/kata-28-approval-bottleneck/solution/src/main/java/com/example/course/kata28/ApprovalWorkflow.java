package com.example.course.kata28;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ApprovalWorkflow {
    @WorkflowMethod
    OrderResult processWithApproval(OrderRequest request);

    @SignalMethod
    void approveOrder(ApprovalDecision decision);

    @QueryMethod
    ApprovalStatus getApprovalStatus();
}

package com.example.course.l17;

import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ApprovalWorkflow {

    /** @return "APPROVED", "REJECTED" or "TIMED_OUT" (no decision within 72 hours) */
    @WorkflowMethod
    String startApproval(ApprovalRequest request);

    @SignalMethod
    void approve(String approver, String comment);

    @SignalMethod
    void reject(String approver, String comment);
}

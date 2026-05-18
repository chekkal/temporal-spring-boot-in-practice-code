package com.example.kata03;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface HighValueOrderWorkflow {

    @WorkflowMethod
    String processOrder(String orderId, double total);

    @SignalMethod
    void approve(String approver, String comment);

    @SignalMethod
    void reject(String approver, String reason);

    @QueryMethod
    String getApprovalStatus();
}

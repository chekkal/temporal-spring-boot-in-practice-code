package com.example.order.api.workflow;

import com.example.order.api.model.ApprovalResult;
import com.example.order.api.model.ApprovalStatus;
import com.example.order.api.model.Order;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Long-running human-in-the-loop approval (Chapter 15).
 *
 * The workflow notifies approvers, then waits — for hours or days — for an external
 * {@link #approve}/{@link #reject} signal. Backed by Temporal's durable execution, the
 * wait survives worker restarts and deployments.
 */
@WorkflowInterface
public interface OrderApprovalWorkflow {

    @WorkflowMethod
    ApprovalResult processApproval(Order order);

    @SignalMethod
    void approve(String approver, String comment);

    @SignalMethod
    void reject(String approver, String reason);

    @QueryMethod
    ApprovalStatus getApprovalStatus();
}

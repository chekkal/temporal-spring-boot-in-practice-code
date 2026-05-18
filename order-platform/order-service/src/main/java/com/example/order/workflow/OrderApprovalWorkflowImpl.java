package com.example.order.workflow;

import java.time.Duration;

import com.example.order.api.activity.NotificationActivity;
import com.example.order.api.model.ApprovalDecision;
import com.example.order.api.model.ApprovalResult;
import com.example.order.api.model.ApprovalStatus;
import com.example.order.api.model.Order;
import com.example.order.api.workflow.OrderApprovalWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

/**
 * Human-in-the-loop approval — Chapter 15.
 *
 * Notifies approvers, then suspends on Workflow.await for up to 72 hours. The wait does
 * not hold a thread and survives worker restarts.
 */
public class OrderApprovalWorkflowImpl implements OrderApprovalWorkflow {

    private final NotificationActivity notification = Workflow.newActivityStub(
            NotificationActivity.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(15))
                    .build());

    private ApprovalDecision decision = null;
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @Override
    public ApprovalResult processApproval(Order order) {
        notification.sendApprovalRequest(order);

        boolean received = Workflow.await(
                Duration.ofHours(72),
                () -> decision != null);

        if (!received) {
            approvalStatus = ApprovalStatus.TIMED_OUT;
            notification.sendEscalation(order);
            return new ApprovalResult(order.getId(),
                    ApprovalStatus.TIMED_OUT, "No response within 72 hours");
        }

        approvalStatus = decision.isApproved()
                ? ApprovalStatus.APPROVED
                : ApprovalStatus.REJECTED;

        notification.sendApprovalResult(order, decision);
        return new ApprovalResult(order.getId(), approvalStatus, decision.getComment());
    }

    @Override
    public void approve(String approver, String comment) {
        this.decision = new ApprovalDecision(true, approver, comment);
    }

    @Override
    public void reject(String approver, String reason) {
        this.decision = new ApprovalDecision(false, approver, reason);
    }

    @Override
    public ApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }
}

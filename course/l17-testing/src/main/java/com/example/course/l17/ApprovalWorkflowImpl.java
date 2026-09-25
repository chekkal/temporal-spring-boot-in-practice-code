package com.example.course.l17;

import java.time.Duration;

import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

public class ApprovalWorkflowImpl implements ApprovalWorkflow {

    static final Duration APPROVAL_TIMEOUT = Duration.ofHours(72);

    private static final Logger log = Workflow.getLogger(ApprovalWorkflowImpl.class);

    private String decision;

    @Override
    public String startApproval(ApprovalRequest request) {
        log.info("Waiting up to {} for approval of order={} amount={}",
                APPROVAL_TIMEOUT, request.orderId(), request.amount());
        boolean decided = Workflow.await(APPROVAL_TIMEOUT, () -> decision != null);
        String result = decided ? decision : "TIMED_OUT";
        log.info("Approval for order={} finished: {}", request.orderId(), result);
        return result;
    }

    @Override
    public void approve(String approver, String comment) {
        if (decision == null) {
            log.info("Approved by {}: {}", approver, comment);
            decision = "APPROVED";
        }
    }

    @Override
    public void reject(String approver, String comment) {
        if (decision == null) {
            log.info("Rejected by {}: {}", approver, comment);
            decision = "REJECTED";
        }
    }
}

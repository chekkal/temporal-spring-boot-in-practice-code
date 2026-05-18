package com.example.kata03;

import java.time.Duration;

import io.temporal.activity.ActivityOptions;
import io.temporal.workflow.Workflow;

public class HighValueOrderWorkflowImpl implements HighValueOrderWorkflow {

    private final NotificationActivities notify = Workflow.newActivityStub(
            NotificationActivities.class,
            ActivityOptions.newBuilder().setStartToCloseTimeout(Duration.ofSeconds(15)).build());

    static final double APPROVAL_THRESHOLD = 5_000.0;

    private volatile Decision decision = null;
    private volatile String currentStatus = "PENDING";

    @Override
    public String processOrder(String orderId, double total) {
        // ┌─────────────────────────────────────────────────────────────────────┐
        // │ KATA 3 — THE APPROVAL BOTTLENECK                                    │
        // ├─────────────────────────────────────────────────────────────────────┤
        // │ Requirements:                                                       │
        // │   1. If total <= 5000 → return "AUTO_APPROVED" immediately.         │
        // │   2. Otherwise: notify.sendApprovalRequest(orderId, total).         │
        // │   3. Workflow.await(Duration.ofHours(24), () -> decision != null).  │
        // │   4. If no signal in 24h → notify.sendEscalation, then              │
        // │      Workflow.await another 24h.                                    │
        // │   5. If still no signal → notify.notifyAutoRejected and return      │
        // │      "AUTO_REJECTED".                                               │
        // │   6. On approve: notify.notifyApproved, return "APPROVED".          │
        // │   7. On reject:  notify.notifyRejected, return "REJECTED".          │
        // │                                                                     │
        // │ The wait must use Workflow.await (NOT Thread.sleep). Update         │
        // │ `currentStatus` so the @QueryMethod reflects each phase.            │
        // │                                                                     │
        // │ Why this works: Workflow.await suspends the workflow at the Temporal│
        // │ server. No thread is held. Worker restarts and deploys survive the  │
        // │ wait. The signal handler runs on the next workflow task once the    │
        // │ signal arrives.                                                     │
        // └─────────────────────────────────────────────────────────────────────┘

        throw new UnsupportedOperationException(
                "Implement the workflow body — see TODO in HighValueOrderWorkflowImpl");
    }

    @Override
    public void approve(String approver, String comment) {
        decision = new Decision(true, approver, comment);
    }

    @Override
    public void reject(String approver, String reason) {
        decision = new Decision(false, approver, reason);
    }

    @Override
    public String getApprovalStatus() {
        return currentStatus;
    }

    record Decision(boolean approved, String approver, String text) {}
}

package com.example.course.kata28;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ApprovalActivities {

    /** No answer after 24h: notify the manager's manager. */
    @ActivityMethod
    void notifyEscalation(OrderRequest request);

    /** Rejected by a manager, or auto-rejected after 48h. */
    @ActivityMethod
    void notifyRejection(OrderRequest request, String reason);

    /** Audit trail of who approved what. */
    @ActivityMethod
    void recordApproval(ApprovalDecision decision);
}

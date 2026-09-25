package com.example.course.kata28;

import java.time.Instant;

/** What the getApprovalStatus() query returns. */
public record ApprovalStatus(
        String orderId,
        String phase,          // WAITING_APPROVAL, ESCALATED, APPROVED, REJECTED, REJECTED_TIMEOUT,
                               // AUTO_APPROVED (<= $5000), COMPLETED, FULFILLMENT_FAILED
        Instant waitingSince,  // null when no approval was needed
        boolean escalated,
        ApprovalDecision decision
) {}

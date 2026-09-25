package com.example.course.kata28;

/** Payload of the approveOrder signal. approved=false is an explicit rejection. */
public record ApprovalDecision(
        boolean approved,
        String approvedBy,
        String reason
) {}

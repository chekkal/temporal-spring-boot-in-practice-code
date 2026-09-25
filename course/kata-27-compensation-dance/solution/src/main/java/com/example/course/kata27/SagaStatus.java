package com.example.course.kata27;

import java.util.List;

/** What the getStatus() query returns: where the saga is now, what it has done, and why it failed. */
public record SagaStatus(
        String currentStep,
        List<String> completedSteps,
        String failureReason
) {}

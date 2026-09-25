package com.example.course.kata27;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

public class FullOrderSagaWorkflowImpl implements FullOrderSagaWorkflow {

    private static final Logger log = Workflow.getLogger(FullOrderSagaWorkflowImpl.class);

    // Conservative retries for forward actions
    private final ActivityOptions forwardOpts =
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(3)
                            .setInitialInterval(Duration.ofSeconds(1))
                            .setDoNotRetry("PaymentDeclinedException",
                                    "ValidationException")
                            .build())
                    .build();

    // Aggressive retries for compensations
    private final ActivityOptions compensationOpts =
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setMaximumAttempts(5)
                            .setInitialInterval(Duration.ofMillis(500))
                            .build())
                    .build();

    private String currentStep = "NOT_STARTED";
    private final List<String> completedSteps = new ArrayList<>();
    private final List<Runnable> compensations = new ArrayList<>();
    private String failureReason = null;

    // Separate stubs for forward and compensation actions
    private final OrderActivities forward =
            Workflow.newActivityStub(OrderActivities.class, forwardOpts);
    private final OrderActivities compensate =
            Workflow.newActivityStub(OrderActivities.class, compensationOpts);

    @Override
    public SagaStatus getStatus() {
        return new SagaStatus(
                currentStep, completedSteps, failureReason);
    }

    private void recordStep(String step) {
        currentStep = step;
        completedSteps.add(step);
    }

    @Override
    public OrderResult process(OrderRequest request) {
        try {
            recordStep("VALIDATING");
            forward.validateOrder(request);
            compensations.add(() -> compensate.logValidationReversal(request));

            recordStep("CHARGING_PAYMENT");
            PaymentResult payment = forward.chargePayment(request);
            compensations.add(() ->
                    compensate.refundPayment(payment.getPaymentId()));

            recordStep("RESERVING_INVENTORY");
            forward.reserveInventory(request);
            compensations.add(() -> compensate.releaseInventory(request));

            recordStep("CREATING_SHIPMENT");
            ShipmentResult shipment = forward.createShipment(request);
            compensations.add(() ->
                    compensate.cancelShipment(shipment.getTrackingId()));

            recordStep("SENDING_NOTIFICATION");
            forward.sendNotification(request);

            currentStep = "COMPLETED";
            log.info("Order {} completed: {}", request.getOrderId(), completedSteps);
            return OrderResult.success(request.getOrderId());

        } catch (ActivityFailure e) {
            failureReason = e.getCause().getMessage();
            log.warn("Order {} failed at {}: {}", request.getOrderId(), currentStep, failureReason);
            compensate(request);
            return OrderResult.failed(request.getOrderId(), failureReason);
        }
    }

    private void compensate(OrderRequest request) {
        currentStep = "COMPENSATING";
        List<Runnable> reversed = new ArrayList<>(compensations);
        Collections.reverse(reversed);

        List<String> compensationErrors = new ArrayList<>();

        for (Runnable comp : reversed) {
            try {
                comp.run(); // Uses compensationOpts (aggressive retry)
            } catch (ActivityFailure e) {
                // Log but continue: must attempt ALL compensations
                compensationErrors.add(e.getCause().getMessage());
            }
        }

        if (!compensationErrors.isEmpty()) {
            currentStep = "COMPENSATION_PARTIAL";
            log.error("Order {} partially compensated: {}", request.getOrderId(), compensationErrors);
            // Alert operations team for manual resolution
            forward.alertOperations(request, compensationErrors);
        } else {
            currentStep = "COMPENSATION_COMPLETE";
            log.info("Order {} fully compensated", request.getOrderId());
        }
    }
}

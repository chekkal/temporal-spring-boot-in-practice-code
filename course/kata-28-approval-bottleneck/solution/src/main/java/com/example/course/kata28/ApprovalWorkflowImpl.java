package com.example.course.kata28;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

public class ApprovalWorkflowImpl implements ApprovalWorkflow {

    /** Orders strictly above this amount need a manager's approval. */
    public static final BigDecimal APPROVAL_THRESHOLD = new BigDecimal("5000");

    private static final Logger log = Workflow.getLogger(ApprovalWorkflowImpl.class);

    private final ActivityOptions options = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(3).build())
            .build();

    private final ApprovalActivities activities =
            Workflow.newActivityStub(ApprovalActivities.class, options);
    private final PaymentActivity payment =
            Workflow.newActivityStub(PaymentActivity.class, options);
    private final InventoryActivity inventory =
            Workflow.newActivityStub(InventoryActivity.class, options);
    private final ShippingActivity shipping =
            Workflow.newActivityStub(ShippingActivity.class, options);

    private ApprovalDecision decision = null;
    private String phase = "WAITING_APPROVAL";
    private boolean escalated = false;
    private String orderId;
    private Instant waitingSince;

    @Override
    public OrderResult processWithApproval(OrderRequest request) {
        orderId = request.getOrderId();

        // Only orders over $5000 need approval; everything else is fulfilled directly.
        if (request.getAmount().compareTo(APPROVAL_THRESHOLD) <= 0) {
            phase = "AUTO_APPROVED";
            log.info("Order {} ({}) is under the approval threshold", orderId, request.getAmount());
            return executeOrderFulfillment(request);
        }

        phase = "WAITING_APPROVAL";
        waitingSince = Instant.ofEpochMilli(Workflow.currentTimeMillis());
        log.info("Order {} ({}) is waiting for approval", orderId, request.getAmount());

        // Phase 1: Wait 24 hours for initial approval
        boolean received = Workflow.await(
                Duration.ofHours(24),
                () -> decision != null);

        if (!received) {
            // Escalate: notify manager's manager
            escalated = true;
            phase = "ESCALATED";
            activities.notifyEscalation(request);

            // Phase 2: Wait another 24 hours
            received = Workflow.await(
                    Duration.ofHours(24),
                    () -> decision != null);
        }

        if (!received) {
            phase = "REJECTED_TIMEOUT";
            activities.notifyRejection(request, "48h timeout");
            return OrderResult.rejected("Approval timeout");
        }

        return handleDecision(request);
    }

    @Override
    public void approveOrder(ApprovalDecision decision) {
        this.decision = decision;
        // Workflow.await() unblocks automatically
    }

    @Override
    public ApprovalStatus getApprovalStatus() {
        return new ApprovalStatus(
                orderId, phase, waitingSince, escalated, decision);
    }

    private OrderResult handleDecision(OrderRequest request) {
        if (decision.approved()) {
            phase = "APPROVED";
            activities.recordApproval(decision);
            // Continue with standard order fulfillment
            return executeOrderFulfillment(request);
        } else {
            phase = "REJECTED";
            activities.notifyRejection(request, decision.reason());
            return OrderResult.rejected(decision.reason());
        }
    }

    /** The standard order fulfillment from Lecture 26: charge, reserve, ship, compensate on failure. */
    private OrderResult executeOrderFulfillment(OrderRequest request) {
        List<Runnable> compensations = new ArrayList<>();
        try {
            PaymentResult paymentResult = payment.chargePayment(request);
            compensations.add(() -> payment.refundPayment(paymentResult.getPaymentId()));

            inventory.reserveInventory(request);
            compensations.add(() -> inventory.releaseInventory(request));

            shipping.createShipment(request);

            phase = "COMPLETED";
            return OrderResult.success(request.getOrderId());

        } catch (ActivityFailure e) {
            Collections.reverse(compensations);
            for (Runnable comp : compensations) {
                comp.run();
            }
            phase = "FULFILLMENT_FAILED";
            return OrderResult.failed(request.getOrderId(), e.getCause().getMessage());
        }
    }
}

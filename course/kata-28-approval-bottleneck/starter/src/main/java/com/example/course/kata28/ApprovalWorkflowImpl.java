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
        // ┌─────────────────────────────────────────────────────────────────────┐
        // │ LECTURE 28 — KATA: THE APPROVAL BOTTLENECK                          │
        // ├─────────────────────────────────────────────────────────────────────┤
        // │ 0. orderId = request.getOrderId().                                  │
        // │    Amount <= APPROVAL_THRESHOLD ($5000)? phase = "AUTO_APPROVED"    │
        // │    and return executeOrderFulfillment(request) — no approval.       │
        // │                                                                     │
        // │ 1. phase = "WAITING_APPROVAL",                                      │
        // │    waitingSince = Instant.ofEpochMilli(Workflow.currentTimeMillis())│
        // │    (never Instant.now() in workflow code: not deterministic).       │
        // │                                                                     │
        // │ 2. Phase 1: boolean received =                                      │
        // │        Workflow.await(Duration.ofHours(24), () -> decision != null);│
        // │                                                                     │
        // │ 3. Not received? escalated = true, phase = "ESCALATED",             │
        // │    activities.notifyEscalation(request), then wait 24h MORE the     │
        // │    same way (phase 2).                                              │
        // │                                                                     │
        // │ 4. Still nothing after 48h? phase = "REJECTED_TIMEOUT",             │
        // │    activities.notifyRejection(request, "48h timeout"),              │
        // │    return OrderResult.rejected("Approval timeout").                 │
        // │                                                                     │
        // │ 5. Otherwise return handleDecision(request) — write it:             │
        // │    approved → phase = "APPROVED", activities.recordApproval(decision),
        // │               return executeOrderFulfillment(request)  (given below)│
        // │    rejected → phase = "REJECTED",                                   │
        // │               activities.notifyRejection(request, decision.reason()),
        // │               return OrderResult.rejected(decision.reason())        │
        // │                                                                     │
        // │ Don't forget approveOrder(...) below: the signal handler.           │
        // └─────────────────────────────────────────────────────────────────────┘

        throw new UnsupportedOperationException(
                "Implement the approval flow — see TODO in ApprovalWorkflowImpl");
    }

    @Override
    public void approveOrder(ApprovalDecision decision) {
        // TODO: store the decision in the `decision` field. That is all a signal handler
        //       needs to do: the Workflow.await(...) condition sees it and unblocks.
    }

    @Override
    public ApprovalStatus getApprovalStatus() {
        return new ApprovalStatus(
                orderId, phase, waitingSince, escalated, decision);
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

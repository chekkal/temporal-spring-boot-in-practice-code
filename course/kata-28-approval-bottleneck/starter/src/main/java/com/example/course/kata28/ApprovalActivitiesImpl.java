package com.example.course.kata28;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** In-memory notification service and approval audit log. */
@Component
public class ApprovalActivitiesImpl implements ApprovalActivities {

    private static final Logger log = LoggerFactory.getLogger(ApprovalActivitiesImpl.class);

    private final List<String> escalations = new CopyOnWriteArrayList<>();
    private final List<String> rejections = new CopyOnWriteArrayList<>();
    private final List<ApprovalDecision> approvals = new CopyOnWriteArrayList<>();

    @Override
    public void notifyEscalation(OrderRequest request) {
        escalations.add(request.getOrderId());
        log.warn("[approval] no decision after 24h for order {} ({}), escalating to the manager's manager",
                request.getOrderId(), request.getAmount());
    }

    @Override
    public void notifyRejection(OrderRequest request, String reason) {
        rejections.add(request.getOrderId() + ": " + reason);
        log.info("[approval] order {} rejected: {} (customer {} notified)",
                request.getOrderId(), reason, request.getCustomerEmail());
    }

    @Override
    public void recordApproval(ApprovalDecision decision) {
        approvals.add(decision);
        log.info("[approval] approved by {}: {}", decision.approvedBy(), decision.reason());
    }

    public List<String> escalations() { return List.copyOf(escalations); }
    public List<String> rejections() { return List.copyOf(rejections); }
    public List<ApprovalDecision> approvals() { return List.copyOf(approvals); }
}

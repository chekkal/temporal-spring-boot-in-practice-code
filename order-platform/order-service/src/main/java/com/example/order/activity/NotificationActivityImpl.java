package com.example.order.activity;

import com.example.order.api.activity.NotificationActivity;
import com.example.order.api.model.ApprovalDecision;
import com.example.order.api.model.Order;
import com.example.order.api.model.ShipmentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificationActivityImpl implements NotificationActivity {

    private static final Logger log = LoggerFactory.getLogger(NotificationActivityImpl.class);

    @Override
    public void sendConfirmation(Order order, ShipmentResult shipment) {
        log.info("[notify] order={} confirmation: tracking={}, recipient={}",
                order.getId(), shipment.getTrackingNumber(), order.getCustomerEmail());
    }

    @Override
    public void sendApprovalRequest(Order order) {
        log.info("[notify] order={} approval requested for total={}", order.getId(), order.getTotal());
    }

    @Override
    public void sendApprovalResult(Order order, ApprovalDecision decision) {
        log.info("[notify] order={} approval decision: approved={} approver={}",
                order.getId(), decision.isApproved(), decision.getApprover());
    }

    @Override
    public void sendEscalation(Order order) {
        log.info("[notify] order={} escalation: approval timed out", order.getId());
    }
}

package com.example.kata03;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class NotificationActivitiesImpl implements NotificationActivities {
    private static final Logger log = LoggerFactory.getLogger(NotificationActivitiesImpl.class);
    @Override public void sendApprovalRequest(String orderId, double total) { log.info("[notify] approval requested order={} total={}", orderId, total); }
    @Override public void sendEscalation(String orderId) { log.info("[notify] ESCALATION order={}", orderId); }
    @Override public void notifyApproved(String orderId, String approver) { log.info("[notify] APPROVED order={} by={}", orderId, approver); }
    @Override public void notifyRejected(String orderId, String approver, String reason) { log.info("[notify] REJECTED order={} by={} reason={}", orderId, approver, reason); }
    @Override public void notifyAutoRejected(String orderId) { log.info("[notify] AUTO-REJECTED order={}", orderId); }
}

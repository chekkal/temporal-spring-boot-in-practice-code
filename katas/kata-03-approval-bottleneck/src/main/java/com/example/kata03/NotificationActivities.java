package com.example.kata03;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface NotificationActivities {

    @ActivityMethod void sendApprovalRequest(String orderId, double total);
    @ActivityMethod void sendEscalation(String orderId);
    @ActivityMethod void notifyApproved(String orderId, String approver);
    @ActivityMethod void notifyRejected(String orderId, String approver, String reason);
    @ActivityMethod void notifyAutoRejected(String orderId);
}

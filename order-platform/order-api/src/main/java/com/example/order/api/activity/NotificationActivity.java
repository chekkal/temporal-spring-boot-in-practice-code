package com.example.order.api.activity;

import com.example.order.api.model.ApprovalDecision;
import com.example.order.api.model.Order;
import com.example.order.api.model.ShipmentResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface NotificationActivity {

    @ActivityMethod
    void sendConfirmation(Order order, ShipmentResult shipment);

    @ActivityMethod
    void sendApprovalRequest(Order order);

    @ActivityMethod
    void sendApprovalResult(Order order, ApprovalDecision decision);

    @ActivityMethod
    void sendEscalation(Order order);
}

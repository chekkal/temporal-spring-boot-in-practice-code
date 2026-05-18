package com.example.kata02;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface SagaActivities {

    @ActivityMethod void validateOrder(String orderId);

    @ActivityMethod String authorizePayment(String orderId);
    @ActivityMethod void voidPayment(String authorizationId);

    @ActivityMethod String reserveInventory(String orderId);
    @ActivityMethod void releaseInventory(String reservationId);

    @ActivityMethod String scheduleShipment(String orderId, String reservationId);
    @ActivityMethod void cancelShipment(String trackingNumber);

    @ActivityMethod void notifyCustomer(String orderId);
}

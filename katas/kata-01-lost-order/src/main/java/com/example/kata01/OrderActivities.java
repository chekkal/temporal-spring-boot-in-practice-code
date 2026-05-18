package com.example.kata01;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface OrderActivities {

    @ActivityMethod
    PaymentConfirmation authorizePayment(String orderId);

    @ActivityMethod
    void voidAuthorization(String authorizationId);

    @ActivityMethod
    String reserveInventory(String orderId, java.util.List<String> items);

    @ActivityMethod
    void sendConfirmation(String orderId, String email);

    record PaymentConfirmation(String authorizationId, String orderId) {}
}

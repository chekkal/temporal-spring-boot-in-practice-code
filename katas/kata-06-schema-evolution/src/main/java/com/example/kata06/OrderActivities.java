package com.example.kata06;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface OrderActivities {

    @ActivityMethod
    PaymentResult authorizePayment(String orderId, double amount);

    /** New activity introduced in v2. Existing running workflows won't call it. */
    @ActivityMethod
    boolean fraudCheck(String orderId, PaymentResult payment);

    @ActivityMethod
    String reserveInventory(String orderId);

    @ActivityMethod
    String ship(String orderId);
}

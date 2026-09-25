package com.example.course.kata28;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface PaymentActivity {
    @ActivityMethod
    PaymentResult chargePayment(OrderRequest request);

    @ActivityMethod
    void refundPayment(String paymentId);
}

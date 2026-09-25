package com.example.course.l17;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface PaymentActivity {

    PaymentResult charge(OrderRequest request);

    void refund(String paymentId);
}

package com.example.course.l14;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface PaymentActivity {

    PaymentResult charge(Order order);

    /** Compensation for {@link #charge(Order)}. Idempotent. */
    void refund(PaymentResult payment);
}

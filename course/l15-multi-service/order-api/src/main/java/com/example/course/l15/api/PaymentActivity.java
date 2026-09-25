package com.example.course.l15.api;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/** Implemented by payment-service. Executed by the worker polling the "payment-service" queue. */
@ActivityInterface
public interface PaymentActivity {

    @ActivityMethod
    PaymentResult authorize(Order order);

    /** Compensation for {@link #authorize(Order)}. Safe to call more than once. */
    @ActivityMethod
    void refund(PaymentResult payment);
}

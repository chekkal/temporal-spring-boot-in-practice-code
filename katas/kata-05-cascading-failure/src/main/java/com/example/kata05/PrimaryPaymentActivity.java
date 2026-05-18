package com.example.kata05;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface(namePrefix = "Primary_")
public interface PrimaryPaymentActivity {

    /**
     * @throws io.temporal.failure.ApplicationFailure (non-retryable) on PaymentDeclined,
     *         (retryable) on GatewayTimeout / 503.
     */
    @ActivityMethod
    String charge(String orderId, double amount);
}

package com.example.kata05;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface(namePrefix = "Secondary_")
public interface SecondaryPaymentActivity {

    @ActivityMethod
    String charge(String orderId, double amount);
}

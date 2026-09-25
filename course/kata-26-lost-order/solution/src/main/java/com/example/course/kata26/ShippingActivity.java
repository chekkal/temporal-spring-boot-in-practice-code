package com.example.course.kata26;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ShippingActivity {
    @ActivityMethod
    void createShipment(OrderRequest request);
}

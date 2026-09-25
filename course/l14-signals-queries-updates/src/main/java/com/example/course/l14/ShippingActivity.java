package com.example.course.l14;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface ShippingActivity {

    ShipmentResult schedule(Order order, Address shippingAddress);
}

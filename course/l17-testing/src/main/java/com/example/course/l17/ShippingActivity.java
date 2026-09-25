package com.example.course.l17;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface ShippingActivity {

    /** @return shipment id */
    String createShipment(OrderRequest request);
}

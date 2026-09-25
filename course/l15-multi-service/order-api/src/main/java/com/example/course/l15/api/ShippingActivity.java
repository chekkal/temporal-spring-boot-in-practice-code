package com.example.course.l15.api;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/** Implemented by shipping-service. Executed by the worker polling the "shipping-service" queue. */
@ActivityInterface
public interface ShippingActivity {

    @ActivityMethod
    ShipmentResult schedule(Order order, InventoryReservation reservation);

    /** Compensation for {@link #schedule(Order, InventoryReservation)}. Safe to call more than once. */
    @ActivityMethod
    void cancel(ShipmentResult shipment);
}

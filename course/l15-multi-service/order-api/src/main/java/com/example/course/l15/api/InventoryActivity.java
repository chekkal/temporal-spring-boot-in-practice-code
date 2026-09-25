package com.example.course.l15.api;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/** Implemented by inventory-service. Executed by the worker polling the "inventory-service" queue. */
@ActivityInterface
public interface InventoryActivity {

    @ActivityMethod
    InventoryReservation reserve(Order order);

    /** Compensation for {@link #reserve(Order)}. Safe to call more than once. */
    @ActivityMethod
    void release(InventoryReservation reservation);
}

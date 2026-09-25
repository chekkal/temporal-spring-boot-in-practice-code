package com.example.course.l14;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface InventoryActivity {

    InventoryReservation reserve(Order order);

    /** Compensation for {@link #reserve(Order)}. Idempotent. */
    void release(InventoryReservation reservation);
}

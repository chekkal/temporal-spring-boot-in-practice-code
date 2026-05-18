package com.example.order.api.activity;

import com.example.order.api.model.InventoryReservation;
import com.example.order.api.model.Order;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface InventoryActivity {

    @ActivityMethod
    InventoryReservation reserve(Order order);

    @ActivityMethod
    void release(Order order);
}

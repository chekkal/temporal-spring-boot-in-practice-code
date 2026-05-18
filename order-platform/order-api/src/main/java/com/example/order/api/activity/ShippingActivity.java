package com.example.order.api.activity;

import com.example.order.api.model.InventoryReservation;
import com.example.order.api.model.Order;
import com.example.order.api.model.ShipmentResult;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ShippingActivity {

    @ActivityMethod
    ShipmentResult schedule(Order order, InventoryReservation reservation);

    @ActivityMethod
    void cancel(Order order);
}

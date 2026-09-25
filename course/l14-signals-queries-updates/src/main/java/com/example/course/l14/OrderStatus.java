package com.example.course.l14;

public enum OrderStatus {
    /** Edit window: items can be changed (update), address changed or order cancelled (signals). */
    PENDING,
    CHARGING_PAYMENT,
    RESERVING_INVENTORY,
    SCHEDULING_SHIPMENT,
    COMPLETED,
    CANCELLED,
    FAILED
}

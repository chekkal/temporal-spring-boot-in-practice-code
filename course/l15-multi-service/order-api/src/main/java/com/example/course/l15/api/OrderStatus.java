package com.example.course.l15.api;

/** Exposed by the getStatus query. The *_ING values tell you which service the order is waiting on. */
public enum OrderStatus {
    RECEIVED,
    AUTHORIZING_PAYMENT,
    RESERVING_INVENTORY,
    SCHEDULING_SHIPMENT,
    COMPLETED,
    COMPENSATING,
    FAILED
}

package com.example.order.api.model;

/**
 * Tracks the order-fulfillment saga's progress. Returned via the workflow's @QueryMethod.
 */
public enum OrderStatus {
    RECEIVED,
    VALIDATING,
    AUTHORIZING_PAYMENT,
    RESERVING_INVENTORY,
    SCHEDULING_SHIPMENT,
    NOTIFYING,
    COMPLETED,
    COMPENSATING,
    FAILED,
    CANCELLED
}

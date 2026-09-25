package com.example.course.l17;

public enum OrderStatus {
    COMPLETED,
    /** The charge was declined; nothing to compensate. */
    PAYMENT_FAILED,
    /** Charged, then stock or shipping failed, so the payment was refunded. */
    REFUNDED
}

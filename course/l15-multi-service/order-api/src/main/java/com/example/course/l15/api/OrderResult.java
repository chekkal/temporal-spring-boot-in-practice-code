package com.example.course.l15.api;

/** Returned by {@link OrderFulfillmentWorkflow#fulfill(Order)} when the order completes. */
public record OrderResult(String orderId, OrderStatus status, String transactionId,
                          String reservationId, String trackingNumber) {}

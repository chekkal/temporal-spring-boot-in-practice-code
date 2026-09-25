package com.example.course.l14;

/** Returned by the {@code getDetails()} query. {@code paymentResult} is null until payment is charged. */
public record OrderDetails(OrderStatus status, Address shippingAddress, PaymentResult paymentResult) {}

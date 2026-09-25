package com.example.course.kata28;

/** What PaymentRepository stores: one charge per order. */
public record Payment(String orderId, PaymentResult result) {
    public PaymentResult toResult() {
        return result;
    }
}

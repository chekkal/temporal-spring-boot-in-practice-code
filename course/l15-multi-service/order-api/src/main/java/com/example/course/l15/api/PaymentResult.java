package com.example.course.l15.api;

import java.math.BigDecimal;

/** Returned by payment-service after a successful authorization. */
public record PaymentResult(String transactionId, String orderId, BigDecimal amount) {}

package com.example.course.kata28;

import java.math.BigDecimal;

public class PaymentResult {

    private String paymentId;
    private BigDecimal amount;

    public PaymentResult() {
    }

    public PaymentResult(String paymentId, BigDecimal amount) {
        this.paymentId = paymentId;
        this.amount = amount;
    }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}

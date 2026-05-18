package com.example.order.api.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Forward-compatible payment outcome. Annotated for backwards compatibility per the
 * "Schema Evolution Kata" (Kata 6).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentResult {

    private String transactionId;
    private Money amount;
    private PaymentStatus status;

    public PaymentResult() {}

    public PaymentResult(String transactionId, Money amount, PaymentStatus status) {
        this.transactionId = transactionId;
        this.amount = amount;
        this.status = status;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public Money getAmount() { return amount; }
    public void setAmount(Money amount) { this.amount = amount; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
}

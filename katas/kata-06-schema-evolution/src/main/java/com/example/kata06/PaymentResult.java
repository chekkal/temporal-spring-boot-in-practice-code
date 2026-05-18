package com.example.kata06;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Forward-compatible payment result.
 *
 * Old workflow runs serialized this with only {transactionId, amount}. The new field
 * {@code riskScore} is added below — old payloads still deserialize because of
 * {@link JsonIgnoreProperties#ignoreUnknown} and the default value.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentResult {

    private String transactionId;
    private double amount;
    private Double riskScore = null;   // new field — null for old serialized payloads

    public PaymentResult() {}
    public PaymentResult(String transactionId, double amount) {
        this.transactionId = transactionId; this.amount = amount;
    }
    public PaymentResult(String transactionId, double amount, Double riskScore) {
        this(transactionId, amount); this.riskScore = riskScore;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public Double getRiskScore() { return riskScore; }
    public void setRiskScore(Double riskScore) { this.riskScore = riskScore; }
}

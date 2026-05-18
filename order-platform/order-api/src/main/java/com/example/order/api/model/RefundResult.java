package com.example.order.api.model;

public class RefundResult {
    private String refundId;
    private PaymentStatus status;

    public RefundResult() {}
    public RefundResult(String refundId, PaymentStatus status) {
        this.refundId = refundId;
        this.status = status;
    }

    public String getRefundId() { return refundId; }
    public void setRefundId(String refundId) { this.refundId = refundId; }
    public PaymentStatus getStatus() { return status; }
    public void setStatus(PaymentStatus status) { this.status = status; }
}

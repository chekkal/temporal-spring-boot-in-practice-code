package com.example.order.api.model;

public class OrderResult {
    private String orderId;
    private OrderStatus status;
    private String failureReason;

    public OrderResult() {}

    public OrderResult(String orderId, OrderStatus status) {
        this.orderId = orderId;
        this.status = status;
    }

    public OrderResult(String orderId, OrderStatus status, String failureReason) {
        this.orderId = orderId;
        this.status = status;
        this.failureReason = failureReason;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }
}

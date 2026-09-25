package com.example.course.kata27;

/** Workflow output. Business failures are returned as a FAILED result, not thrown. */
public class OrderResult {

    private String orderId;
    private String status;   // COMPLETED or FAILED
    private String message;

    public OrderResult() {
    }

    public OrderResult(String orderId, String status, String message) {
        this.orderId = orderId;
        this.status = status;
        this.message = message;
    }

    public static OrderResult success(String orderId) {
        return new OrderResult(orderId, "COMPLETED", "Order fulfilled");
    }

    public static OrderResult failed(String orderId, String reason) {
        return new OrderResult(orderId, "FAILED", reason);
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}

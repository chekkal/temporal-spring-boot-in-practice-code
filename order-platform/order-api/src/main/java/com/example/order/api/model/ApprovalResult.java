package com.example.order.api.model;

public class ApprovalResult {
    private String orderId;
    private ApprovalStatus status;
    private String comment;

    public ApprovalResult() {}
    public ApprovalResult(String orderId, ApprovalStatus status, String comment) {
        this.orderId = orderId; this.status = status; this.comment = comment;
    }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public ApprovalStatus getStatus() { return status; }
    public void setStatus(ApprovalStatus status) { this.status = status; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}

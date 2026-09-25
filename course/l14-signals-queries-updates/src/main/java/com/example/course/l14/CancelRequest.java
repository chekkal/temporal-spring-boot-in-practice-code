package com.example.course.l14;

/** Body of {@code POST /api/orders/{id}/cancel}. */
public class CancelRequest {

    private String reason;

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}

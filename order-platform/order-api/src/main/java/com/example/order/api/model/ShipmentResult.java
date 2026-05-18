package com.example.order.api.model;

public class ShipmentResult {
    private String trackingNumber;
    private String orderId;

    public ShipmentResult() {}
    public ShipmentResult(String trackingNumber, String orderId) {
        this.trackingNumber = trackingNumber; this.orderId = orderId;
    }

    public String getTrackingNumber() { return trackingNumber; }
    public void setTrackingNumber(String trackingNumber) { this.trackingNumber = trackingNumber; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
}

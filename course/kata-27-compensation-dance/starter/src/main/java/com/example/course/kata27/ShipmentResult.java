package com.example.course.kata27;

public class ShipmentResult {

    private String trackingId;

    public ShipmentResult() {
    }

    public ShipmentResult(String trackingId) {
        this.trackingId = trackingId;
    }

    public String getTrackingId() { return trackingId; }
    public void setTrackingId(String trackingId) { this.trackingId = trackingId; }
}

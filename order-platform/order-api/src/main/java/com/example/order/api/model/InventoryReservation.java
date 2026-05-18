package com.example.order.api.model;

public class InventoryReservation {
    private String reservationId;
    private String orderId;

    public InventoryReservation() {}
    public InventoryReservation(String reservationId, String orderId) {
        this.reservationId = reservationId; this.orderId = orderId;
    }

    public String getReservationId() { return reservationId; }
    public void setReservationId(String reservationId) { this.reservationId = reservationId; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
}

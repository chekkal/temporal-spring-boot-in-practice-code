package com.example.course.l17;

public class OrderResult {

    private OrderStatus status;
    private String paymentId;
    private String shipmentId;

    public OrderResult() {}

    public OrderResult(OrderStatus status, String paymentId, String shipmentId) {
        this.status = status;
        this.paymentId = paymentId;
        this.shipmentId = shipmentId;
    }

    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }

    /** Null for digital products and for orders that did not complete. */
    public String getShipmentId() { return shipmentId; }
    public void setShipmentId(String shipmentId) { this.shipmentId = shipmentId; }
}

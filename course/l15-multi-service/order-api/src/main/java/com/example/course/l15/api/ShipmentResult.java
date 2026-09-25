package com.example.course.l15.api;

/** Returned by shipping-service after a shipment has been scheduled. */
public record ShipmentResult(String shipmentId, String orderId, String trackingNumber) {}

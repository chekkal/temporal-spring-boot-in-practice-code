package com.example.course.l15.api;

import java.util.List;

/** Returned by inventory-service after the items of an order have been reserved. */
public record InventoryReservation(String reservationId, String orderId, List<String> skus) {}

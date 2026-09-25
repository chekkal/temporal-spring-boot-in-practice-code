package com.example.course.l15.shipping;

import java.math.BigDecimal;
import java.util.List;

import com.example.course.l15.api.InventoryReservation;
import com.example.course.l15.api.Order;
import com.example.course.l15.api.ShipmentResult;
import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShippingActivityImplTest {

    private final ShippingActivityImpl shipping = new ShippingActivityImpl();

    private static Order order(String id, String address) {
        return new Order(id, "cust-1", List.of(new Order.LineItem("SKU-1", 1)),
                new BigDecimal("10.00"), "card-ok", address);
    }

    private static InventoryReservation reservation(String orderId) {
        return new InventoryReservation("res-" + orderId, orderId, List.of("SKU-1"));
    }

    @Test
    void schedule_recordsAShipmentWithATrackingNumber() {
        ShipmentResult shipment = shipping.schedule(order("ord-1", "1 Main St"), reservation("ord-1"));

        assertEquals("shp-ord-1", shipment.shipmentId());
        assertEquals("TRK-ord-1", shipment.trackingNumber());
        assertEquals("SCHEDULED", shipping.find("shp-ord-1").status());
    }

    @Test
    void schedule_nowhereAddressFailsWithoutRetry() {
        ApplicationFailure failure = assertThrows(ApplicationFailure.class,
                () -> shipping.schedule(order("ord-2", "Nowhere Land"), reservation("ord-2")));

        assertEquals("UndeliverableAddress", failure.getType());
        assertTrue(failure.isNonRetryable());
        assertTrue(shipping.shipments().isEmpty());
    }

    @Test
    void cancel_marksTheShipmentCancelled() {
        ShipmentResult shipment = shipping.schedule(order("ord-3", "1 Main St"), reservation("ord-3"));

        shipping.cancel(shipment);

        assertEquals("CANCELLED", shipping.find("shp-ord-3").status());
    }
}

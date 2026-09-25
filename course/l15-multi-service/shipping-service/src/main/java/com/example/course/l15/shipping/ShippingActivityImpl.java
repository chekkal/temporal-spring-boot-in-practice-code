package com.example.course.l15.shipping;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.example.course.l15.api.InventoryReservation;
import com.example.course.l15.api.Order;
import com.example.course.l15.api.ShipmentResult;
import com.example.course.l15.api.ShippingActivity;
import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory stand-in for a carrier API.
 *
 * Failure trigger: a shippingAddress containing "nowhere" (any case) is rejected with a
 * non-retryable UndeliverableAddress failure. Because shipping is the last step, this is
 * the way to see two compensations run in reverse order: inventory released, then payment refunded.
 */
@Component
public class ShippingActivityImpl implements ShippingActivity {

    private static final Logger log = LoggerFactory.getLogger(ShippingActivityImpl.class);

    public record Shipment(String shipmentId, String orderId, String trackingNumber, String status) {}

    private final ConcurrentMap<String, Shipment> shipments = new ConcurrentHashMap<>();

    @Override
    public ShipmentResult schedule(Order order, InventoryReservation reservation) {
        String address = order.getShippingAddress() == null ? "" : order.getShippingAddress();
        if (address.toLowerCase().contains("nowhere")) {
            log.info("undeliverable order={} address={}", order.getId(), address);
            throw ApplicationFailure.newNonRetryableFailure(
                    "cannot deliver to '" + address + "'", "UndeliverableAddress");
        }
        String shipmentId = "shp-" + order.getId();
        String trackingNumber = "TRK-" + order.getId();
        shipments.putIfAbsent(shipmentId,
                new Shipment(shipmentId, order.getId(), trackingNumber, "SCHEDULED"));
        log.info("scheduled order={} shipment={} reservation={}",
                order.getId(), shipmentId, reservation.reservationId());
        return new ShipmentResult(shipmentId, order.getId(), trackingNumber);
    }

    @Override
    public void cancel(ShipmentResult shipment) {
        shipments.computeIfPresent(shipment.shipmentId(),
                (id, s) -> new Shipment(s.shipmentId(), s.orderId(), s.trackingNumber(), "CANCELLED"));
        log.info("cancelled order={} shipment={}", shipment.orderId(), shipment.shipmentId());
    }

    public Collection<Shipment> shipments() {
        return shipments.values();
    }

    public Shipment find(String shipmentId) {
        return shipments.get(shipmentId);
    }
}

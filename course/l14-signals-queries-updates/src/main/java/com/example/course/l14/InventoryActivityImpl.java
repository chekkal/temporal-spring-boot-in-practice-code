package com.example.course.l14;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** In-memory inventory fake. */
@Component
public class InventoryActivityImpl implements InventoryActivity {

    private static final Logger log = LoggerFactory.getLogger(InventoryActivityImpl.class);

    private final ConcurrentMap<String, InventoryReservation> reservations = new ConcurrentHashMap<>();

    @Override
    public InventoryReservation reserve(Order order) {
        // Demo failure trigger: any SKU starting with "oos-" is out of stock (not retried).
        boolean outOfStock = order.getItems().stream().anyMatch(i -> i.sku().startsWith("oos-"));
        if (outOfStock) {
            throw ApplicationFailure.newNonRetryableFailure(
                    "Out of stock for order " + order.getId(), "OutOfStock");
        }
        InventoryReservation reservation = new InventoryReservation("res-" + UUID.randomUUID(), order.getId());
        reservations.put(reservation.reservationId(), reservation);
        log.info("Reserved order={} items={} reservation={}",
                order.getId(), order.getItems().size(), reservation.reservationId());
        return reservation;
    }

    @Override
    public void release(InventoryReservation reservation) {
        if (reservations.remove(reservation.reservationId()) != null) {
            log.info("Released reservation={}", reservation.reservationId());
        } else {
            log.info("Release for unknown/already-released reservation={} (no-op)", reservation.reservationId());
        }
    }
}

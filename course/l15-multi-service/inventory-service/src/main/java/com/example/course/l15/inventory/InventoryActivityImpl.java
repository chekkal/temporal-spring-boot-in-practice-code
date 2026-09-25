package com.example.course.l15.inventory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.example.course.l15.api.InventoryActivity;
import com.example.course.l15.api.InventoryReservation;
import com.example.course.l15.api.Order;
import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory stand-in for a warehouse system.
 *
 * Failure trigger: any SKU starting with "OOS-" (out of stock) is rejected with a
 * non-retryable OutOfStock failure.
 */
@Component
public class InventoryActivityImpl implements InventoryActivity {

    private static final Logger log = LoggerFactory.getLogger(InventoryActivityImpl.class);

    public record Reservation(String reservationId, String orderId, List<String> skus, String status) {}

    private final ConcurrentMap<String, Reservation> reservations = new ConcurrentHashMap<>();

    @Override
    public InventoryReservation reserve(Order order) {
        List<String> skus = order.getItems().stream().map(Order.LineItem::sku).toList();
        List<String> outOfStock = skus.stream().filter(s -> s != null && s.startsWith("OOS-")).toList();
        if (!outOfStock.isEmpty()) {
            log.info("out of stock order={} skus={}", order.getId(), outOfStock);
            throw ApplicationFailure.newNonRetryableFailure(
                    "out of stock: " + outOfStock, "OutOfStock");
        }
        String reservationId = "res-" + order.getId();
        reservations.putIfAbsent(reservationId,
                new Reservation(reservationId, order.getId(), skus, "RESERVED"));
        log.info("reserved order={} reservation={} skus={}", order.getId(), reservationId, skus);
        return new InventoryReservation(reservationId, order.getId(), skus);
    }

    @Override
    public void release(InventoryReservation reservation) {
        reservations.computeIfPresent(reservation.reservationId(),
                (id, r) -> new Reservation(r.reservationId(), r.orderId(), r.skus(), "RELEASED"));
        log.info("released order={} reservation={}", reservation.orderId(), reservation.reservationId());
    }

    public Collection<Reservation> reservations() {
        return reservations.values();
    }

    public Reservation find(String reservationId) {
        return reservations.get(reservationId);
    }
}

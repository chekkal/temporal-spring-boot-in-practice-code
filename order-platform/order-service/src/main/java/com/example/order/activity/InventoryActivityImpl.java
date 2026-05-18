package com.example.order.activity;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.example.order.api.activity.InventoryActivity;
import com.example.order.api.model.InventoryReservation;
import com.example.order.api.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InventoryActivityImpl implements InventoryActivity {

    private static final Logger log = LoggerFactory.getLogger(InventoryActivityImpl.class);

    /** orderId -> reservationId so release(order) is naturally idempotent. */
    private final ConcurrentMap<String, String> reservations = new ConcurrentHashMap<>();

    @Override
    public InventoryReservation reserve(Order order) {
        String reservationId = "res-" + UUID.randomUUID();
        reservations.put(order.getId(), reservationId);
        log.info("Reserved inventory order={} reservation={} items={}",
                order.getId(), reservationId, order.getItems().size());
        return new InventoryReservation(reservationId, order.getId());
    }

    @Override
    public void release(Order order) {
        String removed = reservations.remove(order.getId());
        if (removed != null) {
            log.info("Released reservation={} for order={}", removed, order.getId());
        } else {
            log.info("Release called for order={} with no reservation (idempotent no-op)", order.getId());
        }
    }
}

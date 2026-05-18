package com.example.order.activity;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.example.order.api.activity.ShippingActivity;
import com.example.order.api.model.InventoryReservation;
import com.example.order.api.model.Order;
import com.example.order.api.model.ShipmentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ShippingActivityImpl implements ShippingActivity {

    private static final Logger log = LoggerFactory.getLogger(ShippingActivityImpl.class);

    private final ConcurrentMap<String, String> shipments = new ConcurrentHashMap<>();

    @Override
    public ShipmentResult schedule(Order order, InventoryReservation reservation) {
        String tracking = "trk-" + UUID.randomUUID();
        shipments.put(order.getId(), tracking);
        log.info("Scheduled shipment order={} tracking={} reservation={}",
                order.getId(), tracking, reservation.getReservationId());
        return new ShipmentResult(tracking, order.getId());
    }

    @Override
    public void cancel(Order order) {
        String removed = shipments.remove(order.getId());
        if (removed != null) {
            log.info("Cancelled shipment tracking={} for order={}", removed, order.getId());
        } else {
            log.info("Cancel called for order={} with no shipment (idempotent no-op)", order.getId());
        }
    }
}

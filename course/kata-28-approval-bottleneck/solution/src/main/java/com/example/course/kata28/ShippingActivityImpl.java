package com.example.course.kata28;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory shipping carrier.
 * Failure trigger: an orderId containing "carrier-down" fails every attempt.
 */
@Component
public class ShippingActivityImpl implements ShippingActivity {

    public static final String CARRIER_DOWN = "carrier-down";

    private static final Logger log = LoggerFactory.getLogger(ShippingActivityImpl.class);
    private final ConcurrentMap<String, String> shipments = new ConcurrentHashMap<>();

    @Override
    public void createShipment(OrderRequest request) {
        if (request.getOrderId().contains(CARRIER_DOWN)) {
            throw new IllegalStateException("Carrier unavailable for order " + request.getOrderId());
        }
        String trackingId = shipments.computeIfAbsent(request.getOrderId(), id -> "trk-" + UUID.randomUUID());
        log.info("[shipping] created shipment {} for order {}", trackingId, request.getOrderId());
    }
}

package com.example.course.l17;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** In-memory shipping fake. */
@Component
public class ShippingActivityImpl implements ShippingActivity {

    private static final Logger log = LoggerFactory.getLogger(ShippingActivityImpl.class);

    @Override
    public String createShipment(OrderRequest request) {
        String shipmentId = "SHIP-" + UUID.randomUUID();
        log.info("Created shipment item={} qty={} shipmentId={}", request.getItemId(), request.getQuantity(), shipmentId);
        return shipmentId;
    }
}

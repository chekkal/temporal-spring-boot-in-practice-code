package com.example.course.l14;

import java.util.UUID;

import io.temporal.failure.ApplicationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** In-memory shipping fake. Logs the address it actually ships to. */
@Component
public class ShippingActivityImpl implements ShippingActivity {

    private static final Logger log = LoggerFactory.getLogger(ShippingActivityImpl.class);

    @Override
    public ShipmentResult schedule(Order order, Address shippingAddress) {
        // Demo failure trigger: zip "00000" is undeliverable (not retried).
        if (shippingAddress == null || "00000".equals(shippingAddress.zip())) {
            throw ApplicationFailure.newNonRetryableFailure(
                    "Undeliverable address for order " + order.getId(), "UndeliverableAddress");
        }
        String tracking = "trk-" + UUID.randomUUID();
        log.info("Scheduled shipment order={} to={} tracking={}", order.getId(), shippingAddress, tracking);
        return new ShipmentResult(tracking, shippingAddress);
    }
}

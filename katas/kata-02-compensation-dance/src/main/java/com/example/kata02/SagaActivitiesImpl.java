package com.example.kata02;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Each activity flips its in-memory record so you can SEE compensations undo their
 * forward step. Failure trigger: orderId starting with "fail-step-N" fails that step.
 */
@Component
public class SagaActivitiesImpl implements SagaActivities {

    private static final Logger log = LoggerFactory.getLogger(SagaActivitiesImpl.class);

    private final ConcurrentMap<String, String> auths = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> reservations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, String> shipments = new ConcurrentHashMap<>();

    @Override public void validateOrder(String orderId) {
        if (orderId.startsWith("fail-step-1")) throw new RuntimeException("validation failed");
        log.info("[1] validated {}", orderId);
    }

    @Override public String authorizePayment(String orderId) {
        if (orderId.startsWith("fail-step-2")) throw new RuntimeException("payment failed");
        String id = "auth-" + UUID.randomUUID();
        auths.put(id, orderId);
        log.info("[2] authorized {} → {}", orderId, id);
        return id;
    }
    @Override public void voidPayment(String authorizationId) {
        if (auths.remove(authorizationId) != null) log.info("[-2] voided {}", authorizationId);
    }

    @Override public String reserveInventory(String orderId) {
        if (orderId.startsWith("fail-step-3")) throw new RuntimeException("inventory failed");
        String id = "res-" + UUID.randomUUID();
        reservations.put(id, orderId);
        log.info("[3] reserved {} → {}", orderId, id);
        return id;
    }
    @Override public void releaseInventory(String reservationId) {
        if (reservations.remove(reservationId) != null) log.info("[-3] released {}", reservationId);
    }

    @Override public String scheduleShipment(String orderId, String reservationId) {
        if (orderId.startsWith("fail-step-4")) throw new RuntimeException("shipping failed");
        String id = "trk-" + UUID.randomUUID();
        shipments.put(id, orderId);
        log.info("[4] scheduled {} → {}", orderId, id);
        return id;
    }
    @Override public void cancelShipment(String trackingNumber) {
        if (shipments.remove(trackingNumber) != null) log.info("[-4] cancelled {}", trackingNumber);
    }

    @Override public void notifyCustomer(String orderId) {
        if (orderId.startsWith("fail-step-5")) throw new RuntimeException("notify failed");
        log.info("[5] notified {}", orderId);
    }
}

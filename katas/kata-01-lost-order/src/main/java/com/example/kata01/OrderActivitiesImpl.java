package com.example.kata01;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderActivitiesImpl implements OrderActivities {

    private static final Logger log = LoggerFactory.getLogger(OrderActivitiesImpl.class);
    private final ConcurrentMap<String, String> auths = new ConcurrentHashMap<>();

    @Override
    public PaymentConfirmation authorizePayment(String orderId) {
        String authId = "auth-" + UUID.randomUUID();
        auths.put(authId, orderId);
        log.info("authorized order={} auth={}", orderId, authId);
        return new PaymentConfirmation(authId, orderId);
    }

    @Override
    public void voidAuthorization(String authorizationId) {
        if (auths.remove(authorizationId) != null) {
            log.info("voided auth={}", authorizationId);
        }
    }

    @Override
    public String reserveInventory(String orderId, List<String> items) {
        // Demo failure trigger: any item starting with "fail-" fails reservation.
        if (items.stream().anyMatch(i -> i.startsWith("fail-"))) {
            throw new RuntimeException("inventory unavailable for one or more items");
        }
        String reservationId = "res-" + UUID.randomUUID();
        log.info("reserved order={} reservation={}", orderId, reservationId);
        return reservationId;
    }

    @Override
    public void sendConfirmation(String orderId, String email) {
        log.info("sent confirmation order={} to={}", orderId, email);
    }
}

package com.example.kata06;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderActivitiesImpl implements OrderActivities {

    private static final Logger log = LoggerFactory.getLogger(OrderActivitiesImpl.class);

    @Override
    public PaymentResult authorizePayment(String orderId, double amount) {
        PaymentResult r = new PaymentResult("auth-" + UUID.randomUUID(), amount, 0.2);
        log.info("authorized order={} → {}", orderId, r.getTransactionId());
        return r;
    }

    @Override
    public boolean fraudCheck(String orderId, PaymentResult payment) {
        boolean ok = payment.getRiskScore() == null || payment.getRiskScore() < 0.8;
        log.info("fraudCheck order={} risk={} ok={}", orderId, payment.getRiskScore(), ok);
        return ok;
    }

    @Override
    public String reserveInventory(String orderId) {
        String id = "res-" + UUID.randomUUID();
        log.info("reserved order={} → {}", orderId, id);
        return id;
    }

    @Override
    public String ship(String orderId) {
        String trk = "trk-" + UUID.randomUUID();
        log.info("shipped order={} → {}", orderId, trk);
        return trk;
    }
}

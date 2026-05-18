package com.example.kata05;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class SecondaryPaymentActivityImpl implements SecondaryPaymentActivity {
    private static final Logger log = LoggerFactory.getLogger(SecondaryPaymentActivityImpl.class);

    @Override
    public String charge(String orderId, double amount) {
        String tx = "secondary-" + UUID.randomUUID();
        log.info("secondary charged order={} amount={} tx={}", orderId, amount, tx);
        return tx;
    }
}

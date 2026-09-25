package com.example.course.kata26;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-memory inventory.
 * Failure trigger: any item named "out-of-stock" makes the reservation fail.
 */
@Component
public class InventoryActivityImpl implements InventoryActivity {

    public static final String OUT_OF_STOCK_ITEM = "out-of-stock";

    private static final Logger log = LoggerFactory.getLogger(InventoryActivityImpl.class);
    private final ConcurrentMap<String, List<String>> reservations = new ConcurrentHashMap<>();

    @Override
    public void reserveInventory(OrderRequest request) {
        if (request.getItems() != null && request.getItems().contains(OUT_OF_STOCK_ITEM)) {
            throw new IllegalStateException("Item out of stock for order " + request.getOrderId());
        }
        reservations.put(request.getOrderId(), List.copyOf(request.getItems()));
        log.info("[inventory] reserved {} for order {}", request.getItems(), request.getOrderId());
    }

    @Override
    public void releaseInventory(OrderRequest request) {
        if (reservations.remove(request.getOrderId()) != null) {
            log.info("[inventory] released reservation for order {}", request.getOrderId());
        } else {
            log.info("[inventory] nothing to release for order {}", request.getOrderId());
        }
    }

    public boolean isReserved(String orderId) {
        return reservations.containsKey(orderId);
    }
}

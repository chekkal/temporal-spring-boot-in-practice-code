package com.example.course.l17;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** In-memory fake of the inventory system. */
@Component
public class InMemoryInventoryService implements InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryInventoryService.class);

    @Override
    public boolean reserve(String itemId, int quantity) {
        // Demo failure triggers:
        //   "oos-..."   -> out of stock (returns false)
        //   "error-..." -> the inventory system itself fails (throws; the activity is retried, then gives up)
        if (itemId.startsWith("error-")) {
            throw new IllegalStateException("Inventory system unavailable");
        }
        if (itemId.startsWith("oos-")) {
            log.info("Out of stock item={} qty={}", itemId, quantity);
            return false;
        }
        log.info("Reserved item={} qty={}", itemId, quantity);
        return true;
    }
}

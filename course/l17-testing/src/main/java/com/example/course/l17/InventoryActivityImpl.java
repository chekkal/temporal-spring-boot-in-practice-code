package com.example.course.l17;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class InventoryActivityImpl implements InventoryActivity {

    private final InventoryService inventoryService;

    /** Used by the plain JUnit tests: {@code new InventoryActivityImpl()} as on the slides. */
    public InventoryActivityImpl() {
        this(new InMemoryInventoryService());
    }

    @Autowired
    public InventoryActivityImpl(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @Override
    public boolean reserveStock(OrderRequest request) {
        return inventoryService.reserve(request.getItemId(), request.getQuantity());
    }
}

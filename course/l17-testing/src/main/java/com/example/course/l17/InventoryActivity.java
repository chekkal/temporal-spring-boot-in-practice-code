package com.example.course.l17;

import io.temporal.activity.ActivityInterface;

@ActivityInterface
public interface InventoryActivity {

    /** @return false when out of stock. The workflow treats a thrown failure the same way. */
    boolean reserveStock(OrderRequest request);
}

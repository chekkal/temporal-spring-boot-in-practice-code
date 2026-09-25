package com.example.course.kata26;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface InventoryActivity {
    @ActivityMethod
    void reserveInventory(OrderRequest request);

    @ActivityMethod
    void releaseInventory(OrderRequest request);
}

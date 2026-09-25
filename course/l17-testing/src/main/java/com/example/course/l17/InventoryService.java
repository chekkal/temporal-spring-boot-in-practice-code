package com.example.course.l17;

/** Boundary to the inventory system. Mocked with @MockitoBean in OrderWorkflowIntegrationTest. */
public interface InventoryService {

    /** @return false when there is not enough stock */
    boolean reserve(String itemId, int quantity);
}

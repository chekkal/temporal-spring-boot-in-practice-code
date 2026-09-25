package com.example.course.l15.inventory;

import java.util.Collection;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only view of this service's own data, so you can watch releases happen. */
@RestController
public class InventoryController {

    private final InventoryActivityImpl inventory;

    public InventoryController(InventoryActivityImpl inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/api/reservations")
    public Collection<InventoryActivityImpl.Reservation> list() {
        return inventory.reservations();
    }
}

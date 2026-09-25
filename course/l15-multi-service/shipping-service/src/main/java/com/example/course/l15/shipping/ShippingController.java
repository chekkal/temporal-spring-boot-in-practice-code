package com.example.course.l15.shipping;

import java.util.Collection;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only view of this service's own data. */
@RestController
public class ShippingController {

    private final ShippingActivityImpl shipping;

    public ShippingController(ShippingActivityImpl shipping) {
        this.shipping = shipping;
    }

    @GetMapping("/api/shipments")
    public Collection<ShippingActivityImpl.Shipment> list() {
        return shipping.shipments();
    }
}

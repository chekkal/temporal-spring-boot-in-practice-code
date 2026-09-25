package com.example.course.l15.inventory;

import java.math.BigDecimal;
import java.util.List;

import com.example.course.l15.api.InventoryReservation;
import com.example.course.l15.api.Order;
import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryActivityImplTest {

    private final InventoryActivityImpl inventory = new InventoryActivityImpl();

    private static Order order(String id, String... skus) {
        List<Order.LineItem> items = java.util.Arrays.stream(skus).map(s -> new Order.LineItem(s, 1)).toList();
        return new Order(id, "cust-1", items, new BigDecimal("10.00"), "card-ok", "1 Main St");
    }

    @Test
    void reserve_recordsAReservationForAllSkus() {
        InventoryReservation reservation = inventory.reserve(order("ord-1", "SKU-1", "SKU-2"));

        assertEquals("res-ord-1", reservation.reservationId());
        assertEquals(List.of("SKU-1", "SKU-2"), reservation.skus());
        assertEquals("RESERVED", inventory.find("res-ord-1").status());
    }

    @Test
    void reserve_outOfStockSkuFailsWithoutRetry() {
        ApplicationFailure failure = assertThrows(ApplicationFailure.class,
                () -> inventory.reserve(order("ord-2", "SKU-1", "OOS-2")));

        assertEquals("OutOfStock", failure.getType());
        assertTrue(failure.isNonRetryable());
        assertTrue(inventory.reservations().isEmpty());
    }

    @Test
    void release_marksTheReservationReleased_andCanRunTwice() {
        InventoryReservation reservation = inventory.reserve(order("ord-3", "SKU-1"));

        inventory.release(reservation);
        inventory.release(reservation);

        assertEquals("RELEASED", inventory.find("res-ord-3").status());
    }
}

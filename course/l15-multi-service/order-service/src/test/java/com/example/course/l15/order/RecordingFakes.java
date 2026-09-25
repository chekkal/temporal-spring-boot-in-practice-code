package com.example.course.l15.order;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.example.course.l15.api.InventoryActivity;
import com.example.course.l15.api.InventoryReservation;
import com.example.course.l15.api.Order;
import com.example.course.l15.api.PaymentActivity;
import com.example.course.l15.api.PaymentResult;
import com.example.course.l15.api.ShipmentResult;
import com.example.course.l15.api.ShippingActivity;
import io.temporal.activity.Activity;
import io.temporal.failure.ApplicationFailure;

/**
 * Test doubles for the three downstream services. They use the same failure triggers as the
 * real implementations ("decline" payment method, "OOS-" SKU, "nowhere" address) and write
 * every call to a shared journal as "method@task-queue", where the task queue is read from
 * the activity context. The journal therefore shows which queue, i.e. which service, ran
 * each step, and in what order.
 */
final class RecordingFakes {

    final List<String> journal = Collections.synchronizedList(new ArrayList<>());

    final PaymentActivity payment = new PaymentActivity() {
        @Override
        public PaymentResult authorize(Order order) {
            record("authorize");
            if (order.getPaymentMethod().contains("decline")) {
                throw ApplicationFailure.newNonRetryableFailure("card declined", "PaymentDeclined");
            }
            return new PaymentResult("txn-" + order.getId(), order.getId(), order.getAmount());
        }

        @Override
        public void refund(PaymentResult payment) {
            record("refund");
        }
    };

    final InventoryActivity inventory = new InventoryActivity() {
        @Override
        public InventoryReservation reserve(Order order) {
            record("reserve");
            List<String> skus = order.getItems().stream().map(Order.LineItem::sku).toList();
            if (skus.stream().anyMatch(s -> s.startsWith("OOS-"))) {
                throw ApplicationFailure.newNonRetryableFailure("out of stock", "OutOfStock");
            }
            return new InventoryReservation("res-" + order.getId(), order.getId(), skus);
        }

        @Override
        public void release(InventoryReservation reservation) {
            record("release");
        }
    };

    final ShippingActivity shipping = new ShippingActivity() {
        @Override
        public ShipmentResult schedule(Order order, InventoryReservation reservation) {
            record("schedule");
            if (order.getShippingAddress().toLowerCase().contains("nowhere")) {
                throw ApplicationFailure.newNonRetryableFailure("undeliverable", "UndeliverableAddress");
            }
            return new ShipmentResult("shp-" + order.getId(), order.getId(), "TRK-" + order.getId());
        }

        @Override
        public void cancel(ShipmentResult shipment) {
            record("cancel");
        }
    };

    private void record(String method) {
        journal.add(method + "@" + Activity.getExecutionContext().getInfo().getActivityTaskQueue());
    }
}

package com.example.order.workflow;

import java.time.Duration;

import com.example.order.api.activity.InventoryActivity;
import com.example.order.api.activity.NotificationActivity;
import com.example.order.api.activity.PaymentActivity;
import com.example.order.api.activity.ShippingActivity;
import com.example.order.api.model.InventoryReservation;
import com.example.order.api.model.Order;
import com.example.order.api.model.OrderResult;
import com.example.order.api.model.OrderStatus;
import com.example.order.api.model.PaymentResult;
import com.example.order.api.model.ShipmentResult;
import com.example.order.api.workflow.OrderFulfillmentWorkflow;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;

/**
 * Reference saga implementation — mirrors the listing in Chapter 13.
 *
 * Order of operations: validate → authorize → reserve → ship → notify.
 * On failure, sequential reverse-order compensation; aggregate errors with
 * setContinueWithError(true) so payment refund + inventory release both run even if
 * the carrier API is down.
 */
public class OrderFulfillmentWorkflowImpl implements OrderFulfillmentWorkflow {

    private final PaymentActivity payment = Workflow.newActivityStub(
            PaymentActivity.class, defaultActivityOptions());
    private final InventoryActivity inventory = Workflow.newActivityStub(
            InventoryActivity.class, defaultActivityOptions());
    private final ShippingActivity shipping = Workflow.newActivityStub(
            ShippingActivity.class, defaultActivityOptions());
    private final NotificationActivity notification = Workflow.newActivityStub(
            NotificationActivity.class, notificationOptions());

    private OrderStatus status = OrderStatus.RECEIVED;
    private volatile String cancelReason = null;

    @Override
    public OrderResult fulfill(Order order) {
        Saga.Options sagaOptions = new Saga.Options.Builder()
                .setParallelCompensation(false)
                .setContinueWithError(true)
                .build();
        Saga saga = new Saga(sagaOptions);

        try {
            status = OrderStatus.VALIDATING;
            payment.validateOrder(order);

            status = OrderStatus.AUTHORIZING_PAYMENT;
            PaymentResult paymentResult = payment.authorize(order);
            saga.addCompensation(() -> payment.voidAuthorization(paymentResult.getTransactionId()));

            status = OrderStatus.RESERVING_INVENTORY;
            InventoryReservation reservation = inventory.reserve(order);
            saga.addCompensation(inventory::release, order);

            status = OrderStatus.SCHEDULING_SHIPMENT;
            ShipmentResult shipment = shipping.schedule(order, reservation);
            saga.addCompensation(shipping::cancel, order);

            status = OrderStatus.NOTIFYING;
            notification.sendConfirmation(order, shipment);

            status = OrderStatus.COMPLETED;
            return new OrderResult(order.getId(), status);

        } catch (ActivityFailure e) {
            status = OrderStatus.COMPENSATING;
            saga.compensate();
            status = OrderStatus.FAILED;
            throw e;
        }
    }

    @Override
    public void cancel(String reason) {
        this.cancelReason = reason;
        // For brevity, this signal records the reason; a full implementation would
        // use Workflow.await on a cancellation flag at safe checkpoints between steps.
    }

    @Override
    public OrderStatus getStatus() {
        return status;
    }

    private static ActivityOptions defaultActivityOptions() {
        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setBackoffCoefficient(2.0)
                        .setMaximumInterval(Duration.ofMinutes(1))
                        .setMaximumAttempts(3)
                        .build())
                .build();
    }

    private static ActivityOptions notificationOptions() {
        // Notifications are best-effort and shouldn't block order completion forever.
        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(15))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumAttempts(2)
                        .build())
                .build();
    }
}

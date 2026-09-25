package com.example.course.l14;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import io.temporal.workflow.WorkflowInit;
import org.slf4j.Logger;

/**
 * Lifecycle:
 * <pre>
 *   PENDING (edit window, up to EDIT_WINDOW)  -- updateOrderItems / updateShippingAddress / cancelOrder
 *     -> CHARGING_PAYMENT                      -- cancel after this point refunds the payment
 *     -> RESERVING_INVENTORY                   -- cancel after this point releases stock and refunds
 *     -> SCHEDULING_SHIPMENT                   -- ships to the *current* shippingAddress
 *     -> COMPLETED
 * </pre>
 */
public class OrderFulfillmentWorkflowImpl implements OrderFulfillmentWorkflow {

    /** How long the order stays PENDING (editable) before it is charged. */
    static final Duration EDIT_WINDOW = Duration.ofSeconds(60);

    private static final Logger log = Workflow.getLogger(OrderFulfillmentWorkflowImpl.class);

    private final PaymentActivity paymentActivity = Workflow.newActivityStub(PaymentActivity.class, activityOptions());
    private final InventoryActivity inventoryActivity = Workflow.newActivityStub(InventoryActivity.class, activityOptions());
    private final ShippingActivity shippingActivity = Workflow.newActivityStub(ShippingActivity.class, activityOptions());

    private OrderStatus status = OrderStatus.PENDING;
    private boolean cancelRequested = false;
    private String cancelReason;
    private Address shippingAddress;
    private PaymentResult paymentResult;
    private List<OrderItem> orderItems;
    private BigDecimal total;

    /**
     * Runs before any signal, query or update handler. A signal or update can arrive in the very
     * first workflow task; initialising state here (instead of at the top of fulfill) guarantees
     * the handlers never see null fields and fulfill never overwrites an early update.
     */
    @WorkflowInit
    public OrderFulfillmentWorkflowImpl(Order order) {
        this.shippingAddress = order.getShippingAddress();
        this.orderItems = order.getItems();
        this.total = calculateTotal(order.getItems());
    }

    @Override
    public OrderResult fulfill(Order order) {
        // PENDING: wait for the edit window to close, or for a cancel signal.
        log.info("Order {} is PENDING for {}", order.getId(), EDIT_WINDOW);
        Workflow.await(EDIT_WINDOW, () -> cancelRequested);
        if (cancelRequested) {
            status = OrderStatus.CANCELLED;
            log.info("Order {} cancelled while pending: {}", order.getId(), cancelReason);
            return new OrderResult(order.getId(), OrderStatus.CANCELLED);
        }

        // Edit window closed: charge for the items as they are now.
        order.setItems(orderItems);
        order.setTotal(total);

        status = OrderStatus.CHARGING_PAYMENT;
        PaymentResult payment;
        try {
            payment = paymentActivity.charge(order);
        } catch (ActivityFailure e) {
            status = OrderStatus.FAILED;
            throw e;
        }
        this.paymentResult = payment;

        // Check for cancellation between steps
        if (cancelRequested) {
            compensatePayment(payment);
            status = OrderStatus.CANCELLED;
            return new OrderResult(order.getId(), OrderStatus.CANCELLED);
        }

        status = OrderStatus.RESERVING_INVENTORY;
        InventoryReservation reservation;
        try {
            reservation = inventoryActivity.reserve(order);
        } catch (ActivityFailure e) {
            compensatePayment(payment);
            status = OrderStatus.FAILED;
            throw e;
        }

        if (cancelRequested) {
            inventoryActivity.release(reservation);
            compensatePayment(payment);
            status = OrderStatus.CANCELLED;
            return new OrderResult(order.getId(), OrderStatus.CANCELLED);
        }

        status = OrderStatus.SCHEDULING_SHIPMENT;
        try {
            // shippingAddress is the field, so a signal received before this line is honoured.
            ShipmentResult shipment = shippingActivity.schedule(order, shippingAddress);
            log.info("Order {} shipped to {} tracking={}", order.getId(), shipment.shippedTo(), shipment.trackingNumber());
        } catch (ActivityFailure e) {
            inventoryActivity.release(reservation);
            compensatePayment(payment);
            status = OrderStatus.FAILED;
            throw e;
        }

        status = OrderStatus.COMPLETED;
        return new OrderResult(order.getId(), OrderStatus.COMPLETED);
    }

    // ---------------------------------------------------------------- signals

    @Override
    public void cancelOrder(String reason) {
        this.cancelReason = reason;
        this.cancelRequested = true;
    }

    @Override
    public void updateShippingAddress(Address newAddress) {
        this.shippingAddress = newAddress;
    }

    // ---------------------------------------------------------------- queries

    @Override
    public OrderStatus getStatus() {
        return this.status;
    }

    @Override
    public OrderDetails getDetails() {
        return new OrderDetails(status, shippingAddress, paymentResult);
    }

    // ---------------------------------------------------------------- update

    @Override
    public UpdateOrderResult updateOrderItems(List<OrderItem> newItems) {
        if (status != OrderStatus.PENDING) {
            throw ApplicationFailure.newFailure(
                    "Cannot modify order in state: " + status,
                    "InvalidState");
        }
        this.orderItems = newItems;
        this.total = calculateTotal(newItems);
        return new UpdateOrderResult(total);
    }

    @Override
    public void validateUpdateOrderItems(List<OrderItem> newItems) {
        if (newItems == null || newItems.isEmpty()) {
            throw ApplicationFailure.newFailure(
                    "Order must have at least one item",
                    "ValidationError");
        }
    }

    // ---------------------------------------------------------------- helpers

    private void compensatePayment(PaymentResult payment) {
        log.info("Refunding payment {}", payment.transactionId());
        paymentActivity.refund(payment);
    }

    private static BigDecimal calculateTotal(List<OrderItem> items) {
        if (items == null) {
            return BigDecimal.ZERO;
        }
        return items.stream()
                .map(i -> i.unitPrice().multiply(BigDecimal.valueOf(i.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static ActivityOptions activityOptions() {
        return ActivityOptions.newBuilder()
                .setStartToCloseTimeout(Duration.ofSeconds(10))
                .setRetryOptions(RetryOptions.newBuilder()
                        .setInitialInterval(Duration.ofSeconds(1))
                        .setMaximumAttempts(3)
                        .build())
                .build();
    }
}

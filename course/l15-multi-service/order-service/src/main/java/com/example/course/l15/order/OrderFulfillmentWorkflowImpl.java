package com.example.course.l15.order;

import java.time.Duration;

import com.example.course.l15.api.InventoryActivity;
import com.example.course.l15.api.InventoryReservation;
import com.example.course.l15.api.Order;
import com.example.course.l15.api.OrderFulfillmentWorkflow;
import com.example.course.l15.api.OrderResult;
import com.example.course.l15.api.OrderStatus;
import com.example.course.l15.api.PaymentActivity;
import com.example.course.l15.api.PaymentResult;
import com.example.course.l15.api.ShipmentResult;
import com.example.course.l15.api.ShippingActivity;
import io.temporal.activity.ActivityOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

/**
 * Lives in order-service and runs on the "order-service" task queue.
 *
 * Every activity stub names the task queue of the service that implements it (slide 3).
 * The calls below read like local method calls; Temporal puts each activity task on the
 * named queue and the worker of that service picks it up.
 */
public class OrderFulfillmentWorkflowImpl
        implements OrderFulfillmentWorkflow {

    private static final Logger log = Workflow.getLogger(OrderFulfillmentWorkflowImpl.class);

    private final PaymentActivity payment =
        Workflow.newActivityStub(PaymentActivity.class,
            ActivityOptions.newBuilder()
                .setTaskQueue("payment-service")
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .build());

    private final InventoryActivity inventory =
        Workflow.newActivityStub(InventoryActivity.class,
            ActivityOptions.newBuilder()
                .setTaskQueue("inventory-service")
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .build());

    private final ShippingActivity shipping =
        Workflow.newActivityStub(ShippingActivity.class,
            ActivityOptions.newBuilder()
                .setTaskQueue("shipping-service")
                .setStartToCloseTimeout(Duration.ofSeconds(30))
                .build());

    private OrderStatus status = OrderStatus.RECEIVED;

    @Override
    public OrderResult fulfill(Order order) {
        // Compensations run one after the other, last registered first.
        Saga saga = new Saga(new Saga.Options.Builder()
                .setParallelCompensation(false)
                .setContinueWithError(true)
                .build());

        try {
            status = OrderStatus.AUTHORIZING_PAYMENT;
            PaymentResult paymentResult = payment.authorize(order);
            saga.addCompensation(payment::refund, paymentResult);

            status = OrderStatus.RESERVING_INVENTORY;
            InventoryReservation reservation = inventory.reserve(order);
            saga.addCompensation(inventory::release, reservation);

            status = OrderStatus.SCHEDULING_SHIPMENT;
            ShipmentResult shipment = shipping.schedule(order, reservation);
            saga.addCompensation(shipping::cancel, shipment);

            status = OrderStatus.COMPLETED;
            log.info("order {} completed, tracking {}", order.getId(), shipment.trackingNumber());
            return new OrderResult(order.getId(), status, paymentResult.transactionId(),
                    reservation.reservationId(), shipment.trackingNumber());

        } catch (ActivityFailure e) {
            log.warn("order {} failed at {}, compensating", order.getId(), status);
            status = OrderStatus.COMPENSATING;
            saga.compensate();
            status = OrderStatus.FAILED;
            throw e;
        }
    }

    @Override
    public OrderStatus getStatus() {
        return status;
    }
}

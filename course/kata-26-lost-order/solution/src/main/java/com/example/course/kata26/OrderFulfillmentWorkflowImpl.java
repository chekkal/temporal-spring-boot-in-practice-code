package com.example.course.kata26;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

public class OrderFulfillmentWorkflowImpl implements OrderFulfillmentWorkflow {

    private static final Logger log = Workflow.getLogger(OrderFulfillmentWorkflowImpl.class);

    private OrderStatus status = OrderStatus.STARTED;
    private final List<Runnable> compensations = new ArrayList<>();

    private final ActivityOptions options = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setMaximumAttempts(3).build())
            .build();

    private final PaymentActivity payment =
            Workflow.newActivityStub(PaymentActivity.class, options);
    private final InventoryActivity inventory =
            Workflow.newActivityStub(InventoryActivity.class, options);
    private final ShippingActivity shipping =
            Workflow.newActivityStub(ShippingActivity.class, options);

    @Override
    public OrderStatus getStatus() {
        return status;
    }

    @Override
    public OrderResult process(OrderRequest request) {
        try {
            status = OrderStatus.CHARGING_PAYMENT;
            PaymentResult paymentResult = payment.chargePayment(request);
            compensations.add(() ->
                    payment.refundPayment(paymentResult.getPaymentId()));

            status = OrderStatus.RESERVING_INVENTORY;
            inventory.reserveInventory(request);
            compensations.add(() ->
                    inventory.releaseInventory(request));

            status = OrderStatus.CREATING_SHIPMENT;
            shipping.createShipment(request);

            status = OrderStatus.COMPLETED;
            log.info("Order {} completed", request.getOrderId());
            return OrderResult.success(request.getOrderId());

        } catch (ActivityFailure e) {
            log.warn("Order {} failed at {}, compensating {} step(s)",
                    request.getOrderId(), status, compensations.size());
            status = OrderStatus.COMPENSATING;
            Collections.reverse(compensations);
            for (Runnable comp : compensations) {
                comp.run();  // Each is an activity call
            }
            status = OrderStatus.FAILED;
            return OrderResult.failed(request.getOrderId(),
                    e.getCause().getMessage());
        }
    }
}

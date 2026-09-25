package com.example.course.l14;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import io.temporal.api.enums.v1.EventType;
import io.temporal.api.history.v1.HistoryEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.client.WorkflowUpdateException;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Lecture 14 behaviour, run in-process against the time-skipping test server (no Temporal server needed).
 *
 * The extension does not start the worker ({@code setDoNotStart(true)}) so each test can register
 * its own Mockito activity mocks first, then call {@code testEnv.start()}.
 *
 * Time skipping: while the test is not blocked on a workflow result the test server's clock only
 * moves with real time, so the 60-second PENDING window stays open for updates and signals.
 * {@code getResult()} or {@code testEnv.sleep(...)} fast-forwards it.
 */
class OrderFulfillmentWorkflowTest {

    @RegisterExtension
    static final TestWorkflowExtension testWorkflow = TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(OrderFulfillmentWorkflowImpl.class)
            .setDoNotStart(true)
            .build();

    private static final Address HOME = new Address("1 Main St", "Springfield", "12345", "US");
    private static final Address OFFICE = new Address("99 Office Park", "Shelbyville", "54321", "US");
    private static final PaymentResult PAYMENT = new PaymentResult("pay-1", new BigDecimal("20.00"));

    private PaymentActivity payment;
    private InventoryActivity inventory;
    private ShippingActivity shipping;

    @BeforeEach
    void mocks() {
        payment = mock(PaymentActivity.class);
        inventory = mock(InventoryActivity.class);
        shipping = mock(ShippingActivity.class);
        when(payment.charge(any())).thenReturn(PAYMENT);
        when(inventory.reserve(any())).thenReturn(new InventoryReservation("res-1", "o-1"));
        when(shipping.schedule(any(), any())).thenAnswer(inv -> new ShipmentResult("trk-1", inv.getArgument(1)));
    }

    private OrderFulfillmentWorkflow start(TestWorkflowEnvironment testEnv, WorkflowClient client, Worker worker) {
        worker.registerActivitiesImplementations(payment, inventory, shipping);
        testEnv.start();
        OrderFulfillmentWorkflow wf = client.newWorkflowStub(OrderFulfillmentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("order-o-1")
                        .setTaskQueue(worker.getTaskQueue())
                        .build());
        WorkflowClient.start(wf::fulfill, new Order("o-1", "cust-1",
                List.of(new OrderItem("sku-a", 2, new BigDecimal("10.00"))), HOME, "card-123"));
        return wf;
    }

    private static OrderResult result(OrderFulfillmentWorkflow wf) {
        try {
            return WorkflowStub.fromTyped(wf).getResult(10, TimeUnit.SECONDS, OrderResult.class);
        } catch (TimeoutException e) {
            throw new AssertionError("workflow did not finish", e);
        }
    }

    // ---------------------------------------------------------------- update

    @Test
    void update_duringPending_changesTotal_andReturnsItSynchronously(
            TestWorkflowEnvironment testEnv, WorkflowClient client, Worker worker) {
        OrderFulfillmentWorkflow wf = start(testEnv, client, worker);

        UpdateOrderResult updated = wf.updateOrderItems(List.of(
                new OrderItem("sku-a", 1, new BigDecimal("10.00")),
                new OrderItem("sku-b", 3, new BigDecimal("5.50"))));

        assertEquals(0, new BigDecimal("26.50").compareTo(updated.total()));

        assertEquals(OrderStatus.COMPLETED, result(wf).status());
        // The charge used the updated items and total.
        verify(payment).charge(org.mockito.ArgumentMatchers.argThat(o ->
                o.getItems().size() == 2 && o.getTotal().compareTo(new BigDecimal("26.50")) == 0));
    }

    @Test
    void validator_rejectsEmptyList_andRejectedUpdateIsNotInHistory(
            TestWorkflowEnvironment testEnv, WorkflowClient client, Worker worker) {
        OrderFulfillmentWorkflow wf = start(testEnv, client, worker);

        WorkflowUpdateException rejected = assertThrows(WorkflowUpdateException.class,
                () -> wf.updateOrderItems(List.of()));
        ApplicationFailure cause = (ApplicationFailure) rejected.getCause();
        // The validator's message reaches the caller. Its type ("ValidationError") does not:
        // Java SDK 1.34 wraps validator exceptions before converting them, so getType() returns
        // "io.temporal.internal.worker.WorkflowExecutionException". Match on the message instead.
        assertEquals("Order must have at least one item", cause.getOriginalMessage());
        // One valid update so we can see what an accepted update looks like in history.
        wf.updateOrderItems(List.of(new OrderItem("sku-a", 1, new BigDecimal("10.00"))));
        wf.cancelOrder("done with this test");
        result(wf);

        List<HistoryEvent> events = client.fetchHistory("order-o-1").getEvents();
        long accepted = count(events, EventType.EVENT_TYPE_WORKFLOW_EXECUTION_UPDATE_ACCEPTED);
        long completed = count(events, EventType.EVENT_TYPE_WORKFLOW_EXECUTION_UPDATE_COMPLETED);
        long rejectedEvents = count(events, EventType.EVENT_TYPE_WORKFLOW_EXECUTION_UPDATE_REJECTED);
        // Only the valid update is recorded. The validator ran on the worker and the
        // rejection went straight back to the caller: nothing was written to history.
        assertEquals(1, accepted);
        assertEquals(1, completed);
        assertEquals(0, rejectedEvents);
    }

    @Test
    void update_afterPending_isRejectedWithInvalidState(
            TestWorkflowEnvironment testEnv, WorkflowClient client, Worker worker) throws Exception {
        CountDownLatch chargeStarted = new CountDownLatch(1);
        CountDownLatch releaseCharge = new CountDownLatch(1);
        when(payment.charge(any())).thenAnswer(inv -> {
            chargeStarted.countDown();
            releaseCharge.await(10, TimeUnit.SECONDS);
            return PAYMENT;
        });
        OrderFulfillmentWorkflow wf = start(testEnv, client, worker);

        testEnv.sleep(Duration.ofSeconds(61));           // close the edit window
        assertTrue(chargeStarted.await(10, TimeUnit.SECONDS));
        assertEquals(OrderStatus.CHARGING_PAYMENT, wf.getStatus());

        WorkflowUpdateException rejected = assertThrows(WorkflowUpdateException.class,
                () -> wf.updateOrderItems(List.of(new OrderItem("sku-z", 1, BigDecimal.ONE))));
        ApplicationFailure cause = (ApplicationFailure) rejected.getCause();
        assertEquals("InvalidState", cause.getType());
        assertThat(cause.getOriginalMessage()).contains("CHARGING_PAYMENT");

        releaseCharge.countDown();
        assertEquals(OrderStatus.COMPLETED, result(wf).status());
    }

    // ---------------------------------------------------------------- signals

    @Test
    void cancel_duringPending_cancelsWithoutCharging(
            TestWorkflowEnvironment testEnv, WorkflowClient client, Worker worker) {
        OrderFulfillmentWorkflow wf = start(testEnv, client, worker);

        wf.cancelOrder("customer changed their mind");

        assertEquals(OrderStatus.CANCELLED, result(wf).status());
        verify(payment, never()).charge(any());
        verify(payment, never()).refund(any());
    }

    @Test
    void cancel_afterCharge_refundsPayment(
            TestWorkflowEnvironment testEnv, WorkflowClient client, Worker worker) throws Exception {
        CountDownLatch chargeStarted = new CountDownLatch(1);
        CountDownLatch releaseCharge = new CountDownLatch(1);
        when(payment.charge(any())).thenAnswer(inv -> {
            chargeStarted.countDown();
            releaseCharge.await(10, TimeUnit.SECONDS);
            return PAYMENT;
        });
        OrderFulfillmentWorkflow wf = start(testEnv, client, worker);

        testEnv.sleep(Duration.ofSeconds(61));
        assertTrue(chargeStarted.await(10, TimeUnit.SECONDS));
        wf.cancelOrder("too late, already charging");       // arrives while charge() is running
        releaseCharge.countDown();

        assertEquals(OrderStatus.CANCELLED, result(wf).status());
        verify(payment).refund(PAYMENT);
        verify(inventory, never()).reserve(any());
        verify(shipping, never()).schedule(any(), any());
    }

    @Test
    void addressSignal_beforeShipping_isTheAddressShippedTo(
            TestWorkflowEnvironment testEnv, WorkflowClient client, Worker worker) {
        OrderFulfillmentWorkflow wf = start(testEnv, client, worker);

        wf.updateShippingAddress(OFFICE);

        assertEquals(OrderStatus.COMPLETED, result(wf).status());
        verify(shipping).schedule(any(), eq(OFFICE));
    }

    // ---------------------------------------------------------------- queries

    @Test
    void queries_reflectStateMidFlight(
            TestWorkflowEnvironment testEnv, WorkflowClient client, Worker worker) throws Exception {
        CountDownLatch reserveStarted = new CountDownLatch(1);
        CountDownLatch releaseReserve = new CountDownLatch(1);
        when(inventory.reserve(any())).thenAnswer(inv -> {
            reserveStarted.countDown();
            releaseReserve.await(10, TimeUnit.SECONDS);
            return new InventoryReservation("res-1", "o-1");
        });
        OrderFulfillmentWorkflow wf = start(testEnv, client, worker);

        assertEquals(OrderStatus.PENDING, wf.getStatus());
        assertEquals(HOME, wf.getDetails().shippingAddress());
        assertEquals(null, wf.getDetails().paymentResult());

        wf.updateShippingAddress(OFFICE);
        // A signal is asynchronous: poll until the workflow has processed it.
        assertEquals(OFFICE, eventually(() -> wf.getDetails().shippingAddress(), OFFICE));

        testEnv.sleep(Duration.ofSeconds(61));
        assertTrue(reserveStarted.await(10, TimeUnit.SECONDS));
        OrderDetails midFlight = wf.getDetails();
        assertEquals(OrderStatus.RESERVING_INVENTORY, midFlight.status());
        assertEquals(PAYMENT, midFlight.paymentResult());

        releaseReserve.countDown();
        assertEquals(OrderStatus.COMPLETED, result(wf).status());
        // Queries still work after the workflow has closed.
        assertEquals(OrderStatus.COMPLETED, wf.getStatus());
    }

    private static long count(List<HistoryEvent> events, EventType type) {
        return events.stream().filter(e -> e.getEventType() == type).count();
    }

    private static <T> T eventually(Supplier<T> read, T expected) throws InterruptedException {
        T value = read.get();
        for (int i = 0; i < 50 && !expected.equals(value); i++) {
            Thread.sleep(100);
            value = read.get();
        }
        return value;
    }
}

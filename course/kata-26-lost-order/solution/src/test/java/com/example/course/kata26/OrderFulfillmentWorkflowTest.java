package com.example.course.kata26;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkflowImplementationOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Runs the workflow against Temporal's in-process test server (no Docker, time skipping on).
 * The real in-memory activity beans are wrapped so every call is recorded in order.
 */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class OrderFulfillmentWorkflowTest {

    private TestWorkflowEnvironment testEnv;
    private WorkflowClient client;

    private final CallLog calls = new CallLog();
    private final PaymentGateway gateway = new PaymentGateway();
    private final PaymentRepository paymentRepository = new PaymentRepository();
    private final PaymentActivityImpl payment = new PaymentActivityImpl(gateway, paymentRepository);
    private final InventoryActivityImpl inventory = new InventoryActivityImpl();
    private final ShippingActivityImpl shipping = new ShippingActivityImpl();

    /** When set, createShipment blocks until the latch is released (to observe the workflow mid-flight). */
    private volatile CountDownLatch shipmentGate;

    @BeforeEach
    void setUp() {
        testEnv = TestWorkflowEnvironment.newInstance();
        Worker worker = testEnv.newWorker(TemporalConfig.TASK_QUEUE);
        // Fail the workflow (instead of retrying the workflow task forever) on any unexpected
        // exception, e.g. the UnsupportedOperationException of an unsolved starter.
        worker.registerWorkflowImplementationTypes(
                WorkflowImplementationOptions.newBuilder()
                        .setFailWorkflowExceptionTypes(Throwable.class)
                        .build(),
                OrderFulfillmentWorkflowImpl.class);
        worker.registerActivitiesImplementations(
                new RecordingPayment(), new RecordingInventory(), new RecordingShipping());
        testEnv.start();
        client = testEnv.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Test
    void happyPath_runsThreeStepsInOrder_andCompensatesNothing() {
        OrderRequest request = order("ok-1", "visa", List.of("sku-a", "sku-b"));
        OrderFulfillmentWorkflow workflow = newWorkflow(request);

        OrderResult result = workflow.process(request);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getOrderId()).isEqualTo("ok-1");
        assertThat(workflow.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(calls.all()).containsExactly("chargePayment", "reserveInventory", "createShipment");
        assertThat(gateway.isCharged(paymentIdOf("ok-1"))).isTrue();
        assertThat(inventory.isReserved("ok-1")).isTrue();
    }

    @Test
    void paymentFails_retriedThreeTimes_nothingToCompensate() {
        OrderRequest request = order("pay-fail-1", PaymentGateway.DECLINED_CARD, List.of("sku-a"));
        OrderFulfillmentWorkflow workflow = newWorkflow(request);

        OrderResult result = workflow.process(request);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getMessage()).contains("Card declined");
        assertThat(workflow.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(calls.all()).containsExactly("chargePayment", "chargePayment", "chargePayment");
    }

    @Test
    void inventoryFails_refundsPayment() {
        OrderRequest request = order("inv-fail-1", "visa", List.of("sku-a", InventoryActivityImpl.OUT_OF_STOCK_ITEM));
        OrderFulfillmentWorkflow workflow = newWorkflow(request);

        OrderResult result = workflow.process(request);

        String paymentId = paymentIdOf("inv-fail-1");
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getMessage()).contains("out of stock");
        assertThat(workflow.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(calls.all()).containsExactly(
                "chargePayment",
                "reserveInventory", "reserveInventory", "reserveInventory",
                "refundPayment:" + paymentId);
        assertThat(gateway.isCharged(paymentId)).isFalse();
    }

    @Test
    void shippingFails_releasesInventoryThenRefundsPayment() {
        OrderRequest request = order("carrier-down-1", "visa", List.of("sku-a"));
        OrderFulfillmentWorkflow workflow = newWorkflow(request);

        OrderResult result = workflow.process(request);

        String paymentId = paymentIdOf("carrier-down-1");
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getMessage()).contains("Carrier unavailable");
        assertThat(workflow.getStatus()).isEqualTo(OrderStatus.FAILED);
        // Compensations run in reverse order: release (step 2) before refund (step 1).
        assertThat(calls.all()).containsExactly(
                "chargePayment",
                "reserveInventory",
                "createShipment", "createShipment", "createShipment",
                "releaseInventory",
                "refundPayment:" + paymentId);
        assertThat(inventory.isReserved("carrier-down-1")).isFalse();
        assertThat(gateway.isCharged(paymentId)).isFalse();
    }

    @Test
    void getStatus_reflectsCurrentStepWhileRunning() throws Exception {
        shipmentGate = new CountDownLatch(1);
        OrderRequest request = order("slow-1", "visa", List.of("sku-a"));
        OrderFulfillmentWorkflow workflow = newWorkflow(request);

        WorkflowClient.start(workflow::process, request);
        awaitStatus(workflow, OrderStatus.CREATING_SHIPMENT);

        assertThat(calls.all()).containsExactly("chargePayment", "reserveInventory", "createShipment");

        shipmentGate.countDown();
        OrderResult result = WorkflowStub.fromTyped(workflow).getResult(OrderResult.class);
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(workflow.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    @Test
    void chargePayment_isIdempotentPerOrder() {
        OrderRequest request = order("idem-1", "visa", List.of("sku-a"));

        PaymentResult first = payment.chargePayment(request);
        PaymentResult second = payment.chargePayment(request);

        assertThat(second.getPaymentId()).isEqualTo(first.getPaymentId());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private OrderFulfillmentWorkflow newWorkflow(OrderRequest request) {
        return client.newWorkflowStub(OrderFulfillmentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("order-" + request.getOrderId())
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        .build());
    }

    private static OrderRequest order(String orderId, String paymentMethod, List<String> items) {
        return new OrderRequest(orderId, "customer@example.com", new BigDecimal("99.00"), paymentMethod, items);
    }

    private String paymentIdOf(String orderId) {
        return paymentRepository.findByOrderId(orderId).orElseThrow().toResult().getPaymentId();
    }

    private static void awaitStatus(OrderFulfillmentWorkflow workflow, OrderStatus expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        OrderStatus last = null;
        while (System.nanoTime() < deadline) {
            last = workflow.getStatus();
            if (last == expected) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("expected status " + expected + " but was " + last);
    }

    // ── recording wrappers around the real activity beans ────────────────────

    private class RecordingPayment implements PaymentActivity {
        @Override public PaymentResult chargePayment(OrderRequest request) {
            calls.add("chargePayment");
            return payment.chargePayment(request);
        }
        @Override public void refundPayment(String paymentId) {
            calls.add("refundPayment:" + paymentId);
            payment.refundPayment(paymentId);
        }
    }

    private class RecordingInventory implements InventoryActivity {
        @Override public void reserveInventory(OrderRequest request) {
            calls.add("reserveInventory");
            inventory.reserveInventory(request);
        }
        @Override public void releaseInventory(OrderRequest request) {
            calls.add("releaseInventory");
            inventory.releaseInventory(request);
        }
    }

    private class RecordingShipping implements ShippingActivity {
        @Override public void createShipment(OrderRequest request) {
            calls.add("createShipment");
            CountDownLatch gate = shipmentGate;
            if (gate != null) {
                try {
                    gate.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            shipping.createShipment(request);
        }
    }
}

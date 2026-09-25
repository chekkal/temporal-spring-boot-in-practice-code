package com.example.course.kata27;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
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
 * Five-step saga against Temporal's in-process test server (time skipping on, so the retry
 * back-offs cost no wall-clock time). Every activity attempt is recorded in order.
 */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class FullOrderSagaWorkflowTest {

    private static final List<String> ALL_STEPS = List.of(
            "VALIDATING", "CHARGING_PAYMENT", "RESERVING_INVENTORY", "CREATING_SHIPMENT", "SENDING_NOTIFICATION");

    private TestWorkflowEnvironment testEnv;
    private WorkflowClient client;

    private final CallLog calls = new CallLog();
    private final OrderActivitiesImpl activities = new OrderActivitiesImpl();
    private final List<List<String>> alertedErrors = new CopyOnWriteArrayList<>();
    private volatile String lastPaymentId;
    private volatile String lastTrackingId;
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
                FullOrderSagaWorkflowImpl.class);
        worker.registerActivitiesImplementations(new RecordingActivities());
        testEnv.start();
        client = testEnv.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Test
    void happyPath_allFiveSteps_noCompensation() {
        OrderRequest request = order("ok-1", "visa", List.of("sku-a"));
        FullOrderSagaWorkflow workflow = newWorkflow(request);

        OrderResult result = workflow.process(request);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(calls.all()).containsExactly(
                "validateOrder", "chargePayment", "reserveInventory", "createShipment", "sendNotification");
        SagaStatus status = workflow.getStatus();
        assertThat(status.currentStep()).isEqualTo("COMPLETED");
        assertThat(status.completedSteps()).containsExactlyElementsOf(ALL_STEPS);
        assertThat(status.failureReason()).isNull();
    }

    @Test
    void validationFailure_isNotRetried_andThereIsNothingToCompensate() {
        OrderRequest request = order("invalid-1", "visa", List.of());   // no items
        FullOrderSagaWorkflow workflow = newWorkflow(request);

        OrderResult result = workflow.process(request);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(calls.all()).containsExactly("validateOrder");      // one attempt: DoNotRetry
        SagaStatus status = workflow.getStatus();
        assertThat(status.currentStep()).isEqualTo("COMPENSATION_COMPLETE");
        assertThat(status.completedSteps()).containsExactly("VALIDATING");
        assertThat(status.failureReason()).contains("Invalid order").contains("ValidationException");
    }

    @Test
    void paymentDeclined_isNotRetried_andValidationIsReversed() {
        OrderRequest request = order("declined-1", OrderActivitiesImpl.DECLINED_CARD, List.of("sku-a"));
        FullOrderSagaWorkflow workflow = newWorkflow(request);

        OrderResult result = workflow.process(request);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(result.getMessage()).contains("Payment declined");
        assertThat(calls.all()).containsExactly("validateOrder", "chargePayment", "logValidationReversal");
        SagaStatus status = workflow.getStatus();
        assertThat(status.currentStep()).isEqualTo("COMPENSATION_COMPLETE");
        assertThat(status.completedSteps()).containsExactly("VALIDATING", "CHARGING_PAYMENT");
        assertThat(status.failureReason()).contains("PaymentDeclinedException");
    }

    @Test
    void inventoryFailure_retriedThreeTimes_thenRefundAndValidationReversal() {
        OrderRequest request = order("stock-1", "visa", List.of(OrderActivitiesImpl.OUT_OF_STOCK_ITEM));
        FullOrderSagaWorkflow workflow = newWorkflow(request);

        workflow.process(request);

        assertThat(calls.all()).containsExactly(
                "validateOrder", "chargePayment",
                "reserveInventory", "reserveInventory", "reserveInventory",
                "refundPayment:" + lastPaymentId,
                "logValidationReversal");
        assertThat(workflow.getStatus().currentStep()).isEqualTo("COMPENSATION_COMPLETE");
        assertThat(activities.isCharged(lastPaymentId)).isFalse();
    }

    @Test
    void shippingFailure_compensatesInventoryPaymentValidation_inReverseOrder() {
        OrderRequest request = order("carrier-down-1", "visa", List.of("sku-a"));
        FullOrderSagaWorkflow workflow = newWorkflow(request);

        workflow.process(request);

        assertThat(calls.all()).containsExactly(
                "validateOrder", "chargePayment", "reserveInventory",
                "createShipment", "createShipment", "createShipment",
                "releaseInventory",
                "refundPayment:" + lastPaymentId,
                "logValidationReversal");
        SagaStatus status = workflow.getStatus();
        assertThat(status.currentStep()).isEqualTo("COMPENSATION_COMPLETE");
        assertThat(status.completedSteps()).containsExactly(
                "VALIDATING", "CHARGING_PAYMENT", "RESERVING_INVENTORY", "CREATING_SHIPMENT");
        assertThat(status.failureReason()).contains("Carrier unavailable");
        assertThat(activities.isReserved("carrier-down-1")).isFalse();
        assertThat(activities.isCharged(lastPaymentId)).isFalse();
    }

    @Test
    void notificationFailure_compensatesAllFourSteps_inReverseOrder() {
        OrderRequest request = order("email-down-1", "visa", List.of("sku-a"));
        FullOrderSagaWorkflow workflow = newWorkflow(request);

        OrderResult result = workflow.process(request);

        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(calls.all()).containsExactly(
                "validateOrder", "chargePayment", "reserveInventory", "createShipment",
                "sendNotification", "sendNotification", "sendNotification",
                "cancelShipment:" + lastTrackingId,
                "releaseInventory",
                "refundPayment:" + lastPaymentId,
                "logValidationReversal");
        assertThat(workflow.getStatus().currentStep()).isEqualTo("COMPENSATION_COMPLETE");
        assertThat(workflow.getStatus().completedSteps()).containsExactlyElementsOf(ALL_STEPS);
        assertThat(activities.hasShipment("email-down-1")).isFalse();
    }

    @Test
    void failingCompensation_isRetriedFiveTimes_othersStillRun_thenPartialAndOpsAlert() {
        OrderRequest request = order("carrier-down-refund-fails-1", "visa", List.of("sku-a"));
        FullOrderSagaWorkflow workflow = newWorkflow(request);

        OrderResult result = workflow.process(request);

        String refund = "refundPayment:" + lastPaymentId;
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(calls.all()).containsExactly(
                "validateOrder", "chargePayment", "reserveInventory",
                "createShipment", "createShipment", "createShipment",
                "releaseInventory",
                refund, refund, refund, refund, refund,          // compensationOpts: 5 attempts
                "logValidationReversal",                        // still attempted after the refund failed
                "alertOperations");
        assertThat(workflow.getStatus().currentStep()).isEqualTo("COMPENSATION_PARTIAL");
        assertThat(alertedErrors).hasSize(1);
        assertThat(alertedErrors.get(0)).singleElement().asString().contains("Refund service unavailable");
        assertThat(activities.isReserved("carrier-down-refund-fails-1")).isFalse();
        assertThat(activities.isCharged(lastPaymentId)).isTrue();          // needs manual resolution
    }

    @Test
    void getStatus_showsCurrentAndCompletedStepsWhileRunning() throws Exception {
        shipmentGate = new CountDownLatch(1);
        OrderRequest request = order("slow-1", "visa", List.of("sku-a"));
        FullOrderSagaWorkflow workflow = newWorkflow(request);

        WorkflowClient.start(workflow::process, request);
        SagaStatus midFlight = awaitStep(workflow, "CREATING_SHIPMENT");

        assertThat(midFlight.completedSteps()).containsExactly(
                "VALIDATING", "CHARGING_PAYMENT", "RESERVING_INVENTORY", "CREATING_SHIPMENT");
        assertThat(midFlight.failureReason()).isNull();

        shipmentGate.countDown();
        OrderResult result = WorkflowStub.fromTyped(workflow).getResult(OrderResult.class);
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(workflow.getStatus().currentStep()).isEqualTo("COMPLETED");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private FullOrderSagaWorkflow newWorkflow(OrderRequest request) {
        return client.newWorkflowStub(FullOrderSagaWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("order-" + request.getOrderId())
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        .build());
    }

    private static OrderRequest order(String orderId, String paymentMethod, List<String> items) {
        return new OrderRequest(orderId, "customer@example.com", new BigDecimal("149.00"), paymentMethod, items);
    }

    private static SagaStatus awaitStep(FullOrderSagaWorkflow workflow, String step) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        SagaStatus last = null;
        while (System.nanoTime() < deadline) {
            last = workflow.getStatus();
            if (step.equals(last.currentStep())) {
                return last;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("expected currentStep " + step + " but status was " + last);
    }

    /** Wraps the real in-memory activity bean and records each attempt. */
    private class RecordingActivities implements OrderActivities {
        @Override public void validateOrder(OrderRequest r) { calls.add("validateOrder"); activities.validateOrder(r); }
        @Override public void logValidationReversal(OrderRequest r) { calls.add("logValidationReversal"); activities.logValidationReversal(r); }
        @Override public PaymentResult chargePayment(OrderRequest r) {
            calls.add("chargePayment");
            PaymentResult result = activities.chargePayment(r);
            lastPaymentId = result.getPaymentId();
            return result;
        }
        @Override public void refundPayment(String paymentId) { calls.add("refundPayment:" + paymentId); activities.refundPayment(paymentId); }
        @Override public void reserveInventory(OrderRequest r) { calls.add("reserveInventory"); activities.reserveInventory(r); }
        @Override public void releaseInventory(OrderRequest r) { calls.add("releaseInventory"); activities.releaseInventory(r); }
        @Override public ShipmentResult createShipment(OrderRequest r) {
            calls.add("createShipment");
            CountDownLatch gate = shipmentGate;
            if (gate != null) {
                try {
                    gate.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            ShipmentResult result = activities.createShipment(r);
            lastTrackingId = result.getTrackingId();
            return result;
        }
        @Override public void cancelShipment(String trackingId) { calls.add("cancelShipment:" + trackingId); activities.cancelShipment(trackingId); }
        @Override public void sendNotification(OrderRequest r) { calls.add("sendNotification"); activities.sendNotification(r); }
        @Override public void alertOperations(OrderRequest r, List<String> errors) {
            calls.add("alertOperations");
            alertedErrors.add(new ArrayList<>(errors));
            activities.alertOperations(r, errors);
        }
    }
}

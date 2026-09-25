package com.example.course.kata28;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
 * Two-phase approval wait against Temporal's in-process test server. testEnv.sleep(...) skips
 * hours of workflow time instantly, so the 24h escalation and 48h auto-reject run in milliseconds.
 */
@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
class ApprovalWorkflowTest {

    private static final BigDecimal BIG = new BigDecimal("7500.00");
    private static final BigDecimal SMALL = new BigDecimal("4999.99");

    private TestWorkflowEnvironment testEnv;
    private WorkflowClient client;

    private final CallLog calls = new CallLog();
    private final ApprovalActivitiesImpl approvals = new ApprovalActivitiesImpl();
    private final PaymentRepository paymentRepository = new PaymentRepository();
    private final PaymentActivityImpl payment = new PaymentActivityImpl(new PaymentGateway(), paymentRepository);
    private final InventoryActivityImpl inventory = new InventoryActivityImpl();
    private final ShippingActivityImpl shipping = new ShippingActivityImpl();

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
                ApprovalWorkflowImpl.class);
        worker.registerActivitiesImplementations(new RecordingApprovals(), new RecordingPayment(),
                new RecordingInventory(), new RecordingShipping());
        testEnv.start();
        client = testEnv.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        testEnv.close();
    }

    @Test
    void orderUnder5000_isFulfilledWithoutApproval() {
        OrderRequest request = order("small-1", SMALL, List.of("sku-a"));
        ApprovalWorkflow workflow = newWorkflow(request);

        OrderResult result = workflow.processWithApproval(request);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(calls.all()).containsExactly("chargePayment", "reserveInventory", "createShipment");
        ApprovalStatus status = workflow.getApprovalStatus();
        assertThat(status.orderId()).isEqualTo("small-1");
        assertThat(status.phase()).isEqualTo("COMPLETED");
        assertThat(status.waitingSince()).isNull();
        assertThat(status.escalated()).isFalse();
        assertThat(status.decision()).isNull();
    }

    @Test
    void orderOfExactly5000_doesNotNeedApproval() {
        OrderRequest request = order("boundary-1", new BigDecimal("5000.00"), List.of("sku-a"));
        ApprovalWorkflow workflow = newWorkflow(request);

        OrderResult result = workflow.processWithApproval(request);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(calls.all()).containsExactly("chargePayment", "reserveInventory", "createShipment");
    }

    @Test
    void approvedWithin24h_isFulfilledWithoutEscalation() {
        OrderRequest request = order("big-1", BIG, List.of("sku-a"));
        ApprovalWorkflow workflow = newWorkflow(request);
        Instant startedAt = Instant.ofEpochMilli(testEnv.currentTimeMillis());

        WorkflowClient.start(workflow::processWithApproval, request);
        testEnv.sleep(Duration.ofHours(2));

        ApprovalStatus waiting = workflow.getApprovalStatus();
        assertThat(waiting.orderId()).isEqualTo("big-1");
        assertThat(waiting.phase()).isEqualTo("WAITING_APPROVAL");
        assertThat(waiting.escalated()).isFalse();
        assertThat(waiting.waitingSince()).isNotNull();
        assertThat(Duration.between(startedAt, waiting.waitingSince()).abs()).isLessThan(Duration.ofMinutes(1));
        assertThat(calls.all()).isEmpty();                       // nothing charged while waiting

        workflow.approveOrder(new ApprovalDecision(true, "alice", "Known customer"));
        OrderResult result = resultOf(workflow);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(calls.all()).containsExactly(
                "recordApproval:alice", "chargePayment", "reserveInventory", "createShipment");
        ApprovalStatus done = workflow.getApprovalStatus();
        assertThat(done.phase()).isEqualTo("COMPLETED");
        assertThat(done.escalated()).isFalse();
        assertThat(done.decision()).isEqualTo(new ApprovalDecision(true, "alice", "Known customer"));
    }

    @Test
    void noAnswerFor24h_escalates_thenApprovalStillWorks() {
        OrderRequest request = order("big-2", BIG, List.of("sku-a"));
        ApprovalWorkflow workflow = newWorkflow(request);

        WorkflowClient.start(workflow::processWithApproval, request);
        testEnv.sleep(Duration.ofHours(23));
        assertThat(workflow.getApprovalStatus().phase()).isEqualTo("WAITING_APPROVAL");
        assertThat(calls.all()).isEmpty();

        testEnv.sleep(Duration.ofHours(2));                      // now 25h
        ApprovalStatus escalated = workflow.getApprovalStatus();
        assertThat(escalated.phase()).isEqualTo("ESCALATED");
        assertThat(escalated.escalated()).isTrue();
        assertThat(calls.all()).containsExactly("notifyEscalation");

        workflow.approveOrder(new ApprovalDecision(true, "bob-director", "Approved after escalation"));
        OrderResult result = resultOf(workflow);

        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(calls.all()).containsExactly(
                "notifyEscalation", "recordApproval:bob-director",
                "chargePayment", "reserveInventory", "createShipment");
        ApprovalStatus done = workflow.getApprovalStatus();
        assertThat(done.phase()).isEqualTo("COMPLETED");
        assertThat(done.escalated()).isTrue();
        assertThat(done.decision().approvedBy()).isEqualTo("bob-director");
    }

    @Test
    void rejectedByManager_notifiesRejection_andChargesNothing() {
        OrderRequest request = order("big-3", BIG, List.of("sku-a"));
        ApprovalWorkflow workflow = newWorkflow(request);

        WorkflowClient.start(workflow::processWithApproval, request);
        testEnv.sleep(Duration.ofHours(1));
        workflow.approveOrder(new ApprovalDecision(false, "alice", "Budget exceeded"));
        OrderResult result = resultOf(workflow);

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getMessage()).isEqualTo("Budget exceeded");
        assertThat(calls.all()).containsExactly("notifyRejection:Budget exceeded");
        assertThat(workflow.getApprovalStatus().phase()).isEqualTo("REJECTED");
        assertThat(paymentRepository.findByOrderId("big-3")).isEmpty();
    }

    @Test
    void noAnswerFor48h_isRejectedAutomatically() {
        OrderRequest request = order("big-4", BIG, List.of("sku-a"));
        ApprovalWorkflow workflow = newWorkflow(request);
        long startedAt = testEnv.currentTimeMillis();

        WorkflowClient.start(workflow::processWithApproval, request);
        testEnv.sleep(Duration.ofHours(47));
        assertThat(workflow.getApprovalStatus().phase()).isEqualTo("ESCALATED");   // still waiting

        OrderResult result = resultOf(workflow);                                     // skips the last hour

        assertThat(result.getStatus()).isEqualTo("REJECTED");
        assertThat(result.getMessage()).isEqualTo("Approval timeout");
        assertThat(calls.all()).containsExactly("notifyEscalation", "notifyRejection:48h timeout");
        ApprovalStatus status = workflow.getApprovalStatus();
        assertThat(status.phase()).isEqualTo("REJECTED_TIMEOUT");
        assertThat(status.escalated()).isTrue();
        assertThat(status.decision()).isNull();
        assertThat(Duration.ofMillis(testEnv.currentTimeMillis() - startedAt)).isGreaterThanOrEqualTo(Duration.ofHours(48));
    }

    @Test
    void approvedButFulfillmentFails_compensatesAndReportsFailure() {
        OrderRequest request = order("big-5", BIG, List.of("sku-a", InventoryActivityImpl.OUT_OF_STOCK_ITEM));
        ApprovalWorkflow workflow = newWorkflow(request);

        WorkflowClient.start(workflow::processWithApproval, request);
        workflow.approveOrder(new ApprovalDecision(true, "alice", "ok"));
        OrderResult result = resultOf(workflow);

        String paymentId = paymentRepository.findByOrderId("big-5").orElseThrow().toResult().getPaymentId();
        assertThat(result.getStatus()).isEqualTo("FAILED");
        assertThat(calls.all()).containsExactly(
                "recordApproval:alice", "chargePayment",
                "reserveInventory", "reserveInventory", "reserveInventory",
                "refundPayment:" + paymentId);
        assertThat(workflow.getApprovalStatus().phase()).isEqualTo("FULFILLMENT_FAILED");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private ApprovalWorkflow newWorkflow(OrderRequest request) {
        return client.newWorkflowStub(ApprovalWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("order-" + request.getOrderId())
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        .build());
    }

    private static OrderResult resultOf(ApprovalWorkflow workflow) {
        return WorkflowStub.fromTyped(workflow).getResult(OrderResult.class);
    }

    private static OrderRequest order(String orderId, BigDecimal amount, List<String> items) {
        return new OrderRequest(orderId, "customer@example.com", amount, "visa", items);
    }

    // ── recording wrappers around the real activity beans ────────────────────

    private class RecordingApprovals implements ApprovalActivities {
        @Override public void notifyEscalation(OrderRequest r) { calls.add("notifyEscalation"); approvals.notifyEscalation(r); }
        @Override public void notifyRejection(OrderRequest r, String reason) {
            calls.add("notifyRejection:" + reason);
            approvals.notifyRejection(r, reason);
        }
        @Override public void recordApproval(ApprovalDecision d) {
            calls.add("recordApproval:" + d.approvedBy());
            approvals.recordApproval(d);
        }
    }

    private class RecordingPayment implements PaymentActivity {
        @Override public PaymentResult chargePayment(OrderRequest r) { calls.add("chargePayment"); return payment.chargePayment(r); }
        @Override public void refundPayment(String id) { calls.add("refundPayment:" + id); payment.refundPayment(id); }
    }

    private class RecordingInventory implements InventoryActivity {
        @Override public void reserveInventory(OrderRequest r) { calls.add("reserveInventory"); inventory.reserveInventory(r); }
        @Override public void releaseInventory(OrderRequest r) { calls.add("releaseInventory"); inventory.releaseInventory(r); }
    }

    private class RecordingShipping implements ShippingActivity {
        @Override public void createShipment(OrderRequest r) { calls.add("createShipment"); shipping.createShipment(r); }
    }
}

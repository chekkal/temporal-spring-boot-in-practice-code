package com.example.course.l17;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Slide 7: skip 73 hours of workflow time in a test that runs in milliseconds. */
class ApprovalWorkflowTimeSkippingTest {

    @RegisterExtension
    static TestWorkflowExtension testWorkflow = TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(ApprovalWorkflowImpl.class)
            .build();

    @Test
    void orderWorkflow_timesOutAfter72Hours(TestWorkflowEnvironment testEnv, WorkflowClient client, Worker worker)
            throws Exception {
        ApprovalWorkflow workflow = client.newWorkflowStub(ApprovalWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue(worker.getTaskQueue()).build());
        long wallClockStart = System.nanoTime();

        // Start workflow asynchronously
        WorkflowClient.start(workflow::startApproval,
            new ApprovalRequest("order-999", 50000));

        // Skip 73 hours instantly — no actual waiting!
        testEnv.sleep(Duration.ofHours(73));

        // Workflow should have timed out
        String result = WorkflowStub.fromTyped(workflow)
            .getResult(1, TimeUnit.SECONDS, String.class);

        assertEquals("TIMED_OUT", result);
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - wallClockStart);
        System.out.printf("73 hours of workflow time took %d ms of real time%n", elapsedMillis);
        assertThat(elapsedMillis).isLessThan(5_000);
    }

    @Test
    void approvalJustBeforeTheDeadline_stillCounts(TestWorkflowEnvironment testEnv, WorkflowClient client, Worker worker)
            throws Exception {
        ApprovalWorkflow workflow = client.newWorkflowStub(ApprovalWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue(worker.getTaskQueue()).build());
        WorkflowClient.start(workflow::startApproval, new ApprovalRequest("order-998", 50000));

        testEnv.sleep(Duration.ofHours(71));
        workflow.approve("manager-2", "Made it in time");

        assertEquals("APPROVED", WorkflowStub.fromTyped(workflow).getResult(1, TimeUnit.SECONDS, String.class));
    }
}

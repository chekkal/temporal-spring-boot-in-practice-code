package com.example.course.l17;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.TimeUnit;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Slide 6: start the workflow asynchronously, signal it, then wait for the result. */
class ApprovalWorkflowSignalTest {

    @RegisterExtension
    static TestWorkflowExtension testWorkflow = TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(ApprovalWorkflowImpl.class)
            .build();

    @Test
    void approvalWorkflow_completesAfterSignal(WorkflowClient client, Worker worker) throws Exception {
        ApprovalWorkflow workflow = client.newWorkflowStub(
            ApprovalWorkflow.class,
            WorkflowOptions.newBuilder()
                .setTaskQueue(worker.getTaskQueue())
                .build());

        // Start workflow asynchronously
        WorkflowClient.start(workflow::startApproval,
            new ApprovalRequest("order-500", 15000));

        // Send approval signal
        workflow.approve("manager-1", "Approved for Q4 budget");

        // Wait for completion
        String result = WorkflowStub.fromTyped(workflow)
            .getResult(5, TimeUnit.SECONDS, String.class);

        assertEquals("APPROVED", result);
    }

    @Test
    void approvalWorkflow_rejectedBySignal(WorkflowClient client, Worker worker) throws Exception {
        ApprovalWorkflow workflow = client.newWorkflowStub(ApprovalWorkflow.class,
            WorkflowOptions.newBuilder().setTaskQueue(worker.getTaskQueue()).build());

        WorkflowClient.start(workflow::startApproval, new ApprovalRequest("order-501", 90000));
        workflow.reject("manager-1", "Over budget");

        assertEquals("REJECTED", WorkflowStub.fromTyped(workflow).getResult(5, TimeUnit.SECONDS, String.class));
    }
}

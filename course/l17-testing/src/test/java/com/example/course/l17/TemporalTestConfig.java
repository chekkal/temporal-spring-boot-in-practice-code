package com.example.course.l17;

import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * Replaces TemporalConfig (which is {@code @Profile("!test")}) in the Spring Boot test:
 * the WorkflowClient and Worker come from an in-process TestWorkflowEnvironment, and the
 * worker runs the real, Spring-wired activity beans. No Temporal server is needed.
 */
@TestConfiguration
public class TemporalTestConfig {

    @Bean(destroyMethod = "close")
    public TestWorkflowEnvironment testWorkflowEnvironment() {
        return TestWorkflowEnvironment.newInstance();
    }

    @Bean
    public WorkflowClient workflowClient(TestWorkflowEnvironment testEnv) {
        return testEnv.getWorkflowClient();
    }

    @Bean
    public Worker orderWorker(TestWorkflowEnvironment testEnv,
                              PaymentActivity paymentActivity,
                              InventoryActivity inventoryActivity,
                              ShippingActivity shippingActivity) {
        Worker worker = testEnv.newWorker(TemporalConfig.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(OrderWorkflowImpl.class, ApprovalWorkflowImpl.class);
        worker.registerActivitiesImplementations(paymentActivity, inventoryActivity, shippingActivity);
        testEnv.start();
        return worker;
    }
}

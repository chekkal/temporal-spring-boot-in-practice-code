package com.example.kata04;

import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {

    public static final String TASK_QUEUE = "kata-04";
    public static final String CRON_WORKFLOW_ID = "nightly-reconciliation";
    private static final Logger log = LoggerFactory.getLogger(TemporalConfig.class);

    @Bean
    public WorkflowServiceStubs serviceStubs(@Value("${spring.temporal.connection.target:127.0.0.1:7233}") String t) {
        return WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder().setTarget(t).build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs s, @Value("${spring.temporal.namespace:default}") String ns) {
        return WorkflowClient.newInstance(s, WorkflowClientOptions.newBuilder().setNamespace(ns).build());
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient c) {
        return WorkerFactory.newInstance(c);
    }

    @Bean
    public Worker katasWorker(WorkerFactory workerFactory, ReconciliationActivities activities) {
        Worker worker = workerFactory.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(ReconciliationWorkflowImpl.class);
        worker.registerActivitiesImplementations(activities);
        return worker;
    }

    /**
     * Starts the worker, then idempotently schedules the cron workflow. If the cron
     * is already scheduled (e.g. on app restart) we swallow the
     * {@code WorkflowExecutionAlreadyStarted} exception.
     */
    @Bean
    public ApplicationRunner startAndSchedule(WorkerFactory workerFactory,
                                              Worker katasWorker,
                                              WorkflowClient client) {
        return args -> {
            workerFactory.start();
            try {
                ReconciliationWorkflow cron = client.newWorkflowStub(
                        ReconciliationWorkflow.class,
                        WorkflowOptions.newBuilder()
                                .setWorkflowId(CRON_WORKFLOW_ID)
                                .setTaskQueue(TASK_QUEUE)
                                .setCronSchedule("0 0 * * *")  // daily at midnight UTC
                                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE)
                                .build());
                WorkflowClient.start(cron::run, 0);
                log.info("Nightly reconciliation scheduled.");
            } catch (Exception e) {
                log.info("Cron workflow already scheduled (this is expected on restart): {}", e.getMessage());
            }
        };
    }
}

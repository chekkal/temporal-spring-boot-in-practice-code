package com.example.course.kata28;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TemporalConfig {

    public static final String TASK_QUEUE = "course-kata-28";

    @Bean
    public WorkflowServiceStubs serviceStubs(
            @Value("${spring.temporal.connection.target:127.0.0.1:7233}") String target) {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder().setTarget(target).build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs,
                                         @Value("${spring.temporal.namespace:default}") String namespace) {
        return WorkflowClient.newInstance(stubs,
                WorkflowClientOptions.newBuilder().setNamespace(namespace).build());
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient client) {
        return WorkerFactory.newInstance(client);
    }

    @Bean
    public Worker kataWorker(WorkerFactory workerFactory,
                             ApprovalActivities approvalActivities,
                             PaymentActivity paymentActivity,
                             InventoryActivity inventoryActivity,
                             ShippingActivity shippingActivity) {
        Worker worker = workerFactory.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(ApprovalWorkflowImpl.class);
        worker.registerActivitiesImplementations(approvalActivities, paymentActivity, inventoryActivity, shippingActivity);
        return worker;
    }

    @Bean
    public ApplicationRunner startWorkerFactory(WorkerFactory workerFactory, Worker kataWorker) {
        return args -> workerFactory.start();
    }
}

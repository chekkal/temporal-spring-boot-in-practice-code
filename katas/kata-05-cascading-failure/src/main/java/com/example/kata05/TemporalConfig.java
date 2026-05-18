package com.example.kata05;

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

    public static final String TASK_QUEUE = "kata-05";

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
    public Worker katasWorker(WorkerFactory workerFactory,
                              PrimaryPaymentActivity primary,
                              SecondaryPaymentActivity secondary) {
        Worker worker = workerFactory.newWorker(TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(PaymentWorkflowImpl.class);
        worker.registerActivitiesImplementations(primary, secondary);
        return worker;
    }

    @Bean
    public ApplicationRunner startWorkerFactory(WorkerFactory workerFactory, Worker katasWorker) {
        return args -> workerFactory.start();
    }
}

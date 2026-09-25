package com.example.course.l15.shipping;

import com.example.course.l15.api.ShippingActivity;
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

    /** Polls only the "shipping-service" queue and knows only ShippingActivity. No workflow is registered here. */
    @Bean
    public Worker shippingWorker(WorkerFactory factory,
                                 ShippingActivity shippingActivity) {
        Worker worker = factory.newWorker("shipping-service");
        worker.registerActivitiesImplementations(shippingActivity);
        return worker;
    }

    @Bean
    public ApplicationRunner startWorkerFactory(WorkerFactory workerFactory, Worker shippingWorker) {
        return args -> workerFactory.start();
    }
}

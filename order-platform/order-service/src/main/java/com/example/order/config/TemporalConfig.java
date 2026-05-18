package com.example.order.config;

import com.example.order.api.activity.InventoryActivity;
import com.example.order.api.activity.NotificationActivity;
import com.example.order.api.activity.PaymentActivity;
import com.example.order.api.activity.ShippingActivity;
import com.example.order.workflow.OrderApprovalWorkflowImpl;
import com.example.order.workflow.OrderFulfillmentWorkflowImpl;
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

/**
 * Temporal wiring as shown in Chapter 25 (End-to-End Reference Application).
 *
 * Plain @Configuration — no auto-magic — so the book reader can follow each bean's role.
 *
 * The Worker is itself a @Bean: Spring resolves its dependencies (WorkerFactory + the
 * four activity beans) lazily through the method parameters, which is the idiomatic way
 * to avoid a circular reference with the WorkerFactory bean that this same class also
 * produces. The separate ApplicationRunner kicks the WorkerFactory once the Worker is
 * fully registered, so polling only begins after registration completes.
 */
@Configuration
public class TemporalConfig {

    public static final String ORDER_TASK_QUEUE = "order-fulfillment";

    @Bean
    public WorkflowServiceStubs serviceStubs(
            @Value("${spring.temporal.connection.target}") String target) {
        return WorkflowServiceStubs.newServiceStubs(
                WorkflowServiceStubsOptions.newBuilder()
                        .setTarget(target)
                        .build());
    }

    @Bean
    public WorkflowClient workflowClient(WorkflowServiceStubs stubs,
                                         @Value("${spring.temporal.namespace}") String namespace) {
        return WorkflowClient.newInstance(stubs,
                WorkflowClientOptions.newBuilder()
                        .setNamespace(namespace)
                        .build());
    }

    @Bean
    public WorkerFactory workerFactory(WorkflowClient client) {
        return WorkerFactory.newInstance(client);
    }

    @Bean
    public Worker orderWorker(WorkerFactory workerFactory,
                              PaymentActivity paymentActivity,
                              InventoryActivity inventoryActivity,
                              ShippingActivity shippingActivity,
                              NotificationActivity notificationActivity) {
        Worker worker = workerFactory.newWorker(ORDER_TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(
                OrderFulfillmentWorkflowImpl.class,
                OrderApprovalWorkflowImpl.class);
        worker.registerActivitiesImplementations(
                paymentActivity, inventoryActivity, shippingActivity, notificationActivity);
        return worker;
    }

    /**
     * Starts the WorkerFactory once Spring has built every singleton (including the
     * orderWorker bean above). Calling start() before registration would leave the
     * worker polling an empty task queue.
     */
    @Bean
    public ApplicationRunner startWorkerFactory(WorkerFactory workerFactory, Worker orderWorker) {
        return args -> workerFactory.start();
    }
}

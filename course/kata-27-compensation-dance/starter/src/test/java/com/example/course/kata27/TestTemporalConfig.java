package com.example.course.kata27;

import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.WorkerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Points the application at Temporal's in-process test server instead of 127.0.0.1:7233.
 *
 * TemporalConfig still creates its own WorkflowServiceStubs/WorkflowClient (lazily, no connection
 * is attempted), but the @Primary beans below win every injection: the controller talks to the
 * test environment's client, and TemporalConfig's Worker and ApplicationRunner use the test
 * environment's WorkerFactory. So the production worker registration is what gets exercised.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestTemporalConfig {

    @Bean(destroyMethod = "close")
    public TestWorkflowEnvironment testWorkflowEnvironment() {
        return TestWorkflowEnvironment.newInstance();
    }

    @Bean(destroyMethod = "")
    @Primary
    public WorkflowClient testWorkflowClient(TestWorkflowEnvironment testEnv) {
        return testEnv.getWorkflowClient();
    }

    @Bean(destroyMethod = "")
    @Primary
    public WorkerFactory testWorkerFactory(TestWorkflowEnvironment testEnv) {
        return testEnv.getWorkerFactory();
    }
}

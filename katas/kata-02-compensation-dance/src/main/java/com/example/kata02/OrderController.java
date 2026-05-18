package com.example.kata02;

import java.util.Map;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final WorkflowClient client;

    public OrderController(WorkflowClient client) { this.client = client; }

    @PostMapping("/{orderId}")
    public ResponseEntity<Map<String, String>> place(@PathVariable String orderId) {
        OrderSagaWorkflow workflow = client.newWorkflowStub(OrderSagaWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(orderId)
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        .build());
        WorkflowClient.start(workflow::fulfill, orderId);
        return ResponseEntity.accepted().body(Map.of("orderId", orderId));
    }

    @GetMapping("/{orderId}/status")
    public ResponseEntity<Map<String, String>> status(@PathVariable String orderId) {
        OrderSagaWorkflow workflow = client.newWorkflowStub(OrderSagaWorkflow.class, orderId);
        return ResponseEntity.ok(Map.of("orderId", orderId, "status", workflow.getStatus()));
    }
}

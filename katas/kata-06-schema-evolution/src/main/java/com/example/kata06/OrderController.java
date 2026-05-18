package com.example.kata06;

import java.util.Map;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final WorkflowClient client;
    public OrderController(WorkflowClient client) { this.client = client; }

    @PostMapping
    public ResponseEntity<Map<String, String>> place(@RequestParam String orderId, @RequestParam double amount) {
        OrderWorkflow w = client.newWorkflowStub(OrderWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("evo-" + orderId)
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        .build());
        String tracking = w.run(orderId, amount);
        return ResponseEntity.ok(Map.of("tracking", tracking));
    }
}

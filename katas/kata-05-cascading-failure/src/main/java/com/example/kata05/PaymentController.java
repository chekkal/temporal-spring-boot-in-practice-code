package com.example.kata05;

import java.util.Map;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pay")
public class PaymentController {
    private final WorkflowClient client;
    public PaymentController(WorkflowClient client) { this.client = client; }

    @PostMapping
    public ResponseEntity<Map<String, String>> pay(@RequestParam String orderId, @RequestParam double amount) {
        PaymentWorkflow w = client.newWorkflowStub(PaymentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("pay-" + orderId)
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        .build());
        String tx = w.pay(orderId, amount);
        return ResponseEntity.ok(Map.of("transactionId", tx));
    }
}

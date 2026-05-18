package com.example.kata03;

import java.util.Map;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class ApprovalController {

    private final WorkflowClient client;
    public ApprovalController(WorkflowClient client) { this.client = client; }

    @PostMapping("/{orderId}")
    public ResponseEntity<Map<String, String>> place(@PathVariable String orderId, @RequestParam double total) {
        HighValueOrderWorkflow w = client.newWorkflowStub(HighValueOrderWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId("hvo-" + orderId)
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        .build());
        WorkflowClient.start(w::processOrder, orderId, total);
        return ResponseEntity.accepted().body(Map.of("orderId", orderId, "total", String.valueOf(total)));
    }

    @PostMapping("/{orderId}/approve")
    public ResponseEntity<Void> approve(@PathVariable String orderId, @RequestParam String approver, @RequestParam(required = false, defaultValue = "") String comment) {
        client.newWorkflowStub(HighValueOrderWorkflow.class, "hvo-" + orderId).approve(approver, comment);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{orderId}/reject")
    public ResponseEntity<Void> reject(@PathVariable String orderId, @RequestParam String approver, @RequestParam(required = false, defaultValue = "") String reason) {
        client.newWorkflowStub(HighValueOrderWorkflow.class, "hvo-" + orderId).reject(approver, reason);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{orderId}/status")
    public ResponseEntity<Map<String, String>> status(@PathVariable String orderId) {
        return ResponseEntity.ok(Map.of("status",
                client.newWorkflowStub(HighValueOrderWorkflow.class, "hvo-" + orderId).getApprovalStatus()));
    }
}

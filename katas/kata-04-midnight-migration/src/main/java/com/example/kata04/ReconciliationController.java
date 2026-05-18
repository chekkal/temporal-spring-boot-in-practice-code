package com.example.kata04;

import java.util.Map;

import io.temporal.client.WorkflowClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reconciliation")
public class ReconciliationController {

    private final WorkflowClient client;
    public ReconciliationController(WorkflowClient client) { this.client = client; }

    @GetMapping("/progress")
    public ResponseEntity<Map<String, String>> progress() {
        ReconciliationWorkflow w = client.newWorkflowStub(ReconciliationWorkflow.class, TemporalConfig.CRON_WORKFLOW_ID);
        return ResponseEntity.ok(Map.of("progress", w.getProgress()));
    }
}

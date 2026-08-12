package com.example.agent;

import java.util.Map;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final WorkflowClient client;

    public AgentController(WorkflowClient client) {
        this.client = client;
    }

    /** Start a run. Returns immediately — the agent may take minutes or days. */
    @PostMapping
    public ResponseEntity<Map<String, String>> start(@RequestBody StartRequest req) {
        ResearchAgentWorkflow agent = client.newWorkflowStub(
                ResearchAgentWorkflow.class,
                WorkflowOptions.newBuilder()
                        // The run id IS the workflow id, so submitting the same run twice
                        // is a no-op rather than a second agent and a second bill.
                        .setWorkflowId(req.runId)
                        .setTaskQueue(TemporalConfig.TASK_QUEUE)
                        .build());

        WorkflowClient.start(agent::research, req.goal, req.budgetMinorUnits);

        return ResponseEntity.accepted().body(Map.of(
                "runId", req.runId,
                "status", "/api/agents/" + req.runId));
    }

    /**
     * Live status via a Temporal query. This reads workflow state directly and does
     * not touch a database, because there is no database — the workflow is the state.
     */
    @GetMapping("/{runId}")
    public ResearchAgentWorkflow.AgentStatus status(@PathVariable String runId) {
        return client.newWorkflowStub(ResearchAgentWorkflow.class, runId).getStatus();
    }

    /** Answer a pending budget request. The run resumes the moment this lands. */
    @PostMapping("/{runId}/budget")
    public ResponseEntity<Map<String, Object>> approveBudget(
            @PathVariable String runId, @RequestBody BudgetDecision decision) {

        client.newWorkflowStub(ResearchAgentWorkflow.class, runId)
                .approveBudget(decision.approved, decision.newBudgetMinorUnits);

        return ResponseEntity.ok(Map.of(
                "runId", runId,
                "approved", decision.approved));
    }

    public static class StartRequest {
        public String runId;
        public String goal;
        public long budgetMinorUnits = 200;
    }

    public static class BudgetDecision {
        public boolean approved;
        public long newBudgetMinorUnits;
    }
}
